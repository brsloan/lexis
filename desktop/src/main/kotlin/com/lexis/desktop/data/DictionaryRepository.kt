package com.lexis.desktop.data

import com.lexis.reader.core.Abbreviations
import com.lexis.reader.core.Article
import com.lexis.reader.core.DictReader
import com.lexis.reader.core.DictZipReader
import com.lexis.reader.core.EntryData
import com.lexis.reader.core.IdxParser
import com.lexis.reader.core.Ifo
import com.lexis.reader.core.IfoInfo
import com.lexis.reader.core.IndexEntry
import com.lexis.reader.core.TextNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

data class DictionaryInfo(
    val id: Long,
    val name: String,
    val dir: String,
    val base: String,
    val dictFile: String,
    val sameTypeSequence: String?,
    val wordCount: Int,
    val sortOrder: Int,
) {
    val file: File get() = File(dir, dictFile)
    val available: Boolean get() = file.isFile
}

data class LookupHit(val dict: DictionaryInfo, val word: String, val offset: Long, val length: Int, val isSynonym: Boolean)

data class Suggestion(val word: String, val dictionaries: List<String>)

data class ImportProgress(val message: String, val fraction: Float?)

class ImportException(message: String) : IOException(message)

/**
 * Dictionaries are indexed where they are: only the word list goes into the database, and the
 * entry text is read from the user's own `.dict.dz` file on demand.
 */
class DictionaryRepository(private val db: Database) {

    private val _dictionaries = MutableStateFlow<List<DictionaryInfo>>(emptyList())
    val dictionaries: StateFlow<List<DictionaryInfo>> = _dictionaries

    private val readers = HashMap<Long, DictReader>()

    init {
        refresh()
    }

    fun refresh() {
        val out = ArrayList<DictionaryInfo>()
        db.read { c ->
            c.query("SELECT id, name, dir, base, dict_file, sts, word_count, sort_order FROM dictionaries ORDER BY sort_order, id") { rs ->
                out.add(
                    DictionaryInfo(
                        id = rs.getLong(1),
                        name = rs.getString(2),
                        dir = rs.getString(3),
                        base = rs.getString(4),
                        dictFile = rs.getString(5),
                        sameTypeSequence = rs.getString(6),
                        wordCount = rs.getInt(7),
                        sortOrder = rs.getInt(8),
                    )
                )
            }
        }
        _dictionaries.value = out
    }

    private fun dictById(id: Long): DictionaryInfo? = _dictionaries.value.firstOrNull { it.id == id }

    @Synchronized
    private fun reader(d: DictionaryInfo): DictReader = readers.getOrPut(d.id) {
        if (!d.available) throw IOException("${d.name}: file not found at ${d.file.path}")
        DictZipReader.open(d.file)
    }

    @Synchronized
    private fun read(hit: LookupHit): ByteArray = reader(hit.dict).read(hit.offset, hit.length)

    // ---------------------------------------------------------------- lookup

    /** All index entries whose normalised form equals the query (falling back to the looser key). */
    suspend fun lookup(query: String): List<LookupHit> = withContext(Dispatchers.IO) {
        val norm = TextNormalizer.norm(query)
        if (norm.isEmpty()) return@withContext emptyList()
        var hits = queryHits("norm", norm)
        if (hits.isEmpty()) {
            val key = TextNormalizer.key(query)
            if (key.isNotEmpty()) hits = queryHits("key", key)
        }
        val dictOrder = _dictionaries.value.withIndex().associate { it.value.id to it.index }
        hits.distinctBy { Triple(it.dict.id, it.offset, it.length) }
            .sortedWith(compareBy({ dictOrder[it.dict.id] ?: 99 }, { it.isSynonym }, { it.word }))
    }

    private fun queryHits(column: String, value: String): List<LookupHit> {
        val out = ArrayList<LookupHit>()
        db.read { c ->
            c.query("SELECT dict_id, word, data_offset, data_length, is_syn FROM words WHERE $column = ? LIMIT 200", value) { rs ->
                val d = dictById(rs.getLong(1)) ?: return@query
                out.add(LookupHit(d, rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getInt(5) != 0))
            }
        }
        return out
    }

    /** Prefix suggestions for the search box, de-duplicated by headword. */
    suspend fun suggest(prefix: String, limit: Int = 60): List<Suggestion> = withContext(Dispatchers.IO) {
        val norm = TextNormalizer.norm(prefix)
        if (norm.isEmpty()) return@withContext emptyList()
        val names = _dictionaries.value.associate { it.id to it.name }
        val ordered = LinkedHashMap<String, LinkedHashSet<String>>()

        fun collect(sql: String, vararg args: Any) {
            db.read { c ->
                c.query(sql, *args) { rs ->
                    if (ordered.size >= limit) return@query
                    val w = rs.getString(1)
                    val dn = names[rs.getLong(2)] ?: return@query
                    ordered.getOrPut(w) { LinkedHashSet() }.add(dn)
                }
            }
        }
        collect(
            "SELECT word, dict_id FROM words WHERE norm >= ? AND norm < ? ORDER BY norm, is_syn, word LIMIT ?",
            norm, norm + "￿", limit * 4,
        )
        if (ordered.size < 5) {
            val key = TextNormalizer.key(prefix)
            if (key.isNotEmpty()) collect(
                "SELECT word, dict_id FROM words WHERE key >= ? AND key < ? ORDER BY key, word LIMIT ?",
                key, key + "￿", limit * 2,
            )
        }
        if (ordered.size < 5 && norm.length >= 3) {
            collect(
                "SELECT word, dict_id FROM words WHERE norm LIKE ? ESCAPE '\\' ORDER BY length(norm), norm LIMIT ?",
                "%" + norm.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", 80,
            )
        }
        ordered.entries.take(limit).map { Suggestion(it.key, it.value.toList()) }
    }

    /** Words adjacent to the given one in index order, for "browse nearby" navigation. */
    suspend fun neighbours(word: String, dictId: Long, before: Int, after: Int): List<String> = withContext(Dispatchers.IO) {
        val norm = TextNormalizer.norm(word)
        val prev = ArrayList<String>()
        val next = ArrayList<String>()
        db.read { c ->
            c.query("SELECT DISTINCT word FROM words WHERE dict_id = ? AND norm < ? ORDER BY norm DESC LIMIT ?", dictId, norm, before) {
                prev.add(it.getString(1))
            }
            c.query("SELECT DISTINCT word FROM words WHERE dict_id = ? AND norm > ? ORDER BY norm ASC LIMIT ?", dictId, norm, after) {
                next.add(it.getString(1))
            }
        }
        prev.asReversed() + word + next
    }

    suspend fun article(hit: LookupHit): Article = withContext(Dispatchers.IO) {
        val blocks = EntryData.toBlocks(read(hit), hit.dict.sameTypeSequence)
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
            val raw = runCatching { read(hit) }.getOrNull() ?: continue
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

    // ---------------------------------------------------------------- adding dictionaries

    private class Candidate(val dir: File, val base: String, val ifo: File, val idx: File, val dict: File, val syn: File?)

    private fun candidatesIn(dir: File): List<Candidate> {
        val files = dir.listFiles()?.filter { it.isFile } ?: return emptyList()
        val byName = files.associateBy { it.name.lowercase() }
        return files.filter { it.name.endsWith(".ifo", ignoreCase = true) }.mapNotNull { ifo ->
            val base = ifo.name.dropLast(4)
            val b = base.lowercase()
            val idx = byName["$b.idx"] ?: byName["$b.idx.gz"] ?: return@mapNotNull null
            val dict = byName["$b.dict.dz"] ?: byName["$b.dict"] ?: return@mapNotNull null
            Candidate(dir, base, ifo, idx, dict, byName["$b.syn"])
        }
    }

    private fun walk(dir: File, depth: Int, out: MutableList<Candidate>) {
        if (depth > 6) return
        out += candidatesIn(dir)
        dir.listFiles()?.filter { it.isDirectory && !it.isHidden }?.sortedBy { it.name }?.forEach { walk(it, depth + 1, out) }
    }

    /** Adds every StarDict dictionary found in [folder] and its sub-folders. */
    suspend fun addFolder(folder: File, onProgress: (ImportProgress) -> Unit): List<DictionaryInfo> = withContext(Dispatchers.IO) {
        onProgress(ImportProgress("Scanning ${folder.name}…", null))
        val candidates = ArrayList<Candidate>()
        walk(folder, 0, candidates)
        if (candidates.isEmpty()) throw ImportException("No StarDict dictionaries (.ifo + .idx + .dict.dz) found in ${folder.path}")
        addCandidates(candidates, onProgress)
    }

    /** Adds the dictionary described by an `.ifo` file (its `.idx` and `.dict.dz` must sit next to it). */
    suspend fun addIfo(ifo: File, onProgress: (ImportProgress) -> Unit): List<DictionaryInfo> = withContext(Dispatchers.IO) {
        val c = candidatesIn(ifo.parentFile).firstOrNull { it.ifo.name.equals(ifo.name, ignoreCase = true) }
            ?: throw ImportException("${ifo.name}: the matching .idx and .dict.dz files must be in the same folder")
        addCandidates(listOf(c), onProgress)
    }

    private fun addCandidates(candidates: List<Candidate>, onProgress: (ImportProgress) -> Unit): List<DictionaryInfo> {
        val added = ArrayList<DictionaryInfo>()
        val existingNames = _dictionaries.value.map { it.name }.toMutableSet()
        for ((n, cand) in candidates.withIndex()) {
            val prefix = if (candidates.size > 1) "(${n + 1}/${candidates.size}) " else ""
            val ifo = Ifo.parse(cand.ifo.readText(Charsets.UTF_8))
            if (ifo.bookname in existingNames) {
                onProgress(ImportProgress("$prefix${ifo.bookname} is already installed, skipping", null))
                continue
            }
            onProgress(ImportProgress("${prefix}Reading index of ${ifo.bookname}…", 0f))
            val idxBytes = if (cand.idx.name.endsWith(".gz", true)) {
                GZIPInputStream(cand.idx.inputStream().buffered(1 shl 16)).use { it.readBytes() }
            } else cand.idx.readBytes()
            val entries = IdxParser.parse(idxBytes, ifo.idxOffsetBits)
            if (entries.isEmpty()) throw ImportException("Index of ${ifo.bookname} is empty")
            val syns = cand.syn?.let { IdxParser.parseSyn(it.readBytes(), entries) } ?: emptyList()
            val info = insertDictionary(ifo, cand, entries, syns) { frac ->
                onProgress(ImportProgress("${prefix}Indexing ${ifo.bookname}…", frac))
            }
            existingNames.add(ifo.bookname)
            added.add(info)
        }
        refresh()
        return added
    }

    private fun insertDictionary(
        ifo: IfoInfo,
        cand: Candidate,
        entries: List<IndexEntry>,
        syns: List<IndexEntry>,
        onFraction: (Float) -> Unit,
    ): DictionaryInfo = db.transaction { c ->
        val order = (_dictionaries.value.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val dir = cand.dir.absolutePath
        val id = c.prepareStatement(
            "INSERT INTO dictionaries(name, dir, base, dict_file, sts, word_count, sort_order, added_at) VALUES (?,?,?,?,?,?,?,?)",
            java.sql.Statement.RETURN_GENERATED_KEYS,
        ).use { ps ->
            ps.bind(arrayOf<Any?>(ifo.bookname, dir, cand.base, cand.dict.name, ifo.sameTypeSequence, entries.size, order, System.currentTimeMillis()))
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> rs.next(); rs.getLong(1) }
        }
        c.prepareStatement(
            "INSERT INTO words(dict_id, word, norm, key, data_offset, data_length, is_syn) VALUES (?,?,?,?,?,?,?)"
        ).use { ps ->
            val all = entries.size + syns.size
            var i = 0
            fun insert(e: IndexEntry, syn: Boolean) {
                ps.setLong(1, id)
                ps.setString(2, e.word)
                ps.setString(3, TextNormalizer.norm(e.word))
                ps.setString(4, TextNormalizer.key(e.word))
                ps.setLong(5, e.offset)
                ps.setInt(6, e.length)
                ps.setInt(7, if (syn) 1 else 0)
                ps.addBatch()
                i++
                if (i % 5000 == 0) {
                    ps.executeBatch()
                    onFraction(i.toFloat() / all)
                }
            }
            for (e in entries) insert(e, false)
            for (e in syns) insert(e, true)
            ps.executeBatch()
        }
        DictionaryInfo(id, ifo.bookname, dir, cand.base, cand.dict.name, ifo.sameTypeSequence, entries.size, order)
    }

    // ---------------------------------------------------------------- management

    /** Forgets a dictionary. The user's files are left untouched. */
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        synchronized(this@DictionaryRepository) { readers.remove(id)?.close() }
        db.transaction { c ->
            c.update("DELETE FROM words WHERE dict_id = ?", id)
            c.update("DELETE FROM dictionaries WHERE id = ?", id)
        }
        refresh()
    }

    suspend fun move(id: Long, up: Boolean) = withContext(Dispatchers.IO) {
        val list = _dictionaries.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j < 0 || j >= list.size) return@withContext
        list[i] = list[j].also { list[j] = list[i] }
        db.transaction { c ->
            list.forEachIndexed { k, d -> c.update("UPDATE dictionaries SET sort_order = ? WHERE id = ?", k + 1, d.id) }
        }
        refresh()
    }

    fun close() {
        synchronized(this) {
            readers.values.forEach { runCatching { it.close() } }
            readers.clear()
        }
    }
}
