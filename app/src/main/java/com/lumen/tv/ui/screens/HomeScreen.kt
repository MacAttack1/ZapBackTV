package com.lumen.tv.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.lumen.tv.ui.components.Pill
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Stream
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.components.ChannelTile
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.MetaChip
import com.lumen.tv.ui.components.NetImage
import com.lumen.tv.ui.components.PosterCard
import com.lumen.tv.ui.components.SectionTitle
import com.lumen.tv.ui.components.WideCard
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale
import com.lumen.tv.ui.components.Eyebrow

@Composable
fun hPad(): Dp = if (LocalIsTv.current) 40.dp else 18.dp

@Composable
fun HomeScreen(vm: AppViewModel) {
    val lib = vm.library.collectAsStateWithLifecycle().value ?: return
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()
    var confirmClear by remember { mutableStateOf<ClearRow?>(null) }
    confirmClear?.let { row ->
        ConfirmDialog(
            title = if (row == ClearRow.CONTINUE) "Clear Continue watching?" else "Clear recently watched channels?",
            message = if (row == ClearRow.CONTINUE) "Movies and episodes you started will be removed from this row."
            else "Channels you watched will be removed from this row and from Live TV's Recently watched list.",
            confirm = "Clear",
            onDismiss = { confirmClear = null },
        ) {
            val entries = history.filter { it.sourceId == lib.sourceId && (if (row == ClearRow.CONTINUE) it.isResumable else it.kind == Kind.LIVE) }
            entries.forEach { vm.store.removeHistory(it) }
            confirmClear = null
            vm.toast = "Cleared"
        }
    }

    // Only titles that are still visible: anything hidden (adult, other countries, switched-off categories) drops out.
    val continueItems = remember(history, lib) {
        history.filter { it.sourceId == lib.sourceId && it.isResumable && lib.find(it.seriesKey ?: it.key) != null }.take(20)
    }
    val recentChannels = remember(history, lib) {
        history.filter { it.sourceId == lib.sourceId && it.kind == Kind.LIVE }.mapNotNull { lib.find(it.key) }.take(20)
    }
    val favChannels = remember(favs, lib) { vm.favoritesOf(Kind.LIVE, lib, favs) }
    val favVod = remember(favs, lib) { vm.favoritesOf(Kind.MOVIE, lib, favs) + vm.favoritesOf(Kind.SERIES, lib, favs) }
    val latestMovies = remember(lib) { lib.streams(Kind.MOVIE).sortedByDescending { it.added }.take(24) }
    val topRated = remember(lib) { lib.streams(Kind.MOVIE).filter { (it.rating ?: 0.0) >= 6.5 }.sortedByDescending { it.rating }.take(24) }
    val latestSeries = remember(lib) { lib.streams(Kind.SERIES).sortedByDescending { it.added }.take(24) }
    val featured = remember(lib) {
        (latestMovies.filter { !it.image.isNullOrBlank() }.take(5) + latestSeries.filter { !it.image.isNullOrBlank() }.take(3))
    }
    val catalog by vm.liveCatalog.collectAsStateWithLifecycle()
    // Sample from the preferred country first and never from adult sections.
    val liveSample = remember(lib, catalog) {
        catalog.asSequence().filter { it.genre != com.lumen.tv.data.Genre.ADULT }
            .flatMap { lib.inCategory(Kind.LIVE, it.category.id).asSequence() }.take(20).toList()
    }
    val movieCats = remember(lib) {
        lib.categories(Kind.MOVIE).take(6).map { it to lib.inCategory(Kind.MOVIE, it.id).take(20) }.filter { it.second.isNotEmpty() }
    }

    val openStream: (Stream) -> Unit = { s ->
        when (s.kind) {
            Kind.LIVE -> vm.playChannel(s, lib.inCategory(Kind.LIVE, s.categoryId))
            Kind.MOVIE -> vm.navigate(Screen.MovieDetail(s))
            Kind.SERIES -> vm.navigate(Screen.SeriesDetail(s))
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        if (featured.isNotEmpty()) {
            item(key = "hero") { Hero(featured, vm, openStream) }
        } else {
            item(key = "greet") {
                Column(Modifier.padding(start = pad, end = pad, top = if (isTv) 36.dp else 12.dp, bottom = 8.dp)) {
                    Text(greeting(), style = MaterialTheme.typography.displayMedium, color = Hue.TextHi)
                    Text("What are we watching?", style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid)
                }
            }
        }

        if (continueItems.isNotEmpty()) shelf(
            "continue", "Continue watching",
            action = { Pill("Clear", selected = false, onClick = { confirmClear = ClearRow.CONTINUE }, icon = Icons.Rounded.DeleteSweep) },
        ) {
            items(continueItems, key = { it.key }) { e ->
                WideCard(
                    title = e.title, subtitle = e.subtitle ?: remaining(e.position, e.duration), image = e.image,
                    progress = e.fraction, width = if (isTv) 280.dp else 240.dp,
                    onLongClick = { vm.store.removeHistory(e); vm.toast = "Removed from Continue watching" },
                ) { vm.resume(e) }
            }
        }
        if (favChannels.isNotEmpty()) shelf("favch", "Favorite channels") {
            items(favChannels, key = { it.listKey }) { c ->
                ChannelTile(c, epg.now(c), width = if (isTv) 220.dp else 180.dp, onLongClick = { vm.toggleFavorite(c) }) {
                    vm.playChannel(c, favChannels)
                }
            }
        }
        if (recentChannels.isNotEmpty()) shelf(
            "recentch", "Recently watched",
            action = { Pill("Clear", selected = false, onClick = { confirmClear = ClearRow.RECENT }, icon = Icons.Rounded.DeleteSweep) },
        ) {
            items(recentChannels, key = { it.listKey }) { c ->
                ChannelTile(c, epg.now(c), width = if (isTv) 220.dp else 180.dp, recent = true, onLongClick = { vm.toggleFavorite(c) }) {
                    vm.playChannel(c, lib.inCategory(Kind.LIVE, c.categoryId))
                }
            }
        }
        if (favVod.isNotEmpty()) posterShelf("favvod", "My list", favVod, vm, openStream)
        if (latestMovies.isNotEmpty()) posterShelf("latest", "Recently added movies", latestMovies, vm, openStream)
        if (latestSeries.isNotEmpty()) posterShelf("series", "New episodes & series", latestSeries, vm, openStream)
        if (topRated.isNotEmpty()) posterShelf("top", "Top rated", topRated, vm, openStream)
        if (recentChannels.isEmpty() && favChannels.isEmpty() && liveSample.isNotEmpty()) shelf("live", "Live channels") {
            items(liveSample, key = { it.listKey }) { c ->
                ChannelTile(c, epg.now(c), width = if (isTv) 220.dp else 180.dp, onLongClick = { vm.toggleFavorite(c) }) {
                    vm.playChannel(c, lib.inCategory(Kind.LIVE, c.categoryId))
                }
            }
        }
        movieCats.forEach { (cat, list) -> posterShelf("cat-${cat.id}", cat.name, list, vm, openStream) }

        if (lib.streams(Kind.MOVIE).isEmpty() && lib.streams(Kind.SERIES).isEmpty() && liveSample.isEmpty()) {
            item { EmptyState(Icons.Rounded.Info, "Nothing here yet", "Your playlist loaded, but it looks empty.") }
        }
    }
}

private fun LazyListScope.shelf(
    key: String,
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    item(key = key) {
        val pad = hPad()
        Column(Modifier.padding(top = 26.dp)) {
            Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(title, Modifier.weight(1f))
                if (action != null) action()
            }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                Modifier.fillMaxWidth().focusRestorer(),
                contentPadding = PaddingValues(horizontal = pad),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

private fun LazyListScope.posterShelf(key: String, title: String, list: List<Stream>, vm: AppViewModel, open: (Stream) -> Unit) {
    shelf(key, title) {
        items(list, key = { it.listKey }) { s ->
            PosterCard(
                s, width = 140.dp,
                progress = vm.positionOf(s.key)?.let { (p, d) -> if (d > 0) p.toFloat() / d else null },
                onLongClick = { vm.toggleFavorite(s) },
            ) { open(s) }
        }
    }
}

@Composable
private fun Hero(items: List<Stream>, vm: AppViewModel, open: (Stream) -> Unit) {
    val isTv = LocalIsTv.current
    val accent = LocalAccent.current
    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val pad = hPad()

    LaunchedEffect(paused, items.size) {
        while (!paused && items.size > 1) {
            delay(9000)
            index = (index + 1) % items.size
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = if (isTv) 440.dp else 520.dp
        val wide = maxWidth > 720.dp
        AnimatedContent(
            targetState = items[index.coerceIn(0, items.lastIndex)],
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
            label = "hero",
        ) { item ->
            Box(Modifier.fillMaxWidth().height(height)) {
                // Blurred poster wash
                val bgMod = Modifier.fillMaxSize().let {
                    if (Build.VERSION.SDK_INT >= 31) it.blur(36.dp).alpha(0.75f) else it.alpha(0.45f)
                }
                NetImage(item.image, item.name, bgMod)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            if (wide) listOf(Hue.Ink, Hue.Ink.copy(alpha = 0.85f), Hue.Ink.copy(alpha = 0.25f))
                            else listOf(Hue.Ink.copy(alpha = 0.55f), Hue.Ink.copy(alpha = 0.35f))
                        )
                    )
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Hue.Ink)))

                if (wide) {
                    NetImage(
                        item.image, item.name,
                        Modifier.align(Alignment.CenterEnd).padding(end = 72.dp, top = 24.dp)
                            .height(height * 0.78f).aspectRatio(2f / 3f)
                            .shadow(40.dp, RoundedCornerShape(18.dp), ambientColor = accent.main, spotColor = accent.main)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                } else {
                    NetImage(
                        item.image, item.name,
                        Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                            .height(height * 0.52f).aspectRatio(2f / 3f)
                            .shadow(30.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
        }

        val item = items[index.coerceIn(0, items.lastIndex)]
        Column(
            Modifier.align(Alignment.BottomStart)
                .padding(start = pad, end = pad, bottom = 8.dp)
                .widthIn(max = if (wide) 560.dp else 1000.dp),
            horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Eyebrow(if (item.kind == Kind.SERIES) "Series" else "New movie")
            Spacer(Modifier.height(6.dp))
            Text(
                item.name, style = if (wide) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displayMedium,
                color = Hue.TextHi, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.year?.let { MetaChip(it) }
                item.rating?.let { MetaChip(String.format(Locale.US, "%.1f", it), icon = Icons.Rounded.Star, tint = Hue.Gold) }
            }
            if (wide && !item.plot.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(item.plot, style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.onFocusChanged { paused = it.hasFocus },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LumenButton(
                    if (item.kind == Kind.SERIES) "Episodes" else "Play", icon = Icons.Rounded.PlayArrow,
                    onClick = { if (item.kind == Kind.MOVIE) vm.playMovie(item) else open(item) },
                    modifier = Modifier.initialFocus(),
                )
                LumenButton("Details", icon = Icons.Rounded.Info, primary = false, onClick = { open(item) })
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.indices.forEach { i ->
                    val w by animateDpAsState(if (i == index) 22.dp else 6.dp, label = "dot")
                    Box(
                        Modifier.height(6.dp).width(w).clip(RoundedCornerShape(50))
                            .background(if (i == index) accent.main else Color.White.copy(alpha = 0.25f))
                    )
                }
            }
        }
    }
}

private enum class ClearRow { CONTINUE, RECENT }

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

fun remaining(position: Long, duration: Long): String {
    if (duration <= 0) return "Resume"
    val left = ((duration - position) / 60_000).coerceAtLeast(1)
    return if (left >= 60) "${left / 60}h ${left % 60}m left" else "${left}m left"
}
