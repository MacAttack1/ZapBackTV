package com.lumen.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumen.tv.crashFile
import com.lumen.tv.data.Kind
import com.lumen.tv.data.LibState
import com.lumen.tv.player.PlayerScreen
import com.lumen.tv.ui.components.AmbientBackground
import com.lumen.tv.ui.components.EmptyState
import com.lumen.tv.ui.components.LoadProgressCard
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.BrandMark
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.SlimProgressBar
import com.lumen.tv.ui.components.UpdateDialog
import com.lumen.tv.ui.components.WhatsNewDialog
import com.lumen.tv.update.UpdateState
import com.lumen.tv.update.Updater
import com.lumen.tv.ui.components.Wordmark
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.components.glass
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.screens.AddSourceScreen
import com.lumen.tv.ui.screens.FavoritesScreen
import com.lumen.tv.ui.screens.GuideScreen
import com.lumen.tv.ui.screens.HomeScreen
import com.lumen.tv.ui.screens.LiveScreen
import com.lumen.tv.ui.screens.MovieDetailScreen
import com.lumen.tv.ui.screens.SearchScreen
import com.lumen.tv.ui.screens.SeriesDetailScreen
import com.lumen.tv.ui.screens.SettingsScreen
import com.lumen.tv.ui.screens.SetupFlow
import com.lumen.tv.ui.screens.VodBrowseScreen
import com.lumen.tv.ui.theme.Hue
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import kotlinx.coroutines.delay

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

private val railItems = listOf(
    NavItem(Screen.Search, "Search", Icons.Rounded.Search),
    NavItem(Screen.Home, "Home", Icons.Rounded.Home),
    NavItem(Screen.Live, "Live TV", Icons.Rounded.LiveTv),
    NavItem(Screen.Guide, "TV Guide", Icons.Rounded.CalendarViewWeek),
    NavItem(Screen.Movies, "Movies", Icons.Rounded.Movie),
    NavItem(Screen.Series, "Series", Icons.Rounded.VideoLibrary),
    NavItem(Screen.Favorites, "Favorites", Icons.Rounded.Favorite),
    NavItem(Screen.Settings, "Settings", Icons.Rounded.Settings),
)
private val dockItems = listOf(
    NavItem(Screen.Home, "Home", Icons.Rounded.Home),
    NavItem(Screen.Live, "Live", Icons.Rounded.LiveTv),
    NavItem(Screen.Guide, "Guide", Icons.Rounded.CalendarViewWeek),
    NavItem(Screen.Movies, "Movies", Icons.Rounded.Movie),
    NavItem(Screen.Series, "Series", Icons.Rounded.VideoLibrary),
)

@Composable
fun LumenRoot(vm: AppViewModel, onExit: () -> Unit) {
    val sources by vm.store.sources.collectAsStateWithLifecycle()
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val screen = vm.current
    // Lives above the player, so each screen's state (guide position, selected category, scroll,
    // what you last opened) is still there when you come back from watching something.
    val holder = rememberSaveableStateHolder()

    BackHandler(enabled = true) {
        if (android.os.SystemClock.uptimeMillis() - vm.lastPlayerExit < 700) return@BackHandler
        if (!vm.back()) onExit()
    }

    Box(Modifier.fillMaxSize().background(Hue.Ink)) {
        when {
            screen is Screen.Player -> PlayerScreen(vm, screen.request, onClose = { vm.back() })
            screen is Screen.EditSource -> AddSourceScreen(vm, screen.source, screen.prefillUrl, firstRun = sources.isEmpty())
            sources.isEmpty() -> AddSourceScreen(vm, null, null, firstRun = true)
            screen is Screen.CountrySetup -> SetupFlow(vm, firstRun = false, startAtCategories = false)
            screen is Screen.CategorySetup -> SetupFlow(vm, firstRun = false, startAtCategories = true)
            // First run, or an update added a setup step: country, then categories.
            !settings.setupDone -> SetupFlow(vm, firstRun = true, startAtCategories = false)
            else -> Shell(vm, screen, holder)
        }
        if (screen !is Screen.Player) UpdatePrompts(vm)
        Toast(vm)
        CrashReport()
    }
}

/** Update dialog, "you're up to date" toast, and the one-time "What's new" after an update. */
@Composable
private fun UpdatePrompts(vm: AppViewModel) {
    val state by vm.updater.state.collectAsStateWithLifecycle()
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        val s = state
        if (s is UpdateState.UpToDate) { vm.toast = s.message; vm.updater.later() }
    }
    UpdateDialog(vm)
    val current = vm.updater.currentVersion
    if (settings.whatsNewVersion.isNotEmpty()) {
        if (settings.whatsNewVersion == current && settings.whatsNewNotes.isNotBlank()) {
            WhatsNewDialog(current, settings.whatsNewNotes) {
                vm.store.updateSettings { it.copy(whatsNewVersion = "", whatsNewNotes = "") }
            }
        } else if (Updater.compareVersions(settings.whatsNewVersion, current) < 0) {
            // Notes for a version that's already behind us (e.g. installed some other way): drop them.
            LaunchedEffect(Unit) { vm.store.updateSettings { it.copy(whatsNewVersion = "", whatsNewNotes = "") } }
        }
    }
}

/** Shows the previous crash (if any) so it can be copied and shared. */
@Composable
private fun CrashReport() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(runCatching { crashFile(context).takeIf { it.exists() }?.readText() }.getOrNull()) }
    val text = report ?: return
    fun dismiss() { runCatching { crashFile(context).delete() }; report = null }
    AlertDialog(
        onDismissRequest = { dismiss() },
        containerColor = Hue.Surface2,
        title = { Text("ZapBackTV closed unexpectedly", color = Hue.TextHi) },
        text = {
            Column {
                Text("Copy this report and send it to whoever is helping you fix the app.", color = Hue.TextMid, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    text.take(4000),
                    color = Hue.TextLo,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.height(260.dp).clip(RoundedCornerShape(10.dp)).background(Hue.Ink)
                        .verticalScroll(rememberScrollState()).padding(10.dp),
                )
            }
        },
        confirmButton = {
            LumenButton("Copy report", onClick = {
                clipboard.setText(AnnotatedString(text))
                dismiss()
            }, modifier = Modifier.initialFocus())
        },
        dismissButton = { LumenButton("Close", onClick = { dismiss() }, primary = false) },
    )
}

// ---------------------------------------------------------------------- shell

@Composable
private fun Shell(vm: AppViewModel, screen: Screen, holder: SaveableStateHolder) {
    val isTv = LocalIsTv.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isTv || maxWidth >= 840.dp
        AmbientBackground()
        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                BackgroundProgress(vm)
                Box(Modifier.weight(1f)) { ScreenContent(vm, screen, holder) }
            }
        }
        if (wide) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(start = 88.dp)) { content() }
                SideRail(vm, Modifier.zIndex(2f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).statusBarsPadding()) {
                    if (screen.isTopLevel && screen != Screen.Search) PhoneTopBar(vm)
                    Box(Modifier.weight(1f)) { content() }
                }
                Dock(vm)
            }
        }
    }
}

@Composable
private fun ScreenContent(vm: AppViewModel, screen: Screen, holder: SaveableStateHolder) {
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val lib by vm.library.collectAsStateWithLifecycle()
    val needsLibrary = screen !is Screen.Settings
    if (needsLibrary && lib == null) {
        when (val s = state) {
            is LibState.Error -> EmptyState(
                Icons.Rounded.CloudOff, "Couldn't load your playlist", s.message,
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LumenButton("Try again", onClick = { vm.refresh() }, modifier = Modifier.initialFocus())
                        LumenButton("Playlists", onClick = { vm.navigate(Screen.Settings) }, primary = false)
                    }
                },
            )
            else -> {
                val progress by vm.repo.progress.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadProgressCard(progress, (s as? LibState.Loading)?.message ?: "Getting things ready…")
                }
            }
        }
        return
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        contentKey = { it.route },
        label = "screen",
    ) { s ->
        holder.SaveableStateProvider(s.route) {
            when (s) {
                Screen.Home -> HomeScreen(vm)
                Screen.Live -> LiveScreen(vm)
                Screen.Guide -> GuideScreen(vm)
                Screen.Movies -> VodBrowseScreen(vm, Kind.MOVIE)
                Screen.Series -> VodBrowseScreen(vm, Kind.SERIES)
                Screen.Search -> SearchScreen(vm)
                Screen.Favorites -> FavoritesScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.MovieDetail -> MovieDetailScreen(vm, s.movie)
                is Screen.SeriesDetail -> SeriesDetailScreen(vm, s.series)
                else -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------- TV / tablet rail

@Composable
private fun SideRail(vm: AppViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 244.dp else 88.dp, tween(220), label = "rail")
    val scrim by animateFloatAsState(if (expanded) 1f else 0f, tween(220), label = "scrim")
    val source by vm.activeSource.collectAsStateWithLifecycle()
    val selectedRoot = vm.backStack.lastOrNull { it.isTopLevel } ?: Screen.Home

    Box(modifier.fillMaxHeight()) {
        // Dims the content beside the rail while it's open.
        Box(
            Modifier.fillMaxHeight().width(width + 240.dp).graphicsLayer { alpha = scrim }
                .background(Brush.horizontalGradient(listOf(Hue.Ink, Hue.Ink.copy(alpha = 0.88f), Color.Transparent)))
        )
        Column(
            Modifier.fillMaxHeight().width(width)
                .background(Hue.Night.copy(alpha = if (expanded) 0.97f else 0.6f))
                .drawBehind { drawRect(Color.White.copy(alpha = 0.07f), topLeft = Offset(size.width - 1f, 0f), size = Size(1f, size.height)) }
                .onFocusChanged { expanded = it.hasFocus }
                .focusRestorer()
                .focusGroup()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 24.dp, horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.padding(start = 12.dp, bottom = 20.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                BrandMark(36.dp)
                AnimatedVisibility(expanded) {
                    Column(Modifier.padding(start = 12.dp)) {
                        Wordmark()
                        Text(source?.name ?: "", style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            railItems.forEach { item ->
                RailItem(item, selected = selectedRoot == item.screen, expanded = expanded) {
                    if (vm.current != item.screen) vm.navigate(item.screen)
                }
            }
        }
    }
}

@Composable
private fun RailItem(item: NavItem, selected: Boolean, expanded: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .focusGlow(shape, scale = 1.03f, ring = 0.dp, onFocus = { focused = it })
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> accent.soft
                    else -> Color.Transparent
                }
            )
            .drawBehind {
                if (selected && !focused) drawRoundRect(
                    accent.brush, topLeft = Offset(0f, size.height * 0.25f),
                    size = Size(3.dp.toPx(), size.height * 0.5f), cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when {
            focused -> Hue.OnAccent
            selected -> accent.main
            else -> Hue.TextMid
        }
        Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(22.dp))
        AnimatedVisibility(expanded) {
            Text(
                item.label, style = MaterialTheme.typography.titleMedium, color = tint,
                modifier = Modifier.padding(start = 16.dp), maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------- phone: top bar + floating dock

@Composable
private fun PhoneTopBar(vm: AppViewModel) {
    val source by vm.activeSource.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(34.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.navigate(Screen.Settings) }) {
            Wordmark()
            Text(source?.name ?: "", style = MaterialTheme.typography.bodySmall, color = Hue.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundIconButton(Icons.Rounded.Search, "Search", { vm.navigate(Screen.Search) }, size = 40.dp)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Rounded.Favorite, "Favorites", { vm.navigate(Screen.Favorites) }, size = 40.dp, active = vm.current == Screen.Favorites)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Rounded.Settings, "Settings", { vm.navigate(Screen.Settings) }, size = 40.dp, active = vm.current == Screen.Settings)
    }
}

/** Floating glass dock: the current tab opens into a gradient capsule with its label. */
@Composable
private fun Dock(vm: AppViewModel) {
    val accent = LocalAccent.current
    val selectedRoot = vm.backStack.lastOrNull { it.isTopLevel } ?: Screen.Home
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), fill = Hue.Night.copy(alpha = 0.94f)).padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dockItems.forEach { item ->
                val selected = selectedRoot == item.screen
                val shape = RoundedCornerShape(22.dp)
                Row(
                    Modifier.clip(shape)
                        .then(if (selected) Modifier.background(accent.brush) else Modifier)
                        .clickable { if (vm.current != item.screen) vm.navigate(item.screen) }
                        .animateContentSize()
                        .padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.icon, item.label, tint = if (selected) Hue.OnAccent else Hue.TextMid, modifier = Modifier.size(22.dp))
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(item.label, style = MaterialTheme.typography.labelLarge, color = Hue.OnAccent, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- toast & background progress

@Composable
private fun Toast(vm: AppViewModel) {
    val msg = vm.toast
    LaunchedEffect(msg) {
        if (msg != null) { delay(2600); vm.toast = null }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 92.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = msg != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Text(
                msg ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = Hue.TextHi,
                modifier = Modifier.glass(RoundedCornerShape(50), fill = Hue.Surface3).padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** Slim bar for work that happens while the app is usable: playlist refreshes and TV guide loading. */
@Composable
private fun BackgroundProgress(vm: AppViewModel) {
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val progress by vm.repo.progress.collectAsStateWithLifecycle()
    val epgProgress by vm.repo.epgProgress.collectAsStateWithLifecycle()
    val epgStatus by vm.repo.epgStatus.collectAsStateWithLifecycle()
    val refresh = progress?.takeIf { state is LibState.Ready }
    val guide = epgProgress
    val (fraction, label) = when {
        refresh != null -> refresh.overall to ("Updating playlist · " + (refresh.current?.let { st -> st.label + (st.detail?.let { " — $it" } ?: "") } ?: "…"))
        guide != null -> guide to (epgStatus ?: "Loading TV guide…")
        else -> null to null
    }
    AnimatedVisibility(fraction != null) {
        SlimProgressBar(fraction ?: 0f, label ?: "")
    }
}
