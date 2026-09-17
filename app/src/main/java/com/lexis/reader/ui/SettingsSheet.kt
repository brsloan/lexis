package com.lexis.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lexis.reader.data.AbbreviationMode
import com.lexis.reader.data.AppSettings
import com.lexis.reader.data.QuotationsMode
import com.lexis.reader.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Text("Text size · ${(settings.fontScale * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.fontScale,
                onValueChange = { v -> onUpdate { it.copy(fontScale = (Math.round(v * 20) / 20f)) } },
                valueRange = 0.7f..1.8f,
                steps = 21,
            )

            Spacer(Modifier.height(8.dp))
            Text("Quotations", style = MaterialTheme.typography.titleSmall)
            Text(
                "How the illustrative quotations under each sense are shown when an entry opens. Tap any quotation block to toggle it.",
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
                "Tapping any abbreviation (Obs., OFr., Shakes.) shows what it stands for. Inline expansion only replaces unambiguous ones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RadioRow("Tap to reveal only", settings.abbreviations == AbbreviationMode.TAP) {
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
        Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
