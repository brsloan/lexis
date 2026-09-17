package com.lexis.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lexis.reader.LexisApp
import com.lexis.reader.core.Abbreviations
import com.lexis.reader.core.Article
import com.lexis.reader.core.ExportEntry
import com.lexis.reader.core.Exporter
import com.lexis.reader.data.AbbreviationMode
import com.lexis.reader.data.AppSettings
import com.lexis.reader.data.DictionaryInfo
import com.lexis.reader.data.ExportFormat
import com.lexis.reader.data.HistoryItem
import com.lexis.reader.data.ImportProgress
import com.lexis.reader.data.Suggestion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen {
    data object Home : Screen()
    data class Entry(val word: String) : Screen()
    data object Dictionaries : Screen()
}

/** Result of opening a word: the articles found, or nearby suggestions when nothing matched. */
data class EntryResult(val word: String, val articles: List<Article>, val suggestions: List<Suggestion>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val lexis = app as LexisApp

    val dictionaries: StateFlow<List<DictionaryInfo>> = lexis.dictionaries.dictionaries
    val history: StateFlow<List<HistoryItem>> = lexis.history.items
    val settings: StateFlow<AppSettings> = lexis.settings.state

    // ------------------------------------------------------------ navigation

    var backStack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
        private set

    val current: Screen get() = backStack.last()

    fun navigate(screen: Screen) {
        if (backStack.last() == screen) return
        backStack = backStack + screen
    }

    /** Pops one screen; returns false when already at the root. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack = backStack.dropLast(1)
        return true
    }

    fun home() {
        backStack = listOf(Screen.Home)
    }

    // ------------------------------------------------------------ search

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun setQuery(q: String) {
        _query.value = q
    }

    val suggestions: StateFlow<List<Suggestion>> = _query
        .debounce(90)
        .mapLatest { q -> if (q.isBlank()) emptyList() else lexis.dictionaries.suggest(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openWord(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        navigate(Screen.Entry(w))
    }

    /** Enter pressed in the search box: exact match if any, else the first suggestion, else the query itself. */
    fun submitQuery() {
        val q = _query.value.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            val hits = lexis.dictionaries.lookup(q)
            val target = when {
                hits.isNotEmpty() -> hits.first().word
                else -> suggestions.value.firstOrNull()?.word ?: q
            }
            _query.value = ""
            openWord(target)
        }
    }

    suspend fun loadEntry(word: String): EntryResult {
        val hits = lexis.dictionaries.lookup(word)
        if (hits.isEmpty()) {
            return EntryResult(word, emptyList(), lexis.dictionaries.suggest(word, 30))
        }
        var articles = hits.map { lexis.dictionaries.article(it) }
        val mode = settings.value.abbreviations
        if (mode != AbbreviationMode.TAP) {
            articles = articles.map { a -> a.copy(blocks = expandAbbreviations(a.blocks, mode)) }
        }
        val canonical = hits.first().word
        lexis.history.record(canonical, hits.first().dict.id, articles.firstOrNull()?.snippet() ?: "")
        return EntryResult(canonical, articles, emptyList())
    }

    /** Inline-expand unambiguous abbreviations according to the mode. */
    suspend fun expandAbbreviations(blocks: List<com.lexis.reader.core.Block>, mode: AbbreviationMode): List<com.lexis.reader.core.Block> {
        if (mode == AbbreviationMode.TAP) return blocks
        val full = lexis.dictionaries.expandAbbreviations(Abbreviations.collect(blocks))
        val readings = HashMap<String, String>()
        for ((k, v) in full) Abbreviations.primaryReading(v)?.let { readings[k] = it }
        return Abbreviations.expand(blocks, readings, includeQuotations = mode == AbbreviationMode.EVERYWHERE)
    }

    /** Full expansion of an abbreviation for the tap popup, or null if the dictionary has none. */
    suspend fun abbreviation(text: String): String? = lexis.dictionaries.expandAbbreviation(text)

    suspend fun neighbours(word: String, dictId: Long): List<String> =
        lexis.dictionaries.neighbours(word, dictId, 12, 12)

    fun shareArticles(word: String, articles: List<Article>, includeQuotations: Boolean): Intent {
        val text = Exporter.toText(listOf(ExportEntry(word, articles)), includeQuotations, word, "")
        return lexis.exporter.shareTextIntent(word, text)
    }

    // ------------------------------------------------------------ history

    fun deleteHistory(id: Long) = viewModelScope.launch { lexis.history.delete(id) }
    fun clearHistory() = viewModelScope.launch { lexis.history.clear() }

    fun historySinceLastExport(): List<HistoryItem> = lexis.history.since(settings.value.lastExportAt)
    fun historyAll(): List<HistoryItem> = lexis.history.all()

    // ------------------------------------------------------------ settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) = lexis.settings.update(transform)

    // ------------------------------------------------------------ dictionaries

    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress: StateFlow<ImportProgress?> = _importProgress

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun importTree(uri: Uri) = runImport { lexis.dictionaries.importTree(uri) { _importProgress.value = it } }
    fun importFiles(uris: List<Uri>) = runImport { lexis.dictionaries.importFiles(uris) { _importProgress.value = it } }

    private fun runImport(block: suspend () -> List<DictionaryInfo>) {
        if (_importProgress.value != null) return
        _importProgress.value = ImportProgress("Starting import…", null)
        viewModelScope.launch {
            try {
                val added = block()
                _message.value = if (added.isEmpty()) "Nothing new was imported" else "Added " + added.joinToString { it.name }
            } catch (e: Exception) {
                _message.value = "Import failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                _importProgress.value = null
            }
        }
    }

    fun removeDictionary(id: Long) = viewModelScope.launch { lexis.dictionaries.remove(id) }
    fun moveDictionary(id: Long, up: Boolean) = viewModelScope.launch { lexis.dictionaries.move(id, up) }

    // ------------------------------------------------------------ export

    var exporting by mutableStateOf(false)
        private set

    /**
     * Export history items and hand back a share intent. Remembers the chosen options and the
     * export time so that "only new words" works next time.
     */
    fun export(format: ExportFormat, includeQuotations: Boolean, onlySinceLastExport: Boolean, onReady: (Intent) -> Unit) {
        if (exporting) return
        val items = if (onlySinceLastExport) historySinceLastExport() else historyAll()
        if (items.isEmpty()) {
            _message.value = "Nothing to export"
            return
        }
        exporting = true
        viewModelScope.launch {
            try {
                val result = lexis.exporter.export(items, format, includeQuotations, settings.value.abbreviations)
                lexis.settings.update {
                    it.copy(lastExportAt = System.currentTimeMillis(), exportFormat = format, exportQuotations = includeQuotations)
                }
                onReady(lexis.exporter.shareIntent(result))
            } catch (e: Exception) {
                _message.value = "Export failed: " + (e.message ?: e.javaClass.simpleName)
            } finally {
                exporting = false
            }
        }
    }
}
