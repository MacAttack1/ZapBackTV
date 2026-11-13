package com.lumen.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lumen.tv.data.Programme
import com.lumen.tv.data.Stream
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.Hue
import java.util.Locale

/** 2:3 poster for movies and series. */
@Composable
fun PosterCard(
    stream: Stream,
    modifier: Modifier = Modifier,
    width: Dp? = 140.dp,
    progress: Float? = null,
    isFavorite: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(if (width != null) modifier.width(width) else modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusGlow(shape, onFocus = { focused = it; onFocus?.invoke(it) })
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            NetImage(stream.image, stream.name, Modifier.fillMaxSize())
            // Readability scrim
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))
                )
            )
            Row(Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stream.rating?.let { RatingBadge(it) }
            }
            if (isFavorite) Icon(
                Icons.Rounded.Favorite, null, tint = LocalAccent.current.main,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp),
            )
            if (progress != null && progress > 0f) ProgressLine(
                progress, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp)
            )
            // Fade the play badge in on focus (a plain alpha animation avoids the
            // ColumnScope/BoxScope AnimatedVisibility overload clash here).
            val playAlpha by animateFloatAsState(if (focused) 1f else 0f, label = "play")
            Box(
                Modifier.align(Alignment.Center).alpha(playAlpha)
                    .size(48.dp).clip(RoundedCornerShape(50)).background(LocalAccent.current.brush),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Hue.OnAccent, modifier = Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stream.name, style = MaterialTheme.typography.titleSmall,
            color = if (focused) Hue.TextHi else Hue.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (stream.year != null) Text(stream.year, style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1)
    }
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Star, null, tint = Hue.Gold, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(3.dp))
        Text(String.format(Locale.US, "%.1f", rating.coerceAtMost(10.0)), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** 16:9 card used for "Continue watching". */
@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    image: String?,
    progress: Float,
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.width(width)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .focusGlow(shape, onFocus = { focused = it })
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            NetImage(image, title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(50))
                        .then(if (focused) Modifier.background(accent.brush) else Modifier.background(Color.White.copy(alpha = 0.2f))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, null, tint = if (focused) Hue.OnAccent else Color.White, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(10.dp))
                ProgressLine(progress, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (focused) Hue.TextHi else Hue.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Channel tile for home rows: logo on a soft panel plus what's on now. */
@Composable
fun ChannelTile(
    channel: Stream,
    now: Programme?,
    modifier: Modifier = Modifier,
    width: Dp = 200.dp,
    recent: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.width(width)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .focusGlow(shape, onFocus = { focused = it })
                .glass(shape, fill = if (focused) Hue.Surface3 else Hue.Glass)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            NetImage(
                channel.image, channel.name,
                Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 22.dp).background(Color.Transparent),
                contentScale = ContentScale.Fit,
            )
            Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (recent) Icon(Icons.Rounded.History, null, tint = Hue.TextLo, modifier = Modifier.size(14.dp))
            }
            if (now != null) ProgressLine(
                now.progress(), Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), height = 2.5.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(channel.name, style = MaterialTheme.typography.titleSmall, color = if (focused) Hue.TextHi else Hue.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(now?.title ?: " ", style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Dense row for the Live TV list. */
@Composable
fun ChannelListItem(
    channel: Stream,
    now: Programme?,
    selected: Boolean,
    showNumber: Boolean,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
    onFocus: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .focusGlow(shape, scale = 1.02f, ring = 2.dp, onFocus = { focused = it; onFocus?.invoke(it) })
            .clip(shape)
            .background(
                when {
                    focused -> Hue.Surface3
                    selected -> accent.soft
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNumber) {
            Text(
                channel.number.toString(), style = MaterialTheme.typography.labelLarge,
                color = if (selected || focused) accent.main else Hue.TextLo,
                modifier = Modifier.width(40.dp),
            )
        }
        Box(
            Modifier.size(width = 64.dp, height = 40.dp).glass(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            NetImage(channel.image, channel.name, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit, initialsSize = 13)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name, style = MaterialTheme.typography.titleSmall, color = Hue.TextHi,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (isFavorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Favorite, null, tint = accent.main, modifier = Modifier.size(13.dp))
                }
                if (channel.catchupDays > 0) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.History, "Catch-up", tint = Hue.TextLo, modifier = Modifier.size(13.dp))
                }
            }
            if (now != null) {
                Text(now.title, style = MaterialTheme.typography.bodySmall, color = Hue.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                ProgressLine(now.progress(), Modifier.fillMaxWidth(0.7f), height = 2.dp)
            } else {
                Text("No program info", style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1)
            }
        }
    }
}
