package com.lumen.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumen.tv.R

// --------------------------------------------------------------------- themes
// A theme colors everything: the background, the panels, the bars and the accent. Backgrounds are
// saturated (deep purple, ocean blue, cherry red…) rather than near-black, and accents are bold flat
// colors. Text stays white, so every theme keeps a dark-enough base for it to read.

/** One full color theme. Panels and text tints are worked out from [ink] so they always match. */
class AppTheme(
    val id: String,
    val name: String,
    val ink: Color,            // the app background
    val night: Color,          // rails, bars and sheets
    val main: Color,           // buttons, selection, focus
    val second: Color,         // highlights, the second color in patterns
    val gradient: Boolean = false, // accent as a two-color gradient (the classic look) instead of flat
) {
    val surface1: Color = mix(ink, main, 0.06f, 0.05f)
    val surface2: Color = mix(ink, main, 0.10f, 0.10f)
    val surface3: Color = mix(ink, main, 0.14f, 0.16f)
    val textMid: Color = lerp(ink, Color.White, 0.76f)
    val textLo: Color = lerp(ink, Color.White, 0.54f)
    val onAccent: Color = lerp(ink, Color.Black, 0.7f)
    val accent = Accent(name, main, second, gradient)

    private companion object {
        fun mix(base: Color, tint: Color, t: Float, light: Float) = lerp(lerp(base, tint, t), Color.White, light)
    }
}

val Themes = listOf(
    AppTheme("zap", "ZapBack", Color(0xFF2B0B63), Color(0xFF1F0749), Color(0xFFFF2E97), Color(0xFFFFE11A)),
    AppTheme("arcade", "Neon Arcade", Color(0xFF0A0A24), Color(0xFF050514), Color(0xFF00F0FF), Color(0xFFFF2BD6)),
    AppTheme("electric", "Electric", Color(0xFF0D1A9C), Color(0xFF0A1378), Color(0xFF00E5FF), Color(0xFFFF3D81)),
    AppTheme("ocean", "Ocean Pop", Color(0xFF00386F), Color(0xFF002A55), Color(0xFFFFD60A), Color(0xFF00E5C3)),
    AppTheme("toxic", "Toxic", Color(0xFF07301A), Color(0xFF042211), Color(0xFFC6FF00), Color(0xFF00FFA3)),
    AppTheme("lava", "Lava", Color(0xFF4F0814), Color(0xFF3A050E), Color(0xFFFF8A00), Color(0xFFFFDD00)),
    AppTheme("sunset", "Sunset Pop", Color(0xFF5C0B3A), Color(0xFF45072B), Color(0xFFFFC300), Color(0xFFFF5A5F)),
    AppTheme("grape", "Grape Soda", Color(0xFF420C6B), Color(0xFF310851), Color(0xFFFF9100), Color(0xFFFF3DA0)),
    AppTheme("bubblegum", "Bubblegum", Color(0xFF6B1260), Color(0xFF520C49), Color(0xFF7DF9FF), Color(0xFFFFF05A)),
    AppTheme("midnight", "Midnight", Color(0xFF07060E), Color(0xFF0A0C15), Color(0xFFFF3DA0), Color(0xFFFFB21E), gradient = true),
)

fun themeById(id: String): AppTheme = Themes.firstOrNull { it.id == id } ?: Themes[0]

/**
 * The live palette. Every color reads from the current theme, so switching themes repaints the
 * whole app at once (each read is tracked by Compose).
 */
object Hue {
    var theme: AppTheme by mutableStateOf(Themes[0])

    val Ink: Color get() = theme.ink
    val Night: Color get() = theme.night
    val Surface1: Color get() = theme.surface1
    val Surface2: Color get() = theme.surface2
    val Surface3: Color get() = theme.surface3
    val Stroke = Color(0x29FFFFFF)    // hairline borders
    val Glass = Color(0x14FFFFFF)     // translucent panel fill
    val GlassHi = Color(0x24FFFFFF)
    val TextHi = Color(0xFFFFFFFF)
    val TextMid: Color get() = theme.textMid
    val TextLo: Color get() = theme.textLo
    val Live = Color(0xFFFF3B5C)
    val Gold = Color(0xFFFFC857)
    val Success = Color(0xFF3DDC97)
    val OnAccent: Color get() = theme.onAccent  // text/icons on top of the accent
}

class Accent(val name: String, val main: Color, val second: Color, val gradient: Boolean = false) {
    /** Buttons, selected chips, focus rings, progress: a flat bold color, or the classic two-color gradient. */
    val brush: Brush = if (gradient) Brush.linearGradient(listOf(main, second)) else SolidColor(main)
    val soft: Color get() = main.copy(alpha = 0.2f)
}

/** What's drawn behind the app: a built-in pattern, a photo, or a picture from a link. */
data class Backdrop(val value: String = "glow", val dim: Int = 35, val blur: Boolean = false)

val LocalAccent = staticCompositionLocalOf { Themes[0].accent }
val LocalBackdrop = staticCompositionLocalOf { Backdrop() }
val LocalIsTv = staticCompositionLocalOf { false }

// ------------------------------------------------------------------ typography

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)
val InterDisplay = FontFamily(
    Font(R.font.inter_display_bold, FontWeight.Bold),
    Font(R.font.inter_display_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_display_black, FontWeight.Black),
)

// Heavy, tightly tracked display type against wide-tracked small caps labels.
private fun typography(scale: Float) = Typography(
    displayLarge = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Black, fontSize = (46 * scale).sp, lineHeight = (49 * scale).sp, letterSpacing = (-0.03).em),
    displayMedium = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Black, fontSize = (36 * scale).sp, lineHeight = (39 * scale).sp, letterSpacing = (-0.03).em),
    headlineLarge = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, fontSize = (28 * scale).sp, lineHeight = (32 * scale).sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.Bold, fontSize = (22 * scale).sp, lineHeight = (27 * scale).sp, letterSpacing = (-0.015).em),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = (18 * scale).sp, lineHeight = (24 * scale).sp, letterSpacing = (-0.005).em),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = (15 * scale).sp, lineHeight = (20 * scale).sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = (15 * scale).sp, lineHeight = (22 * scale).sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = (13.5f * scale).sp, lineHeight = (19 * scale).sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = (12 * scale).sp, lineHeight = (16 * scale).sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = (14 * scale).sp, lineHeight = (18 * scale).sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = (12 * scale).sp, lineHeight = (16 * scale).sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = (10.5f * scale).sp, lineHeight = (14 * scale).sp, letterSpacing = 0.14.em),
)

@Composable
fun LumenTheme(backdrop: Backdrop, isTv: Boolean, content: @Composable () -> Unit) {
    val accent = Hue.theme.accent
    val scheme = darkColorScheme(
        primary = accent.main,
        onPrimary = Hue.OnAccent,
        secondary = accent.second,
        background = Hue.Ink,
        onBackground = Hue.TextHi,
        surface = Hue.Surface1,
        onSurface = Hue.TextHi,
        surfaceVariant = Hue.Surface2,
        onSurfaceVariant = Hue.TextMid,
        surfaceContainer = Hue.Surface2,
        surfaceContainerHigh = Hue.Surface3,
        surfaceContainerHighest = Hue.Surface3,
        outline = Hue.Stroke,
        outlineVariant = Hue.Stroke,
        error = Hue.Live,
    )
    CompositionLocalProvider(LocalAccent provides accent, LocalBackdrop provides backdrop, LocalIsTv provides isTv) {
        // TV UIs are viewed from ~3m away; Compose dp already scales with density, so only nudge type.
        MaterialTheme(colorScheme = scheme, typography = typography(if (isTv) 0.95f else 1f), content = content)
    }
}
