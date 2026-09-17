package com.lexis.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenWord: (String) -> Unit, onOpenDictionaries: () -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val dictionaries by vm.dictionaries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var menuOpen by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lexis") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Export word list…") }, onClick = { menuOpen = false; showExport = true })
                        DropdownMenuItem(text = { Text("Dictionaries") }, onClick = { menuOpen = false; onOpenDictionaries() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menuOpen = false; showSettings = true })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Clear history") }, onClick = { menuOpen = false; confirmClear = true })
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                singleLine = true,
                placeholder = { Text("Look up a word") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitQuery() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (dictionaries.isEmpty()) {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No dictionaries yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add a folder containing StarDict files (.ifo, .idx and .dict.dz) to start looking up words.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenDictionaries) { Text("Add dictionaries") }
                    }
                }
            }

            if (query.isNotBlank()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (suggestions.isEmpty()) {
                        item {
                            Text(
                                "No matches",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                    items(suggestions.size, key = { suggestions[it].word }) { i ->
                        val s = suggestions[i]
                        val supporting: (@Composable () -> Unit)? =
                            if (s.dictionaries.size > 1 || dictionaries.size > 1) {
                                { Text(s.dictionaries.joinToString(), style = MaterialTheme.typography.labelSmall) }
                            } else null
                        ListItem(
                            headlineContent = { Text(s.word) },
                            supportingContent = supporting,
                            modifier = Modifier.clickable { vm.setQuery(""); onOpenWord(s.word) },
                        )
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (history.isEmpty()) "Recent lookups" else "Recent lookups · ${history.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (history.isEmpty()) {
                    Text(
                        "Words you look up will be listed here, ready to reopen or export.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(history.size, key = { history[it].id }) { i ->
                        val h = history[i]
                        val isNew = h.lastAt > settings.lastExportAt
                        ListItem(
                            headlineContent = {
                                Text(h.word, fontWeight = if (isNew) FontWeight.SemiBold else FontWeight.Normal)
                            },
                            supportingContent = {
                                Column {
                                    if (h.snippet.isNotEmpty()) {
                                        Text(h.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(h.lastAt)) +
                                            (if (h.count > 1) " · ${h.count}×" else ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { vm.deleteHistory(h.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove from history")
                                }
                            },
                            modifier = Modifier.clickable { onOpenWord(h.word) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showExport) ExportDialog(vm, onDismiss = { showExport = false })
    if (showSettings) SettingsSheet(settings, onUpdate = vm::updateSettings, onDismiss = { showSettings = false })
    if (confirmClear) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes all ${history.size} looked-up words from the home screen. Dictionaries are not affected.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.clearHistory(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
