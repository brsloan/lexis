package com.lexis.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lexis.reader.core.Article
import com.lexis.reader.core.Block
import com.lexis.reader.core.OutlineItem
import com.lexis.reader.core.outline
import com.lexis.reader.core.plain
import com.lexis.reader.data.QuotationsMode
import kotlinx.coroutines.launch

/** One row of the entry list: either a dictionary header or a block of an article. */
private sealed class EntryRow {
    abstract val key: String

    data class Header(val articleIndex: Int, val article: Article) : EntryRow() {
        override val key = "h$articleIndex"
    }

    data class BlockRow(val articleIndex: Int, val blockIndex: Int, val block: Block) : EntryRow() {
        override val key = "$articleIndex-$blockIndex"
    }
}

private fun EntryRow.searchText(): String = when (this) {
    is EntryRow.Header -> article.dictionaryName
    is EntryRow.BlockRow -> when (val b = block) {
        is Block.Headword -> b.text.plain()
        is Block.HomographHeader -> b.label + " " + b.text.plain()
        is Block.Pronunciation -> b.text.plain()
        is Block.Etymology -> b.text.plain()
        is Block.Sense -> b.label + " " + b.text.plain()
        is Block.Paragraph -> b.text.plain()
        is Block.Quotations -> b.items.joinToString(" ") { it.before.plain() + " " + it.quote.plain() }
        Block.Rule -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(vm: AppViewModel, word: String, onBack: () -> Unit, onOpenWord: (String) -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val result by produceState<EntryResult?>(initialValue = null, key1 = word) { value = vm.loadEntry(word) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val rows: List<EntryRow> = remember(result) {
        val r = result
        val list = ArrayList<EntryRow>()
        if (r != null) {
            r.articles.forEachIndexed { ai, a ->
                if (r.articles.size > 1) list.add(EntryRow.Header(ai, a))
                a.blocks.forEachIndexed { bi, b -> list.add(EntryRow.BlockRow(ai, bi, b)) }
            }
        }
        list
    }
    val rowIndex = remember(rows) { rows.withIndex().associate { it.value.key to it.index } }

    // Per-block expansion choices; a global override wins when set.
    val expanded = remember(word) { mutableStateMapOf<String, Boolean>() }
    var allQuotes by remember(word) { mutableStateOf<Boolean?>(null) }
    val etymExpanded = remember(word) { mutableStateMapOf<String, Boolean>() }

    var menuOpen by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showNearby by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember(word) { mutableStateOf("") }
    var findPos by remember(word) { mutableStateOf(0) }

    val matches = remember(rows, findQuery) {
        val q = findQuery.trim()
        if (q.length < 2) emptyList() else rows.indices.filter { rows[it].searchText().contains(q, ignoreCase = true) }
    }
    LaunchedEffect(matches, findPos) {
        if (matches.isNotEmpty()) listState.animateScrollToItem(matches[findPos.coerceIn(0, matches.size - 1)])
    }

    val onLink: (String) -> Unit = remember(onOpenWord) { { target -> onOpenWord(target) } }
    var abbrTapped by remember { mutableStateOf<String?>(null) }
    val onAbbreviation: (String) -> Unit = remember { { text -> abbrTapped = text } }
    val styler = rememberRunStyler(onLink, onAbbreviation)
    val typo = rememberArticleTypography(settings.fontScale, settings.serif)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(result?.word ?: word, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val r = result
                            if (r != null && r.articles.size > 1) {
                                Text("${r.articles.size} entries", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        IconButton(onClick = { findOpen = !findOpen; if (!findOpen) findQuery = "" }) {
                            Icon(Icons.Default.Search, contentDescription = "Find in entry")
                        }
                        val hasOutline = result?.articles?.any { it.outline().isNotEmpty() } == true
                        if (hasOutline) {
                            IconButton(onClick = { showOutline = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Senses")
                            }
                        }
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Expand all quotations") },
                                onClick = { allQuotes = true; expanded.clear(); menuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Collapse all quotations") },
                                onClick = { allQuotes = false; expanded.clear(); menuOpen = false },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Larger text") },
                                onClick = { vm.updateSettings { it.copy(fontScale = (it.fontScale + 0.1f).coerceAtMost(1.8f)) }; menuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Smaller text") },
                                onClick = { vm.updateSettings { it.copy(fontScale = (it.fontScale - 0.1f).coerceAtLeast(0.7f)) }; menuOpen = false },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Nearby words") },
                                onClick = { showNearby = true; menuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Share entry as text") },
                                onClick = {
                                    menuOpen = false
                                    val r = result
                                    if (r != null && r.articles.isNotEmpty()) {
                                        context.startActivity(vm.shareArticles(r.word, r.articles, allQuotes ?: (settings.quotations != QuotationsMode.COLLAPSED)))
                                    }
                                },
                            )
                        }
                    },
                )
                if (findOpen) {
                    FindBar(
                        query = findQuery,
                        onQuery = { findQuery = it; findPos = 0 },
                        count = matches.size,
                        pos = findPos,
                        onPrev = { if (matches.isNotEmpty()) findPos = (findPos - 1 + matches.size) % matches.size },
                        onNext = { if (matches.isNotEmpty()) findPos = (findPos + 1) % matches.size },
                        onClose = { findOpen = false; findQuery = "" },
                    )
                }
            }
        },
    ) { padding ->
        val r = result
        when {
            r == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            r.articles.isEmpty() -> NotFound(r, onOpenWord, Modifier.padding(padding))
            else -> {
                val matchSet = remember(matches) { matches.toHashSet() }
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(bottom = 48.dp),
                    ) {
                        items(count = rows.size, key = { rows[it].key }) { i ->
                            when (val row = rows[i]) {
                                is EntryRow.Header -> DictionaryHeader(row.article.dictionaryName)
                                is EntryRow.BlockRow -> BlockView(
                                    block = row.block,
                                    typo = typo,
                                    styler = styler,
                                    quotesMode = settings.quotations,
                                    quotesExpanded = allQuotes ?: expanded[row.key],
                                    etymExpanded = etymExpanded[row.key] == true,
                                    highlighted = i in matchSet,
                                    onToggle = {
                                        when (row.block) {
                                            is Block.Quotations -> {
                                                val cur = allQuotes ?: expanded[row.key] ?: (settings.quotations == QuotationsMode.EXPANDED)
                                                expanded[row.key] = !cur
                                                allQuotes = null
                                            }
                                            is Block.Etymology -> etymExpanded[row.key] = !(etymExpanded[row.key] ?: false)
                                            else -> {}
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val tapped = abbrTapped
    if (tapped != null) {
        val expansion by produceState<String?>(initialValue = null, key1 = tapped) { value = vm.abbreviation(tapped) ?: "" }
        AlertDialog(
            onDismissRequest = { abbrTapped = null },
            title = { Text(tapped) },
            text = {
                when (val e = expansion) {
                    null -> Text("Looking up…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    "" -> Text("No expansion found in the installed dictionaries.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Text(e, style = MaterialTheme.typography.bodyLarge)
                }
            },
            confirmButton = { TextButton(onClick = { abbrTapped = null }) { Text("Close") } },
        )
    }

    if (showOutline && result != null) {
        ModalBottomSheet(onDismissRequest = { showOutline = false }) {
            OutlineSheet(result!!.articles, rowIndex) { articleIndex, blockIndex ->
                showOutline = false
                val idx = rowIndex["$articleIndex-$blockIndex"] ?: return@OutlineSheet
                scope.launch { listState.animateScrollToItem(idx) }
            }
        }
    }

    if (showNearby && result != null && result!!.articles.isNotEmpty()) {
        val r = result!!
        val words by produceState<List<String>?>(initialValue = null, key1 = r.word) {
            value = vm.neighbours(r.word, r.articles.first().dictionaryId)
        }
        ModalBottomSheet(onDismissRequest = { showNearby = false }) {
            Text("Nearby words", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            val list = words
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(list.size) { i ->
                        val w = list[i]
                        val isCurrent = w == r.word
                        ListItem(
                            headlineContent = { Text(w, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.clickable(enabled = !isCurrent) { showNearby = false; onOpenWord(w) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DictionaryHeader(name: String) {
    Text(
        name.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp),
    )
}

@Composable
private fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    count: Int,
    pos: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Find in entry") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
        )
        Text(
            if (count == 0) "0" else "${pos + 1}/$count",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        IconButton(onClick = onPrev, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match") }
        IconButton(onClick = onNext, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match") }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close find") }
    }
}

@Composable
private fun OutlineSheet(articles: List<Article>, rowIndex: Map<String, Int>, onPick: (Int, Int) -> Unit) {
    val entries = remember(articles) {
        buildList {
            articles.forEachIndexed { ai, a ->
                val items = a.outline()
                if (items.isNotEmpty()) add(Triple(ai, -1, OutlineItem(-1, "", a.dictionaryName, 0)))
                items.forEach { add(Triple(ai, it.blockIndex, it)) }
            }
        }
    }
    Text("Senses", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        items(entries.size) { i ->
            val (ai, bi, item) = entries[i]
            if (bi < 0) {
                if (articles.size > 1) DictionaryHeader(item.snippet)
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(ai, bi) }
                        .padding(start = (20 + item.level * 18).dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                ) {
                    Text(
                        item.label,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        item.snippet,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotFound(r: EntryResult, onOpenWord: (String) -> Unit, modifier: Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Text(
                "No entry for “${r.word}”",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (r.suggestions.isNotEmpty()) {
                Text(
                    "Did you mean:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        items(r.suggestions.size) { i ->
            val s = r.suggestions[i]
            val supporting: (@Composable () -> Unit)? =
                if (s.dictionaries.size > 1) { { Text(s.dictionaries.joinToString()) } } else null
            ListItem(
                headlineContent = { Text(s.word) },
                supportingContent = supporting,
                modifier = Modifier.clickable { onOpenWord(s.word) },
            )
        }
    }
}
