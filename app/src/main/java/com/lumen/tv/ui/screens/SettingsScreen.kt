package com.lumen.tv.ui.screens

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stream
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lumen.tv.BuildConfig
import com.lumen.tv.data.Catalog
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Settings
import com.lumen.tv.data.Source
import com.lumen.tv.data.SourceType
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.Screen
import com.lumen.tv.ui.components.BrandMark
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.PatternSwatch
import com.lumen.tv.ui.components.Patterns
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.components.glass
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.components.isPictureBackground
import com.lumen.tv.ui.components.openLink
import com.lumen.tv.ui.theme.AppTheme
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Themes
import com.lumen.tv.ui.theme.themeById
import com.lumen.tv.update.UpdateState
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class Page(val title: String, val icon: ImageVector) {
    APPEARANCE("Appearance", Icons.Rounded.Palette),
    PLAYLISTS("Playlists", Icons.Rounded.PlaylistPlay),
    CONTENT("What you see", Icons.Rounded.Public),
    PLAYBACK("Playback", Icons.Rounded.Tv),
    GUIDE("TV guide", Icons.Rounded.Schedule),
    PARENTAL("Parental controls", Icons.Rounded.Lock),
    UPDATES("Updates", Icons.Rounded.SystemUpdate),
    STORAGE("History & storage", Icons.Rounded.History),
    ABOUT("About", Icons.Rounded.Info),
}

private enum class PinMode { SET, VERIFY_THEN_SET, VERIFY_THEN_REMOVE, VERIFY_THEN_CATEGORIES, VERIFY_THEN_ADULT }

/**
 * Settings, organized into pages. On TV and wide screens the categories sit on the left and the
 * page on the right (moving through the list switches pages); on phones the list opens each page.
 */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val isTv = LocalIsTv.current
    var pageName by rememberSaveable { mutableStateOf<String?>(null) }
    val page = pageName?.let { n -> Page.entries.firstOrNull { it.name == n } }

    val dialogs = remember { SettingsDialogs() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isTv || maxWidth >= 720.dp
        if (wide) {
            val shown = page ?: Page.APPEARANCE
            Row(Modifier.fillMaxSize().padding(start = hPad(), top = if (isTv) 28.dp else 12.dp)) {
                Column(Modifier.width(if (isTv) 320.dp else 290.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
                    Text("Settings", style = MaterialTheme.typography.displayMedium, color = Hue.TextHi, modifier = Modifier.padding(start = 6.dp, bottom = 16.dp))
                    CategoryList(vm, selected = shown, wide = true) { pageName = it.name }
                }
                Spacer(Modifier.width(24.dp))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PageBody(vm, shown, dialogs, header = false)
                }
            }
        } else if (page == null) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = hPad(), end = hPad(), top = 8.dp, bottom = 110.dp)
            ) {
                Text("Settings", style = MaterialTheme.typography.displayMedium, color = Hue.TextHi, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
                CategoryList(vm, selected = null, wide = false) { pageName = it.name }
            }
        } else {
            BackHandler { pageName = null }
            PageBody(vm, page, dialogs, header = true, onBack = { pageName = null })
        }
    }

    SettingsDialogHost(vm, dialogs)
}

/** Dialog state shared by every page. */
private class SettingsDialogs {
    var confirmDelete by mutableStateOf<Source?>(null)
    var pin by mutableStateOf<PinMode?>(null)
    var confirmAdult by mutableStateOf(false)
    var confirmClearHistory by mutableStateOf(false)
    var linkDialog by mutableStateOf(false)
}

// ======================================================================= category list

@Composable
private fun CategoryList(vm: AppViewModel, selected: Page?, wide: Boolean, onOpen: (Page) -> Unit) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val sources by vm.store.sources.collectAsStateWithLifecycle()
    val active by vm.activeSource.collectAsStateWithLifecycle()
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val update by vm.updater.state.collectAsStateWithLifecycle()
    val history by vm.store.history.collectAsStateWithLifecycle()

    HeroCard(settings, onClick = { onOpen(Page.APPEARANCE) })
    Spacer(Modifier.height(14.dp))

    fun summary(p: Page): String = when (p) {
        Page.APPEARANCE -> themeById(settings.theme).name + " · " + backgroundLabel(settings.background)
        Page.PLAYLISTS -> when (sources.size) {
            0 -> "None yet"
            1 -> active?.name ?: sources[0].name
            else -> "${sources.size} playlists · ${active?.name ?: ""}"
        }
        Page.CONTENT -> listOfNotNull(
            settings.preferredRegion.takeIf { it.isNotBlank() }?.let { Catalog.region(it)?.label },
            if (settings.countryOnly) "only" else null,
        ).joinToString(" ").ifBlank { "All countries" }
        Page.PLAYBACK -> (if (settings.liveFormat == "m3u8") "HLS" else "MPEG-TS") + " · " + bufferLabel(settings.bufferSeconds) + " buffer"
        Page.GUIDE -> if (epg.isEmpty) "No guide loaded" else "${epg.channelCount} channels"
        Page.PARENTAL -> (if (settings.showAdult) "Adult shown" else "Adult hidden") + " · PIN " + (if (settings.pin.isBlank()) "off" else "on")
        Page.UPDATES -> if (update is UpdateState.Available) "Update ready" else "Version ${vm.updater.currentVersion}"
        Page.STORAGE -> if (history.isEmpty()) "No history" else "${history.size} watched"
        Page.ABOUT -> "ZapBackTV ${BuildConfig.VERSION_NAME}"
    }

    val groups = listOf(
        listOf(Page.APPEARANCE),
        listOf(Page.PLAYLISTS, Page.CONTENT),
        listOf(Page.PLAYBACK, Page.GUIDE),
        listOf(Page.PARENTAL, Page.UPDATES, Page.STORAGE, Page.ABOUT),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        groups.forEachIndexed { gi, pages ->
            Panel {
                pages.forEachIndexed { i, p ->
                    CategoryRow(
                        p, summary(p), selected = p == selected, chevron = !wide,
                        modifier = if (gi == 0 && i == 0) Modifier.initialFocus() else Modifier,
                        // On TV, moving through the list shows each page right away.
                        onFocus = if (wide) ({ onOpen(p) }) else null,
                        onClick = { onOpen(p) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(settings: Settings, onClick: () -> Unit) {
    val theme = Hue.theme
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.fillMaxWidth().height(112.dp)
            .focusGlow(shape, scale = 1.02f, onFocus = { focused = it })
            .clip(shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Hue.Stroke, shape)
            .clickable(onClick = onClick)
    ) {
        BackgroundPreview(settings.background, theme, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(theme.ink.copy(alpha = 0.35f)))
        Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(70.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("ZapBackTV", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(
                    "${theme.name} theme · tap to restyle", style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.size(28.dp).clip(CircleShape).background(theme.main).border(2.dp, Color.White, CircleShape))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(28.dp).clip(CircleShape).background(theme.second).border(2.dp, Color.White, CircleShape))
        }
    }
}

@Composable
private fun CategoryRow(
    page: Page,
    summary: String,
    selected: Boolean,
    chevron: Boolean,
    modifier: Modifier = Modifier,
    onFocus: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth()
            .focusGlow(shape, scale = 1.02f, ring = 0.dp, onFocus = { focused = it; if (it) onFocus?.invoke() })
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> accent.soft
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(page.icon, filled = selected || focused, onWhite = focused)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(page.title, style = MaterialTheme.typography.titleMedium, color = if (focused) Hue.OnAccent else Hue.TextHi, maxLines = 1)
            Text(
                summary, style = MaterialTheme.typography.bodySmall, color = if (focused) Hue.OnAccent.copy(alpha = 0.7f) else Hue.TextMid,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = if (focused) Hue.OnAccent else Hue.TextLo, modifier = Modifier.size(22.dp))
    }
}

// ======================================================================= pages

@Composable
private fun PageBody(vm: AppViewModel, page: Page, d: SettingsDialogs, header: Boolean, onBack: () -> Unit = {}) {
    val isTv = LocalIsTv.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(
                start = if (header) hPad() else 0.dp, end = hPad(),
                top = if (isTv) 4.dp else 8.dp, bottom = if (isTv) 60.dp else 110.dp,
            )
            .widthIn(max = 980.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (header) {
                RoundIconButton(Icons.Rounded.ChevronLeft, "Back", onBack, modifier = Modifier.initialFocus())
                Spacer(Modifier.width(12.dp))
            } else {
                IconTile(page.icon, filled = true, size = 44.dp)
                Spacer(Modifier.width(14.dp))
            }
            Text(page.title, style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
        }
        when (page) {
            Page.APPEARANCE -> AppearancePage(vm, d)
            Page.PLAYLISTS -> PlaylistsPage(vm, d)
            Page.CONTENT -> ContentPage(vm, d)
            Page.PLAYBACK -> PlaybackPage(vm)
            Page.GUIDE -> GuidePage(vm)
            Page.PARENTAL -> ParentalPage(vm, d)
            Page.UPDATES -> UpdatesPage(vm)
            Page.STORAGE -> StoragePage(vm, d)
            Page.ABOUT -> AboutPage(vm)
        }
    }
}

// ----------------------------------------------------------------------- appearance

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearancePage(vm: AppViewModel, d: SettingsDialogs) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    fun update(t: (Settings) -> Settings) = vm.store.updateSettings(t)

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.setBackgroundPhoto(uri)
    }

    Group("Theme", footer = "Colors the whole app: background, panels, buttons and highlights.") {
        FlowRow(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Themes.forEach { t ->
                ThemeCard(t, selected = settings.theme == t.id, width = if (isTv) 176.dp else 150.dp) {
                    update { it.copy(theme = t.id) }
                }
            }
        }
    }

    Group("Background", footer = "Patterns are drawn in your theme's colors. Pictures get a wash of the theme color so text stays readable.") {
        val tileW = if (isTv) 150.dp else 112.dp
        FlowRow(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Patterns.forEach { p ->
                BackgroundTile(p.name, selected = settings.background == p.id, width = tileW, onClick = { vm.setBackground(p.id) }) {
                    PatternSwatch(p.id, Hue.theme, Modifier.fillMaxSize())
                }
            }
            val isFile = settings.background.startsWith("file:")
            BackgroundTile("Your photo", selected = isFile, width = tileW, onClick = {
                try {
                    photoPicker.launch("image/*")
                } catch (_: ActivityNotFoundException) {
                    vm.toast = "No photo picker on this device. Use “From a link” instead."
                }
            }) {
                if (isFile) AsyncImage(File(settings.background.removePrefix("file:")), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else TileIcon(Icons.Rounded.AddPhotoAlternate)
            }
            val isUrl = settings.background.startsWith("url:")
            BackgroundTile("From a link", selected = isUrl, width = tileW, onClick = { d.linkDialog = true }) {
                if (isUrl) AsyncImage(settings.background.removePrefix("url:"), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else TileIcon(Icons.Rounded.Link)
            }
        }
    }

    if (isPictureBackground(settings.background)) {
        Group("Picture") {
            ChoiceRow(
                "Color wash", Icons.Rounded.Contrast,
                listOf(0 to "None", 20 to "Light", 35 to "Medium", 55 to "Strong", 75 to "Max"),
                closest(settings.backgroundDim, listOf(0, 20, 35, 55, 75)),
                hint = "More wash makes text easier to read over busy pictures.",
            ) { v -> update { it.copy(backgroundDim = v) } }
            val canBlur = Build.VERSION.SDK_INT >= 31
            SwitchRow(
                "Blur picture", Icons.Rounded.BlurOn, settings.backgroundBlur && canBlur,
                hint = if (canBlur) "Softens the picture so it stays in the background." else "Needs Android 12 or newer.",
                enabled = canBlur,
            ) { v -> update { it.copy(backgroundBlur = v) } }
            ActionRow("Back to a pattern", Icons.Rounded.Wallpaper, hint = "Removes the picture.") { vm.setBackground("glow") }
        }
    }
}

@Composable
private fun ThemeCard(t: AppTheme, selected: Boolean, width: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(width)
            .focusGlow(shape, scale = 1.06f, ring = 0.dp, onFocus = { focused = it })
            .clip(shape)
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(5.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(width * 0.62f).clip(RoundedCornerShape(14.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Hue.Stroke, RoundedCornerShape(14.dp))
        ) {
            PatternSwatch("glow", t, Modifier.fillMaxSize())
            MiniUi(t)
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, "Selected", tint = t.ink, modifier = Modifier.size(16.dp)) }
        }
        Text(
            t.name, style = MaterialTheme.typography.titleSmall,
            color = if (focused) Hue.OnAccent else Hue.TextHi,
            modifier = Modifier.padding(start = 4.dp, top = 7.dp, bottom = 2.dp), maxLines = 1,
        )
    }
}

/** A tiny sketch of the app in a theme: side rail, a panel, a button and a highlight. */
@Composable
private fun MiniUi(t: AppTheme) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(t.night, size = Size(w * 0.14f, h))
        for (i in 0..2) drawCircle(if (i == 0) t.main else t.textLo, radius = w * 0.022f, center = Offset(w * 0.07f, h * (0.25f + i * 0.2f)))
        val r = CornerRadius(w * 0.04f)
        drawRoundRect(t.surface1, Offset(w * 0.22f, h * 0.18f), Size(w * 0.7f, h * 0.48f), r)
        drawRoundRect(Color.White, Offset(w * 0.28f, h * 0.27f), Size(w * 0.36f, h * 0.07f), r)
        drawRoundRect(t.textMid, Offset(w * 0.28f, h * 0.39f), Size(w * 0.5f, h * 0.05f), r)
        drawRoundRect(t.main, Offset(w * 0.28f, h * 0.5f), Size(w * 0.22f, h * 0.1f), CornerRadius(h * 0.05f))
        drawRoundRect(t.second, Offset(w * 0.22f, h * 0.74f), Size(w * 0.18f, h * 0.06f), r)
    }
}

@Composable
private fun BackgroundTile(label: String, selected: Boolean, width: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.width(width)
            .focusGlow(shape, scale = 1.06f, ring = 0.dp, onFocus = { focused = it })
            .clip(shape)
            .background(if (focused) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(width * 0.64f).clip(RoundedCornerShape(12.dp)).background(Hue.Surface2)
                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Hue.Stroke, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            content()
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, "Selected", tint = Hue.Ink, modifier = Modifier.size(15.dp)) }
        }
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = if (focused) Hue.OnAccent else Hue.TextMid,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp), maxLines = 1,
        )
    }
}

@Composable
private fun TileIcon(icon: ImageVector) {
    Icon(icon, null, tint = LocalAccent.current.main, modifier = Modifier.size(30.dp))
}

/** The current background, small: a pattern, or the picture. */
@Composable
private fun BackgroundPreview(value: String, theme: AppTheme, modifier: Modifier) {
    when {
        value.startsWith("file:") -> AsyncImage(File(value.removePrefix("file:")), null, contentScale = ContentScale.Crop, modifier = modifier)
        value.startsWith("url:") -> AsyncImage(value.removePrefix("url:"), null, contentScale = ContentScale.Crop, modifier = modifier)
        else -> PatternSwatch(value, theme, modifier)
    }
}

private fun backgroundLabel(v: String) = when {
    v.startsWith("file:") -> "Your photo"
    v.startsWith("url:") -> "Picture from a link"
    else -> Patterns.firstOrNull { it.id == v }?.name ?: "Glow"
}

private fun closest(v: Int, options: List<Int>) = options.minBy { kotlin.math.abs(it - v) }

private fun bufferLabel(s: Int) = when {
    s <= 5 -> "Low"
    s <= 15 -> "Normal"
    s <= 30 -> "Large"
    else -> "Max"
}

// ----------------------------------------------------------------------- playlists

@Composable
private fun PlaylistsPage(vm: AppViewModel, d: SettingsDialogs) {
    val sources by vm.store.sources.collectAsStateWithLifecycle()
    val active by vm.activeSource.collectAsStateWithLifecycle()
    val account by vm.repo.account.collectAsStateWithLifecycle()

    Group("Your playlists", footer = "Tap a playlist to switch to it.") {
        if (sources.isEmpty()) {
            Text("No playlists yet.", style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, modifier = Modifier.padding(14.dp))
        }
        sources.forEach { s ->
            SourceRow(
                s, isActive = s.id == active?.id,
                onUse = { vm.switchSource(s); vm.navigate(Screen.Home) },
                onEdit = { vm.navigate(Screen.EditSource(s)) },
                onRefresh = { vm.switchSource(s); vm.repo.load(s, force = true); vm.toast = "Refreshing ${s.name}…" },
                onDelete = { d.confirmDelete = s },
            )
        }
        ActionRow("Add playlist", Icons.Rounded.Add, hint = "Xtream Codes login, M3U link or file.") { vm.navigate(Screen.EditSource(null)) }
    }

    account?.let { a ->
        Group("Subscription") {
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

// ----------------------------------------------------------------------- content

@Composable
private fun ContentPage(vm: AppViewModel, d: SettingsDialogs) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val region = settings.preferredRegion.takeIf { it.isNotBlank() }?.let { Catalog.region(it) }
    val lib by vm.library.collectAsStateWithLifecycle()
    val shown = lib?.let { l -> Kind.entries.sumOf { l.categories(it).size } }

    Group("Country") {
        ActionRow("Your country", Icons.Rounded.Public, value = region?.label ?: "Not set", hint = "Its channels and categories are listed first.") {
            vm.navigate(Screen.CountrySetup)
        }
        SwitchRow(
            "Only show my country", Icons.Rounded.FilterAlt, settings.countryOnly && region != null,
            hint = "Hides other countries unless you picked them in Categories, and titles tagged for other countries.",
        ) { v ->
            if (region == null) vm.navigate(Screen.CountrySetup) else vm.store.updateSettings { it.copy(countryOnly = v) }
        }
    }
    Group("Categories") {
        ActionRow(
            "Choose categories", Icons.Rounded.Category,
            value = shown?.let { "$it shown" },
            hint = "Pick which live TV, movie and series categories appear.",
        ) { if (settings.pin.isBlank()) vm.navigate(Screen.CategorySetup) else d.pin = PinMode.VERIFY_THEN_CATEGORIES }
    }
}

// ----------------------------------------------------------------------- playback

@Composable
private fun PlaybackPage(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    fun update(t: (Settings) -> Settings) = vm.store.updateSettings(t)

    Group("Stream") {
        ChoiceRow(
            "Live stream format", Icons.Rounded.Stream,
            listOf("ts" to "MPEG-TS", "m3u8" to "HLS"), settings.liveFormat,
            hint = "Xtream only. Try HLS if channels stutter or won't start.",
        ) { v -> update { it.copy(liveFormat = v) } }
        ChoiceRow(
            "Buffer", Icons.Rounded.Speed,
            listOf(5 to "Low", 15 to "Normal", 30 to "Large", 60 to "Max"), settings.bufferSeconds,
            hint = "Bigger buffers ride out a shaky connection but take longer to start.",
        ) { v -> update { it.copy(bufferSeconds = v) } }
        ChoiceRow(
            "Picture", Icons.Rounded.AspectRatio,
            listOf(0 to "Fit", 1 to "Zoom", 2 to "Stretch"), settings.aspect,
        ) { v -> update { it.copy(aspect = v) } }
    }
    Group("Behavior") {
        SwitchRow("Auto-play next episode", Icons.Rounded.SkipNext, settings.autoplayNext) { v -> update { it.copy(autoplayNext = v) } }
        SwitchRow(
            "Live preview while browsing", Icons.Rounded.Tv, settings.previewOnFocus,
            hint = "Uses one of your provider connections while you browse.",
        ) { v -> update { it.copy(previewOnFocus = v) } }
    }
}

// ----------------------------------------------------------------------- guide

@Composable
private fun GuidePage(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val epgStatus by vm.repo.epgStatus.collectAsStateWithLifecycle()

    Group("Listings") {
        InfoLine("Status", epgStatus ?: if (epg.isEmpty) "No guide loaded" else "${epg.channelCount} channels with listings" + if (epg.trimmed) " (shortened to fit memory)" else "")
        ActionRow("Refresh guide now", Icons.Rounded.Refresh) { vm.repo.refreshEpg(); vm.toast = "Refreshing guide…" }
    }
    Group("Display") {
        ChoiceRow(
            "Time shift", Icons.Rounded.Schedule,
            (-6..6).map { it to (if (it > 0) "+${it}h" else if (it == 0) "None" else "${it}h") }, settings.epgShiftHours,
            hint = "Fix listings that are off by a few hours.",
        ) { v -> vm.store.updateSettings { it.copy(epgShiftHours = v) } }
        SwitchRow("Show channel numbers", Icons.Rounded.FormatListNumbered, settings.showNumbers) { v ->
            vm.store.updateSettings { it.copy(showNumbers = v) }
        }
    }
}

// ----------------------------------------------------------------------- parental

@Composable
private fun ParentalPage(vm: AppViewModel, d: SettingsDialogs) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    Group("Adult content", footer = "Off by default. Adult categories are hidden from live TV, movies, series and search.") {
        SwitchRow("Show adult content", Icons.Rounded.VisibilityOff, settings.showAdult) { v ->
            when {
                !v -> { vm.store.updateSettings { it.copy(showAdult = false) }; vm.toast = "Adult content hidden" }
                settings.pin.isBlank() -> d.confirmAdult = true
                else -> d.pin = PinMode.VERIFY_THEN_ADULT
            }
        }
    }
    Group("PIN", footer = "Asked for before adult content or the category list can be changed.") {
        ActionRow(
            if (settings.pin.isBlank()) "Set a PIN" else "Change PIN", Icons.Rounded.Lock,
            value = if (settings.pin.isBlank()) "Off" else "On",
        ) { d.pin = if (settings.pin.isBlank()) PinMode.SET else PinMode.VERIFY_THEN_SET }
        if (settings.pin.isNotBlank()) ActionRow("Remove PIN", Icons.Rounded.LockOpen) { d.pin = PinMode.VERIFY_THEN_REMOVE }
    }
}

// ----------------------------------------------------------------------- updates

@Composable
private fun UpdatesPage(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val updateState by vm.updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val available = updateState as? UpdateState.Available

    Group("This app") {
        InfoLine("Installed version", vm.updater.currentVersion)
        ActionRow(
            if (available != null) "Install version ${available.release.version}" else "Check for updates",
            Icons.Rounded.SystemUpdate,
            value = when (updateState) {
                is UpdateState.Checking -> "Checking…"
                is UpdateState.Downloading -> "Downloading…"
                else -> null
            },
            hint = if (available != null) "See what's new and install." else "Looks for a newer release on GitHub.",
            highlight = available != null,
        ) { if (available != null) vm.updater.reopen() else vm.updater.check(manual = true) }
        SwitchRow(
            "Check automatically", Icons.Rounded.Autorenew, settings.autoCheckUpdates,
            hint = "Checks when the app opens, a few times a day at most. You always choose whether to install.",
        ) { v -> vm.store.updateSettings { it.copy(autoCheckUpdates = v) } }
    }
    Group("Releases") {
        ActionRow("Release notes on GitHub", Icons.Rounded.OpenInNew, value = vm.updater.repo) {
            openLink(context, vm.updater.releasesPage) { vm.toast = it }
        }
    }
}

// ----------------------------------------------------------------------- storage

@Composable
private fun StoragePage(vm: AppViewModel, d: SettingsDialogs) {
    val watched by vm.store.history.collectAsStateWithLifecycle()
    val searched by vm.store.searches.collectAsStateWithLifecycle()
    Group("History") {
        ActionRow(
            "Clear watch history", Icons.Rounded.History,
            value = if (watched.isEmpty()) "Empty" else "${watched.size}",
            hint = "Continue watching, recently watched channels and resume points.",
        ) { if (watched.isEmpty()) vm.toast = "Watch history is already empty" else d.confirmClearHistory = true }
        ActionRow(
            "Clear search history", Icons.Rounded.Search,
            value = if (searched.isEmpty()) "Empty" else "${searched.size}",
            hint = "Recent searches shown on the Search screen.",
        ) {
            if (searched.isEmpty()) vm.toast = "Search history is already empty"
            else { vm.store.clearSearches(); vm.searchQuery = ""; vm.searchResults = null; vm.toast = "Search history cleared" }
        }
    }
    Group("Storage") {
        ActionRow("Clear cache and reload", Icons.Rounded.DeleteSweep, hint = "Re-downloads your playlist and TV guide.") {
            vm.repo.clearCache(); vm.toast = "Cache cleared — reloading"
        }
    }
}

// ----------------------------------------------------------------------- about

@Composable
private fun AboutPage(vm: AppViewModel) {
    val context = LocalContext.current
    Group(null) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(84.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("ZapBackTV", style = MaterialTheme.typography.headlineLarge, color = Hue.TextHi)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid)
            }
        }
        Text(
            "A player for your own IPTV subscriptions: Xtream Codes and M3U playlists, on Android TV and phones. " +
                "ZapBackTV does not provide any content.",
            style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
        )
        ActionRow("Project on GitHub", Icons.Rounded.OpenInNew, value = vm.updater.repo) {
            openLink(context, "https://github.com/${vm.updater.repo}") { vm.toast = it }
        }
    }
}

// ======================================================================= building blocks

/** A rounded panel of rows. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), fill = Hue.Surface1.copy(alpha = 0.88f)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** A titled panel with an optional note under it. */
@Composable
private fun Group(title: String?, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) Text(
            title.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalAccent.current.main,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Panel(content)
        if (footer != null) Text(
            footer, style = MaterialTheme.typography.bodySmall, color = Hue.TextMid,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
        )
    }
}

@Composable
private fun IconTile(icon: ImageVector, filled: Boolean, size: Dp = 38.dp, onWhite: Boolean = false) {
    val accent = LocalAccent.current
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(
            when {
                onWhite -> Hue.OnAccent
                filled -> accent.main
                else -> accent.soft
            }
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, null, modifier = Modifier.size(size * 0.55f),
            tint = when {
                onWhite -> Color.White
                filled -> Hue.OnAccent
                else -> accent.main
            },
        )
    }
}

/** The base row: icon tile, title, hint, and something on the right. */
@Composable
private fun SettingRow(
    title: String,
    icon: ImageVector?,
    hint: String? = null,
    enabled: Boolean = true,
    highlight: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable (focused: Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth()
            .focusGlow(shape, scale = 1.015f, ring = 0.dp, onFocus = { focused = it })
            .clip(shape)
            .background(if (focused) Color.White else if (highlight) LocalAccent.current.soft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { IconTile(icon, filled = highlight, onWhite = focused); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (focused) Hue.OnAccent else Hue.TextHi.copy(alpha = if (enabled) 1f else 0.5f),
            )
            if (hint != null) Text(
                hint, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                color = if (focused) Hue.OnAccent.copy(alpha = 0.7f) else Hue.TextMid,
            )
        }
        Spacer(Modifier.width(10.dp))
        trailing(focused)
    }
}

@Composable
private fun ActionRow(
    title: String,
    icon: ImageVector,
    value: String? = null,
    hint: String? = null,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    SettingRow(title, icon, hint, highlight = highlight, onClick = onClick) { focused ->
        if (value != null) Text(
            value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (focused) Hue.OnAccent else Hue.TextMid, modifier = Modifier.widthIn(max = 200.dp),
        )
        Icon(Icons.Rounded.ChevronRight, null, tint = if (focused) Hue.OnAccent else Hue.TextLo, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    icon: ImageVector,
    value: Boolean,
    hint: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    SettingRow(title, icon, hint, enabled = enabled, onClick = { onChange(!value) }) { focused -> Toggle(value, focused) }
}

/** A switch drawn to match the theme; the whole row is the touch/remote target. */
@Composable
private fun Toggle(on: Boolean, focused: Boolean) {
    val accent = LocalAccent.current
    val x by animateDpAsState(if (on) 22.dp else 2.dp, label = "toggle")
    Box(
        Modifier.size(48.dp, 28.dp).clip(RoundedCornerShape(50))
            .background(
                when {
                    on && focused -> Hue.OnAccent
                    on -> accent.main
                    focused -> Hue.OnAccent.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.18f)
                }
            )
    ) {
        Box(
            Modifier.offset(x = x).align(Alignment.CenterStart).size(24.dp).clip(CircleShape)
                .background(if (on) Color.White else Color.White.copy(alpha = 0.85f))
        )
    }
}

/** A setting with a few named options, shown side by side. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    icon: ImageVector,
    options: List<Pair<T, String>>,
    value: T,
    hint: String? = null,
    onChange: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, filled = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Hue.TextHi)
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = Hue.TextMid)
            }
        }
        FlowRow(
            Modifier.padding(start = 50.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (v, label) -> Pill(label, selected = v == value, onClick = { onChange(v) }) }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, modifier = Modifier.width(160.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Hue.TextHi, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SourceRow(
    s: Source,
    isActive: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            SettingRow(
                s.name, if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.PlaylistPlay,
                hint = AppViewModel.sourceLabel(s.type) + when (s.type) {
                    SourceType.XTREAM -> " · ${s.username}@${Uri.parse(s.url).host ?: s.url}"
                    SourceType.M3U_URL -> " · ${Uri.parse(s.url).host ?: ""}"
                    SourceType.M3U_FILE -> ""
                },
                highlight = isActive,
                onClick = onUse,
            ) { focused ->
                Text(
                    if (isActive) "Active" else "Use", style = MaterialTheme.typography.labelLarge,
                    color = if (focused) Hue.OnAccent else if (isActive) LocalAccent.current.main else Hue.TextMid,
                )
            }
        }
        RoundIconButton(Icons.Rounded.Refresh, "Refresh", onRefresh, size = 40.dp)
        Spacer(Modifier.width(6.dp))
        RoundIconButton(Icons.Rounded.Edit, "Edit", onEdit, size = 40.dp)
        Spacer(Modifier.width(6.dp))
        RoundIconButton(Icons.Rounded.Delete, "Remove", onDelete, size = 40.dp)
        Spacer(Modifier.width(6.dp))
    }
}

// ======================================================================= dialogs

@Composable
private fun SettingsDialogHost(vm: AppViewModel, d: SettingsDialogs) {
    fun update(t: (Settings) -> Settings) = vm.store.updateSettings(t)

    d.confirmDelete?.let { s ->
        ConfirmDialog(
            title = "Remove “${s.name}”?",
            message = "Its favorites and watch history will be removed too.",
            confirm = "Remove",
            onDismiss = { d.confirmDelete = null },
        ) { vm.deleteSource(s); d.confirmDelete = null }
    }
    if (d.confirmClearHistory) {
        ConfirmDialog("Clear watch history?", "Continue watching, recent channels and resume points will be reset.", "Clear", { d.confirmClearHistory = false }) {
            vm.store.clearHistory(); d.confirmClearHistory = false; vm.toast = "History cleared"
        }
    }
    if (d.confirmAdult) {
        ConfirmDialog("Show adult content?", "Adult categories will appear in live TV, movies, series and search. Set a PIN to keep this switch locked.", "Show", { d.confirmAdult = false }) {
            update { it.copy(showAdult = true) }; d.confirmAdult = false
        }
    }
    if (d.linkDialog) {
        LinkDialog(onDismiss = { d.linkDialog = false }) { url ->
            vm.setBackground("url:$url"); d.linkDialog = false; vm.toast = "Background updated"
        }
    }
    d.pin?.let { mode ->
        PinDialog(
            title = if (mode == PinMode.SET) "Choose a 4-digit PIN" else "Enter your PIN",
            onDismiss = { d.pin = null },
        ) { pin ->
            when {
                mode == PinMode.SET -> { update { it.copy(pin = pin) }; d.pin = null; vm.toast = "PIN saved" }
                !vm.checkPin(pin) -> vm.toast = "Wrong PIN"
                mode == PinMode.VERIFY_THEN_SET -> d.pin = PinMode.SET
                mode == PinMode.VERIFY_THEN_REMOVE -> { update { it.copy(pin = "") }; d.pin = null; vm.toast = "PIN removed" }
                mode == PinMode.VERIFY_THEN_CATEGORIES -> { d.pin = null; vm.navigate(Screen.CategorySetup) }
                mode == PinMode.VERIFY_THEN_ADULT -> { update { it.copy(showAdult = true) }; d.pin = null; vm.toast = "Adult content shown" }
            }
        }
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val accent = LocalAccent.current
    var url by remember { mutableStateOf("") }
    val valid = url.trim().let { it.startsWith("http://") || it.startsWith("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hue.Surface2,
        title = { Text("Picture from a link", color = Hue.TextHi) },
        text = {
            Column {
                Text("Paste a link to a JPG, PNG or WebP image.", color = Hue.TextMid, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://…", color = Hue.TextLo) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent.main, cursorColor = accent.main,
                        focusedTextColor = Hue.TextHi, unfocusedTextColor = Hue.TextHi,
                    ),
                    modifier = Modifier.fillMaxWidth().initialFocus(),
                )
            }
        },
        confirmButton = { LumenButton("Use picture", onClick = { if (valid) onSave(url.trim()) }, enabled = valid) },
        dismissButton = { LumenButton("Cancel", onClick = onDismiss, primary = false) },
    )
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
