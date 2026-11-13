package com.lumen.tv.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Episode
import com.lumen.tv.data.SeriesInfo
import com.lumen.tv.data.Stream
import com.lumen.tv.data.VodInfo
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.MetaChip
import com.lumen.tv.ui.components.NetImage
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.ProgressLine
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import java.util.Locale
import com.lumen.tv.ui.components.Eyebrow

// ------------------------------------------------------------------------ shared header

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(
    vm: AppViewModel,
    stream: Stream,
    backdrop: String?,
    meta: List<String>,
    rating: Double?,
    plot: String?,
    credits: List<Pair<String, String?>>,
    buttons: @Composable () -> Unit,
) {
    val accent = LocalAccent.current
    val isTv = LocalIsTv.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth > 720.dp
        val heroHeight = if (wide) 520.dp else 640.dp
        Box(Modifier.fillMaxWidth().height(heroHeight)) {
            val art = backdrop ?: stream.image
            val bgMod = Modifier.fillMaxSize().let {
                if (backdrop == null && Build.VERSION.SDK_INT >= 31) it.blur(40.dp).alpha(0.7f)
                else if (backdrop == null) it.alpha(0.4f) else it.alpha(0.85f)
            }
            NetImage(art, stream.name, bgMod)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Hue.Ink, Hue.Ink.copy(alpha = 0.75f), Hue.Ink.copy(alpha = 0.2f)))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Hue.Ink)))

            if (!isTv) RoundIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack, "Back", { vm.back() },
                Modifier.align(Alignment.TopStart).padding(14.dp),
            )

            val pad = hPad()
            if (wide) {
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = pad, end = pad, bottom = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    NetImage(
                        stream.image, stream.name,
                        Modifier.width(220.dp).aspectRatio(2f / 3f)
                            .shadow(36.dp, RoundedCornerShape(16.dp), ambientColor = accent.main, spotColor = accent.main)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(1f).widthIn(max = 760.dp)) {
                        HeaderText(stream, meta, rating, plot, credits, buttons)
                    }
                }
            } else {
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = pad, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NetImage(
                        stream.image, stream.name,
                        Modifier.width(170.dp).aspectRatio(2f / 3f).shadow(30.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(18.dp))
                    HeaderText(stream, meta, rating, plot, credits, buttons)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderText(
    stream: Stream,
    meta: List<String>,
    rating: Double?,
    plot: String?,
    credits: List<Pair<String, String?>>,
    buttons: @Composable () -> Unit,
) {
    Text(stream.name, style = MaterialTheme.typography.displayMedium, color = Hue.TextHi, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rating?.let { MetaChip(String.format(Locale.US, "%.1f", it.coerceAtMost(10.0)), icon = Icons.Rounded.Star, tint = Hue.Gold) }
        meta.filter { it.isNotBlank() }.forEach { MetaChip(it) }
    }
    if (!plot.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        Text(plot, style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    credits.filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
        Spacer(Modifier.height(6.dp))
        Row {
            Text("$label  ", style = MaterialTheme.typography.labelLarge, color = Hue.TextLo)
            Text(value!!, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(20.dp))
    buttons()
}

private fun formatDuration(secs: Int): String? {
    if (secs <= 0) return null
    val m = secs / 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

// ------------------------------------------------------------------------ movie

@Composable
fun MovieDetailScreen(vm: AppViewModel, movie: Stream) {
    var info by remember(movie.id) { mutableStateOf<VodInfo?>(null) }
    LaunchedEffect(movie.id) { info = vm.repo.vodInfo(movie) }
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()
    val isFav = remember(favs, movie) { vm.isFavorite(movie) }
    val pos = remember(history, movie) { vm.positionOf(movie.key) }
    val fraction = pos?.let { (p, d) -> if (d > 0) p.toFloat() / d else 0f } ?: 0f
    val resumable = pos != null && pos.first > 30_000 && fraction < 0.94f

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
        item {
            DetailHeader(
                vm, movie, info?.backdrop,
                meta = listOfNotNull(
                    info?.releaseDate?.take(4) ?: movie.year,
                    formatDuration(info?.durationSecs ?: 0),
                    info?.genre?.split(',', '/')?.take(3)?.joinToString(" · ") { it.trim() },
                ),
                rating = info?.rating ?: movie.rating,
                plot = info?.plot ?: movie.plot,
                credits = listOf("Director" to info?.director, "Cast" to info?.cast),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    LumenButton(
                        if (resumable) "Resume" else "Play", onClick = { vm.playMovie(movie) },
                        icon = Icons.Rounded.PlayArrow, modifier = Modifier.initialFocus(),
                    )
                    if (resumable) LumenButton("Start over", onClick = { vm.playMovie(movie, fromStart = true) }, icon = Icons.Rounded.Replay, primary = false)
                    LumenButton(
                        if (isFav) "In my list" else "My list", onClick = { vm.toggleFavorite(movie) },
                        icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, primary = false,
                    )
                }
                if (resumable) {
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressLine(fraction, Modifier.width(220.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(remaining(pos!!.first, pos.second), style = MaterialTheme.typography.labelMedium, color = Hue.TextLo)
                    }
                }
            }
        }
        // More like this: same category
        item {
            val lib = vm.library.collectAsStateWithLifecycle().value
            val similar = remember(lib, movie) {
                lib?.inCategory(movie.kind, movie.categoryId)?.filter { it.key != movie.key }?.take(20).orEmpty()
            }
            if (similar.isNotEmpty()) {
                Column(Modifier.padding(top = 20.dp)) {
                    Text("More like this", style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi, modifier = Modifier.padding(horizontal = hPad()))
                    Spacer(Modifier.height(14.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = hPad()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(similar, key = { it.listKey }) { s ->
                            com.lumen.tv.ui.components.PosterCard(s, width = 130.dp, onLongClick = { vm.toggleFavorite(s) }) {
                                vm.replaceTop(com.lumen.tv.ui.Screen.MovieDetail(s))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ series

@Composable
fun SeriesDetailScreen(vm: AppViewModel, series: Stream) {
    var info by remember(series.id) { mutableStateOf<SeriesInfo?>(null) }
    var error by remember(series.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(series.id) {
        runCatching { vm.repo.seriesInfo(series) }
            .onSuccess { info = it }
            .onFailure { error = it.message ?: "Couldn't load episodes" }
    }
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()
    val isFav = remember(favs, series) { vm.isFavorite(series) }
    val seasons = info?.seasons?.keys?.toList().orEmpty()
    var season by rememberSaveable(series.id) { mutableIntStateOf(-1) }

    // Where to pick up: the last watched episode of this show, else S1E1.
    val lastEntry = remember(history, series) { history.firstOrNull { it.seriesKey == series.key } }
    val nextUp: Episode? = remember(info, lastEntry) {
        val all = info?.allEpisodes.orEmpty()
        val i = all.indexOfFirst { it.watchKey == lastEntry?.key }
        when {
            i < 0 -> all.firstOrNull()
            lastEntry != null && !lastEntry.isResumable && lastEntry.fraction >= 0.94f -> all.getOrNull(i + 1) ?: all[i]
            else -> all[i]
        }
    }
    LaunchedEffect(info) {
        if (season == -1 && info != null) season = nextUp?.season ?: seasons.firstOrNull() ?: 1
    }
    val episodes = info?.seasons?.get(season).orEmpty()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 60.dp)) {
        item {
            DetailHeader(
                vm, series, info?.backdrop,
                meta = listOfNotNull(
                    info?.releaseDate?.take(4) ?: series.year,
                    seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" },
                    info?.genre?.split(',', '/')?.take(3)?.joinToString(" · ") { it.trim() },
                ),
                rating = series.rating,
                plot = info?.plot ?: series.plot,
                credits = listOf("Created by" to info?.director, "Cast" to info?.cast),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val ep = nextUp
                    LumenButton(
                        when {
                            ep == null -> "Loading…"
                            lastEntry == null -> "Play S${ep.season} E${ep.number}"
                            else -> "Continue S${ep.season} E${ep.number}"
                        },
                        onClick = { if (ep != null) info?.let { vm.playEpisode(it, ep) } },
                        icon = Icons.Rounded.PlayArrow, enabled = ep != null, modifier = Modifier.initialFocus(),
                    )
                    LumenButton(
                        if (isFav) "In my list" else "My list", onClick = { vm.toggleFavorite(series) },
                        icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, primary = false,
                    )
                }
            }
        }
        when {
            error != null -> item { EmptyState(Icons.Rounded.Schedule, "Episodes unavailable", error!!, Modifier.height(260.dp)) }
            info == null -> item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalAccent.current.main)
                }
            }
            else -> {
                if (seasons.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = hPad(), vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { s -> Pill("Season $s", s == season, { season = s }, count = info?.seasons?.get(s)?.size) }
                    }
                }
                items(episodes, key = { it.id }) { ep ->
                    val p = vm.positionOf(ep.watchKey)
                    EpisodeRow(
                        ep, progress = p?.let { (a, b) -> if (b > 0) a.toFloat() / b else 0f } ?: 0f,
                        isNext = ep.id == nextUp?.id,
                    ) { info?.let { vm.playEpisode(it, ep) } }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, progress: Float, isNext: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val pad = hPad()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = pad - 10.dp, vertical = 3.dp)
            .focusGlow(shape, scale = 1.015f, ring = 2.dp, onFocus = { focused = it })
            .clip(shape)
            .background(if (focused) Hue.Surface3 else if (isNext) accent.soft.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(176.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
            NetImage(ep.image, ep.title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
            Icon(
                if (progress >= 0.94f) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow, null,
                tint = Color.White, modifier = Modifier.align(Alignment.Center).size(30.dp),
            )
            if (progress > 0f && progress < 0.94f) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("E${ep.number}", style = MaterialTheme.typography.labelLarge, color = accent.main)
                Spacer(Modifier.width(8.dp))
                Text(ep.title, style = MaterialTheme.typography.titleMedium, color = Hue.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                formatDuration(ep.durationSecs)?.let {
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = Hue.TextLo)
                }
            }
            ep.plot?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (isNext) {
                Spacer(Modifier.height(6.dp))
                Eyebrow("Up next")
            }
        }
    }
}
