package com.lexis.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lexis.reader.data.ExportFormat

@Composable
fun ExportDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val newCount = remember(history, settings.lastExportAt) { history.count { it.lastAt > settings.lastExportAt } }

    var format by remember { mutableStateOf(settings.exportFormat) }
    var quotations by remember { mutableStateOf(settings.exportQuotations) }
    var onlyNew by remember { mutableStateOf(settings.lastExportAt > 0L && newCount > 0) }

    val count = if (onlyNew) newCount else history.size

    AlertDialog(
        onDismissRequest = { if (!vm.exporting) onDismiss() },
        title = { Text("Export word list") },
        text = {
            Column {
                Text(
                    "Creates a file with every looked-up word and its full entry, then opens the share sheet so you can email it to yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text("Format", style = MaterialTheme.typography.titleSmall)
                ChoiceRow("HTML (best for reading in email)", format == ExportFormat.HTML) { format = ExportFormat.HTML }
                ChoiceRow("Plain text", format == ExportFormat.TEXT) { format = ExportFormat.TEXT }
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
            TextButton(
                enabled = !vm.exporting && count > 0,
                onClick = {
                    vm.export(format, quotations, onlyNew) { intent ->
                        context.startActivity(intent)
                        onDismiss()
                    }
                },
            ) { Text(if (vm.exporting) "Preparing…" else "Export & share") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !vm.exporting) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 2.dp),
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
