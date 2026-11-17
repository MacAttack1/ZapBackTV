package com.lumen.tv.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.formatBytes
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.update.Release
import com.lumen.tv.update.UpdateState
import java.text.DateFormat
import java.util.Date

/** Opens a link in a browser; TVs often have none, so say where to go instead. */
fun openLink(context: Context, url: String, onFail: (String) -> Unit) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        onFail("No browser on this device. Visit ${url.removePrefix("https://")}")
    }
}

/** The update prompt: version, changelog, and update / skip / later. Drives the whole download-and-install flow. */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val state by vm.updater.state.collectAsStateWithLifecycle()
    val release: Release = when (val s = state) {
        is UpdateState.Available -> if (s.prompt) s.release else null
        is UpdateState.Downloading -> s.release
        is UpdateState.Ready -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    } ?: run {
        // A failed manual check with nothing to offer.
        val s = state as? UpdateState.Failed ?: return
        MessageDialog("Update check failed", s.message) { vm.updater.later() }
        return
    }
    val context = LocalContext.current
    val busy = state is UpdateState.Downloading || state is UpdateState.Installing

    Dialog(
        onDismissRequest = { if (!busy) vm.updater.later() },
        properties = DialogProperties(dismissOnClickOutside = !busy, usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), fill = Hue.Surface1.copy(alpha = 0.97f)).padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(58.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow("Update available")
                    Text("ZapBackTV ${release.version}", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
                    Text(
                        listOfNotNull(
                            "You have ${vm.updater.currentVersion}",
                            release.publishedAt.takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
                            release.apkSize.takeIf { it > 0 }?.let { formatBytes(it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = Hue.TextLo,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("What's new", style = MaterialTheme.typography.titleMedium, color = Hue.TextHi)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(Hue.Ink.copy(alpha = 0.6f))
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) { Changelog(release.notes.ifBlank { release.title }) }
            Spacer(Modifier.height(18.dp))

            when (val s = state) {
                is UpdateState.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Downloading…", style = MaterialTheme.typography.labelLarge, color = Hue.TextMid, modifier = Modifier.weight(1f))
                        s.fraction?.let { Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = LocalAccent.current.main) }
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressLine(s.fraction ?: 0.05f, Modifier.fillMaxWidth(), height = 6.dp)
                }
                is UpdateState.Installing -> Text(
                    "Confirm the install in Android's prompt. ZapBackTV will close and reopen updated.",
                    style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid,
                )
                is UpdateState.Ready -> if (s.needsPermission) Text(
                    "Android needs your OK to let ZapBackTV install updates. Tap “Allow installs”, switch it on, then come back and tap “Install”.",
                    style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid,
                ) else Text("Downloaded and ready to install.", style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid)
                is UpdateState.Failed -> Text(s.message, style = MaterialTheme.typography.bodyMedium, color = Hue.Live)
                else -> Unit
            }
            if (state !is UpdateState.Available) Spacer(Modifier.height(16.dp))

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val s = state) {
                    is UpdateState.Available -> {
                        LumenButton("Update now", onClick = { vm.updater.download(release) }, icon = Icons.Rounded.Download, modifier = Modifier.initialFocus())
                        LumenButton("Skip this version", onClick = { vm.updater.skip(release) }, primary = false)
                        LumenButton("Later", onClick = { vm.updater.later() }, primary = false)
                    }
                    is UpdateState.Ready -> {
                        if (s.needsPermission) LumenButton("Allow installs", onClick = { vm.updater.openInstallPermission() }, icon = Icons.Rounded.Security, modifier = Modifier.initialFocus())
                        LumenButton("Install", onClick = { vm.updater.install() }, primary = !s.needsPermission)
                        LumenButton("Later", onClick = { vm.updater.later() }, primary = false)
                    }
                    is UpdateState.Failed -> {
                        LumenButton("Try again", onClick = { vm.updater.download(release) }, icon = Icons.Rounded.Refresh, modifier = Modifier.initialFocus())
                        LumenButton("Releases page", onClick = { openLink(context, release.pageUrl) { vm.toast = it } }, icon = Icons.Rounded.OpenInNew, primary = false)
                        LumenButton("Close", onClick = { vm.updater.later() }, primary = false)
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** Shown once after an update installs, with the notes saved when it was downloaded. */
@Composable
fun WhatsNewDialog(version: String, notes: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), fill = Hue.Surface1.copy(alpha = 0.97f)).padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(58.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Eyebrow("Updated")
                    Text("What's new in $version", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(16.dp)).background(Hue.Ink.copy(alpha = 0.6f))
                    .verticalScroll(rememberScrollState()).padding(16.dp)
            ) { Changelog(notes) }
            Spacer(Modifier.height(18.dp))
            LumenButton("Got it", onClick = onClose, modifier = Modifier.initialFocus())
        }
    }
}

@Composable
private fun MessageDialog(title: String, message: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.glass(RoundedCornerShape(24.dp), fill = Hue.Surface2).padding(22.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid)
            Spacer(Modifier.height(18.dp))
            LumenButton("OK", onClick = onClose, modifier = Modifier.initialFocus())
        }
    }
}

// ------------------------------------------------------------------ changelog (light Markdown)

private val mdLink = Regex("""\[([^\]]+)]\([^)]*\)""")
private val mdMarks = Regex("""\*\*|__|`""")

private fun plain(s: String) = s.replace(mdLink, "$1").replace(mdMarks, "").trim()

/** Renders GitHub release notes: headings, bullet lists and paragraphs. */
@Composable
fun Changelog(markdown: String, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val t = line.trimStart()
            when {
                t.isEmpty() -> Spacer(Modifier.height(2.dp))
                t.startsWith("#") -> Text(
                    plain(t.trimStart('#')), style = MaterialTheme.typography.titleSmall, color = Hue.TextHi,
                    modifier = Modifier.padding(top = 6.dp),
                )
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> Row(
                    Modifier.padding(start = if (line.length - t.length >= 2) 18.dp else 0.dp),
                ) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(accent.brush))
                    Spacer(Modifier.width(10.dp))
                    Text(plain(t.drop(2)), style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid)
                }
                else -> Text(plain(t), style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, overflow = TextOverflow.Clip)
            }
        }
    }
}
