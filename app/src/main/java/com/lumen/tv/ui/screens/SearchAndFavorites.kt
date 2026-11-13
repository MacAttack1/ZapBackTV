package com.lumen.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Stream
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.components.ChannelTile
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.PosterCard
import com.lumen.tv.ui.components.SectionTitle
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.Normalizer

private data class Results(val live: List<Stream>, val movies: List<Stream>, val series: List<Stream>) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

private val marks = Regex("\\p{Mn}+")

/** Lower-cases and strips accents; skips the (slow) Unicode normalizer for plain ASCII names. */
private fun fold(s: String): String {
    val lower = s.lowercase()
    return if (lower.all { it.code < 128 }) lower else Normalizer.normalize(lower, Normalizer.Form.NFD).replace(marks, "")
}

/** Names pre-folded once per library so each keystroke is a quick scan. */
private class SearchIndex(val items: List<Stream>) {
    val names: Array<String> = Array(items.size) { fold(items[it].name) }

    fun find(q: String, terms: List<String>, limit: Int, check: () -> Unit): List<Stream> {
        val first = ArrayList<Stream>()
        val rest = ArrayList<Stream>()
        for (i in names.indices) {
            if (i and 1023 == 0) check()
            val n = names[i]
            if (terms.all { it in n }) {
                if (n.startsWith(q)) first += items[i] else if (rest.size < limit) rest += items[i]
                if (first.size >= limit) break
            }
        }
        return (first + rest).take(limit)
    }
}

@Composable
fun SearchScreen(vm: AppViewModel) {
    val lib = vm.library.collectAsStateWithLifecycle().value ?: return
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<Results?>(null) }
    var searching by remember { mutableStateOf(false) }
    val index by produceState<Map<Kind, SearchIndex>?>(null, lib) {
        value = withContext(Dispatchers.Default) { Kind.entries.associateWith { SearchIndex(lib.streams(it)) } }
    }

    LaunchedEffect(query, index) {
        val q = fold(query.trim()).replace(Regex("\\s+"), " ")
        if (q.length < 2) { results = null; searching = false; return@LaunchedEffect }
        searching = true
        delay(200) // debounce typing
        val idx = index ?: return@LaunchedEffect // still indexing; this effect re-runs when ready
        val terms = q.split(' ').filter { it.isNotBlank() }
        results = withContext(Dispatchers.Default) {
            val check = { ensureActive() }
            Results(
                idx.getValue(Kind.LIVE).find(q, terms, 60, check),
                idx.getValue(Kind.MOVIE).find(q, terms, 60, check),
                idx.getValue(Kind.SERIES).find(q, terms, 60, check),
            )
        }
        searching = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = if (isTv) 28.dp else 8.dp, bottom = 48.dp)) {
        item {
            Column(Modifier.padding(horizontal = pad)) {
                if (isTv) {
                    Text("Search", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
                    Spacer(Modifier.height(14.dp))
                }
                FilterField(query, { query = it }, "Channels, movies, series…", Modifier.initialFocus())
            }
        }
        val r = results
        when {
            searching && r == null -> item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = com.lumen.tv.ui.theme.LocalAccent.current.main)
                }
            }
            r == null -> item {
                EmptyState(
                    Icons.Rounded.Search, "Find anything",
                    "Search across ${lib.streams(Kind.LIVE).size} channels, ${lib.streams(Kind.MOVIE).size} movies and ${lib.streams(Kind.SERIES).size} series.",
                    Modifier.height(380.dp),
                )
            }
            r.isEmpty -> item { EmptyState(Icons.Rounded.SearchOff, "No results", "Nothing matches “$query”.", Modifier.height(380.dp)) }
            else -> {
                if (r.live.isNotEmpty()) item(key = "live") {
                    ResultRow("Channels", r.live.size) {
                        items(r.live, key = { it.listKey }) { c ->
                            ChannelTile(c, epg.now(c), width = if (isTv) 220.dp else 180.dp, onLongClick = { vm.toggleFavorite(c) }) {
                                vm.playChannel(c, r.live)
                            }
                        }
                    }
                }
                if (r.movies.isNotEmpty()) item(key = "movies") {
                    ResultRow("Movies", r.movies.size) {
                        items(r.movies, key = { it.listKey }) { m ->
                            PosterCard(m, width = 140.dp, onLongClick = { vm.toggleFavorite(m) }) { vm.navigate(Screen.MovieDetail(m)) }
                        }
                    }
                }
                if (r.series.isNotEmpty()) item(key = "series") {
                    ResultRow("Series", r.series.size) {
                        items(r.series, key = { it.listKey }) { s ->
                            PosterCard(s, width = 140.dp, onLongClick = { vm.toggleFavorite(s) }) { vm.navigate(Screen.SeriesDetail(s)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, count: Int, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val pad = hPad()
    Column(Modifier.padding(top = 26.dp)) {
        SectionTitle(title, Modifier.padding(horizontal = pad), trailing = if (count >= 60) "Top 60" else "$count")
        Spacer(Modifier.height(14.dp))
        LazyRow(
            Modifier.fillMaxWidth().focusRestorer(),
            contentPadding = PaddingValues(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

// --------------------------------------------------------------------------- favorites

@Composable
fun FavoritesScreen(vm: AppViewModel) {
    val lib = vm.library.collectAsStateWithLifecycle().value ?: return
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()
    var tab by rememberSaveable { mutableStateOf(Kind.LIVE) }
    val list = remember(favs, lib, tab) { vm.favoritesOf(tab, lib, favs) }
    val favKeys = remember(favs, lib) { favs.filter { it.startsWith("${lib.sourceId}|") }.map { it.substringAfter('|') }.toHashSet() }

    Column(Modifier.fillMaxSize().padding(top = if (isTv) 28.dp else 8.dp)) {
        Column(Modifier.padding(horizontal = pad)) {
            Text("Favorites", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
            Text("Hold OK (or long-press) on anything to add or remove it.", style = MaterialTheme.typography.bodyMedium, color = Hue.TextLo)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Channels", tab == Kind.LIVE, { tab = Kind.LIVE }, Modifier.initialFocus(), count = vm.favoritesOf(Kind.LIVE, lib, favs).size)
                Pill("Movies", tab == Kind.MOVIE, { tab = Kind.MOVIE }, count = vm.favoritesOf(Kind.MOVIE, lib, favs).size)
                Pill("Series", tab == Kind.SERIES, { tab = Kind.SERIES }, count = vm.favoritesOf(Kind.SERIES, lib, favs).size)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (list.isEmpty()) {
            EmptyState(Icons.Rounded.FavoriteBorder, "Nothing saved yet", "Your favorite ${if (tab == Kind.LIVE) "channels" else if (tab == Kind.MOVIE) "movies" else "series"} will appear here.")
        } else if (tab == Kind.LIVE) {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(if (isTv) 210.dp else 160.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = pad - 6.dp),
                contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(list.size, key = { list[it].listKey }) { i ->
                    val c = list[i]
                    ChannelTile(c, epg.now(c), width = 400.dp, modifier = Modifier.fillMaxWidth(), onLongClick = { vm.toggleFavorite(c) }) {
                        vm.playChannel(c, list)
                    }
                }
            }
        } else {
            PosterGrid(
                items = list, favKeys = favKeys, progress = emptyMap(), resetKey = tab.name,
                onLongClick = { vm.toggleFavorite(it) },
                onClick = { vm.navigate(if (it.kind == Kind.MOVIE) Screen.MovieDetail(it) else Screen.SeriesDetail(it)) },
                modifier = Modifier.fillMaxSize().padding(horizontal = pad - 6.dp),
                minWidth = if (isTv) 150.dp else 108.dp,
            )
        }
    }
}

