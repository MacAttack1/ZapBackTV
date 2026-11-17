package com.lumen.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.EpgData
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Library
import com.lumen.tv.data.Programme
import com.lumen.tv.data.Stream
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.PlayRequest
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.NetImage
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN = 60_000L
private const val PAST_HOURS = 6
private const val FUTURE_HOURS = 30

@Composable
fun GuideScreen(vm: AppViewModel) {
    val lib = vm.library.collectAsStateWithLifecycle().value ?: return
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val epgStatus by vm.repo.epgStatus.collectAsStateWithLifecycle()
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    val windowStart = remember { (System.currentTimeMillis() / (30 * MIN)) * (30 * MIN) - PAST_HOURS * 3600_000L }
    val initialOffset = ((System.currentTimeMillis() - windowStart) / MIN - 30).toFloat()
    var offsetMin by rememberSaveable { mutableFloatStateOf(initialOffset) }
    val f = rememberLiveFilter(vm, lib)
    val channels = f.channels
    val catId = "${f.region}|${f.genre}|${f.catId}"
    val dpPerMin: Dp = if (isTv) 7.dp else 5.dp
    val channelCol: Dp = if (isTv) 230.dp else 112.dp
    val rowHeight: Dp = if (isTv) 66.dp else 60.dp

    Column(Modifier.fillMaxSize().padding(top = if (isTv) 24.dp else 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TV Guide", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
                val viewStart = windowStart + (offsetMin * MIN).toLong()
                Text(
                    epgStatus ?: SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(viewStart)),
                    style = MaterialTheme.typography.bodyMedium, color = Hue.TextLo,
                )
            }
            RoundIconButton(Icons.Rounded.ChevronLeft, "Earlier", { offsetMin = (offsetMin - 120).coerceAtLeast(0f) })
            Spacer(Modifier.width(8.dp))
            LumenButton("Now", onClick = { offsetMin = ((System.currentTimeMillis() - windowStart) / MIN - 30).toFloat() }, icon = Icons.Rounded.Schedule, primary = false)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.ChevronRight, "Later", { offsetMin += 120 })
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.Refresh, "Refresh guide", { vm.repo.refreshEpg(); vm.toast = "Refreshing guide…" })
        }
        Spacer(Modifier.height(8.dp))
        FilterChips(f, pad)
        Row(Modifier.padding(horizontal = pad, vertical = 8.dp)) {
            CategoryPickerButton(f, Modifier.widthIn(max = 520.dp).fillMaxWidth())
        }

        if (epg.isEmpty && epgStatus == null) {
            EmptyState(
                Icons.Rounded.CalendarViewWeek, "No TV guide yet",
                "This playlist doesn't include an EPG link. Add an XMLTV URL to the playlist in Settings to see what's on.",
                action = { LumenButton("Open settings", onClick = { vm.navigate(Screen.Settings) }) },
            )
            return@Column
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gridWidth = maxWidth - channelCol - pad
            val visibleMin = (gridWidth / dpPerMin)
            val maxOffset = ((PAST_HOURS + FUTURE_HOURS) * 60 - visibleMin).coerceAtLeast(0f)
            val clamp: (Float) -> Float = { it.coerceIn(0f, maxOffset) }
            val pxPerMin = with(LocalDensity.current) { dpPerMin.toPx() }
            val viewStart = windowStart + (offsetMin * MIN).toLong()
            val viewEnd = viewStart + (visibleMin * MIN).toLong()

            Column(Modifier.fillMaxSize()) {
                // Time ruler
                Row(Modifier.fillMaxWidth().height(30.dp)) {
                    Spacer(Modifier.width(channelCol + pad))
                    TimeRuler(viewStart, viewEnd, dpPerMin, Modifier.weight(1f).fillMaxHeight().padding(end = pad))
                }
                val listState = rememberLazyListState()
                // Remembered across playback: which list this is and which channel you opened.
                var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
                var openedKey by rememberSaveable { mutableStateOf<String?>(null) }
                val targetKey = openedKey?.takeIf { k -> channels.any { it.listKey == k } } ?: channels.firstOrNull()?.listKey
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(catId) {
                    // Back to the top only for a new category, not when returning from the player.
                    if (shownKey != null && shownKey != catId) { openedKey = null; listState.scrollToItem(0) }
                    shownKey = catId
                    if (isTv) { delay(150); runCatching { firstFocus.requestFocus() } }
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.fillMaxSize().pointerInput(maxOffset) {
                            detectHorizontalDragGestures { change, dx ->
                                change.consume()
                                offsetMin = clamp(offsetMin - dx / pxPerMin)
                            }
                        },
                        state = listState,
                        contentPadding = PaddingValues(bottom = 40.dp),
                    ) {
                        items(channels, key = { it.listKey }) { ch ->
                            val isTarget = ch.listKey == targetKey
                            GuideRow(
                                vm = vm, channel = ch, channels = channels, epg = epg, now = now,
                                viewStart = viewStart, viewEnd = viewEnd, dpPerMin = dpPerMin,
                                channelCol = channelCol, rowHeight = rowHeight, pad = pad,
                                focusRequester = if (isTarget) firstFocus else null,
                                onOpened = { openedKey = ch.listKey },
                                onReveal = { startMs ->
                                    offsetMin = clamp(((startMs - windowStart) / MIN).toFloat())
                                },
                            )
                        }
                    }
                    // "Now" line
                    if (now in viewStart until viewEnd) {
                        val x = channelCol + pad + dpPerMin * ((now - viewStart) / MIN.toFloat())
                        Box(
                            Modifier.offset(x = x).width(2.dp).fillMaxHeight()
                                .background(LocalAccent.current.main.copy(alpha = 0.85f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRuler(viewStart: Long, viewEnd: Long, dpPerMin: Dp, modifier: Modifier) {
    val fmt = remember { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT) }
    val first = (viewStart / (30 * MIN)) * (30 * MIN)
    val marks = generateSequence(first) { it + 30 * MIN }.takeWhile { it < viewEnd }.toList()
    val density = LocalDensity.current
    Layout(
        content = {
            marks.forEach { t ->
                Text(fmt.format(Date(t)), style = MaterialTheme.typography.labelMedium, color = Hue.TextLo, maxLines = 1)
            }
        },
        modifier = modifier.clipToBounds(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val x = with(density) { (dpPerMin * ((marks[i] - viewStart) / MIN.toFloat())).toPx() }
                p.place(x.toInt() + 6, (constraints.maxHeight - p.height) / 2)
            }
        }
    }
}

@Composable
private fun GuideRow(
    vm: AppViewModel,
    channel: Stream,
    channels: List<Stream>,
    epg: EpgData,
    now: Long,
    viewStart: Long,
    viewEnd: Long,
    dpPerMin: Dp,
    channelCol: Dp,
    rowHeight: Dp,
    pad: Dp,
    focusRequester: FocusRequester?,
    onOpened: () -> Unit,
    onReveal: (Long) -> Unit,
) {
    val isTv = LocalIsTv.current
    val span = viewEnd - viewStart
    // Render a margin either side so D-pad focus can always find a neighbour to move to.
    val programmes = remember(epg, channel.key, viewStart / (15 * MIN)) {
        epg.between(channel, viewStart - 4 * 3600_000L, viewEnd + 4 * 3600_000L)
    }
    Row(Modifier.fillMaxWidth().height(rowHeight).padding(start = pad, end = pad, bottom = 4.dp)) {
        // Channel cell
        Row(
            Modifier.width(channelCol - 6.dp).fillMaxHeight()
                .focusGlow(RoundedCornerShape(10.dp), scale = 1.03f, ring = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Hue.Surface1)
                .clickable { onOpened(); vm.playChannel(channel, channels) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NetImage(channel.image, channel.name, Modifier.size(if (isTv) 52.dp else 44.dp, 34.dp), contentScale = ContentScale.Fit, initialsSize = 11)
            if (isTv) {
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(channel.number.toString(), style = MaterialTheme.typography.labelSmall, color = Hue.TextLo)
                    Text(channel.name, style = MaterialTheme.typography.titleSmall, color = Hue.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        val density = LocalDensity.current
        if (programmes.isEmpty()) {
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .focusGlow(RoundedCornerShape(10.dp), scale = 1f, ring = 2.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Hue.Surface1.copy(alpha = 0.6f))
                    .clickable { onOpened(); vm.playChannel(channel, channels) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("No information", style = MaterialTheme.typography.bodyMedium, color = Hue.TextLo) }
        } else {
            val firstLiveIndex = programmes.indexOfFirst { now in it.start until it.end }.let { if (it < 0) 0 else it }
            Layout(
                content = {
                    programmes.forEachIndexed { i, p ->
                        val inset = with(density) { (dpPerMin * ((viewStart - p.start).coerceAtLeast(0) / MIN.toFloat())) }
                        ProgrammeCell(
                            p = p, now = now, inset = inset,
                            catchup = channel.catchupDays > 0 && p.end < now && p.start > now - channel.catchupDays * 24 * 3600_000L,
                            modifier = if (i == firstLiveIndex && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                            onFocused = {
                                val out = p.end <= viewStart || p.start >= viewEnd
                                if (out) {
                                    if (p.start >= viewEnd) onReveal(p.start - 30 * MIN)
                                    else onReveal(maxOf(p.start, p.end - span / 2))
                                }
                            },
                            onClick = { onOpened(); openProgramme(vm, channel, channels, p, now) },
                        )
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds(),
            ) { measurables, constraints ->
                val pxPerMin = with(density) { dpPerMin.toPx() }
                val gap = with(density) { 4.dp.roundToPx() }
                val h = constraints.maxHeight
                val placed = measurables.mapIndexed { i, m ->
                    val p = programmes[i]
                    val x = ((p.start - viewStart) / MIN.toFloat() * pxPerMin).toInt()
                    val w = ((p.end - p.start) / MIN.toFloat() * pxPerMin).toInt() - gap
                    x to m.measure(Constraints.fixed(w.coerceAtLeast(1), h))
                }
                layout(constraints.maxWidth, h) { placed.forEach { (x, pl) -> pl.place(x, 0) } }
            }
        }
    }
}

@Composable
private fun ProgrammeCell(
    p: Programme,
    now: Long,
    inset: Dp,
    catchup: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val live = now in p.start until p.end
    val past = p.end <= now
    Box(
        modifier
            .focusGlow(RoundedCornerShape(10.dp), scale = 1f, ring = 2.dp, onFocus = { focused = it; if (it) onFocused() })
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> Color.White
                    live -> accent.soft
                    past -> Hue.Surface1.copy(alpha = 0.55f)
                    else -> Hue.Surface1
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(Modifier.padding(start = inset + 12.dp, end = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (catchup) {
                    Icon(Icons.Rounded.History, "Catch-up", tint = if (focused) Color(0xFF3A3F55) else accent.main, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = when { focused -> Color(0xFF070912); past -> Hue.TextLo; else -> Hue.TextHi },
                )
            }
            Text(
                timeRange(p), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                color = if (focused) Color(0xFF3A3F55) else Hue.TextLo,
            )
        }
        if (live) Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(p.progress(now)).height(2.dp).background(accent.main)
        )
    }
}

private fun openProgramme(vm: AppViewModel, channel: Stream, channels: List<Stream>, p: Programme, now: Long) {
    when {
        now in p.start until p.end -> vm.playChannel(channel, channels)
        p.end <= now -> {
            val url = vm.repo.catchupUrl(channel, p)
            if (url != null) vm.navigate(
                Screen.Player(
                    PlayRequest(
                        title = p.title, subtitle = "${channel.name} · ${timeRange(p)}", url = url, kind = Kind.LIVE,
                        streamId = channel.id, watchKey = channel.key, image = channel.image,
                        userAgent = channel.userAgent, isCatchup = true,
                    )
                )
            ) else vm.toast = "Catch-up isn't available for this channel"
        }
        else -> vm.toast = "“${p.title}” starts at ${time(p.start)}"
    }
}
