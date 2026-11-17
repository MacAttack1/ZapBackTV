package com.lumen.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lumen.tv.R

// --------------------------------------------------------------------- palette
// Night-sky neutrals with a violet cast, so the accent gradient reads as light.

object Hue {
    val Ink = Color(0xFF07060E)       // app background
    val Night = Color(0xFF0A0C15)     // rails, sheets, bars
    val Surface1 = Color(0xFF10131F)
    val Surface2 = Color(0xFF181C2B)
    val Surface3 = Color(0xFF23283B)
    val Stroke = Color(0x1FFFFFFF)    // hairline borders
    val Glass = Color(0x0FFFFFFF)     // translucent panel fill
    val GlassHi = Color(0x1AFFFFFF)
    val TextHi = Color(0xFFF4F6FF)
    val TextMid = Color(0xFFA8B0CC)
    val TextLo = Color(0xFF6B7391)
    val Live = Color(0xFFFF4D6A)
    val Gold = Color(0xFFFFC857)
    val Success = Color(0xFF3DDC97)
    val OnAccent = Color(0xFF070912)  // text/icons on top of the accent gradient
}

data class Accent(val name: String, val main: Color, val second: Color) {
    /** The signature gradient: buttons, selected chips, focus rings, progress. */
    val brush: Brush get() = Brush.linearGradient(listOf(main, second))
    val soft: Color get() = main.copy(alpha = 0.16f)
}

val Accents = listOf(
    Accent("Zap", Color(0xFFFF3DA0), Color(0xFFFFB21E)),     // the logo's magenta and orange
    Accent("Dawn", Color(0xFFFF6A9A), Color(0xFFFFB06B)),
    Accent("Aurora", Color(0xFF9B8CFF), Color(0xFF22D3EE)),
    Accent("Ember", Color(0xFFFF7A45), Color(0xFFFFB55C)),
    Accent("Nova", Color(0xFFFF5CA8), Color(0xFFFF9D6C)),
    Accent("Lagoon", Color(0xFF22C7D9), Color(0xFF4ADEAA)),
    Accent("Lime", Color(0xFFA3E35D), Color(0xFF4ADE80)),
    Accent("Gold", Color(0xFFFFC45C), Color(0xFFFFE08A)),
)

val LocalAccent = staticCompositionLocalOf { Accents[0] }
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
fun LumenTheme(accentIndex: Int, isTv: Boolean, content: @Composable () -> Unit) {
    val accent = Accents.getOrElse(accentIndex) { Accents[0] }
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
    CompositionLocalProvider(LocalAccent provides accent, LocalIsTv provides isTv) {
        // TV UIs are viewed from ~3m away; Compose dp already scales with density, so only nudge type.
        MaterialTheme(colorScheme = scheme, typography = typography(if (isTv) 0.95f else 1f), content = content)
    }
}
