package com.lumen.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.BuildConfig
import com.lumen.tv.data.Kind
import com.lumen.tv.data.LibState
import com.lumen.tv.data.Settings
import com.lumen.tv.data.Source
import com.lumen.tv.data.SourceType
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.components.ListRow
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.components.glass
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.theme.Accents
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val sources by vm.store.sources.collectAsStateWithLifecycle()
    val active by vm.activeSource.collectAsStateWithLifecycle()
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val account by vm.repo.account.collectAsStateWithLifecycle()
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val epgStatus by vm.repo.epgStatus.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val pad = hPad()

    var confirmDelete by remember { mutableStateOf<Source?>(null) }
    var pinDialog by remember { mutableStateOf<PinMode?>(null) }
    var confirmAdult by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    fun update(t: (Settings) -> Settings) = vm.store.updateSettings(t)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = pad, end = pad, top = if (isTv) 28.dp else 8.dp, bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi) }

        // ------------------------------------------------------------ playlists
        item {
            Section("Playlists", Icons.Rounded.PlaylistPlay) {
                sources.forEachIndexed { i, s ->
                    SourceRow(
                        s, isActive = s.id == active?.id,
                        modifier = if (i == 0) Modifier.initialFocus() else Modifier,
                        onUse = { vm.switchSource(s); vm.navigate(Screen.Home) },
                        onEdit = { vm.navigate(Screen.EditSource(s)) },
                        onRefresh = { vm.switchSource(s); vm.repo.load(s, force = true); vm.toast = "Refreshing ${s.name}…" },
                        onDelete = { confirmDelete = s },
                    )
                }
                Spacer(Modifier.height(8.dp))
                LumenButton("Add playlist", onClick = { vm.navigate(Screen.EditSource(null)) }, icon = Icons.Rounded.Add, primary = false)
            }
        }

        // ------------------------------------------------------------ account
        account?.let { a ->
            item {
                Section("Subscription", Icons.Rounded.CheckCircle) {
                    InfoLine("Status", a.status + if (a.isTrial) " (trial)" else "")
                    InfoLine("Expires", a.expiresAt?.let { DateFormat.getDateInstance(DateFormat.LONG).format(Date(it)) } ?: "Never")
                    a.expiresAt?.let { exp ->
                        val days = ((exp - System.currentTimeMillis()) / 86_400_000L)
                        InfoLine("Days left", if (days >= 0) "$days" else "Expired")
                    }
                    InfoLine("Connections", "${a.activeConnections ?: 0} of ${a.maxConnections ?: "?"} in use")
                    a.serverTimezone?.let { InfoLine("Server time zone", it) }
                }
            }
        }

        // ------------------------------------------------------------ playback
        item {
            Section("Playback", Icons.Rounded.Tv) {
                ChoiceRow(
                    "Live stream format", Icons.Rounded.Stream,
                    listOf("ts" to "MPEG-TS", "m3u8" to "HLS"), settings.liveFormat,
                    hint = "Xtream only. Try HLS if channels stutter or won't start.",
                ) { v -> update { it.copy(liveFormat = v) } }
                ChoiceRow(
                    "Buffer size", Icons.Rounded.Speed,
                    listOf(5 to "Low", 15 to "Normal", 30 to "Large", 60 to "Max"), settings.bufferSeconds,
                    hint = "Bigger buffers ride out a shaky connection but take longer to start.",
                ) { v -> update { it.copy(bufferSeconds = v) } }
                ChoiceRow(
                    "Default picture", Icons.Rounded.AspectRatio,
                    listOf(0 to "Fit", 1 to "Zoom", 2 to "Stretch"), settings.aspect,
                ) { v -> update { it.copy(aspect = v) } }
                ToggleRow("Auto-play next episode", Icons.Rounded.SkipNext, settings.autoplayNext) { v -> update { it.copy(autoplayNext = v) } }
                ToggleRow("Live preview while browsing", Icons.Rounded.Tv, settings.previewOnFocus,
                    hint = "Uses one of your provider connections while you browse.") { v -> update { it.copy(previewOnFocus = v) } }
            }
        }

        // ------------------------------------------------------------ content
        item {
            val region = settings.preferredRegion.takeIf { it.isNotBlank() }?.let { com.lumen.tv.data.Catalog.region(it) }
            val lib by vm.library.collectAsStateWithLifecycle()
            val shown = lib?.let { l -> com.lumen.tv.data.Kind.entries.sumOf { l.categories(it).size } }
            Section("What you see", Icons.Rounded.Public) {
                ActionRow("Your country", Icons.Rounded.Public, value = region?.label ?: "Not set") { vm.navigate(Screen.CountrySetup) }
                ActionRow(
                    "Categories", Icons.Rounded.Category,
                    value = shown?.let { "$it shown" },
                    hint = "Choose which live TV, movie and series categories appear.",
                ) { if (settings.pin.isBlank()) vm.navigate(Screen.CategorySetup) else pinDialog = PinMode.VERIFY_THEN_CATEGORIES }
                ToggleRow(
                    "Only show my country", Icons.Rounded.FilterAlt, settings.countryOnly && region != null,
                    hint = "Hides other countries unless you picked them in Categories, and titles tagged for other countries.",
                ) { v ->
                    if (region == null) vm.navigate(Screen.CountrySetup) else update { it.copy(countryOnly = v) }
                }
            }
        }

        // ------------------------------------------------------------ guide
        item {
            Section("TV guide", Icons.Rounded.Schedule) {
                InfoLine("Status", epgStatus ?: if (epg.isEmpty) "No guide loaded" else "${epg.channelCount} channels with listings")
                ChoiceRow(
                    "Time shift", Icons.Rounded.Schedule,
                    (-6..6).map { it to (if (it > 0) "+${it}h" else if (it == 0) "None" else "${it}h") }, settings.epgShiftHours,
                    hint = "Fix listings that are off by a few hours.",
                ) { v -> update { it.copy(epgShiftHours = v) } }
                ToggleRow("Show channel numbers", Icons.Rounded.FormatListNumbered, settings.showNumbers) { v -> update { it.copy(showNumbers = v) } }
                ActionRow("Refresh guide now", Icons.Rounded.Refresh) { vm.repo.refreshEpg(); vm.toast = "Refreshing guide…" }
            }
        }

        // ------------------------------------------------------------ appearance
        item {
            Section("Appearance", Icons.Rounded.Palette) {
                Text("Accent color", style = MaterialTheme.typography.titleSmall, color = Hue.TextMid, modifier = Modifier.padding(start = 14.dp, bottom = 10.dp))
                Row(Modifier.padding(start = 14.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Accents.forEachIndexed { i, a ->
                        val sel = settings.accent == i
                        Box(
                            Modifier.focusGlow(CircleShape, scale = 1.15f, ring = 0.dp)
                                .size(44.dp).clip(CircleShape).background(a.brush)
                                .border(if (sel) 3.dp else 0.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                                .clickable { update { it.copy(accent = i) } },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Rounded.CheckCircle, a.name, tint = Color(0xFF070912), modifier = Modifier.size(22.dp)) }
                    }
                }
            }
        }

        // ------------------------------------------------------------ parental
        item {
            Section("Parental controls", Icons.Rounded.Lock) {
                ToggleRow(
                    "Show adult content", Icons.Rounded.VisibilityOff, settings.showAdult,
                    hint = "Off by default. Adult categories are hidden from live TV, movies, series and search.",
                ) { v ->
                    when {
                        !v -> { update { it.copy(showAdult = false) }; vm.toast = "Adult content hidden" }
                        settings.pin.isBlank() -> confirmAdult = true
                        else -> pinDialog = PinMode.VERIFY_THEN_ADULT
                    }
                }
                ActionRow(
                    if (settings.pin.isBlank()) "Set a PIN" else "Change PIN", Icons.Rounded.Lock,
                    value = if (settings.pin.isBlank()) "Off" else "On",
                    hint = "Asked for before adult content or the category list can be changed.",
                ) { pinDialog = if (settings.pin.isBlank()) PinMode.SET else PinMode.VERIFY_THEN_SET }
                if (settings.pin.isNotBlank()) ActionRow("Remove PIN", Icons.Rounded.LockOpen) { pinDialog = PinMode.VERIFY_THEN_REMOVE }
            }
        }

        // ------------------------------------------------------------ updates
        item {
            val updateState by vm.updater.state.collectAsStateWithLifecycle()
            val context = androidx.compose.ui.platform.LocalContext.current
            Section("Updates", Icons.Rounded.SystemUpdate) {
                InfoLine("Installed version", vm.updater.currentVersion)
                val available = updateState as? com.lumen.tv.update.UpdateState.Available
                ActionRow(
                    if (available != null) "Install version ${available.release.version}" else "Check for updates",
                    Icons.Rounded.SystemUpdate,
                    value = when (updateState) {
                        is com.lumen.tv.update.UpdateState.Checking -> "Checking…"
                        is com.lumen.tv.update.UpdateState.Downloading -> "Downloading…"
                        else -> null
                    },
                    hint = if (available != null) "See what's new and install." else "Looks for a newer release on GitHub.",
                ) { if (available != null) vm.updater.reopen() else vm.updater.check(manual = true) }
                ToggleRow(
                    "Check automatically", Icons.Rounded.Autorenew, settings.autoCheckUpdates,
                    hint = "Checks when the app opens, a few times a day at most. You always choose whether to install.",
                ) { v -> update { it.copy(autoCheckUpdates = v) } }
                ActionRow("Release notes on GitHub", Icons.Rounded.OpenInNew, value = vm.updater.repo) {
                    com.lumen.tv.ui.components.openLink(context, vm.updater.releasesPage) { vm.toast = it }
                }
            }
        }

        // ------------------------------------------------------------ data
        item {
            Section("Storage", Icons.Rounded.History) {
                ActionRow("Clear watch history", Icons.Rounded.History) { confirmClearHistory = true }
                ActionRow("Clear cache and reload", Icons.Rounded.DeleteSweep) { vm.repo.clearCache(); vm.toast = "Cache cleared — reloading" }
            }
        }

        item {
            Column(Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text("ZapBackTV ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall, color = Hue.TextMid)
                Text(
                    "A player for your own IPTV subscriptions. ZapBackTV does not provide any content.",
                    style = MaterialTheme.typography.bodySmall, color = Hue.TextLo,
                )
            }
        }
    }

    confirmDelete?.let { s ->
        ConfirmDialog(
            title = "Remove “${s.name}”?",
            message = "Its favorites and watch history will be removed too.",
            confirm = "Remove",
            onDismiss = { confirmDelete = null },
        ) { vm.deleteSource(s); confirmDelete = null }
    }
    if (confirmClearHistory) {
        ConfirmDialog("Clear watch history?", "Continue watching, recent channels and resume points will be reset.", "Clear", { confirmClearHistory = false }) {
            vm.store.clearHistory(); confirmClearHistory = false; vm.toast = "History cleared"
        }
    }
    if (confirmAdult) {
        ConfirmDialog("Show adult content?", "Adult categories will appear in live TV, movies, series and search. Set a PIN to keep this switch locked.", "Show", { confirmAdult = false }) {
            update { it.copy(showAdult = true) }; confirmAdult = false
        }
    }
    pinDialog?.let { mode ->
        PinDialog(
            title = if (mode == PinMode.SET) "Choose a 4-digit PIN" else "Enter your PIN",
            onDismiss = { pinDialog = null },
        ) { pin ->
            when {
                mode == PinMode.SET -> { update { it.copy(pin = pin) }; pinDialog = null; vm.toast = "PIN saved" }
                !vm.checkPin(pin) -> vm.toast = "Wrong PIN"
                mode == PinMode.VERIFY_THEN_SET -> pinDialog = PinMode.SET
                mode == PinMode.VERIFY_THEN_REMOVE -> { update { it.copy(pin = "") }; pinDialog = null; vm.toast = "PIN removed" }
                mode == PinMode.VERIFY_THEN_CATEGORIES -> { pinDialog = null; vm.navigate(Screen.CategorySetup) }
                mode == PinMode.VERIFY_THEN_ADULT -> { update { it.copy(showAdult = true) }; pinDialog = null; vm.toast = "Adult content shown" }
            }
        }
    }
}

private enum class PinMode { SET, VERIFY_THEN_SET, VERIFY_THEN_REMOVE, VERIFY_THEN_CATEGORIES, VERIFY_THEN_ADULT }

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth().widthIn(max = 900.dp).glass(RoundedCornerShape(24.dp))
            .padding(vertical = 16.dp, horizontal = 10.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent.main, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Hue.TextHi)
        }
        content()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Hue.TextLo, modifier = Modifier.width(170.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Hue.TextHi)
    }
}

@Composable
private fun ActionRow(title: String, icon: ImageVector, value: String? = null, hint: String? = null, onClick: () -> Unit) {
    ListRow(title, icon = icon, subtitle = hint, trailing = value, onClick = onClick)
}

@Composable
private fun ToggleRow(title: String, icon: ImageVector, value: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    ListRow(title, icon = icon, subtitle = hint, trailing = if (value) "On" else "Off") { onChange(!value) }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    icon: ImageVector,
    options: List<Pair<T, String>>,
    value: T,
    hint: String? = null,
    onChange: (T) -> Unit,
) {
    // Clicking cycles through options — the friendliest pattern on a remote.
    val idx = options.indexOfFirst { it.first == value }.coerceAtLeast(0)
    ListRow(title, icon = icon, subtitle = hint, trailing = options[idx].second) {
        onChange(options[(idx + 1) % options.size].first)
    }
}

@Composable
private fun SourceRow(
    s: Source,
    isActive: Boolean,
    modifier: Modifier,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
            .background(if (isActive) accent.soft else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            ListRow(
                s.name, modifier = modifier,
                subtitle = AppViewModel.sourceLabel(s.type) + when (s.type) {
                    SourceType.XTREAM -> " · ${s.username}@${android.net.Uri.parse(s.url).host ?: s.url}"
                    SourceType.M3U_URL -> " · ${android.net.Uri.parse(s.url).host ?: ""}"
                    SourceType.M3U_FILE -> ""
                },
                trailing = if (isActive) "Active" else "Use",
                icon = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.PlaylistPlay,
                onClick = onUse,
            )
        }
        RoundIconButton(Icons.Rounded.Refresh, "Refresh", onRefresh, size = 40.dp)
        Spacer(Modifier.width(6.dp))
        RoundIconButton(Icons.Rounded.Edit, "Edit", onEdit, size = 40.dp)
        Spacer(Modifier.width(6.dp))
        RoundIconButton(Icons.Rounded.Delete, "Remove", onDelete, size = 40.dp)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hue.Surface2,
        title = { Text(title, color = Hue.TextHi) },
        text = { Text(message, color = Hue.TextMid) },
        confirmButton = { LumenButton(confirm, onClick = onConfirm, modifier = Modifier.initialFocus()) },
        dismissButton = { LumenButton("Cancel", onClick = onDismiss, primary = false) },
    )
}

@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val accent = LocalAccent.current
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hue.Surface2,
        title = { Text(title, color = Hue.TextHi) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter { it.isDigit() }.take(4) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent.main, cursorColor = accent.main,
                    focusedTextColor = Hue.TextHi, unfocusedTextColor = Hue.TextHi,
                ),
                modifier = Modifier.initialFocus(),
            )
        },
        confirmButton = { LumenButton("OK", onClick = { if (pin.length == 4) onSubmit(pin) }, enabled = pin.length == 4) },
        dismissButton = { LumenButton("Cancel", onClick = onDismiss, primary = false) },
    )
}
