package com.lumen.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.data.Kind
import com.lumen.tv.data.WatchEntry
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.NetImage
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.ProgressLine
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.components.glass
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val filters = listOf("all" to "Everything", "live" to "Channels", "movies" to "Movies", "episodes" to "Episodes", "resume" to "In progress")

private fun matches(e: WatchEntry, filter: String) = when (filter) {
    "live" -> e.kind == Kind.LIVE
    "movies" -> e.kind == Kind.MOVIE
    "episodes" -> e.kind == Kind.SERIES
    "resume" -> e.isResumable
    else -> true
}

private fun id(e: WatchEntry) = "${e.sourceId}|${e.key}"

/**
 * Everything you've watched, newest first and grouped by day. Tap to play again; "Select" lets you
 * tick any number of items and remove just those.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(vm: AppViewModel, initialFilter: String) {
    val history by vm.store.history.collectAsStateWithLifecycle()
    val sources by vm.store.sources.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()

    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf<List<WatchEntry>?>(null) }

    val shown = history.filter { matches(it, filter) }
    val chosen = shown.filter { id(it) in selected }
    val sourceNames = if (sources.size > 1) sources.associate { it.id to it.name } else emptyMap()

    if (selecting) BackHandler { selecting = false; selected = emptySet() }

    Column(Modifier.fillMaxSize().padding(top = if (isTv) 24.dp else 8.dp)) {
        // ---- header
        Row(Modifier.fillMaxWidth().padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Rounded.ChevronLeft, "Back", { vm.back() })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Watch history", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
                Text(
                    if (selecting) "${chosen.size} selected" else "${history.size} item${if (history.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---- filters and actions
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEach { (k, label) ->
                val n = history.count { matches(it, k) }
                if (n > 0 || k == "all" || k == filter) Pill(label, selected = filter == k, count = n, onClick = { filter = k })
            }
        }
        Spacer(Modifier.height(10.dp))
        if (shown.isNotEmpty()) FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!selecting) {
                LumenButton("Select", onClick = { selecting = true }, icon = Icons.Rounded.CheckCircle, primary = false, modifier = Modifier.initialFocus())
                LumenButton(
                    if (filter == "all") "Clear all" else "Clear these", onClick = { confirm = shown },
                    icon = Icons.Rounded.DeleteSweep, primary = false,
                )
            } else {
                LumenButton(
                    "Remove ${chosen.size}", onClick = { if (chosen.isNotEmpty()) confirm = chosen },
                    icon = Icons.Rounded.Delete, enabled = chosen.isNotEmpty(), modifier = Modifier.initialFocus(),
                )
                val all = shown.all { id(it) in selected }
                LumenButton(
                    if (all) "Select none" else "Select all",
                    onClick = { selected = if (all) selected - shown.map(::id).toSet() else selected + shown.map(::id) },
                    primary = false,
                )
                LumenButton("Done", onClick = { selecting = false; selected = emptySet() }, icon = Icons.Rounded.Close, primary = false)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            EmptyState(
                Icons.Rounded.History,
                if (history.isEmpty()) "Nothing watched yet" else "Nothing here",
                if (history.isEmpty()) "Channels, movies and episodes you watch will show up here." else "Nothing you've watched matches this filter.",
            )
            return@Column
        }

        // ---- the list, grouped by day
        val groups = remember(shown) { shown.groupBy { dayLabel(it.updated) }.toList() }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = pad, end = pad, top = 4.dp, bottom = if (isTv) 48.dp else 110.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            groups.forEach { (day, entries) ->
                item(key = "day|$day") {
                    Text(
                        day.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalAccent.current.main,
                        modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(entries, key = { id(it) }) { e ->
                    HistoryRow(
                        e,
                        sourceName = sourceNames[e.sourceId],
                        selecting = selecting,
                        checked = id(e) in selected,
                        onClick = {
                            if (selecting) selected = if (id(e) in selected) selected - id(e) else selected + id(e)
                            else vm.resume(e)
                        },
                        onLongClick = {
                            // Long press starts selecting with this item ticked.
                            selecting = true; selected = selected + id(e)
                        },
                        onRemove = { vm.store.removeHistory(listOf(e)); vm.toast = "Removed “${e.title}”" },
                    )
                }
            }
        }
    }

    confirm?.let { list ->
        ConfirmDialog(
            title = if (list.size == 1) "Remove “${list[0].title}”?" else "Remove ${list.size} items?",
            message = "They'll disappear from your history, Continue watching and Recently watched. Resume points are forgotten too.",
            confirm = "Remove",
            onDismiss = { confirm = null },
        ) {
            vm.store.removeHistory(list)
            selected = selected - list.map(::id).toSet()
            if (selected.isEmpty()) selecting = false
            confirm = null
            vm.toast = if (list.size == 1) "Removed" else "Removed ${list.size} items"
        }
    }
}

@Composable
private fun HistoryRow(
    e: WatchEntry,
    sourceName: String?,
    selecting: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = LocalAccent.current
    val isTv = LocalIsTv.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f)
                .focusGlow(shape, scale = 1.015f, ring = 0.dp, onFocus = { focused = it })
                .clip(shape)
                .background(
                    when {
                        focused -> Color.White
                        checked -> accent.soft
                        else -> Hue.Surface1.copy(alpha = 0.85f)
                    }
                )
                .then(if (checked && !focused) Modifier.border(2.dp, accent.main, shape) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (checked) accent.main else Color.Transparent)
                        .border(2.dp, if (checked) accent.main else if (focused) Hue.OnAccent.copy(alpha = 0.5f) else Hue.TextLo, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (checked) Icon(Icons.Rounded.Check, "Selected", tint = Hue.OnAccent, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
            }
            val live = e.kind == Kind.LIVE
            Box(Modifier.size(if (isTv) 112.dp else 92.dp, if (isTv) 63.dp else 52.dp).clip(RoundedCornerShape(10.dp)).background(Hue.Surface2)) {
                NetImage(
                    e.image, e.title, Modifier.fillMaxSize().padding(if (live) 6.dp else 0.dp),
                    contentScale = if (live) ContentScale.Fit else ContentScale.Crop, initialsSize = 14,
                )
                if (!live && e.fraction > 0f) ProgressLine(e.fraction, Modifier.align(Alignment.BottomStart).fillMaxWidth(), height = 4.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                val fg = if (focused) Hue.OnAccent else Hue.TextHi
                val dim = if (focused) Hue.OnAccent.copy(alpha = 0.7f) else Hue.TextMid
                Text(e.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val kind = when (e.kind) { Kind.LIVE -> "Channel"; Kind.MOVIE -> "Movie"; Kind.SERIES -> "Episode" }
                Text(
                    listOfNotNull(kind, e.subtitle?.takeIf { e.kind != Kind.LIVE }, sourceName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.updated)),
                        when {
                            e.kind == Kind.LIVE -> null
                            e.isResumable -> "${(e.fraction * 100).toInt()}% watched"
                            e.fraction >= 0.94f -> "Finished"
                            else -> null
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = dim,
                )
            }
            if (!selecting) Icon(Icons.Rounded.PlayArrow, null, tint = if (focused) Hue.OnAccent else Hue.TextLo, modifier = Modifier.size(22.dp))
        }
        if (!selecting) {
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.Delete, "Remove ${e.title}", onRemove, size = 42.dp)
        }
    }
}

private fun dayLabel(t: Long): String {
    val c = Calendar.getInstance()
    fun dayIndex(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.YEAR) * 400 + it.get(Calendar.DAY_OF_YEAR) }
    val today = dayIndex(c.timeInMillis)
    return when (dayIndex(t)) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.FULL).format(Date(t))
    }
}
