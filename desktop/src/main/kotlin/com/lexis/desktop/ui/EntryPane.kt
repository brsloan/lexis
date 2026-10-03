package com.lexis.desktop.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lexis.desktop.data.QuotationsMode
import com.lexis.reader.core.Article
import com.lexis.reader.core.Block
import com.lexis.reader.core.OutlineItem
import com.lexis.reader.core.outline
import com.lexis.reader.core.plain
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

/** Commands from the menu bar / keyboard shortcuts that the entry pane reacts to. */
class EntryCommands {
    var findRequest by mutableIntStateOf(0)
    var expandAllRequest by mutableIntStateOf(0)
    var collapseAllRequest by mutableIntStateOf(0)
    var copyRequest by mutableIntStateOf(0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryPane(state: AppState, word: String, commands: EntryCommands, modifier: Modifier = Modifier) {
    val settings by state.settings.collectAsState()
    val result by produceState<EntryResult?>(initialValue = null, key1 = word, key2 = settings.abbreviations) {
        value = state.loadEntry(word)
    }
    val scope = rememberCoroutineScope()
    val listState = remember(word) { LazyListState() }

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
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember(word) { mutableStateOf("") }
    var findPos by remember(word) { mutableIntStateOf(0) }
    val findFocus = remember { FocusRequester() }

    val matches = remember(rows, findQuery) {
        val q = findQuery.trim()
        if (q.length < 2) emptyList() else rows.indices.filter { rows[it].searchText().contains(q, ignoreCase = true) }
    }
    LaunchedEffect(matches, findPos) {
        if (matches.isNotEmpty()) listState.animateScrollToItem(matches[findPos.coerceIn(0, matches.size - 1)])
    }

    // React to menu-bar commands (skip the initial values).
    val initialFind = remember { commands.findRequest }
    LaunchedEffect(commands.findRequest) {
        if (commands.findRequest != initialFind) {
            findOpen = true
            runCatching { findFocus.requestFocus() }
        }
    }
    val initialExpand = remember { commands.expandAllRequest }
    LaunchedEffect(commands.expandAllRequest) {
        if (commands.expandAllRequest != initialExpand) { allQuotes = true; expanded.clear() }
    }
    val initialCollapse = remember { commands.collapseAllRequest }
    LaunchedEffect(commands.collapseAllRequest) {
        if (commands.collapseAllRequest != initialCollapse) { allQuotes = false; expanded.clear() }
    }
    val quotesForCopy = allQuotes ?: (settings.quotations != QuotationsMode.COLLAPSED)
    val initialCopy = remember { commands.copyRequest }
    LaunchedEffect(commands.copyRequest) {
        val r = result
        if (commands.copyRequest != initialCopy && r != null && r.articles.isNotEmpty()) state.copyArticles(r.word, r.articles, quotesForCopy)
    }

    var abbrTapped by remember { mutableStateOf<String?>(null) }
    val onLink: (String) -> Unit = remember(state) { { target -> state.openWord(target) } }
    val onAbbreviation: (String) -> Unit = remember { { text -> abbrTapped = text } }
    val styler = rememberRunStyler(onLink, onAbbreviation)
    val typo = rememberArticleTypography(settings.fontScale, settings.serif)

    Column(modifier) {
        // ---------------------------------------------------------------- toolbar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = state::back, enabled = state.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = state::forward, enabled = state.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(result?.word ?: word, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val r = result
                if (r != null && r.articles.size > 1) {
                    Text("${r.articles.size} entries", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { findOpen = !findOpen; if (!findOpen) findQuery = "" }) {
                Icon(Icons.Default.Search, contentDescription = "Find in entry")
            }
            IconButton(onClick = { state.updateSettings { it.copy(showOutline = !it.showOutline) } }) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = "Senses and nearby words",
                    tint = if (settings.showOutline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Expand all quotations") }, onClick = { allQuotes = true; expanded.clear(); menuOpen = false })
                    DropdownMenuItem(text = { Text("Collapse all quotations") }, onClick = { allQuotes = false; expanded.clear(); menuOpen = false })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Larger text") }, onClick = { state.changeFontScale(0.1f); menuOpen = false })
                    DropdownMenuItem(text = { Text("Smaller text") }, onClick = { state.changeFontScale(-0.1f); menuOpen = false })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Copy entry as text") },
                        onClick = {
                            menuOpen = false
                            val r = result
                            if (r != null && r.articles.isNotEmpty()) state.copyArticles(r.word, r.articles, quotesForCopy)
                        },
                    )
                }
            }
        }
        if (findOpen) {
            FindBar(
                query = findQuery,
                onQuery = { findQuery = it; findPos = 0 },
                count = matches.size,
                pos = findPos,
                focus = findFocus,
                onPrev = { if (matches.isNotEmpty()) findPos = (findPos - 1 + matches.size) % matches.size },
                onNext = { if (matches.isNotEmpty()) findPos = (findPos + 1) % matches.size },
                onClose = { findOpen = false; findQuery = "" },
            )
            LaunchedEffect(Unit) { runCatching { findFocus.requestFocus() } }
        }
        HorizontalDivider()

        // ---------------------------------------------------------------- body
        val r = result
        when {
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            r.articles.isEmpty() -> NotFound(r, state::openWord, Modifier.fillMaxSize())
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val sidePanel = settings.showOutline && maxWidth > 760.dp
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val matchSet = remember(matches) { matches.toHashSet() }
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                items(count = rows.size, key = { rows[it].key }) { i ->
                                    // Keep lines at a comfortable reading length on wide windows.
                                    Box(Modifier.widthIn(max = 860.dp).fillMaxWidth().padding(horizontal = 12.dp)) {
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
                        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                    if (sidePanel) {
                        VerticalDivider()
                        SidePanel(
                            state = state,
                            result = r,
                            onPick = { articleIndex, blockIndex ->
                                val idx = rowIndex["$articleIndex-$blockIndex"]
                                if (idx != null) scope.launch { listState.animateScrollToItem(idx) }
                            },
                            modifier = Modifier.width(300.dp).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }

    val tapped = abbrTapped
    if (tapped != null) {
        val expansion by produceState<String?>(initialValue = null, key1 = tapped) { value = state.abbreviation(tapped) ?: "" }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SidePanel(state: AppState, result: EntryResult, onPick: (Int, Int) -> Unit, modifier: Modifier) {
    val hasOutline = remember(result) { result.articles.any { it.outline().isNotEmpty() } }
    var tab by remember(hasOutline) { mutableIntStateOf(if (hasOutline) 0 else 1) }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, enabled = hasOutline, text = { Text("Senses") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Nearby") })
        }
        if (tab == 0) OutlineList(result.articles, onPick) else NearbyList(state, result)
    }
}

@Composable
private fun OutlineList(articles: List<Article>, onPick: (Int, Int) -> Unit) {
    val entries = remember(articles) {
        buildList {
            articles.forEachIndexed { ai, a ->
                val items = a.outline()
                if (items.isNotEmpty()) add(Triple(ai, -1, OutlineItem(-1, "", a.dictionaryName, 0)))
                items.forEach { add(Triple(ai, it.blockIndex, it)) }
            }
        }
    }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
            items(entries.size) { i ->
                val (ai, bi, item) = entries[i]
                if (bi < 0) {
                    if (articles.size > 1) DictionaryHeader(item.snippet)
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(ai, bi) }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(start = (14 + item.level * 14).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Text(
                            item.label,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(item.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun NearbyList(state: AppState, result: EntryResult) {
    val words by produceState<List<String>?>(initialValue = null, key1 = result.word) {
        value = state.neighbours(result.word, result.articles.first().dictionaryId)
    }
    val list = words
    if (list == null) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val listState = remember(result.word) { LazyListState(firstVisibleItemIndex = (list.indexOf(result.word) - 6).coerceAtLeast(0)) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list.size) { i ->
                val w = list[i]
                val isCurrent = w == result.word
                Text(
                    w,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable(enabled = !isCurrent) { state.openWord(w) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
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
    focus: FocusRequester,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Find in entry") },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> { if (e.isShiftPressed) onPrev() else onNext(); true }
                        Key.Escape -> { onClose(); true }
                        else -> false
                    }
                },
        )
        Text(
            if (count == 0) "0" else "${pos + 1}/$count",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onPrev, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match") }
        IconButton(onClick = onNext, enabled = count > 0) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match") }
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close find") }
    }
}

@Composable
private fun NotFound(r: EntryResult, onOpenWord: (String) -> Unit, modifier: Modifier) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 12.dp)) {
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenWord(s.word) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(s.word, style = MaterialTheme.typography.bodyLarge)
                if (s.dictionaries.size > 1) {
                    Text(s.dictionaries.joinToString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
