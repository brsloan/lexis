package com.lexis.reader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lexis.reader.data.DictionaryInfo
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val dictionaries by vm.dictionaries.collectAsStateWithLifecycle()
    val progress by vm.importProgress.collectAsStateWithLifecycle()
    var toRemove by remember { mutableStateOf<DictionaryInfo?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.importTree(uri)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dictionaries") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "StarDict dictionaries are copied into the app and indexed once. " +
                            "Pick the folder that holds the .ifo, .idx and .dict.dz files (sub-folders are searched too), or select the files directly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { pickFolder.launch(null) }, enabled = progress == null) { Text("Add folder") }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { pickFiles.launch(arrayOf("*/*")) }, enabled = progress == null) { Text("Add files") }
                    }
                }
            }
            val p = progress
            if (p != null) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(p.message, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            val f = p.fraction
                            if (f == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (dictionaries.isEmpty() && p == null) {
                item {
                    Text(
                        "No dictionaries installed.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(dictionaries.size, key = { dictionaries[it].id }) { i ->
                val d = dictionaries[i]
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = {
                        Text(NumberFormat.getInstance().format(d.wordCount) + " headwords" + (d.sameTypeSequence?.let { " · type $it" } ?: ""))
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.moveDictionary(d.id, up = true) }, enabled = i > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(onClick = { vm.moveDictionary(d.id, up = false) }, enabled = i < dictionaries.size - 1) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                            }
                            IconButton(onClick = { toRemove = d }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    val r = toRemove
    if (r != null) {
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove dictionary?") },
            text = { Text("“${r.name}” and its index will be deleted from the app. Your original files are not touched.") },
            confirmButton = { TextButton(onClick = { vm.removeDictionary(r.id); toRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}
