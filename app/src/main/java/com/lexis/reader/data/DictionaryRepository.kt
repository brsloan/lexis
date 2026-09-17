package com.lexis.reader.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lexis.reader.core.Abbreviations
import com.lexis.reader.core.Article
import com.lexis.reader.core.DictReader
import com.lexis.reader.core.DictZipReader
import com.lexis.reader.core.EntryData
import com.lexis.reader.core.IdxParser
import com.lexis.reader.core.Ifo
import com.lexis.reader.core.IndexEntry
import com.lexis.reader.core.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

data class DictionaryInfo(
    val id: Long,
    val name: String,
    val dir: String,
    val dictFile: String,
    val sameTypeSequence: String?,
    val wordCount: Int,
    val sortOrder: Int,
)

data class LookupHit(val dict: DictionaryInfo, val word: String, val offset: Long, val length: Int, val isSynonym: Boolean)

data class Suggestion(val word: String, val dictionaries: List<String>)

data class ImportProgress(val message: String, val fraction: Float?)

class ImportException(message: String) : IOException(message)

class DictionaryRepository(private val context: Context, private val dbHelper: AppDatabase) {

    private val _dictionaries = MutableStateFlow<List<DictionaryInfo>>(emptyList())
    val dictionaries: StateFlow<List<DictionaryInfo>> = _dictionaries

    private val readers = HashMap<Long, DictReader>()

    init {
        refresh()
    }

    fun refresh() {
        val db = dbHelper.readableDatabase
        val out = ArrayList<DictionaryInfo>()
        db.query("dictionaries", null, null, null, null, null, "sort_order ASC, id ASC").use { c ->
            while (c.moveToNext()) {
                out.add(
                    DictionaryInfo(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        dir = c.getString(c.getColumnIndexOrThrow("dir")),
                        dictFile = c.getString(c.getColumnIndexOrThrow("dict_file")),
                        sameTypeSequence = c.getString(c.getColumnIndexOrThrow("sts")),
                        wordCount = c.getInt(c.getColumnIndexOrThrow("word_count")),
                        sortOrder = c.getInt(c.getColumnIndexOrThrow("sort_order")),
                    )
                )
            }
        }
        _dictionaries.value = out
    }

    private fun dictById(id: Long): DictionaryInfo? = _dictionaries.value.firstOrNull { it.id == id }

    @Synchronized
    private fun reader(d: DictionaryInfo): DictReader =
        readers.getOrPut(d.id) { DictZipReader.open(File(d.dir, d.dictFile)) }

    // ---------------------------------------------------------------- lookup

    /** All index entries whose normalised form equals the query (falling back to the looser key). */
    suspend fun lookup(query: String): List<LookupHit> = withContext(Dispatchers.IO) {
        val norm = TextNormalizer.norm(query)
        if (norm.isEmpty()) return@withContext emptyList()
        var hits = queryHits("norm = ?", arrayOf(norm))
        if (hits.isEmpty()) {
            val key = TextNormalizer.key(query)
            if (key.isNotEmpty()) hits = queryHits("key = ?", arrayOf(key))
        }
        val dictOrder = _dictionaries.value.withIndex().associate { it.value.id to it.index }
        hits.distinctBy { Triple(it.dict.id, it.offset, it.length) }
            .sortedWith(compareBy({ dictOrder[it.dict.id] ?: 99 }, { it.isSynonym }, { it.word }))
    }

    private fun queryHits(where: String, args: Array<String>): List<LookupHit> {
        val db = dbHelper.readableDatabase
        val out = ArrayList<LookupHit>()
        db.query("words", arrayOf("dict_id", "word", "data_offset", "data_length", "is_syn"), where, args, null, null, null, "200").use { c ->
            while (c.moveToNext()) {
                val d = dictById(c.getLong(0)) ?: continue
                out.add(LookupHit(d, c.getString(1), c.getLong(2), c.getInt(3), c.getInt(4) != 0))
            }
        }
        return out
    }

    /** Prefix suggestions for the search box, de-duplicated by headword. */
    suspend fun suggest(prefix: String, limit: Int = 60): List<Suggestion> = withContext(Dispatchers.IO) {
        val norm = TextNormalizer.norm(prefix)
        if (norm.isEmpty()) return@withContext emptyList()
        val db = dbHelper.readableDatabase
        val names = _dictionaries.value.associate { it.id to it.name }
        val ordered = LinkedHashMap<String, LinkedHashSet<String>>()

        fun collect(sql: String, args: Array<String>) {
            db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) {
                    val w = c.getString(0)
                    val dn = names[c.getLong(1)] ?: continue
                    ordered.getOrPut(w) { LinkedHashSet() }.add(dn)
                    if (ordered.size >= limit) break
                }
            }
        }
        collect(
            "SELECT word, dict_id FROM words WHERE norm >= ? AND norm < ? ORDER BY norm, is_syn, word LIMIT ?",
            arrayOf(norm, norm + "￿", (limit * 4).toString()),
        )
        if (ordered.size < 5) {
            val key = TextNormalizer.key(prefix)
            if (key.isNotEmpty()) collect(
                "SELECT word, dict_id FROM words WHERE key >= ? AND key < ? ORDER BY key, word LIMIT ?",
                arrayOf(key, key + "￿", (limit * 2).toString()),
            )
        }
        if (ordered.size < 5 && norm.length >= 3) {
            collect(
                "SELECT word, dict_id FROM words WHERE norm LIKE ? ESCAPE '\\' ORDER BY length(norm), norm LIMIT ?",
                arrayOf("%" + norm.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", "80"),
            )
        }
        ordered.entries.take(limit).map { Suggestion(it.key, it.value.toList()) }
    }

    /** Words adjacent to the given one in index order, for "browse nearby" navigation. */
    suspend fun neighbours(word: String, dictId: Long, before: Int, after: Int): List<String> = withContext(Dispatchers.IO) {
        val norm = TextNormalizer.norm(word)
        val db = dbHelper.readableDatabase
        val prev = ArrayList<String>()
        db.rawQuery(
            "SELECT DISTINCT word FROM words WHERE dict_id = ? AND norm < ? ORDER BY norm DESC LIMIT ?",
            arrayOf(dictId.toString(), norm, before.toString()),
        ).use { c -> while (c.moveToNext()) prev.add(c.getString(0)) }
        val next = ArrayList<String>()
        db.rawQuery(
            "SELECT DISTINCT word FROM words WHERE dict_id = ? AND norm > ? ORDER BY norm ASC LIMIT ?",
            arrayOf(dictId.toString(), norm, after.toString()),
        ).use { c -> while (c.moveToNext()) next.add(c.getString(0)) }
        prev.asReversed() + word + next
    }

    suspend fun article(hit: LookupHit): Article = withContext(Dispatchers.IO) {
        val raw = reader(hit.dict).read(hit.offset, hit.length)
        val blocks = EntryData.toBlocks(raw, hit.dict.sameTypeSequence)
        Article(hit.word, blocks, hit.dict.name, hit.dict.id)
    }

    // ---------------------------------------------------------------- abbreviations

    private val abbreviationCache = HashMap<String, String?>()

    /**
     * Full expansion of an abbreviation such as "Obs." or "Shakes.", from the dictionary's
     * own glossary entries, or null when there is none. Results are cached for the session.
     */
    suspend fun expandAbbreviation(abbr: String): String? = withContext(Dispatchers.IO) {
        val key = abbr.trim()
        if (key.isEmpty()) return@withContext null
        synchronized(abbreviationCache) { if (abbreviationCache.containsKey(key)) return@withContext abbreviationCache[key] }
        var found: String? = null
        for (hit in lookup(key)) {
            val raw = reader(hit.dict).read(hit.offset, hit.length)
            val seg = EntryData.segments(raw, hit.dict.sameTypeSequence).firstOrNull() ?: continue
            if (Abbreviations.isGlossaryEntry(seg.text)) {
                found = Abbreviations.expansionText(seg.text)
                break
            }
        }
        synchronized(abbreviationCache) { abbreviationCache[key] = found }
        found
    }

    /** Expansions for every abbreviation in the set that has one. */
    suspend fun expandAbbreviations(abbrs: Collection<String>): Map<String, String> {
        val out = HashMap<String, String>()
        for (a in abbrs) expandAbbreviation(a)?.let { out[a] = it }
        return out
    }

    /** Raw entry text, for "view source" style sharing. */
    suspend fun rawText(hit: LookupHit): String = withContext(Dispatchers.IO) {
        val raw = reader(hit.dict).read(hit.offset, hit.length)
        EntryData.segments(raw, hit.dict.sameTypeSequence).joinToString("\n") { it.text }
    }

    // ---------------------------------------------------------------- import

    private class Candidate(val base: String, val ifo: DocumentFile, val idx: DocumentFile, val dict: DocumentFile, val syn: DocumentFile?)

    private fun groupCandidates(files: List<DocumentFile>): List<Candidate> {
        val byName = files.associateBy { it.name ?: "" }
        val out = ArrayList<Candidate>()
        for (f in files) {
            val name = f.name ?: continue
            if (!name.endsWith(".ifo", ignoreCase = true)) continue
            val base = name.substring(0, name.length - 4)
            val idx = byName["$base.idx"] ?: byName["$base.idx.gz"] ?: continue
            val dict = byName["$base.dict.dz"] ?: byName["$base.dict"] ?: continue
            out.add(Candidate(base, f, idx, dict, byName["$base.syn"]))
        }
        return out
    }

    private fun walk(dir: DocumentFile, depth: Int, out: MutableList<DocumentFile>) {
        if (depth > 4) return
        for (f in dir.listFiles()) {
            if (f.isDirectory) walk(f, depth + 1, out) else if (f.isFile) out.add(f)
        }
    }

    suspend fun importTree(uri: Uri, onProgress: (ImportProgress) -> Unit): List<DictionaryInfo> = withContext(Dispatchers.IO) {
        onProgress(ImportProgress("Scanning folder…", null))
        val root = DocumentFile.fromTreeUri(context, uri) ?: throw ImportException("Cannot open folder")
        val files = ArrayList<DocumentFile>()
        walk(root, 0, files)
        // group per directory so same-named files in different folders don't collide
        val byDir = files.groupBy { it.uri.toString().substringBeforeLast("%2F", it.uri.toString()) }
        val candidates = byDir.values.flatMap { groupCandidates(it) }
        if (candidates.isEmpty()) throw ImportException("No StarDict dictionaries (.ifo + .idx + .dict.dz) found in that folder")
        importCandidates(candidates, onProgress)
    }

    suspend fun importFiles(uris: List<Uri>, onProgress: (ImportProgress) -> Unit): List<DictionaryInfo> = withContext(Dispatchers.IO) {
        val files = uris.mapNotNull { DocumentFile.fromSingleUri(context, it) }
        val candidates = groupCandidates(files)
        if (candidates.isEmpty()) throw ImportException("Select the .ifo, .idx and .dict.dz files of a dictionary together")
        importCandidates(candidates, onProgress)
    }

    private fun importCandidates(candidates: List<Candidate>, onProgress: (ImportProgress) -> Unit): List<DictionaryInfo> {
        val added = ArrayList<DictionaryInfo>()
        val existingNames = _dictionaries.value.map { it.name }.toMutableSet()
        for ((n, cand) in candidates.withIndex()) {
            val prefix = if (candidates.size > 1) "(${n + 1}/${candidates.size}) " else ""
            val ifo = Ifo.parse(readAll(cand.ifo).toString(Charsets.UTF_8))
            if (ifo.bookname in existingNames) {
                onProgress(ImportProgress("$prefix${ifo.bookname} is already installed, skipping", null))
                continue
            }
            val dir = File(File(context.filesDir, "dicts"), System.currentTimeMillis().toString() + "-" + sanitize(cand.base))
            dir.mkdirs()
            try {
                val total = (cand.dict.length() + cand.idx.length()).coerceAtLeast(1L)
                var done = 0L
                fun report(msg: String, bytes: Long) {
                    done += bytes
                    onProgress(ImportProgress("$prefix$msg", (done.toFloat() / total).coerceIn(0f, 0.85f) * 0.85f))
                }
                val dictName = if ((cand.dict.name ?: "").endsWith(".dz", true)) "data.dict.dz" else "data.dict"
                copy(cand.dict, File(dir, dictName), gunzip = false) { report("Copying ${ifo.bookname}…", it) }
                copy(cand.idx, File(dir, "data.idx"), gunzip = (cand.idx.name ?: "").endsWith(".gz", true)) { report("Copying index…", it) }
                cand.syn?.let { copy(it, File(dir, "data.syn"), gunzip = false) { _ -> } }
                File(dir, "data.ifo").writeBytes(readAll(cand.ifo))

                onProgress(ImportProgress("${prefix}Indexing ${ifo.bookname}…", 0.85f))
                val entries = IdxParser.parse(File(dir, "data.idx").readBytes(), ifo.idxOffsetBits)
                if (entries.isEmpty()) throw ImportException("Index of ${ifo.bookname} is empty")
                val syns = cand.syn?.let { IdxParser.parseSyn(File(dir, "data.syn").readBytes(), entries) } ?: emptyList()
                val info = insertDictionary(ifo, dir, dictName, entries, syns) { frac ->
                    onProgress(ImportProgress("${prefix}Indexing ${ifo.bookname}…", 0.85f + 0.15f * frac))
                }
                existingNames.add(ifo.bookname)
                added.add(info)
            } catch (e: Exception) {
                dir.deleteRecursively()
                throw e
            }
        }
        refresh()
        return added
    }

    private fun insertDictionary(
        ifo: com.lexis.reader.core.IfoInfo,
        dir: File,
        dictName: String,
        entries: List<IndexEntry>,
        syns: List<IndexEntry>,
        onFraction: (Float) -> Unit,
    ): DictionaryInfo {
        val db = dbHelper.writableDatabase
        val order = (_dictionaries.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("name", ifo.bookname)
                put("dir", dir.absolutePath)
                put("dict_file", dictName)
                put("sts", ifo.sameTypeSequence)
                put("word_count", entries.size)
                put("sort_order", order)
                put("added_at", System.currentTimeMillis())
            }
            val id = db.insertOrThrow("dictionaries", null, cv)
            val stmt = db.compileStatement(
                "INSERT INTO words(dict_id, word, norm, key, data_offset, data_length, is_syn) VALUES (?,?,?,?,?,?,?)"
            )
            val all = entries.size + syns.size
            var i = 0
            fun insert(e: IndexEntry, syn: Boolean) {
                stmt.clearBindings()
                stmt.bindLong(1, id)
                stmt.bindString(2, e.word)
                stmt.bindString(3, TextNormalizer.norm(e.word))
                stmt.bindString(4, TextNormalizer.key(e.word))
                stmt.bindLong(5, e.offset)
                stmt.bindLong(6, e.length.toLong())
                stmt.bindLong(7, if (syn) 1 else 0)
                stmt.executeInsert()
                i++
                if (i % 5000 == 0) onFraction(i.toFloat() / all)
            }
            for (e in entries) insert(e, false)
            for (e in syns) insert(e, true)
            db.setTransactionSuccessful()
            return DictionaryInfo(id, ifo.bookname, dir.absolutePath, dictName, ifo.sameTypeSequence, entries.size, order)
        } finally {
            db.endTransaction()
        }
    }

    private fun readAll(f: DocumentFile): ByteArray =
        context.contentResolver.openInputStream(f.uri)?.use { it.readBytes() } ?: throw ImportException("Cannot read ${f.name}")

    private fun copy(src: DocumentFile, dst: File, gunzip: Boolean, onBytes: (Long) -> Unit) {
        val raw: InputStream = context.contentResolver.openInputStream(src.uri) ?: throw ImportException("Cannot read ${src.name}")
        val counting = CountingInputStream(raw, onBytes)
        val input = if (gunzip) GZIPInputStream(counting, 1 shl 16) else counting
        input.use { ins ->
            FileOutputStream(dst).use { out ->
                val buf = ByteArray(1 shl 18)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
        }
    }

    private class CountingInputStream(private val inner: InputStream, private val onBytes: (Long) -> Unit) : InputStream() {
        private var pending = 0L
        override fun read(): Int {
            val b = inner.read()
            if (b >= 0) count(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = inner.read(b, off, len)
            if (n > 0) count(n.toLong())
            return n
        }

        private fun count(n: Long) {
            pending += n
            if (pending >= (1 shl 20)) {
                onBytes(pending)
                pending = 0
            }
        }

        override fun close() {
            if (pending > 0) onBytes(pending)
            pending = 0
            inner.close()
        }
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)

    // ---------------------------------------------------------------- management

    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        val d = dictById(id) ?: return@withContext
        synchronized(this@DictionaryRepository) { readers.remove(id)?.close() }
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("words", "dict_id = ?", arrayOf(id.toString()))
            db.delete("dictionaries", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        File(d.dir).deleteRecursively()
        refresh()
    }

    suspend fun move(id: Long, up: Boolean) = withContext(Dispatchers.IO) {
        val list = _dictionaries.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j < 0 || j >= list.size) return@withContext
        list[i] = list[j].also { list[j] = list[i] }
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            list.forEachIndexed { k, d ->
                db.execSQL("UPDATE dictionaries SET sort_order = ? WHERE id = ?", arrayOf(k + 1, d.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refresh()
    }
}
