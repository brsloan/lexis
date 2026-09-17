package com.lexis.reader.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.lexis.reader.core.Abbreviations
import com.lexis.reader.core.ExportEntry
import com.lexis.reader.core.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportResult(val file: File, val format: ExportFormat, val wordCount: Int)

class ExportService(
    private val context: Context,
    private val dictionaries: DictionaryRepository,
) {

    /** Build an export document for the given history items and write it to the cache directory. */
    suspend fun export(
        items: List<HistoryItem>,
        format: ExportFormat,
        includeQuotations: Boolean,
        abbreviations: AbbreviationMode = AbbreviationMode.TAP,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            val entries = items.map { item ->
                val hits = dictionaries.lookup(item.word)
                var articles = hits.map { dictionaries.article(it) }
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
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600 * 1000L }?.forEach { it.delete() }
            val fileStamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            val ext = if (format == ExportFormat.HTML) "html" else "txt"
            val file = File(dir, "lexis-words-$fileStamp.$ext")
            file.writeText(body, Charsets.UTF_8)
            ExportResult(file, format, entries.size)
        }

    /** Share sheet intent (email clients attach the file). */
    fun shareIntent(result: ExportResult): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", result.file)
        val mime = if (result.format == ExportFormat.HTML) "text/html" else "text/plain"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Lexis word list (${result.wordCount} words)")
            putExtra(Intent.EXTRA_TEXT, "Attached: ${result.wordCount} looked-up words with their dictionary entries.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Send word list").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Plain-text share of a single article (for quick copying into notes, etc.). */
    fun shareTextIntent(subject: String, text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, "Share entry").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
