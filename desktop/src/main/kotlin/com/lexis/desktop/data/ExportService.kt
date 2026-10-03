package com.lexis.desktop.data

import com.lexis.reader.core.Abbreviations
import com.lexis.reader.core.ExportEntry
import com.lexis.reader.core.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportService(private val dictionaries: DictionaryRepository) {

    fun defaultFileName(format: ExportFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "lexis-words-$stamp." + if (format == ExportFormat.HTML) "html" else "txt"
    }

    /** Builds an export document for the given history items and writes it to [target]. */
    suspend fun export(
        items: List<HistoryItem>,
        format: ExportFormat,
        includeQuotations: Boolean,
        abbreviations: AbbreviationMode,
        target: File,
    ): File = withContext(Dispatchers.IO) {
        val entries = items.map { item ->
            val hits = dictionaries.lookup(item.word)
            var articles = hits.mapNotNull { runCatching { dictionaries.article(it) }.getOrNull() }
            if (abbreviations != AbbreviationMode.TAP) {
                articles = articles.map { a ->
                    val full = dictionaries.expandAbbreviations(Abbreviations.collect(a.blocks))
                    val readings = HashMap<String, String>()
                    for ((k, v) in full) Abbreviations.primaryReading(v)?.let { readings[k] = it }
                    a.copy(blocks = Abbreviations.expand(a.blocks, readings, abbreviations == AbbreviationMode.EVERYWHERE))
                }
            }
            ExportEntry(item.word, articles)
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val title = "Lexis word list"
        val subtitle = "${entries.size} word${if (entries.size == 1) "" else "s"} · exported $stamp"
        val body = when (format) {
            ExportFormat.HTML -> Exporter.toHtml(entries, includeQuotations, title, subtitle)
            ExportFormat.TEXT -> Exporter.toText(entries, includeQuotations, title, subtitle)
        }
        target.parentFile?.mkdirs()
        target.writeText(body, Charsets.UTF_8)
        target
    }
}
