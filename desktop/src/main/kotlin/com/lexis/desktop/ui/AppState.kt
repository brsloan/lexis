package com.lexis.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.lexis.desktop.LexisDesktop
import com.lexis.desktop.data.AbbreviationMode
import com.lexis.desktop.data.AppSettings
import com.lexis.desktop.data.DictionaryInfo
import com.lexis.desktop.data.ExportFormat
import com.lexis.desktop.data.HistoryItem
import com.lexis.desktop.data.ImportProgress
import com.lexis.desktop.data.Suggestion
import com.lexis.reader.core.Abbreviations
import com.lexis.reader.core.Article
import com.lexis.reader.core.Block
import com.lexis.reader.core.ExportEntry
import com.lexis.reader.core.Exporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.io.File

/** Result of opening a word: the articles found, or nearby suggestions when nothing matched. */
data class EntryResult(val word: String, val articles: List<Article>, val suggestions: List<Suggestion>)

/** A transient message for the snackbar, optionally with one action. */
data class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppState(private val app: LexisDesktop, private val scope: CoroutineScope) {

    val dictionaries: StateFlow<List<DictionaryInfo>> = app.dictionaries.dictionaries
    val history: StateFlow<List<HistoryItem>> = app.history.items
    val settings: StateFlow<AppSettings> = app.settings.state

    // ------------------------------------------------------------ navigation (browser-style)

    private var visited by mutableStateOf<List<String>>(emptyList())
    private var position by mutableIntStateOf(-1)

    val currentWord: String? get() = visited.getOrNull(position)
    val canGoBack: Boolean get() = position > 0
    val canGoForward: Boolean get() = position < visited.size - 1

    fun openWord(word: String) {
        val w = word.trim()
        if (w.isEmpty() || w == currentWord) return
        visited = visited.take(position + 1) + w
        position = visited.size - 1
    }

    fun back() {
        if (canGoBack) position--
    }

    fun forward() {
        if (canGoForward) position++
    }

    /** Bumped to ask the search box to take focus and select its text. */
    var focusSearchRequest by mutableIntStateOf(0)
        private set

    fun focusSearch() {
        focusSearchRequest++
    }

    // ------------------------------------------------------------ search

    /** Search box text. Plain Compose state so the text field never sees a stale value. */
    var query by mutableStateOf("")

    val suggestions: StateFlow<List<Suggestion>> = snapshotFlow { query }
        .debounce(90)
        .mapLatest { q -> if (q.isBlank()) emptyList() else app.dictionaries.suggest(q) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Enter pressed in the search box: exact match if any, else the first suggestion, else the query itself. */
    fun submitQuery() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            val hits = app.dictionaries.lookup(q)
            val target = when {
                hits.isNotEmpty() -> hits.first().word
                else -> app.dictionaries.suggest(q, 1).firstOrNull()?.word ?: q
            }
            query = ""
            openWord(target)
        }
    }

    fun openSuggestion(word: String) {
        query = ""
        openWord(word)
    }

    /** Looks up whatever text is on the system clipboard. */
    fun lookUpClipboard() {
        val text = runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
        }.getOrNull()?.trim()?.take(80)
        if (text.isNullOrEmpty()) {
            show("The clipboard has no text to look up")
        } else {
            query = ""
            openWord(text)
        }
    }

    suspend fun loadEntry(word: String): EntryResult {
        val hits = app.dictionaries.lookup(word)
        if (hits.isEmpty()) {
            return EntryResult(word, emptyList(), app.dictionaries.suggest(word, 30))
        }
        var articles = hits.mapNotNull { hit ->
            runCatching { app.dictionaries.article(hit) }
                .onFailure { show("Could not read ${hit.dict.name}: ${it.message ?: it.javaClass.simpleName}") }
                .getOrNull()
        }
        val mode = settings.value.abbreviations
        if (mode != AbbreviationMode.TAP) {
            articles = articles.map { a -> a.copy(blocks = expandAbbreviations(a.blocks, mode)) }
        }
        val canonical = hits.first().word
        if (articles.isNotEmpty()) app.history.record(canonical, hits.first().dict.id, articles.first().snippet())
        return EntryResult(canonical, articles, emptyList())
    }

    /** Inline-expand unambiguous abbreviations according to the mode. */
    private suspend fun expandAbbreviations(blocks: List<Block>, mode: AbbreviationMode): List<Block> {
        val full = app.dictionaries.expandAbbreviations(Abbreviations.collect(blocks))
        val readings = HashMap<String, String>()
        for ((k, v) in full) Abbreviations.primaryReading(v)?.let { readings[k] = it }
        return Abbreviations.expand(blocks, readings, includeQuotations = mode == AbbreviationMode.EVERYWHERE)
    }

    /** Full expansion of an abbreviation for the popup, or null if the dictionary has none. */
    suspend fun abbreviation(text: String): String? = app.dictionaries.expandAbbreviation(text)

    suspend fun neighbours(word: String, dictId: Long): List<String> =
        app.dictionaries.neighbours(word, dictId, 15, 15)

    fun copyArticles(word: String, articles: List<Article>, includeQuotations: Boolean) {
        val text = Exporter.toText(listOf(ExportEntry(word, articles)), includeQuotations, word, "")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        show("Copied “$word” to the clipboard")
    }

    // ------------------------------------------------------------ history

    fun deleteHistory(id: Long) = scope.launch { app.history.delete(id) }
    fun clearHistory() = scope.launch { app.history.clear() }

    // ------------------------------------------------------------ settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) = app.settings.update(transform)

    fun changeFontScale(delta: Float) = updateSettings {
        it.copy(fontScale = (Math.round((it.fontScale + delta) * 20) / 20f).coerceIn(0.7f, 1.8f))
    }

    fun resetFontScale() = updateSettings { it.copy(fontScale = 1f) }

    // ------------------------------------------------------------ messages

    private val _message = MutableStateFlow<Message?>(null)
    val message: StateFlow<Message?> = _message

    fun show(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _message.value = Message(text, actionLabel, action)
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ------------------------------------------------------------ dictionaries

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress

    fun addFolder(folder: File) {
        updateSettings { it.copy(lastAddFolder = folder.absolutePath) }
        runImport { app.dictionaries.addFolder(folder) { _importProgress.value = it } }
    }

    fun addIfo(file: File) {
        updateSettings { it.copy(lastAddFolder = file.parentFile?.absolutePath) }
        runImport { app.dictionaries.addIfo(file) { _importProgress.value = it } }
    }

    private fun runImport(block: suspend () -> List<DictionaryInfo>) {
        if (_importProgress.value != null) return
        _importProgress.value = ImportProgress("Starting…", null)
        scope.launch {
            try {
                val added = block()
                show(if (added.isEmpty()) "Nothing new was added" else "Added " + added.joinToString { it.name })
            } catch (e: Exception) {
                show("Could not add dictionary: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                _importProgress.value = null
            }
        }
    }

    fun removeDictionary(id: Long) = scope.launch { app.dictionaries.remove(id) }
    fun moveDictionary(id: Long, up: Boolean) = scope.launch { app.dictionaries.move(id, up) }

    // ------------------------------------------------------------ export

    var exporting by mutableStateOf(false)
        private set

    fun exportCount(onlySinceLastExport: Boolean): Int =
        if (onlySinceLastExport) app.history.since(settings.value.lastExportAt).size else history.value.size

    fun defaultExportName(format: ExportFormat) = app.exporter.defaultFileName(format)

    /**
     * Writes the word list to [target]. Remembers the chosen options and the export time so
     * that "only new words" works next time.
     */
    fun export(format: ExportFormat, includeQuotations: Boolean, onlySinceLastExport: Boolean, target: File, onDone: () -> Unit) {
        if (exporting) return
        val items = if (onlySinceLastExport) app.history.since(settings.value.lastExportAt) else app.history.all()
        if (items.isEmpty()) {
            show("Nothing to export")
            return
        }
        exporting = true
        scope.launch {
            try {
                val file = app.exporter.export(items, format, includeQuotations, settings.value.abbreviations, target)
                updateSettings {
                    it.copy(
                        lastExportAt = System.currentTimeMillis(),
                        exportFormat = format,
                        exportQuotations = includeQuotations,
                        lastExportFolder = file.parentFile?.absolutePath,
                    )
                }
                onDone()
                show("Exported ${items.size} word${if (items.size == 1) "" else "s"} to ${file.name}", "Open") { openFile(file) }
            } catch (e: Exception) {
                show("Export failed: " + (e.message ?: e.javaClass.simpleName))
            } finally {
                exporting = false
            }
        }
    }

    private fun openFile(file: File) {
        runCatching { Desktop.getDesktop().open(file) }
            .onFailure { show("Could not open ${file.name}: ${it.message ?: it.javaClass.simpleName}") }
    }
}
