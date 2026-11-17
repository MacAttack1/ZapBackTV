package com.lumen.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.lumen.tv.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.theme.InterDisplay
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import kotlinx.coroutines.delay
import kotlin.math.abs

// ------------------------------------------------------------------ focus & surfaces

/** A soft neon halo in [color] drawn just outside the element's [shape]. [strength] runs 0..1. */
fun Modifier.neonGlow(color: Color, shape: Shape, strength: Float, spread: Dp = 3.dp): Modifier = drawBehind {
    if (strength <= 0.01f || size.minDimension <= 0f) return@drawBehind
    val outline = shape.createOutline(size, layoutDirection, this)
    val step = spread.toPx()
    val layers = 6
    for (i in 1..layers) {
        val grow = i * step
        scale((size.width + grow * 2) / size.width, (size.height + grow * 2) / size.height) {
            drawOutline(outline, color, alpha = strength * 0.26f * (1f - (i - 1f) / layers), style = Stroke(width = step * 1.2f))
        }
    }
}

/**
 * The focus treatment used everywhere: a springy scale-up, a gradient ring and a neon halo.
 * Put it *before* clickable/focusable in the chain so it observes their focus.
 */
fun Modifier.focusGlow(
    shape: Shape = RoundedCornerShape(14.dp),
    scale: Float = 1.06f,
    ring: Dp = 2.5.dp,
    onFocus: ((Boolean) -> Unit)? = null,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow), label = "fs")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "fg")
    val accent = LocalAccent.current
    this
        .onFocusChanged { if (focused != it.isFocused) { focused = it.isFocused; onFocus?.invoke(it.isFocused) } }
        .graphicsLayer { scaleX = s; scaleY = s }
        .neonGlow(accent.main, shape, glow)
        .then(if (focused && ring > 0.dp) Modifier.border(ring, accent.brush, shape) else Modifier)
}

/** Requests focus once when first composed — only on TV, where something must always own focus. */
fun Modifier.initialFocus(enabled: Boolean = true): Modifier = composed {
    val isTv = LocalIsTv.current
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (enabled && isTv) { delay(80); runCatching { fr.requestFocus() } }
    }
    focusRequester(fr)
}

/** Translucent "glass" panel: faint fill plus a hairline edge. */
fun Modifier.glass(shape: Shape, fill: Color = Hue.Glass, stroke: Color = Hue.Stroke): Modifier =
    clip(shape).background(fill).border(1.dp, stroke, shape)

/** Paints whatever is drawn (usually text or an icon) with [brush]. */
fun Modifier.gradientTint(brush: Brush): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent { drawContent(); drawRect(brush, blendMode = BlendMode.SrcAtop) }

/** Two soft washes of the accent colors behind the whole app. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier.fillMaxSize().background(Hue.Ink).drawBehind {
            val r = size.maxDimension * 0.7f
            val a = Offset(size.width * 0.08f, -size.height * 0.12f)
            drawCircle(Brush.radialGradient(listOf(accent.main.copy(alpha = 0.17f), Color.Transparent), center = a, radius = r), radius = r, center = a)
            val b = Offset(size.width * 1.02f, size.height * 1.05f)
            drawCircle(Brush.radialGradient(listOf(accent.second.copy(alpha = 0.10f), Color.Transparent), center = b, radius = r), radius = r, center = b)
        }
    )
}

// ------------------------------------------------------------------ brand

/** The ZapBackTV mark (chunky CRT + remote). Same artwork as the launcher icon. */
@Composable
fun BrandMark(diameter: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.brand_mark), contentDescription = null,
        modifier = modifier.size(diameter), contentScale = ContentScale.Fit,
    )
}

/** The "ZapBack TV" wordmark artwork, sized from a text size so it lines up with nearby text. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 15.sp) {
    Image(
        painter = painterResource(R.drawable.brand_wordmark), contentDescription = "ZapBackTV",
        modifier = modifier.height((fontSize.value * 1.7f).dp), contentScale = ContentScale.Fit,
    )
}

/** Expanding rings around a bright core — the app's loading indicator. */
@Composable
fun PulseLoader(modifier: Modifier = Modifier, diameter: Dp = 76.dp) {
    val accent = LocalAccent.current
    val t by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier.size(diameter)) {
        val max = size.minDimension / 2f
        for (i in 0 until 3) {
            val p = (t + i / 3f) % 1f
            drawCircle(lerp(accent.main, accent.second, p), radius = max * (0.24f + 0.74f * p), alpha = (1f - p) * 0.85f, style = Stroke(max * 0.06f))
        }
        drawCircle(Brush.linearGradient(listOf(accent.main, accent.second)), radius = max * 0.2f)
    }
}

// ------------------------------------------------------------------ images

private val placeholderColors = listOf(
    Color(0xFF2B2452), Color(0xFF1B3352), Color(0xFF3D2440), Color(0xFF173D45), Color(0xFF3A2A5C), Color(0xFF1F2E5C),
)

@Composable
fun NetImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    initialsSize: Int = 22,
) {
    val bg = placeholderColors[abs(name.hashCode()) % placeholderColors.size]
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        if (loaded) modifier else modifier.background(Brush.linearGradient(listOf(bg, bg.copy(alpha = 0.55f), Hue.Surface2))),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) Text(
            initials(name), color = Color.White.copy(alpha = 0.55f), fontSize = initialsSize.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(220).build(),
                contentDescription = name,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun initials(name: String): String {
    val words = name.replace(Regex("""^[A-Za-z]{2,4}\s*[:|]\s*"""), "")
        .split(' ', '-', '_', '.').filter { it.isNotBlank() && it.first().isLetterOrDigit() }
    return when {
        words.isEmpty() -> "•"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

// ------------------------------------------------------------------ buttons & chips

@Composable
fun LumenButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val fg = if (primary || focused) Hue.OnAccent else Hue.TextHi
    Row(
        modifier
            .focusGlow(shape, scale = 1.05f, ring = 0.dp, onFocus = { focused = it })
            .clip(shape)
            .then(
                when {
                    primary -> Modifier.background(accent.brush).border(2.dp, if (focused) Color.White else Color.Transparent, shape)
                    focused -> Modifier.background(Color.White)
                    else -> Modifier.background(Hue.GlassHi).border(1.dp, Hue.Stroke, shape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Hue.TextHi,
    active: Boolean = false,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .focusGlow(CircleShape, scale = 1.12f, ring = 0.dp, onFocus = { focused = it })
            .size(size)
            .clip(CircleShape)
            .then(
                when {
                    focused -> Modifier.background(Color.White)
                    active -> Modifier.background(accent.soft).border(1.dp, accent.main.copy(alpha = 0.45f), CircleShape)
                    else -> Modifier.background(Hue.Glass).border(1.dp, Hue.Stroke, CircleShape)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription,
            tint = if (focused) Hue.OnAccent else if (active) accent.main else tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val fg = if (focused || selected) Hue.OnAccent else Hue.TextMid
    Row(
        modifier
            .focusGlow(shape, scale = 1.06f, ring = 0.dp, onFocus = { focused = it })
            .clip(shape)
            .then(
                when {
                    focused -> Modifier.background(Color.White)
                    selected -> Modifier.background(accent.brush)
                    else -> Modifier.background(Hue.Glass).border(1.dp, Hue.Stroke, shape)
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A row in a vertical list (categories rail, settings, etc.). */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    subtitle: String? = null,
    trailing: String? = null,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .focusGlow(shape, scale = 1.02f, ring = 0.dp, onFocus = { focused = it; onFocus?.invoke(it) })
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> accent.soft
                    else -> Color.Transparent
                }
            )
            .drawBehind {
                // Selected marker: a short gradient bar on the leading edge.
                if (selected && !focused) drawRoundRect(
                    accent.brush, topLeft = Offset(0f, size.height * 0.26f),
                    size = Size(3.dp.toPx(), size.height * 0.48f), cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) Hue.OnAccent else if (selected) accent.main else Hue.TextHi
        val dim = if (focused) Color(0xFF3A3F55) else Hue.TextLo
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = dim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(trailing, color = dim, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ headers & states

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Hue.TextLo)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val accent = LocalAccent.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 20.dp).clip(RoundedCornerShape(50)).background(accent.brush))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = Hue.TextLo)
    }
}

/** Small all-caps label in the accent gradient, used above titles. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = modifier.gradientTint(LocalAccent.current.brush))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(accent.soft).border(1.5.dp, accent.brush, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = accent.main, modifier = Modifier.size(38.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message, style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp),
        )
        if (action != null) { Spacer(Modifier.height(24.dp)); action() }
    }
}

@Composable
fun LoadingState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PulseLoader()
        Spacer(Modifier.height(18.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid)
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, track: Color = Color.White.copy(alpha = 0.16f)) {
    val accent = LocalAccent.current
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height)
                .clip(RoundedCornerShape(50)).background(accent.brush)
        )
    }
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier, text: String = "LIVE") {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f, targetValue = 0.3f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot",
    )
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Hue.Live).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).graphicsLayer { alpha = pulse }.clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(5.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun MetaChip(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Hue.TextMid) {
    Row(
        modifier.glass(RoundedCornerShape(8.dp), fill = Hue.GlassHi).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text, color = tint, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
