package com.lumen.tv.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.lumen.tv.data.Kind
import com.lumen.tv.data.Programme
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.PlayRequest
import com.lumen.tv.ui.components.ListRow
import com.lumen.tv.ui.components.LiveBadge
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.NetImage
import com.lumen.tv.ui.components.ProgressLine
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.focusGlow
import com.lumen.tv.ui.screens.time
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.lumen.tv.ui.components.Eyebrow
import com.lumen.tv.ui.components.glass

private enum class Panel { CHANNELS, TRACKS, SPEED, SLEEP }

private data class TrackOption(val label: String, val group: Tracks.Group?, val index: Int, val selected: Boolean)

@Composable
fun PlayerScreen(vm: AppViewModel, initial: PlayRequest, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current
    val isTv = LocalIsTv.current
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val epg by vm.repo.epg.collectAsStateWithLifecycle()
    val favs by vm.store.favorites.collectAsStateWithLifecycle()

    var req by remember { mutableStateOf(initial) }
    val player = remember { PlayerFactory.build(context, initial.userAgent, initial.referrer, settings.bufferSeconds) }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var quality by remember { mutableStateOf<String?>(null) }
    var tracksVersion by remember { mutableIntStateOf(0) }

    var overlay by remember { mutableStateOf(true) }
    var poke by remember { mutableLongStateOf(0L) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var aspect by remember { mutableIntStateOf(settings.aspect) }
    var speed by remember { mutableFloatStateOf(1f) }
    var sleepAt by remember { mutableStateOf<Long?>(null) }
    var digits by remember { mutableStateOf("") }
    var previousIndex by remember { mutableStateOf<Int?>(null) }
    var upNext by remember { mutableStateOf<Int?>(null) }
    var hint by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    var seekFlash by remember { mutableLongStateOf(0L) }
    var showSeek by remember { mutableStateOf(false) }

    val isLive = req.kind == Kind.LIVE && !req.isCatchup
    val currentChannel = req.channels.getOrNull(req.channelIndex)
    val hasNextEpisode = req.episodeIndex >= 0 && req.episodeIndex < req.episodes.lastIndex
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }

    fun show() { overlay = true; poke = System.currentTimeMillis() }
    fun flashHint(icon: ImageVector, text: String) { hint = icon to text }

    // ----------------------------------------------------------------- playback wiring
    val latestReq by rememberUpdatedState(req)
    fun saveProgress() {
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: 0L
        vm.recordWatch(latestReq, if (latestReq.kind == Kind.LIVE) 0 else pos, dur)
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) { error = null; retries = 0 }
                if (state == Player.STATE_ENDED) {
                    when {
                        latestReq.kind == Kind.LIVE && !latestReq.isCatchup -> { player.seekToDefaultPosition(); player.prepare() }
                        latestReq.episodeIndex >= 0 && latestReq.episodeIndex < latestReq.episodes.lastIndex && vm.store.settings.value.autoplayNext ->
                            upNext = 10
                        else -> { saveProgress(); onClose() }
                    }
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                if (retries < 4) {
                    retries++
                    error = "Reconnecting… (${retries}/4)"
                    scope.launch { delay(1500L * retries); player.prepare(); player.play() }
                } else {
                    error = friendlyError(e)
                    show()
                }
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                quality = when {
                    size.height >= 2000 -> "4K"
                    size.height >= 1000 -> "FHD"
                    size.height >= 700 -> "HD"
                    size.height > 0 -> "SD"
                    else -> null
                }
            }
            override fun onTracksChanged(tracks: Tracks) { tracksVersion++ }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }

    // Load whatever request is current (initial, zapped channel, next episode…)
    LaunchedEffect(req.url) {
        error = null; retries = 0; upNext = null; quality = null
        player.setMediaItem(PlayerFactory.mediaItem(req.url), req.startMs)
        player.prepare()
        player.playWhenReady = true
        player.setPlaybackSpeed(if (isLive) 1f else speed)
        if (req.kind == Kind.LIVE && !req.isCatchup) vm.recordWatch(req, 0, 0)
    }

    // Poll position & periodically persist resume points
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            if (++tick % 30 == 0 && latestReq.kind != Kind.LIVE && duration > 0) saveProgress()
            sleepAt?.let { if (System.currentTimeMillis() >= it) { player.pause(); saveProgress(); onClose() } }
            delay(500)
        }
    }

    // Auto-hide controls
    LaunchedEffect(overlay, poke, panel, isPlaying, error) {
        if (overlay && panel == null && isPlaying && error == null) {
            delay(if (isTv) 5000 else 3500)
            overlay = false
        }
    }
    LaunchedEffect(overlay, panel) {
        if (!isTv) return@LaunchedEffect
        delay(60)
        runCatching { if (overlay && panel == null) playFocus.requestFocus() else if (!overlay && panel == null) rootFocus.requestFocus() }
    }
    LaunchedEffect(hint) { if (hint != null) { delay(1200); hint = null } }
    LaunchedEffect(upNext) {
        val n = upNext ?: return@LaunchedEffect
        if (n <= 0) { upNext = null; playEpisodeIndex(vm, req, req.episodeIndex + 1) { req = it } }
        else { delay(1000); if (upNext == n) upNext = n - 1 }
    }
    // Numeric channel entry: commit after a short pause
    LaunchedEffect(digits) {
        if (digits.isEmpty()) return@LaunchedEffect
        delay(1600)
        val n = digits.toIntOrNull()
        digits = ""
        val idx = req.channels.indexOfFirst { it.number == n }
        if (idx >= 0) { previousIndex = req.channelIndex; req = vm.liveRequest(req.channels[idx], req.channels, idx); show() }
        else vm.toast = "Channel $n isn't in this list"
    }

    // ----------------------------------------------------------------- window setup
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        val oldOrientation = activity?.requestedOrientation
        if (!isTv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (!isTv) activity?.requestedOrientation = oldOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
        }
    }

    // ----------------------------------------------------------------- actions
    fun zap(delta: Int) {
        if (req.channels.size < 2) return
        val i = Math.floorMod(req.channelIndex + delta, req.channels.size)
        previousIndex = req.channelIndex
        req = vm.liveRequest(req.channels[i], req.channels, i)
        show()
    }
    fun jumpBack() {
        val p = previousIndex ?: return
        if (p !in req.channels.indices) return
        previousIndex = req.channelIndex
        req = vm.liveRequest(req.channels[p], req.channels, p)
        show()
    }
    fun seekBy(ms: Long) {
        if (isLive) return
        val target = (player.currentPosition + ms).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        position = target
        seekFlash = System.currentTimeMillis()
    }
    fun togglePlay() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() } ; show() }
    fun cycleAspect() {
        aspect = (aspect + 1) % 3
        flashHint(Icons.Rounded.AspectRatio, listOf("Fit", "Zoom", "Stretch")[aspect])
    }
    fun nextEpisode() { if (hasNextEpisode) { saveProgress(); playEpisodeIndex(vm, req, req.episodeIndex + 1) { req = it } } }
    fun prevEpisode() { if (req.episodeIndex > 0) { saveProgress(); playEpisodeIndex(vm, req, req.episodeIndex - 1) { req = it } } }

    BackHandler {
        when {
            panel != null -> panel = null
            upNext != null -> upNext = null
            isTv && overlay && error == null -> overlay = false
            else -> onClose()
        }
    }

    // ----------------------------------------------------------------- UI
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                poke = System.currentTimeMillis()
                val code = ev.key.nativeKeyCode
                // Keys that work regardless of what's focused
                when (code) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { togglePlay(); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_LAST_CHANNEL -> { jumpBack(); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(30_000); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-30_000); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> { if (isLive) zap(1) else nextEpisode(); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { if (isLive) zap(-1) else prevEpisode(); return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_CAPTIONS -> { panel = Panel.TRACKS; return@onPreviewKeyEvent true }
                    android.view.KeyEvent.KEYCODE_INFO, android.view.KeyEvent.KEYCODE_MENU -> {
                        if (overlay) overlay = false else show(); return@onPreviewKeyEvent true
                    }
                    android.view.KeyEvent.KEYCODE_GUIDE -> { if (isLive) panel = Panel.CHANNELS; return@onPreviewKeyEvent true }
                }
                if (code in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 && isLive) {
                    digits = (digits + (code - android.view.KeyEvent.KEYCODE_0)).take(5)
                    return@onPreviewKeyEvent true
                }
                if (overlay || panel != null) return@onPreviewKeyEvent false
                // Controls hidden: the D-pad drives playback directly
                when (ev.key) {
                    Key.DirectionUp -> { if (isLive) zap(-1) else show(); true }
                    Key.DirectionDown -> { if (isLive) zap(1) else show(); true }
                    Key.DirectionLeft -> { if (isLive) panel = Panel.CHANNELS else seekBy(-10_000); true }
                    Key.DirectionRight -> { if (isLive) show() else seekBy(10_000); true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { show(); true }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.15f)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { it.resizeMode = PlayerFactory.resizeMode(aspect) },
            modifier = Modifier.fillMaxSize(),
        )

        // Touch layer (phones/tablets): tap, double-tap seek, swipe brightness/volume
        if (!isTv && !vm.inPip) TouchLayer(
            onTap = { if (overlay) overlay = false else show() },
            onDoubleTap = { left -> if (!isLive) { seekBy(if (left) -10_000 else 10_000); flashHint(if (left) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, if (left) "−10s" else "+10s") } else zap(if (left) -1 else 1) },
            onBrightness = { v ->
                activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = v } }
                flashHint(Icons.Rounded.BrightnessMedium, "${(v * 100).roundToInt()}%")
            },
            onVolume = { v -> flashHint(Icons.AutoMirrored.Rounded.VolumeUp, "${(v * 100).roundToInt()}%") },
            context = context,
        )

        if (vm.inPip) return@Box

        // Center status
        Box(Modifier.align(Alignment.Center)) {
            when {
                error != null && retries >= 4 -> ErrorCard(error!!, onRetry = { retries = 0; error = null; player.prepare(); player.play() }, onClose = onClose)
                buffering || error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = accent.main, strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
                    if (error != null) { Spacer(Modifier.height(12.dp)); Text(error!!, color = Color.White, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        // Transient hint (aspect, volume, brightness, seek)
        AnimatedVisibility(hint != null, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            hint?.let { (icon, text) ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(text, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        // Quick seek bar when controls are hidden (TV seek with D-pad)
        LaunchedEffect(seekFlash) { if (seekFlash > 0) { showSeek = true; delay(1600); showSeek = false } }
        AnimatedVisibility(!overlay && showSeek && !isLive, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))).padding(horizontal = 48.dp, vertical = 28.dp)) {
                SeekBar(position, duration, focusable = false, onSeek = {}, onSeekTo = {})
            }
        }

        // Channel number entry
        AnimatedVisibility(digits.isNotEmpty(), Modifier.align(Alignment.TopEnd).padding(36.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(
                digits, style = MaterialTheme.typography.displayLarge, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 26.dp, vertical = 10.dp),
            )
        }

        // ----------------------------------------------------------- controls overlay
        AnimatedVisibility(overlay && error.let { it == null || retries < 4 }, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.75f), 0.25f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.9f))))

                // Top bar
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = if (isTv) 32.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isTv) { RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onClose); Spacer(Modifier.width(14.dp)) }
                    if (isLive && currentChannel != null) {
                        Box(Modifier.size(64.dp, 42.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.1f))) {
                            NetImage(currentChannel.image, currentChannel.name, Modifier.fillMaxSize().padding(5.dp), contentScale = ContentScale.Fit, initialsSize = 13)
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLive && currentChannel != null && settings.showNumbers) {
                                Text("${currentChannel.number}  ", style = MaterialTheme.typography.headlineMedium, color = accent.main)
                            }
                            Text(req.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        req.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        quality?.let { Badge(it) }
                        if (sleepAt != null) Badge("Sleep ${(((sleepAt ?: 0) - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)}m")
                        if (isLive) LiveBadge() else if (req.isCatchup) LiveBadge(text = "CATCH-UP")
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }

                // Bottom area
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(horizontal = if (isTv) 48.dp else 20.dp, vertical = if (isTv) 32.dp else 16.dp)
                ) {
                    if (isLive && currentChannel != null) {
                        val now = epg.now(currentChannel)
                        val next = epg.upcoming(currentChannel, 2).firstOrNull { it.start >= (now?.end ?: 0) - 1000 }
                        LiveInfo(now, next)
                        Spacer(Modifier.height(18.dp))
                    } else if (!isLive) {
                        SeekBar(
                            position, duration, focusable = isTv,
                            onSeek = { seekBy(it) },
                            onSeekTo = { f -> if (duration > 0) { player.seekTo((duration * f).toLong()); show() } },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isLive) {
                                CtrlButton(Icons.Rounded.KeyboardArrowUp, "Previous channel") { zap(-1) }
                                CtrlButton(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", big = true, modifier = Modifier.focusRequester(playFocus)) { togglePlay() }
                                CtrlButton(Icons.Rounded.KeyboardArrowDown, "Next channel") { zap(1) }
                                if (previousIndex != null) CtrlButton(Icons.Rounded.SwapVert, "Last channel") { jumpBack() }
                            } else {
                                if (req.episodeIndex > 0) CtrlButton(Icons.Rounded.SkipPrevious, "Previous episode") { prevEpisode() }
                                CtrlButton(Icons.Rounded.Replay10, "Back 10 seconds") { seekBy(-10_000); show() }
                                CtrlButton(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", big = true, modifier = Modifier.focusRequester(playFocus)) { togglePlay() }
                                CtrlButton(Icons.Rounded.Forward10, "Forward 10 seconds") { seekBy(10_000); show() }
                                if (hasNextEpisode) CtrlButton(Icons.Rounded.SkipNext, "Next episode") { nextEpisode() }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isLive && req.channels.size > 1) CtrlButton(Icons.Rounded.FormatListBulleted, "Channels") { panel = Panel.CHANNELS }
                            CtrlButton(Icons.Rounded.ClosedCaption, "Audio & subtitles") { panel = Panel.TRACKS }
                            CtrlButton(Icons.Rounded.AspectRatio, "Aspect ratio") { cycleAspect(); show() }
                            if (!isLive) CtrlButton(Icons.Rounded.Speed, "Speed") { panel = Panel.SPEED }
                            CtrlButton(Icons.Rounded.Bedtime, "Sleep timer", active = sleepAt != null) { panel = Panel.SLEEP }
                            if (isLive && currentChannel != null) {
                                val fav = "${vm.activeSource.value?.id}|${currentChannel.key}" in favs
                                CtrlButton(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", active = fav) { vm.toggleFavorite(currentChannel) }
                            }
                        }
                    }
                    if (isTv && isLive) {
                        Spacer(Modifier.height(12.dp))
                        Text("▲▼ change channel · ◀ channel list · 0–9 jump to number", style = MaterialTheme.typography.labelMedium, color = Hue.TextLo)
                    }
                }
            }
        }

        // ----------------------------------------------------------- up next
        AnimatedVisibility(upNext != null, Modifier.align(Alignment.BottomEnd).padding(36.dp), enter = fadeIn() + slideInHorizontally { it }, exit = fadeOut()) {
            val ep = req.episodes.getOrNull(req.episodeIndex + 1)
            if (ep != null) Column(
                Modifier.width(380.dp).glass(RoundedCornerShape(22.dp), fill = Hue.Surface2).padding(18.dp)
            ) {
                Eyebrow("Up next in ${upNext ?: 0}")
                Spacer(Modifier.height(6.dp))
                Text("${ep.label} · ${ep.title}", style = MaterialTheme.typography.titleLarge, color = Hue.TextHi, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                val upFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(60); runCatching { upFocus.requestFocus() } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LumenButton("Play now", onClick = { upNext = 0 }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.focusRequester(upFocus))
                    LumenButton("Cancel", onClick = { upNext = null; onClose() }, primary = false)
                }
            }
        }

        // ----------------------------------------------------------- side panels
        AnimatedVisibility(
            panel != null, Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            Column(
                Modifier.fillMaxHeight().width(if (isTv) 400.dp else 340.dp)
                    .background(Hue.Night.copy(alpha = 0.96f))
                    .padding(horizontal = 18.dp, vertical = 24.dp)
            ) {
                when (panel) {
                    Panel.CHANNELS -> ChannelsPanel(req, epg) { i ->
                        previousIndex = req.channelIndex
                        req = vm.liveRequest(req.channels[i], req.channels, i)
                        panel = null; show()
                    }
                    Panel.TRACKS -> TracksPanel(player, tracksVersion)
                    Panel.SPEED -> OptionsPanel("Playback speed", listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { (if (it == 1f) "Normal" else "${it}×") to it }, speed) {
                        speed = it; player.setPlaybackSpeed(it); panel = null
                    }
                    Panel.SLEEP -> OptionsPanel(
                        "Sleep timer", listOf("Off" to 0, "15 minutes" to 15, "30 minutes" to 30, "45 minutes" to 45, "1 hour" to 60, "90 minutes" to 90, "2 hours" to 120),
                        sleepAt?.let { -1 } ?: 0,
                    ) { m ->
                        sleepAt = if (m == 0) null else System.currentTimeMillis() + m * 60_000L
                        vm.toast = if (m == 0) "Sleep timer off" else "Stopping playback in $m minutes"
                        panel = null
                    }
                    null -> Unit
                }
            }
        }
    }
}

// ===================================================================== pieces

private fun playEpisodeIndex(vm: AppViewModel, req: PlayRequest, i: Int, set: (PlayRequest) -> Unit) {
    val series = req.series ?: return
    if (i !in req.episodes.indices) return
    val ep = req.episodes[i]
    val start = vm.positionOf(ep.watchKey)?.let { (p, d) -> if (d > 0 && p.toFloat() / d < 0.94f) p else 0L } ?: 0L
    set(vm.episodeRequest(series, req.episodes, i, start))
}

private fun friendlyError(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "The provider refused this stream. You may have reached your connection limit."
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Network problem — couldn't reach the stream."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
        "This stream's format isn't supported. For live TV, try switching the stream format in Settings."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This device can't decode this video format."
    else -> e.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun TouchLayer(
    onTap: () -> Unit,
    onDoubleTap: (left: Boolean) -> Unit,
    onBrightness: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    context: Context,
) {
    val tap by rememberUpdatedState(onTap)
    val doubleTap by rememberUpdatedState(onDoubleTap)
    val bright by rememberUpdatedState(onBrightness)
    val vol by rememberUpdatedState(onVolume)
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { o -> doubleTap(o.x < size.width / 2f) },
                )
            }
            .pointerInput(Unit) {
                var leftSide = false
                detectVerticalDragGestures(
                    onDragStart = { o -> leftSide = o.x < size.width / 2f },
                ) { change, dy ->
                    change.consume()
                    val delta = -dy / (size.height * 0.8f)
                    if (leftSide) {
                        brightness = (brightness + delta).coerceIn(0.02f, 1f); bright(brightness)
                    } else {
                        volume = (volume + delta).coerceIn(0f, 1f)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVol).roundToInt(), 0)
                        vol(volume)
                    }
                }
            }
    )
}

@Composable
private fun CtrlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    RoundIconButton(icon, label, onClick, modifier, size = if (big) 64.dp else 48.dp, active = active)
}

@Composable
private fun Badge(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun LiveInfo(now: Programme?, next: Programme?) {
    val accent = LocalAccent.current
    Column(Modifier.widthIn(max = 900.dp)) {
        if (now != null) {
            Text(now.title, style = MaterialTheme.typography.headlineLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time(now.start), style = MaterialTheme.typography.labelLarge, color = Hue.TextMid)
                Spacer(Modifier.width(12.dp))
                ProgressLine(now.progress(), Modifier.weight(1f), height = 4.dp)
                Spacer(Modifier.width(12.dp))
                Text(time(now.end), style = MaterialTheme.typography.labelLarge, color = Hue.TextMid)
            }
            now.desc?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text("No program information", style = MaterialTheme.typography.titleLarge, color = Hue.TextMid)
        }
        if (next != null) {
            Spacer(Modifier.height(8.dp))
            Row {
                Text("NEXT  ", style = MaterialTheme.typography.labelLarge, color = accent.main)
                Text("${time(next.start)}  ${next.title}", style = MaterialTheme.typography.bodyMedium, color = Hue.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SeekBar(position: Long, duration: Long, focusable: Boolean, onSeek: (Long) -> Unit, onSeekTo: (Float) -> Unit) {
    val accent = LocalAccent.current
    var focused by remember { mutableStateOf(false) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column {
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .then(
                    if (focusable) Modifier
                        .onFocusChanged { focused = it.isFocused }
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.DirectionLeft -> { onSeek(-10_000); true }
                                Key.DirectionRight -> { onSeek(10_000); true }
                                else -> false
                            }
                        }
                        .focusable()
                    else Modifier
                )
                .pointerInput(duration) {
                    detectTapGestures { o -> onSeekTo(o.x / size.width) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(if (focused) 8.dp else 5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(fraction).height(if (focused) 8.dp else 5.dp).clip(RoundedCornerShape(50)).background(accent.brush))
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.requiredSize(if (focused) 22.dp else 14.dp).clip(CircleShape).background(Color.White))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(fmt(position), style = MaterialTheme.typography.labelLarge, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text(if (duration > 0) "−" + fmt(duration - position) else "--:--", style = MaterialTheme.typography.labelLarge, color = Hue.TextMid)
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.widthIn(max = 480.dp).glass(RoundedCornerShape(24.dp), fill = Hue.Surface2).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Playback stopped", style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val fr = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
            LumenButton("Try again", onClick = onRetry, icon = Icons.Rounded.Refresh, modifier = Modifier.focusRequester(fr))
            LumenButton("Close", onClick = onClose, primary = false)
        }
    }
}

@Composable
private fun ChannelsPanel(req: PlayRequest, epg: com.lumen.tv.data.EpgData, onPick: (Int) -> Unit) {
    val accent = LocalAccent.current
    Text("Channels", style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi)
    Spacer(Modifier.height(12.dp))
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (req.channelIndex - 3).coerceAtLeast(0))
    val current = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { current.requestFocus() } }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(req.channels, key = { _, c -> c.listKey }) { i, c ->
            val now = epg.now(c)
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth()
                    .then(if (i == req.channelIndex) Modifier.focusRequester(current) else Modifier)
                    .focusGlow(RoundedCornerShape(12.dp), scale = 1.02f, ring = 0.dp, onFocus = { focused = it })
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (focused) Color.White else if (i == req.channelIndex) accent.soft else Color.Transparent)
                    .clickable { onPick(i) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.number.toString(), style = MaterialTheme.typography.labelLarge, color = if (focused) Color(0xFF3A3F55) else accent.main, modifier = Modifier.width(38.dp))
                NetImage(c.image, c.name, Modifier.size(44.dp, 30.dp), contentScale = ContentScale.Fit, initialsSize = 11)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, color = if (focused) Color(0xFF070912) else Hue.TextHi, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    now?.let { Text(it.title, style = MaterialTheme.typography.bodySmall, color = if (focused) Color(0xFF3A3F55) else Hue.TextLo, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun TracksPanel(player: Player, version: Int) {
    val audio = remember(version) { trackOptions(player, C.TRACK_TYPE_AUDIO) }
    val text = remember(version) { trackOptions(player, C.TRACK_TYPE_TEXT) }
    val video = remember(version) { trackOptions(player, C.TRACK_TYPE_VIDEO) }
    val textDisabled = remember(version) { C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes }
    val videoAuto = remember(version) { player.trackSelectionParameters.overrides.keys.none { it.type == C.TRACK_TYPE_VIDEO } }
    var refresh by remember { mutableIntStateOf(0) }

    fun select(type: Int, o: TrackOption?) {
        val b = player.trackSelectionParameters.buildUpon()
        val g = o?.group
        if (o == null || g == null) {
            if (type == C.TRACK_TYPE_TEXT) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) else b.clearOverridesOfType(type)
        } else {
            b.setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, o.index))
        }
        player.trackSelectionParameters = b.build()
        refresh++
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PanelHeader("Audio") }
        if (audio.isEmpty()) item { Text("Default", color = Hue.TextLo, modifier = Modifier.padding(14.dp)) }
        audio.forEachIndexed { i, o ->
            item(key = "a$i$refresh") {
                ListRow(o.label, selected = o.selected, trailing = if (o.selected) "✓" else null) { select(C.TRACK_TYPE_AUDIO, o) }
            }
        }
        item { PanelHeader("Subtitles") }
        item(key = "toff$refresh") { ListRow("Off", selected = textDisabled || text.none { it.selected }) { select(C.TRACK_TYPE_TEXT, null) } }
        text.forEachIndexed { i, o ->
            item(key = "t$i$refresh") {
                ListRow(o.label, selected = o.selected && !textDisabled, trailing = if (o.selected && !textDisabled) "✓" else null) { select(C.TRACK_TYPE_TEXT, o) }
            }
        }
        if (video.size > 1) {
            item { PanelHeader("Quality") }
            item(key = "vauto$refresh") { ListRow("Auto", selected = videoAuto) { select(C.TRACK_TYPE_VIDEO, null) } }
            video.forEachIndexed { i, o ->
                item(key = "v$i$refresh") { ListRow(o.label, selected = !videoAuto && o.selected) { select(C.TRACK_TYPE_VIDEO, o) } }
            }
        }
    }
}

@Composable
private fun PanelHeader(text: String) {
    Text(text.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = LocalAccent.current.main, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun <T> OptionsPanel(title: String, options: List<Pair<String, T>>, current: T, onPick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Hue.TextHi)
    Spacer(Modifier.height(12.dp))
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    options.forEachIndexed { i, (label, v) ->
        ListRow(label, selected = v == current, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { onPick(v) }
    }
}

private fun trackOptions(player: Player, type: Int): List<TrackOption> =
    player.currentTracks.groups.filter { it.type == type }.flatMap { g ->
        (0 until g.length).filter { g.isTrackSupported(it) }.map { i ->
            TrackOption(trackLabel(g.getTrackFormat(i), type), g, i, g.isTrackSelected(i))
        }
    }.distinctBy { it.label + it.selected }

private fun trackLabel(f: Format, type: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
    return when (type) {
        C.TRACK_TYPE_VIDEO -> buildString {
            append(if (f.height > 0) "${f.height}p" else "Video")
            if (f.bitrate > 0) append(" · ${String.format(Locale.US, "%.1f", f.bitrate / 1_000_000f)} Mbps")
        }
        C.TRACK_TYPE_AUDIO -> listOfNotNull(
            f.label ?: lang ?: "Track",
            when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null },
            f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { it.length <= 6 },
        ).joinToString(" · ")
        else -> listOfNotNull(f.label ?: lang ?: "Subtitles", if (f.selectionFlags and C.SELECTION_FLAG_FORCED != 0) "Forced" else null).joinToString(" · ")
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

