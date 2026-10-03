package com.lexis.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lexis.desktop.data.AbbreviationMode
import com.lexis.desktop.data.DictionaryInfo
import com.lexis.desktop.data.ExportFormat
import com.lexis.desktop.data.QuotationsMode
import com.lexis.desktop.data.ThemeMode
import java.text.NumberFormat

/** A titled panel dialog wider than [AlertDialog] allows, for lists and settings. */
@Composable
private fun PanelDialog(title: String, onDismiss: () -> Unit, width: Int = 620, content: @Composable () -> Unit) {
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.width(width.dp).heightIn(max = 640.dp),
        ) {
            Column(Modifier.padding(top = 20.dp, bottom = 12.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.weight(1f, fill = false)) {
                    Box(Modifier.verticalScroll(scroll).padding(horizontal = 24.dp)) { content() }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.matchParentSize().wrapContentWidth(Alignment.End).padding(end = 4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ dictionaries

@Composable
fun DictionariesDialog(state: AppState, onAddFolder: () -> Unit, onAddFile: () -> Unit, onDismiss: () -> Unit) {
    val dictionaries by state.dictionaries.collectAsState()
    val progress by state.importProgress.collectAsState()
    var toRemove by remember { mutableStateOf<DictionaryInfo?>(null) }

    PanelDialog("Dictionaries", onDismiss) {
        Column {
            Text(
                "Lexis reads StarDict dictionaries where they are on disk and builds a search index for them " +
                    "(a very large dictionary takes a minute). Choose a folder to add every dictionary inside it, " +
                    "or pick one dictionary's .ifo file. Keep the files where they are after adding them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onAddFolder, enabled = progress == null) { Text("Add folder…") }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = onAddFile, enabled = progress == null) { Text("Add .ifo file…") }
            }
            val p = progress
            if (p != null) {
                Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        val f = p.fraction
                        if (f == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                        else LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (dictionaries.isEmpty() && p == null) {
                Text("No dictionaries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            dictionaries.forEachIndexed { i, d ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            NumberFormat.getInstance().format(d.wordCount) + " headwords" + (d.sameTypeSequence?.let { " · type $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!d.available) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 4.dp).width(14.dp))
                            }
                            Text(
                                if (d.available) d.file.path else "Missing: ${d.file.path}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (d.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = { state.moveDictionary(d.id, up = true) }, enabled = i > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { state.moveDictionary(d.id, up = false) }, enabled = i < dictionaries.size - 1) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { toRemove = d }, enabled = progress == null) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
    }

    val r = toRemove
    if (r != null) {
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove dictionary?") },
            text = { Text("“${r.name}” and its search index will be removed from Lexis. Your dictionary files are not touched.") },
            confirmButton = { TextButton(onClick = { state.removeDictionary(r.id); toRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}

// ------------------------------------------------------------------------------------ settings

@Composable
fun SettingsDialog(state: AppState, onDismiss: () -> Unit) {
    val settings by state.settings.collectAsState()
    val onUpdate = state::updateSettings
    PanelDialog("Settings", onDismiss, width = 560) {
        Column {
            Text("Text size · ${(settings.fontScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.fontScale,
                onValueChange = { v -> onUpdate { it.copy(fontScale = Math.round(v * 20) / 20f) } },
                valueRange = 0.7f..1.8f,
                steps = 21,
            )

            Spacer(Modifier.height(8.dp))
            Text("Quotations", style = MaterialTheme.typography.titleSmall)
            Text(
                "How the illustrative quotations under each sense are shown when an entry opens. Click any quotation block to toggle it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RadioRow("Collapsed – show count and date range only", settings.quotations == QuotationsMode.COLLAPSED) {
                onUpdate { it.copy(quotations = QuotationsMode.COLLAPSED) }
            }
            RadioRow("First quotation only", settings.quotations == QuotationsMode.FIRST) {
                onUpdate { it.copy(quotations = QuotationsMode.FIRST) }
            }
            RadioRow("All quotations", settings.quotations == QuotationsMode.EXPANDED) {
                onUpdate { it.copy(quotations = QuotationsMode.EXPANDED) }
            }

            Spacer(Modifier.height(8.dp))
            Text("Abbreviations", style = MaterialTheme.typography.titleSmall)
            Text(
                "Clicking any abbreviation (Obs., OFr., Shakes.) shows what it stands for. Inline expansion only replaces unambiguous ones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RadioRow("Click to reveal only", settings.abbreviations == AbbreviationMode.TAP) {
                onUpdate { it.copy(abbreviations = AbbreviationMode.TAP) }
            }
            RadioRow("Expand in definitions and etymologies", settings.abbreviations == AbbreviationMode.DEFINITIONS) {
                onUpdate { it.copy(abbreviations = AbbreviationMode.DEFINITIONS) }
            }
            RadioRow("Expand everywhere, including quotation sources", settings.abbreviations == AbbreviationMode.EVERYWHERE) {
                onUpdate { it.copy(abbreviations = AbbreviationMode.EVERYWHERE) }
            }

            Spacer(Modifier.height(8.dp))
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            RadioRow("Follow system", settings.theme == ThemeMode.SYSTEM) { onUpdate { it.copy(theme = ThemeMode.SYSTEM) } }
            RadioRow("Light", settings.theme == ThemeMode.LIGHT) { onUpdate { it.copy(theme = ThemeMode.LIGHT) } }
            RadioRow("Dark", settings.theme == ThemeMode.DARK) { onUpdate { it.copy(theme = ThemeMode.DARK) } }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onUpdate { it.copy(serif = !it.serif) } }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Serif typeface for entries", style = MaterialTheme.typography.bodyLarge)
                    Text("Book-like reading; turn off for the system sans-serif.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.serif, onCheckedChange = { v -> onUpdate { it.copy(serif = v) } })
            }
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).pointerHoverIcon(PointerIcon.Hand).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------------------------------ export

/** Export options; [onExport] is called with the chosen options and should ask where to save. */
@Composable
fun ExportDialog(state: AppState, onExport: (ExportFormat, Boolean, Boolean) -> Unit, onDismiss: () -> Unit) {
    val settings by state.settings.collectAsState()
    val history by state.history.collectAsState()
    val newCount = remember(history, settings.lastExportAt) { history.count { it.lastAt > settings.lastExportAt } }

    var format by remember { mutableStateOf(settings.exportFormat) }
    var quotations by remember { mutableStateOf(settings.exportQuotations) }
    var onlyNew by remember { mutableStateOf(settings.lastExportAt > 0L && newCount > 0) }

    val count = if (onlyNew) newCount else history.size

    AlertDialog(
        onDismissRequest = { if (!state.exporting) onDismiss() },
        title = { Text("Export word list") },
        text = {
            Column {
                Text(
                    "Saves a file with every looked-up word and its full entry, for reviewing, printing or emailing to yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text("Format", style = MaterialTheme.typography.titleSmall)
                RadioRow("HTML (best for reading and printing)", format == ExportFormat.HTML) { format = ExportFormat.HTML }
                RadioRow("Plain text", format == ExportFormat.TEXT) { format = ExportFormat.TEXT }
                Spacer(Modifier.height(8.dp))
                CheckRow("Include quotations", quotations) { quotations = it }
                CheckRow(
                    if (settings.lastExportAt > 0L) "Only words since last export ($newCount)" else "Only words since last export",
                    onlyNew,
                    enabled = settings.lastExportAt > 0L,
                ) { onlyNew = it }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (count == 1) "1 word will be exported." else "$count words will be exported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !state.exporting && count > 0, onClick = { onExport(format, quotations, onlyNew) }) {
                Text(if (state.exporting) "Preparing…" else "Save…")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.exporting) { Text("Cancel") } },
    )
}
