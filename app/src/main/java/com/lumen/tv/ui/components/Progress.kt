package com.lumen.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.tv.data.LoadProgress
import com.lumen.tv.data.LoadStep
import com.lumen.tv.data.StepState
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.Hue

/** Full progress card: overall bar plus one row per step (channels, movies, series…). */
@Composable
fun LoadProgressCard(progress: LoadProgress?, fallbackMessage: String, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val overall by animateFloatAsState(progress?.overall ?: 0.05f, tween(400), label = "overall")
    Column(
        modifier.widthIn(max = 520.dp).fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), fill = Hue.Surface1.copy(alpha = 0.78f)).padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                progress?.title ?: fallbackMessage, style = MaterialTheme.typography.titleLarge, color = Hue.TextHi,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("${(overall * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = accent.main)
        }
        Spacer(Modifier.height(12.dp))
        ProgressLine(overall, Modifier.fillMaxWidth(), height = 8.dp)
        Spacer(Modifier.height(18.dp))
        if (progress == null) {
            Text(fallbackMessage, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                progress.steps.forEach { StepRow(it) }
            }
        }
    }
}

@Composable
private fun StepRow(step: LoadStep) {
    val accent = LocalAccent.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when (step.state) {
                StepState.DONE -> Box(Modifier.size(22.dp).clip(CircleShape).background(accent.main), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = Color(0xFF070912), modifier = Modifier.size(15.dp))
                }
                StepState.RUNNING -> {
                    val f = step.fraction
                    if (f == null) CircularProgressIndicator(color = accent.main, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                    else CircularProgressIndicator(
                        progress = { f }, color = accent.main, trackColor = Color.White.copy(alpha = 0.12f),
                        strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp),
                    )
                }
                StepState.FAILED -> Box(Modifier.size(22.dp).clip(CircleShape).background(Hue.Live), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                StepState.WAITING -> Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                step.label, style = MaterialTheme.typography.titleSmall,
                color = if (step.state == StepState.WAITING) Hue.TextLo else Hue.TextHi,
            )
            step.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Hue.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Thin bar shown at the top of the app during background refreshes and guide loading. */
@Composable
fun SlimProgressBar(fraction: Float, label: String, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0.02f, 1f), tween(300), label = "slim")
    Column(modifier.fillMaxWidth().background(Hue.Night.copy(alpha = 0.92f)).padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Hue.TextMid, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${(f * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = LocalAccent.current.main)
        }
        Spacer(Modifier.height(4.dp))
        ProgressLine(f, Modifier.fillMaxWidth(), height = 3.dp)
    }
}
