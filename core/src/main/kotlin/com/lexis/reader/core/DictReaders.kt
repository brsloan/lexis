package com.lexis.reader.core

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.zip.Inflater

/** Random access into the uncompressed `.dict` data. */
interface DictReader : Closeable {
    fun read(offset: Long, length: Int): ByteArray
}

/** Reader for an uncompressed `.dict` file. */
class PlainDictReader(file: File) : DictReader {
    private val raf = RandomAccessFile(file, "r")

    @Synchronized
    override fun read(offset: Long, length: Int): ByteArray {
        val buf = ByteArray(length)
        raf.seek(offset)
        raf.readFully(buf)
        return buf
    }

    @Synchronized
    override fun close() = raf.close()
}

/**
 * Reader for a dictzip (`.dict.dz`) file: a gzip member whose "RA" extra field lists the
 * compressed size of each fixed-length chunk, so any byte range can be inflated on demand.
 */
class DictZipReader(file: File, cacheChunks: Int = 16) : DictReader {
    private val raf = RandomAccessFile(file, "r")
    val chunkLength: Int
    private val chunkOffsets: LongArray   // absolute file offset of each compressed chunk
    private val chunkSizes: IntArray
    private val cache = object : LinkedHashMap<Int, ByteArray>(cacheChunks, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?) = size > cacheChunks
    }

    init {
        val header = ByteArray(10)
        raf.seek(0)
        raf.readFully(header)
        if ((header[0].toInt() and 0xff) != 0x1f || (header[1].toInt() and 0xff) != 0x8b) {
            throw IOException("Not a gzip file")
        }
        if (header[2].toInt() != 8) throw IOException("Unsupported compression method")
        val flags = header[3].toInt() and 0xff
        var pos = 10L
        var chLen = 0
        var sizes: IntArray? = null
        if (flags and 0x04 != 0) {
            val xlen = readLE16(pos)
            pos += 2
            val extraEnd = pos + xlen
            var p = pos
            while (p + 4 <= extraEnd) {
                val si1 = readByte(p)
                val si2 = readByte(p + 1)
                val len = readLE16(p + 2)
                if (si1 == 'R'.code && si2 == 'A'.code) {
                    val ver = readLE16(p + 4)
                    if (ver != 1) throw IOException("Unsupported dictzip version $ver")
                    chLen = readLE16(p + 6)
                    val chCnt = readLE16(p + 8)
                    val arr = IntArray(chCnt)
                    val buf = ByteArray(chCnt * 2)
                    raf.seek(p + 10)
                    raf.readFully(buf)
                    for (i in 0 until chCnt) arr[i] = (buf[2 * i].toInt() and 0xff) or ((buf[2 * i + 1].toInt() and 0xff) shl 8)
                    sizes = arr
                }
                p += 4 + len
            }
            pos = extraEnd
        }
        if (flags and 0x08 != 0) pos = skipNul(pos)    // FNAME
        if (flags and 0x10 != 0) pos = skipNul(pos)    // FCOMMENT
        if (flags and 0x02 != 0) pos += 2              // FHCRC
        if (sizes == null || chLen == 0) throw IOException("Missing dictzip random-access header")
        chunkLength = chLen
        chunkSizes = sizes
        chunkOffsets = LongArray(sizes.size)
        var off = pos
        for (i in sizes.indices) {
            chunkOffsets[i] = off
            off += sizes[i]
        }
    }

    private fun readByte(p: Long): Int {
        raf.seek(p)
        return raf.read()
    }

    private fun readLE16(p: Long): Int {
        raf.seek(p)
        val a = raf.read()
        val b = raf.read()
        return a or (b shl 8)
    }

    private fun skipNul(start: Long): Long {
        var p = start
        raf.seek(p)
        while (true) {
            val b = raf.read()
            p++
            if (b <= 0) return p
        }
    }

    @Synchronized
    override fun read(offset: Long, length: Int): ByteArray {
        val out = ByteArray(length)
        if (length == 0) return out
        val first = (offset / chunkLength).toInt()
        val last = ((offset + length - 1) / chunkLength).toInt()
        if (last >= chunkSizes.size) throw IOException("Read beyond end of dictzip data")
        var written = 0
        for (ci in first..last) {
            val chunk = chunk(ci)
            val chunkStart = ci.toLong() * chunkLength
            val from = if (ci == first) (offset - chunkStart).toInt() else 0
            val n = minOf(chunk.size - from, length - written)
            if (n <= 0) break
            System.arraycopy(chunk, from, out, written, n)
            written += n
        }
        if (written < length) throw IOException("Short read from dictzip data")
        return out
    }

    private fun chunk(index: Int): ByteArray {
        cache[index]?.let { return it }
        val comp = ByteArray(chunkSizes[index])
        raf.seek(chunkOffsets[index])
        raf.readFully(comp)
        val inflater = Inflater(true)
        try {
            inflater.setInput(comp)
            val buf = ByteArray(chunkLength)
            var total = 0
            while (total < chunkLength && !inflater.finished()) {
                val n = inflater.inflate(buf, total, chunkLength - total)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                total += n
            }
            val result = if (total == chunkLength) buf else buf.copyOf(total)
            cache[index] = result
            return result
        } finally {
            inflater.end()
        }
    }

    @Synchronized
    override fun close() = raf.close()

    companion object {
        fun open(file: File): DictReader =
            if (file.name.endsWith(".dz", ignoreCase = true)) DictZipReader(file) else PlainDictReader(file)
    }
}
