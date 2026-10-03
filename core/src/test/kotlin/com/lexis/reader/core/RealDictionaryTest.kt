package com.lexis.reader.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Exercises the engine against real StarDict files. Runs only when the system property or
 * environment variable DICT_DIR points at a folder containing `.ifo` files (searched recursively).
 */
class RealDictionaryTest {

    private val dir: File? = (System.getProperty("DICT_DIR") ?: System.getenv("DICT_DIR"))?.let { File(it) }?.takeIf { it.isDirectory }

    private data class Dict(val ifo: IfoInfo, val entries: List<IndexEntry>, val reader: DictReader, val base: File)

    private fun openAll(): List<Dict> {
        val ifos = dir!!.walkTopDown().filter { it.isFile && it.name.endsWith(".ifo") }.toList()
        assumeTrue("no .ifo files under $dir", ifos.isNotEmpty())
        return ifos.map { ifoFile ->
            val base = File(ifoFile.parentFile, ifoFile.name.removeSuffix(".ifo"))
            val ifo = Ifo.parse(ifoFile.readText())
            val entries = IdxParser.parse(File(base.path + ".idx").readBytes(), ifo.idxOffsetBits)
            val dz = File(base.path + ".dict.dz")
            val reader = DictZipReader.open(if (dz.exists()) dz else File(base.path + ".dict"))
            Dict(ifo, entries, reader, base)
        }
    }

    @Test
    fun indexMatchesIfo() {
        assumeTrue(dir != null)
        for (d in openAll()) {
            assertEquals(d.ifo.wordCount, d.entries.size)
            assertTrue(d.entries.all { it.length > 0 })
        }
    }

    @Test
    fun dictzipReadMatchesStreamingGzip() {
        assumeTrue(dir != null)
        for (d in openAll()) {
            // compare a few early entries against a plain streaming decompression
            val early = d.entries.filter { it.offset < 3_000_000 }.sortedBy { it.offset }.take(40)
            if (early.isEmpty()) continue
            val end = early.maxOf { it.offset + it.length }
            val ref = ByteArray(end.toInt())
            GZIPInputStream(File(d.base.path + ".dict.dz").inputStream(), 1 shl 16).use { gz ->
                var read = 0
                while (read < ref.size) {
                    val n = gz.read(ref, read, ref.size - read)
                    if (n < 0) break
                    read += n
                }
            }
            for (e in early) {
                val ours = d.reader.read(e.offset, e.length)
                assertArrayEquals("entry ${e.word}", ref.copyOfRange(e.offset.toInt(), (e.offset + e.length).toInt()), ours)
            }
            // and one that spans a chunk boundary far into the file
            val big = d.entries.maxBy { it.length }
            val bytes = d.reader.read(big.offset, big.length)
            val text = String(bytes, Charsets.UTF_8)
            assertTrue(text.startsWith("<k>"))
            assertTrue(text.trimEnd().endsWith("</blockquote>") || text.trimEnd().endsWith("</b>") || text.length > 100)
        }
    }

    @Test
    fun parseEveryNthEntry() {
        assumeTrue(dir != null)
        var total = 0
        var withHeadword = 0
        var citations = 0
        var dated = 0
        var senses = 0
        val t0 = System.currentTimeMillis()
        for (d in openAll()) {
            for ((i, e) in d.entries.withIndex()) {
                if (i % 50 != 0) continue
                val raw = d.reader.read(e.offset, e.length)
                val blocks = EntryData.toBlocks(raw, d.ifo.sameTypeSequence)
                total++
                assertTrue("empty parse for ${e.word}", blocks.isNotEmpty())
                if (blocks.first() is Block.Headword) withHeadword++
                for (b in blocks) when (b) {
                    is Block.Quotations -> {
                        citations += b.items.size
                        dated += b.items.count { it.date != null }
                        assertTrue("citation without any text in ${e.word}", b.items.all { it.quote.isNotEmpty() || it.before.isNotEmpty() })
                    }
                    is Block.Sense -> senses++
                    else -> {}
                }
            }
        }
        val ms = System.currentTimeMillis() - t0
        println("parsed $total entries in $ms ms; headword-first=$withHeadword senses=$senses citations=$citations dated=$dated")
        assertTrue(withHeadword >= total * 0.98)
        assertTrue(dated >= citations * 0.9)
    }

    @Test
    fun biggestEntriesParseQuickly() {
        assumeTrue(dir != null)
        for (d in openAll()) {
            val big = d.entries.sortedByDescending { it.length }.take(3)
            for (e in big) {
                val raw = d.reader.read(e.offset, e.length)
                val t0 = System.nanoTime()
                val blocks = EntryData.toBlocks(raw, d.ifo.sameTypeSequence)
                val ms = (System.nanoTime() - t0) / 1_000_000
                val article = Article(e.word, blocks)
                println("${e.word}: ${e.length} bytes -> ${blocks.size} blocks, ${article.quotationCount} quotations, outline ${article.outline().size}, $ms ms")
                assertTrue(blocks.size > 100)
                assertTrue(ms < 5000)
            }
        }
    }

    @Test
    fun specificWords() {
        assumeTrue(dir != null)
        val dicts = openAll()
        fun find(word: String): Pair<Dict, IndexEntry>? {
            for (d in dicts) d.entries.firstOrNull { it.word == word }?.let { return d to it }
            return null
        }
        val (d, e) = find("abandon") ?: return
        val blocks = EntryData.toBlocks(d.reader.read(e.offset, e.length), d.ifo.sameTypeSequence)
        assertEquals(listOf("I.", "II."), blocks.filterIsInstance<Block.HomographHeader>().map { it.label }.take(2))
        find("serendipity")?.let { (d2, e2) ->
            val b = EntryData.toBlocks(d2.reader.read(e2.offset, e2.length), d2.ifo.sameTypeSequence)
            assertEquals(8, Article("serendipity", b).quotationCount)
        }
    }
}
