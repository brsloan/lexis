package com.lexis.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.lexis.desktop.data.HistoryItem
import com.lexis.desktop.data.Suggestion
import java.text.DateFormat
import java.util.Date

@Composable
fun Sidebar(state: AppState, modifier: Modifier = Modifier) {
    val query = state.query
    val suggestions by state.suggestions.collectAsState()
    val history by state.history.collectAsState()
    val settings by state.settings.collectAsState()
    val dictionaries by state.dictionaries.collectAsState()

    // Local field value so the selection can be controlled (select-all on Ctrl+L).
    var field by remember { mutableStateOf(TextFieldValue(query)) }
    if (field.text != query) field = TextFieldValue(query, TextRange(query.length))

    var highlighted by remember { mutableIntStateOf(-1) }
    LaunchedEffect(suggestions) { highlighted = -1 }

    val focus = remember { FocusRequester() }
    LaunchedEffect(state.focusSearchRequest) {
        runCatching { focus.requestFocus() }
        field = field.copy(selection = TextRange(0, field.text.length))
    }

    val suggestionList = rememberLazyListState()
    LaunchedEffect(highlighted) {
        if (highlighted >= 0) suggestionList.animateScrollToItem(highlighted)
    }

    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != query) state.query = it.text
            },
            singleLine = true,
            placeholder = { Text("Look up a word") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { state.query = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> {
                            if (suggestions.isNotEmpty()) highlighted = (highlighted + 1).coerceAtMost(suggestions.size - 1)
                            true
                        }
                        Key.DirectionUp -> {
                            if (suggestions.isNotEmpty()) highlighted = (highlighted - 1).coerceAtLeast(-1)
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            val pick = suggestions.getOrNull(highlighted)
                            if (pick != null && query.isNotBlank()) state.openSuggestion(pick.word) else state.submitQuery()
                            true
                        }
                        Key.Escape -> {
                            if (query.isNotEmpty()) {
                                state.query = ""
                                true
                            } else false
                        }
                        else -> false
                    }
                },
        )

        if (query.isNotBlank()) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = suggestionList, contentPadding = PaddingValues(bottom = 16.dp)) {
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
                        SuggestionRow(
                            s = suggestions[i],
                            showDictionaries = dictionaries.size > 1,
                            selected = i == highlighted,
                            onClick = { state.openSuggestion(suggestions[i].word) },
                        )
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(suggestionList), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        } else {
            Text(
                if (history.isEmpty()) "Recent lookups" else "Recent lookups · ${history.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (history.isEmpty()) {
                Text(
                    "Words you look up are listed here, ready to reopen or export.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            val historyList = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = historyList, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(history.size, key = { history[it].id }) { i ->
                        val h = history[i]
                        HistoryRow(
                            h = h,
                            isNew = h.lastAt > settings.lastExportAt,
                            current = h.word == state.currentWord,
                            onOpen = { state.openWord(h.word) },
                            onDelete = { state.deleteHistory(h.id) },
                        )
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(historyList), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, showDictionaries: Boolean, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(s.word, style = MaterialTheme.typography.bodyLarge)
        if (showDictionaries) {
            Text(
                s.dictionaries.joinToString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HistoryRow(h: HistoryItem, isNew: Boolean, current: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .hoverable(interaction)
            .clickable(onClick = onOpen)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                h.word,
                fontWeight = if (isNew) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (h.snippet.isNotEmpty()) {
                Text(
                    h.snippet,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(h.lastAt)) + (if (h.count > 1) " · ${h.count}×" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (hovered) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove from history", modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.size(32.dp))
        }
    }
}
