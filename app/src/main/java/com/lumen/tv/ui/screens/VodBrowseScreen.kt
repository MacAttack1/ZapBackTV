package com.lumen.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Category
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Library
import com.lumen.tv.data.Stream
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.ListRow
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.PosterCard
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import kotlinx.coroutines.delay

private enum class Sort(val label: String) { LATEST("Latest"), AZ("A–Z"), RATING("Top rated"), YEAR("Year") }

@Composable
fun VodBrowseScreen(vm: AppViewModel, kind: Kind) {
    val lib = vm.library.collectAsStateWithLifecycle().value ?: return
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()

    var catId by rememberSaveable(kind) { mutableStateOf(Library.ALL) }
    var sort by rememberSaveable(kind) { mutableStateOf(Sort.LATEST) }
    var query by rememberSaveable(kind) { mutableStateOf("") }

    val categories = remember(lib, kind) {
        listOf(
            Category(Library.ALL, "All", kind, lib.streams(kind).size),
            Category(Library.FAVORITES, "My list", kind),
        ) + lib.categories(kind)
    }
    val items = remember(lib, kind, catId, sort, favs, query) {
        val base = if (catId == Library.FAVORITES) vm.favoritesOf(kind, lib, favs) else lib.inCategory(kind, catId)
        val filtered = if (query.isBlank()) base else base.filter { it.name.contains(query.trim(), true) }
        when (sort) {
            Sort.LATEST -> filtered.sortedByDescending { it.added }
            Sort.AZ -> filtered.sortedBy { it.name.lowercase() }
            Sort.RATING -> filtered.sortedByDescending { it.rating ?: 0.0 }
            Sort.YEAR -> filtered.sortedByDescending { it.year ?: "" }
        }
    }
    val favKeys = remember(favs, lib) { favs.filter { it.startsWith("${lib.sourceId}|") }.map { it.substringAfter('|') }.toHashSet() }
    val progress = remember(history) {
        history.filter { it.kind == kind || (kind == Kind.SERIES && it.seriesKey != null) }
            .associate { (it.seriesKey ?: it.key) to it.fraction }
    }
    val title = if (kind == Kind.MOVIE) "Movies" else "Series"
    val open: (Stream) -> Unit = {
        vm.navigate(if (kind == Kind.MOVIE) Screen.MovieDetail(it) else Screen.SeriesDetail(it))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isTv || maxWidth >= 840.dp
        val grid: @Composable (Modifier) -> Unit = { m ->
            PosterGrid(
                items = items, favKeys = favKeys, progress = progress, resetKey = "$catId|$sort",
                onLongClick = { vm.toggleFavorite(it) }, onClick = open, modifier = m,
                minWidth = if (isTv) 150.dp else if (wide) 150.dp else 108.dp,
                emptyTitle = if (catId == Library.FAVORITES) "Your list is empty" else "Nothing here",
                emptyMessage = if (catId == Library.FAVORITES) "Long-press any title to add it to your list." else "Try another category or clear the filter.",
            )
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(top = 24.dp)) {
                Column(Modifier.width(260.dp).fillMaxHeight().padding(start = 16.dp, end = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi, modifier = Modifier.padding(start = 14.dp, bottom = 14.dp))
                    LazyColumn(Modifier.fillMaxSize().focusRestorer(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                        items(categories, key = { it.id }) { c ->
                            ListRow(
                                c.name, selected = c.id == catId, trailing = c.count.takeIf { it > 0 }?.toString(),
                                icon = when (c.id) { Library.ALL -> Icons.Rounded.ViewList; Library.FAVORITES -> Icons.Rounded.Favorite; else -> null },
                                onLongClick = { vm.hideCategory(kind, c.id, c.name); if (catId == c.id) catId = Library.ALL },
                            ) { catId = c.id }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(end = 24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterField(query, { query = it }, "Filter ${items.size} titles", Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Sort.entries.forEach { s ->
                            Pill(s.label, sort == s, { sort = s }, Modifier.padding(start = 6.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    grid(Modifier.weight(1f))
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                LazyRow(contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { c ->
                        Pill(c.name, c.id == catId, { catId = c.id }, onLongClick = { vm.hideCategory(kind, c.id, c.name); if (catId == c.id) catId = Library.ALL })
                    }
                }
                Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
                    FilterField(query, { query = it }, "Search ${title.lowercase()}", Modifier.weight(1f))
                }
                LazyRow(contentPadding = PaddingValues(horizontal = pad, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(Sort.entries.toList()) { s -> Pill(s.label, sort == s, { sort = s }) }
                }
                grid(Modifier.weight(1f).padding(horizontal = pad - 6.dp))
            }
        }
    }
}

@Composable
fun PosterGrid(
    items: List<Stream>,
    favKeys: Set<String>,
    progress: Map<String, Float>,
    resetKey: String,
    onLongClick: (Stream) -> Unit,
    onClick: (Stream) -> Unit,
    modifier: Modifier = Modifier,
    minWidth: androidx.compose.ui.unit.Dp = 140.dp,
    emptyTitle: String = "Nothing here",
    emptyMessage: String = "",
) {
    if (items.isEmpty()) {
        EmptyState(Icons.Rounded.Movie, emptyTitle, emptyMessage, modifier)
        return
    }
    val isTv = LocalIsTv.current
    val state = rememberLazyGridState()
    var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val targetKey = openedKey?.takeIf { k -> items.any { it.listKey == k } } ?: items.first().listKey
    val target = remember { FocusRequester() }
    LaunchedEffect(resetKey) {
        // Scroll to the top only for a new category/sort, not when coming back from a title.
        if (shownKey != null && shownKey != resetKey) { openedKey = null; state.scrollToItem(0) }
        shownKey = resetKey
        if (isTv) { delay(150); runCatching { target.requestFocus() } }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minWidth),
        state = state,
        modifier = modifier.focusRestorer(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(items, key = { _, s -> s.listKey }) { i, s ->
            PosterCard(
                s, width = null,
                modifier = if (s.listKey == targetKey) Modifier.focusRequester(target) else Modifier,
                isFavorite = s.key in favKeys,
                progress = progress[s.key],
                onLongClick = { onLongClick(s) },
            ) { openedKey = s.listKey; onClick(s) }
        }
    }
}
