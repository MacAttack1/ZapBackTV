package com.lumen.tv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Category
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Library
import com.lumen.tv.data.Programme
import com.lumen.tv.data.Stream
import com.lumen.tv.player.MiniPlayer
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.components.ChannelListItem
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.ListRow
import com.lumen.tv.ui.components.LiveBadge
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.NetImage
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.ProgressLine
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import com.lumen.tv.ui.components.Eyebrow

@Composable
fun LiveScreen(vm: AppViewModel) {
    val lib = vm.library.collectAsStateWithLifecycle().value ?: return
    val favs by vm.store.favorites.collectAsStateWithLifecycle()
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current

    var query by rememberSaveable { mutableStateOf("") }
    val f = rememberLiveFilter(vm, lib, query)
    val channels = f.channels
    val listKey = "${f.region}|${f.genre}|${f.catId}"
    val favKeys = remember(favs, lib) { favs.filter { it.startsWith("${lib.sourceId}|") }.map { it.substringAfter('|') }.toHashSet() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isTv || maxWidth >= 840.dp
        if (wide) {
            var focused by remember { mutableStateOf<Stream?>(null) }
            LaunchedEffect(channels) { if (focused == null || channels.none { it.key == focused?.key }) focused = channels.firstOrNull() }
            Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
                Row(Modifier.padding(start = 30.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Live TV", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
                    Spacer(Modifier.width(14.dp))
                    Text("${f.regionLabel} · ${f.genre?.label ?: "All genres"}", style = MaterialTheme.typography.bodyLarge, color = Hue.TextLo)
                }
                FilterChips(f, pad = 16.dp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxSize()) {
                    // Categories, grouped by genre
                    LazyColumn(
                        Modifier.width(270.dp).fillMaxHeight().padding(start = 16.dp, end = 8.dp).focusRestorer(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 32.dp),
                    ) {
                        groupedCategories(f) { f.setCategory(it) }
                    }
                    // Channels
                    Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
                        FilterField(query, onChange = { query = it }, placeholder = "Filter ${channels.size} channels")
                        Spacer(Modifier.height(10.dp))
                        ChannelList(
                            channels, epg = { epg.now(it) }, selectedKey = focused?.key, favKeys = favKeys,
                            showNumbers = settings.showNumbers,
                            onFocus = { focused = it },
                            onClick = { c -> if (isTv || focused?.key == c.key) vm.playChannel(c, channels) else focused = c },
                            onLongClick = { vm.toggleFavorite(it) },
                            modifier = Modifier.weight(1f),
                            catKey = listKey,
                            focusKey = f.catId,
                        )
                    }
                    // Preview
                    Box(Modifier.weight(1.1f).fillMaxHeight().padding(start = 8.dp, end = 32.dp)) {
                        focused?.let { ch ->
                            PreviewPane(
                                vm, ch, now = epg.now(ch), upcoming = epg.upcoming(ch, 5),
                                autoplay = settings.previewOnFocus, isFavorite = ch.key in favKeys,
                                onWatch = { vm.playChannel(ch, channels) },
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(4.dp))
                FilterChips(f, pad = 18.dp)
                Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    CategoryPickerButton(f, Modifier.fillMaxWidth())
                }
                Box(Modifier.padding(horizontal = 18.dp)) {
                    FilterField(query, onChange = { query = it }, placeholder = "Search these channels")
                }
                Spacer(Modifier.height(6.dp))
                ChannelList(
                    channels, epg = { epg.now(it) }, selectedKey = null, favKeys = favKeys,
                    showNumbers = settings.showNumbers, onFocus = {},
                    onClick = { vm.playChannel(it, channels) },
                    onLongClick = { vm.toggleFavorite(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    catKey = listKey,
                )
            }
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<Stream>,
    epg: (Stream) -> Programme?,
    selectedKey: String?,
    favKeys: Set<String>,
    showNumbers: Boolean,
    onFocus: (Stream) -> Unit,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    catKey: String,
    modifier: Modifier = Modifier,
    focusKey: String = catKey,
) {
    if (channels.isEmpty()) {
        val fav = catKey.endsWith("|${Library.FAVORITES}")
        EmptyState(
            if (fav) Icons.Rounded.FavoriteBorder else Icons.Rounded.SearchOff,
            if (fav) "No favorites yet" else "No channels",
            if (fav) "Long-press any channel to add it here." else "Nothing matches here. Try another country or genre.",
            modifier,
        )
        return
    }
    val state = rememberLazyListState()
    val isTv = LocalIsTv.current
    // Remembered across playback: which list this is and which channel was opened last.
    var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
    var openedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val targetKey = openedKey?.takeIf { k -> channels.any { it.listKey == k } } ?: channels.first().listKey
    val target = remember { FocusRequester() }
    // Back to the top only when the list really changes, not when returning from the player.
    LaunchedEffect(catKey) {
        if (shownKey != null && shownKey != catKey) { openedKey = null; state.scrollToItem(0) }
        shownKey = catKey
    }
    // Focus the channel you were on (or the first one); only when a category is picked or the
    // screen is re-entered, not while flipping country/genre chips.
    LaunchedEffect(focusKey) { if (isTv) { delay(150); runCatching { target.requestFocus() } } }
    LazyColumn(
        modifier.fillMaxWidth().focusRestorer(),
        state = state,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 40.dp, top = 4.dp, start = 4.dp, end = 4.dp),
    ) {
        itemsIndexed(channels, key = { _, c -> c.listKey }) { i, c ->
            ChannelListItem(
                c, now = epg(c), selected = c.key == selectedKey, showNumber = showNumbers,
                isFavorite = c.key in favKeys,
                modifier = if (c.listKey == targetKey) Modifier.focusRequester(target) else Modifier,
                onFocus = { if (it) onFocus(c) },
                onLongClick = { onLongClick(c) },
            ) { openedKey = c.listKey; onClick(c) }
        }
    }
}

@Composable
fun FilterField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        placeholder = { Text(placeholder, color = Hue.TextLo, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Hue.TextLo) },
        trailingIcon = if (value.isNotEmpty()) ({
            Icon(
                Icons.Rounded.Close, "Clear", tint = Hue.TextMid,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onChange("") }.padding(4.dp),
            )
        }) else null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Hue.Surface2,
            unfocusedContainerColor = Hue.Surface1,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = accent.main,
            focusedTextColor = Hue.TextHi,
            unfocusedTextColor = Hue.TextHi,
        ),
    )
}

@Composable
private fun PreviewPane(
    vm: AppViewModel,
    channel: Stream,
    now: Programme?,
    upcoming: List<Programme>,
    autoplay: Boolean,
    isFavorite: Boolean,
    onWatch: () -> Unit,
) {
    val accent = LocalAccent.current
    // Dwell before tuning so fast scrolling doesn't hammer the provider (and its connection limit).
    var previewUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(channel.key, autoplay) {
        previewUrl = null
        if (autoplay) { delay(900); previewUrl = channel.url }
    }
    var fallback by remember(channel.key) { mutableStateOf<List<Programme>>(emptyList()) }
    LaunchedEffect(channel.key, upcoming.isEmpty()) {
        if (upcoming.isEmpty()) { delay(400); fallback = vm.repo.shortEpg(channel) }
    }
    val schedule = upcoming.ifEmpty { fallback }
    val current = now ?: schedule.firstOrNull { it.isLive() }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            NetImage(channel.image, channel.name, Modifier.size(140.dp, 90.dp), contentScale = ContentScale.Fit)
            if (previewUrl != null) MiniPlayer(previewUrl, channel.userAgent, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveBadge()
            }
            Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(54.dp, 34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f))) {
                    NetImage(channel.image, channel.name, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit, initialsSize = 12)
                }
                Spacer(Modifier.width(10.dp))
                Text(channel.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (current != null) {
            Eyebrow("Now")
            Spacer(Modifier.height(4.dp))
            Text(current.title, style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(timeRange(current), style = MaterialTheme.typography.labelMedium, color = Hue.TextMid)
                Spacer(Modifier.width(12.dp))
                ProgressLine(current.progress(), Modifier.width(140.dp))
                Spacer(Modifier.width(10.dp))
                Text("${((current.end - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)} min left", style = MaterialTheme.typography.labelMedium, color = Hue.TextLo)
            }
            current.desc?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text("No guide information for this channel", style = MaterialTheme.typography.bodyLarge, color = Hue.TextLo)
        }
        val next = schedule.filter { it.start >= (current?.end ?: System.currentTimeMillis()) - 1000 }.take(3)
        if (next.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text("UP NEXT", style = MaterialTheme.typography.labelSmall, color = Hue.TextLo)
            Spacer(Modifier.height(6.dp))
            next.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(time(p.start), style = MaterialTheme.typography.labelLarge, color = Hue.TextMid, modifier = Modifier.width(64.dp))
                    Text(p.title, style = MaterialTheme.typography.bodyMedium, color = Hue.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(!LocalIsTv.current, enter = fadeIn(), exit = fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                LumenButton("Watch", onClick = onWatch, icon = Icons.Rounded.OpenInFull)
                LumenButton(if (isFavorite) "Favorited" else "Favorite", onClick = { vm.toggleFavorite(channel) },
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, primary = false)
            }
        }
        if (LocalIsTv.current) {
            Text(
                "OK to watch · hold OK to favorite",
                style = MaterialTheme.typography.labelMedium, color = Hue.TextLo, modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

fun time(t: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))
fun timeRange(p: Programme): String = "${time(p.start)} – ${time(p.end)}"
