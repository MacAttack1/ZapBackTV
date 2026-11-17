package com.lumen.tv.ui.components

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lumen.tv.ui.theme.AppTheme
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.theme.LocalBackdrop
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** The built-in backgrounds. Each is drawn in the current theme's colors. */
class Pattern(val id: String, val name: String)

val Patterns = listOf(
    Pattern("glow", "Glow"),
    Pattern("burst", "Starburst"),
    Pattern("grid", "Synthwave"),
    Pattern("halftone", "Halftone"),
    Pattern("waves", "Waves"),
    Pattern("bubbles", "Bubbles"),
    Pattern("confetti", "Confetti"),
    Pattern("stripes", "Stripes"),
    Pattern("solid", "Solid"),
)

fun isPictureBackground(value: String) = value.startsWith("file:") || value.startsWith("url:")

/** Behind the whole app: the chosen pattern, or a picture with the theme color laid over it. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current
    val theme = Hue.theme
    val v = backdrop.value
    if (!isPictureBackground(v)) {
        Canvas(modifier.fillMaxSize()) { drawPattern(v, theme) }
        return
    }
    val model: Any = if (v.startsWith("file:")) File(v.removePrefix("file:")) else v.removePrefix("url:")
    Box(modifier.fillMaxSize().clipToBounds()) {
        // Shows while the picture loads, and stays if it can't be loaded.
        Canvas(Modifier.fillMaxSize()) { drawPattern("glow", theme) }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(model).crossfade(300).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            // Blur needs Android 12; older versions show the picture sharp.
            modifier = Modifier.fillMaxSize()
                .then(if (backdrop.blur && Build.VERSION.SDK_INT >= 31) Modifier.blur(22.dp) else Modifier),
        )
        Box(Modifier.fillMaxSize().background(theme.ink.copy(alpha = backdrop.dim.coerceIn(0, 90) / 100f)))
    }
}

/** A small live preview of a pattern, for the background picker. */
@Composable
fun PatternSwatch(id: String, theme: AppTheme, modifier: Modifier = Modifier) {
    Canvas(modifier.clipToBounds()) { drawPattern(id, theme) }
}

/** Draws pattern [id] filling the area. Sizes scale with the area, so swatches match the real thing. */
fun DrawScope.drawPattern(id: String, t: AppTheme) {
    val w = size.width
    val h = size.height
    val u = maxOf(w, h) / 100f
    drawRect(t.ink)
    when (id) {
        "solid" -> Unit

        "burst" -> {
            val c = Offset(w * 0.78f, h * 0.2f)
            val r = maxOf(w, h) * 1.6f
            val rays = 28
            val sweep = 360f / rays
            for (i in 0 until rays step 2) {
                drawArc(
                    lerp(t.ink, t.main, 0.28f), startAngle = i * sweep, sweepAngle = sweep, useCenter = true,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                )
            }
            glow(t.second, c, 30 * u, 0.55f)
            drawCircle(t.second, radius = 5 * u, center = c)
        }

        "grid" -> {
            val horizon = h * 0.6f
            // sky
            drawRect(Brush.verticalGradient(listOf(t.ink, lerp(t.ink, t.main, 0.35f)), 0f, horizon), size = Size(w, horizon))
            // striped sun sitting on the horizon
            val sr = minOf(w, h) * 0.26f
            val sc = Offset(w * 0.5f, horizon)
            clipRect(0f, 0f, w, horizon) {
                drawCircle(Brush.verticalGradient(listOf(t.second, t.main), sc.y - sr, sc.y), radius = sr, center = sc)
                var y = sc.y - sr * 0.45f
                var gap = sr * 0.03f
                while (y < horizon) {
                    drawRect(lerp(t.ink, t.main, 0.3f), Offset(sc.x - sr, y), Size(sr * 2, gap))
                    y += sr * 0.13f; gap *= 1.35f
                }
            }
            // floor grid in perspective
            drawRect(t.night, Offset(0f, horizon), Size(w, h - horizon))
            val line = t.main.copy(alpha = 0.85f)
            val stroke = maxOf(1.5f, 0.22f * u)
            var k = 1f
            while (true) {
                val y = horizon + (h - horizon) * (k * k) / 60f
                if (y > h) break
                drawLine(line, Offset(0f, y), Offset(w, y), stroke)
                k += 1f
            }
            val vx = w * 0.5f
            for (i in -14..14) {
                drawLine(line, Offset(vx + i * w * 0.012f, horizon), Offset(vx + i * w * 0.16f, h), stroke)
            }
            drawLine(t.second, Offset(0f, horizon), Offset(w, horizon), stroke * 1.6f)
        }

        "halftone" -> {
            glow(t.main, Offset(0f, 0f), 70 * u, 0.35f)
            val step = 2.6f * u
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) {
                    val f = 1f - (x / w * 0.55f + y / h * 0.6f)
                    if (f > 0.06f) drawCircle(t.main.copy(alpha = 0.55f), radius = step * 0.46f * f, center = Offset(x, y))
                    val g = (x / w * 0.55f + y / h * 0.6f) - 0.62f
                    if (g > 0.04f) drawCircle(t.second.copy(alpha = 0.45f), radius = step * 0.9f * g, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }

        "waves" -> {
            glow(t.main, Offset(w * 0.15f, h * 0.05f), 60 * u, 0.35f)
            val bands = 5
            for (i in 0 until bands) {
                val base = h * (0.5f + i * 0.1f)
                val amp = 3.2f * u
                val len = w / (1.1f + i * 0.25f)
                val phase = i * 1.3f
                val path = Path().apply {
                    moveTo(0f, h)
                    var x = 0f
                    while (x <= w + 4f) {
                        lineTo(x, base + amp * sin((x / len * 2 * PI + phase).toFloat()))
                        x += 6f
                    }
                    lineTo(w, h); close()
                }
                val color = if (i % 2 == 0) t.main else t.second
                drawPath(path, lerp(t.ink, color, 0.3f + i * 0.13f))
            }
        }

        "bubbles" -> {
            glow(t.main, Offset(w * 0.2f, h * 0.2f), 60 * u, 0.3f)
            val rnd = Random(11)
            repeat(30) {
                val r = (1.5f + rnd.nextFloat() * 11f) * u
                val c = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                val color = if (rnd.nextBoolean()) t.main else t.second
                drawCircle(color.copy(alpha = 0.16f), radius = r, center = c)
                drawCircle(color.copy(alpha = 0.6f), radius = r, center = c, style = Stroke(0.3f * u))
                drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.16f, center = Offset(c.x - r * 0.42f, c.y - r * 0.42f))
            }
        }

        "confetti" -> {
            glow(t.main, Offset(w * 0.5f, h * 0.5f), 70 * u, 0.25f)
            val rnd = Random(5)
            val colors = listOf(t.main, t.second, Color.White)
            repeat(110) {
                val c = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)
                val color = colors[rnd.nextInt(colors.size)].copy(alpha = 0.75f)
                val angle = rnd.nextFloat() * 360f
                when (rnd.nextInt(3)) {
                    0 -> rotate(angle, c) { drawRect(color, Offset(c.x - 0.35f * u, c.y - 1.1f * u), Size(0.7f * u, 2.2f * u)) }
                    1 -> drawCircle(color, radius = 0.55f * u, center = c)
                    else -> rotate(angle, c) {
                        drawPath(Path().apply {
                            moveTo(c.x, c.y - 0.9f * u); lineTo(c.x + 0.8f * u, c.y + 0.6f * u); lineTo(c.x - 0.8f * u, c.y + 0.6f * u); close()
                        }, color)
                    }
                }
            }
        }

        "stripes" -> {
            // Bold diagonal racing stripes across one corner.
            val colors = listOf(t.main, t.second, lerp(t.ink, t.main, 0.35f))
            val band = 9f * u
            rotate(-32f, Offset(w, h)) {
                colors.forEachIndexed { i, c ->
                    drawRect(c, Offset(-w, h - band * (i + 1) * 1.15f - h * 0.1f), Size(w * 3, band))
                }
            }
            glow(t.main, Offset(0f, 0f), 55 * u, 0.3f)
        }

        else -> { // "glow"
            glow(t.main, Offset(w * 0.05f, -h * 0.1f), maxOf(w, h) * 0.8f, 0.6f)
            glow(t.second, Offset(w * 1.02f, h * 1.05f), maxOf(w, h) * 0.7f, 0.45f)
        }
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius), radius = radius, center = center)
}
