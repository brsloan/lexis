package com.lexis.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import com.lexis.desktop.data.AppPaths
import com.lexis.desktop.data.AppSettings
import com.lexis.desktop.data.ThemeMode
import com.lexis.desktop.ui.theme.LexisTheme
import java.text.NumberFormat

/** Primary-modifier shortcut: Cmd on macOS, Ctrl elsewhere. */
private fun primary(key: Key, shift: Boolean = false) =
    if (AppPaths.isMac) KeyShortcut(key, meta = true, shift = shift) else KeyShortcut(key, ctrl = true, shift = shift)

/** Which dialogs are open, plus commands for the entry pane; shared by the menu bar and the content. */
class UiState {
    var showDictionaries by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showExport by mutableStateOf(false)
    var confirmClear by mutableStateOf(false)
    val commands = EntryCommands()
}

@Composable
fun FrameWindowScope.LexisWindowContent(state: AppState, settings: AppSettings) {
    val ui = remember { UiState() }
    LexisMenuBar(state, settings, ui)
    LexisMain(state, settings, ui, window)
}

@Composable
private fun FrameWindowScope.LexisMenuBar(state: AppState, settings: AppSettings, ui: UiState) {
    val history by state.history.collectAsState()
    val commands = ui.commands
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("Look up…", shortcut = primary(Key.L), onClick = state::focusSearch)
            Item("Look up clipboard text", shortcut = primary(Key.V, shift = true), onClick = state::lookUpClipboard)
            Separator()
            Item("Dictionaries…", shortcut = primary(Key.D), onClick = { ui.showDictionaries = true })
            Item("Export word list…", shortcut = primary(Key.E), onClick = { ui.showExport = true })
            Item("Settings…", shortcut = primary(Key.Comma), onClick = { ui.showSettings = true })
            if (!AppPaths.isMac) {
                Separator()
                Item("Exit", onClick = { window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING)) })
            }
        }
        Menu("Entry", mnemonic = 'E') {
            Item(
                "Back",
                enabled = state.canGoBack,
                shortcut = if (AppPaths.isMac) KeyShortcut(Key.LeftBracket, meta = true) else KeyShortcut(Key.DirectionLeft, alt = true),
                onClick = state::back,
            )
            Item(
                "Forward",
                enabled = state.canGoForward,
                shortcut = if (AppPaths.isMac) KeyShortcut(Key.RightBracket, meta = true) else KeyShortcut(Key.DirectionRight, alt = true),
                onClick = state::forward,
            )
            Separator()
            Item("Find in entry…", enabled = state.currentWord != null, shortcut = primary(Key.F), onClick = { commands.findRequest++ })
            Item("Copy entry as text", enabled = state.currentWord != null, shortcut = primary(Key.C, shift = true), onClick = { commands.copyRequest++ })
            Separator()
            Item("Expand all quotations", enabled = state.currentWord != null, shortcut = primary(Key.RightBracket, shift = true), onClick = { commands.expandAllRequest++ })
            Item("Collapse all quotations", enabled = state.currentWord != null, shortcut = primary(Key.LeftBracket, shift = true), onClick = { commands.collapseAllRequest++ })
        }
        Menu("View", mnemonic = 'V') {
            Item("Larger text", shortcut = primary(Key.Equals), onClick = { state.changeFontScale(0.1f) })
            Item("Smaller text", shortcut = primary(Key.Minus), onClick = { state.changeFontScale(-0.1f) })
            Item("Actual size", shortcut = primary(Key.Zero), onClick = state::resetFontScale)
            Separator()
            CheckboxItem(
                "Senses and nearby words panel",
                checked = settings.showOutline,
                shortcut = primary(Key.O),
                onCheckedChange = { v -> state.updateSettings { it.copy(showOutline = v) } },
            )
            CheckboxItem("Serif typeface", checked = settings.serif, onCheckedChange = { v -> state.updateSettings { it.copy(serif = v) } })
            Separator()
            RadioButtonItem("Follow system theme", selected = settings.theme == ThemeMode.SYSTEM, onClick = { state.updateSettings { it.copy(theme = ThemeMode.SYSTEM) } })
            RadioButtonItem("Light", selected = settings.theme == ThemeMode.LIGHT, onClick = { state.updateSettings { it.copy(theme = ThemeMode.LIGHT) } })
            RadioButtonItem("Dark", selected = settings.theme == ThemeMode.DARK, onClick = { state.updateSettings { it.copy(theme = ThemeMode.DARK) } })
        }
        Menu("History", mnemonic = 'H') {
            Item("Export word list…", onClick = { ui.showExport = true })
            Item("Clear history…", enabled = history.isNotEmpty(), onClick = { ui.confirmClear = true })
        }
    }
}

/** Window content below the menu bar. [parent] owns the native file dialogs. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LexisMain(state: AppState, settings: AppSettings, ui: UiState, parent: java.awt.Frame?) {
    val history by state.history.collectAsState()
    val commands = ui.commands
    var showDictionaries by ui::showDictionaries
    var showSettings by ui::showSettings
    var showExport by ui::showExport
    var confirmClear by ui::confirmClear

    val addFolder = {
        FilePickers.chooseFolder(parent, "Choose a folder containing StarDict dictionaries", settings.lastAddFolder)?.let(state::addFolder)
        Unit
    }
    val addFile = {
        FilePickers.chooseIfo(parent, settings.lastAddFolder)?.let(state::addIfo)
        Unit
    }

    LexisTheme(settings.theme) {
        Surface(
            Modifier
                .fillMaxSize()
                // Mouse back / forward buttons navigate like a browser.
                .onPointerEvent(PointerEventType.Press) { e ->
                    when (e.button) {
                        PointerButton.Back -> state.back()
                        PointerButton.Forward -> state.forward()
                        else -> {}
                    }
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    Sidebar(state, Modifier.width(300.dp).fillMaxHeight())
                    VerticalDivider()
                    val word = state.currentWord
                    if (word != null) {
                        EntryPane(state, word, commands, Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Welcome(state, onOpenDictionaries = { showDictionaries = true }, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
                Snackbars(state, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }

            if (showDictionaries) DictionariesDialog(state, onAddFolder = addFolder, onAddFile = addFile, onDismiss = { showDictionaries = false })
            if (showSettings) SettingsDialog(state, onDismiss = { showSettings = false })
            if (showExport) {
                ExportDialog(
                    state,
                    onExport = { format, quotations, onlyNew ->
                        val target = FilePickers.chooseSaveFile(parent, "Save word list", settings.lastExportFolder, state.defaultExportName(format))
                        if (target != null) state.export(format, quotations, onlyNew, target) { showExport = false }
                    },
                    onDismiss = { showExport = false },
                )
            }
            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = { Text("Clear history?") },
                    text = { Text("This removes all ${history.size} looked-up words from the list. Dictionaries are not affected.") },
                    confirmButton = { TextButton(onClick = { state.clearHistory(); confirmClear = false }) { Text("Clear") } },
                    dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

@Composable
private fun Snackbars(state: AppState, modifier: Modifier) {
    val host = remember { SnackbarHostState() }
    val message by state.message.collectAsState()
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        state.consumeMessage()
        val result = host.showSnackbar(
            message = m.text,
            actionLabel = m.actionLabel,
            withDismissAction = m.actionLabel != null,
            duration = if (m.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
    }
    SnackbarHost(host, modifier.widthIn(max = 640.dp))
}

@Composable
private fun Welcome(state: AppState, onOpenDictionaries: () -> Unit, modifier: Modifier) {
    val dictionaries by state.dictionaries.collectAsState()
    val mod = if (AppPaths.isMac) "⌘" else "Ctrl+"
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).padding(32.dp)) {
            Text("Lexis", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            if (dictionaries.isEmpty()) {
                Text(
                    "Add a folder containing StarDict dictionaries (.ifo, .idx and .dict.dz files) to start looking up words.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onOpenDictionaries) { Text("Add dictionaries…") }
            } else {
                val words = dictionaries.sumOf { it.wordCount.toLong() }
                Text(
                    "${dictionaries.size} ${if (dictionaries.size == 1) "dictionary" else "dictionaries"} · " +
                        NumberFormat.getInstance().format(words) + " headwords",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                val alt = if (AppPaths.isMac) "⌘[ / ⌘]" else "Alt+← / Alt+→"
                listOf(
                    "${mod}L" to "Look up a word",
                    "↑ ↓  Enter" to "Pick a suggestion",
                    "${mod}⇧V".replace("Ctrl+⇧", "Ctrl+Shift+") to "Look up the clipboard",
                    "${mod}F" to "Find in the entry",
                    alt to "Back / forward",
                    "${mod}= / ${mod}-" to "Text size",
                ).forEach { (keys, what) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(keys, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(150.dp))
                        Text(what, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
