package com.lumen.tv.player

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.lumen.tv.data.Http

object PlayerFactory {

    fun build(context: Context, userAgent: String?, referrer: String?, bufferSeconds: Int): ExoPlayer {
        val http = OkHttpDataSource.Factory(Http.client)
            .setUserAgent(userAgent?.takeIf { it.isNotBlank() } ?: Http.DEFAULT_UA)
            .apply { if (!referrer.isNullOrBlank()) setDefaultRequestProperties(mapOf("Referer" to referrer)) }

        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setConstantBitrateSeekingEnabled(true)

        val minBuffer = (bufferSeconds * 1000).coerceIn(5_000, 120_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxOf(minBuffer * 2, 50_000), 2_000, 4_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setTunnelingEnabled(false).build())
        }

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http, extractors))
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    /** Gives ExoPlayer a hint for URLs whose extension doesn't reveal the format. */
    fun mediaItem(url: String): MediaItem {
        val lower = url.lowercase()
        val mime = when {
            ".m3u8" in lower || "type=m3u8" in lower || "/hls/" in lower -> MimeTypes.APPLICATION_M3U8
            ".mpd" in lower -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        return MediaItem.Builder().setUri(url).apply { if (mime != null) setMimeType(mime) }.build()
    }

    fun resizeMode(aspect: Int) = when (aspect) {
        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }
}

/** Small muted-by-choice player used for live previews in the channel browser. */
@Composable
fun MiniPlayer(url: String?, userAgent: String?, modifier: Modifier = Modifier, muted: Boolean = false) {
    val context = LocalContext.current
    val player = remember { PlayerFactory.build(context, userAgent, null, 5) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(url) {
        if (url == null) { player.stop(); player.clearMediaItems(); return@LaunchedEffect }
        player.setMediaItem(PlayerFactory.mediaItem(url))
        player.volume = if (muted) 0f else 1f
        player.prepare()
        player.playWhenReady = true
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                this.player = player
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(false)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        },
        modifier = modifier,
    )
}
