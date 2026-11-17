package com.lumen.tv.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lumen.tv.data.Source
import com.lumen.tv.data.SourceType
import com.lumen.tv.data.XtreamClient
import com.lumen.tv.ui.AppViewModel
import com.lumen.tv.ui.components.LumenButton
import com.lumen.tv.ui.components.Pill
import com.lumen.tv.ui.components.RoundIconButton
import com.lumen.tv.ui.components.initialFocus
import com.lumen.tv.ui.theme.LocalAccent
import com.lumen.tv.ui.theme.LocalIsTv
import com.lumen.tv.ui.theme.Hue
import kotlinx.coroutines.launch
import com.lumen.tv.ui.components.AmbientBackground
import com.lumen.tv.ui.components.BrandMark
import com.lumen.tv.ui.components.Wordmark
import com.lumen.tv.ui.components.glass
import androidx.compose.ui.unit.sp

@Composable
fun AddSourceScreen(vm: AppViewModel, existing: Source?, prefillUrl: String?, firstRun: Boolean) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()

    val prefillIsFile = prefillUrl != null && (prefillUrl.startsWith("content:") || prefillUrl.startsWith("file:"))
    var type by rememberSaveable {
        mutableStateOf(existing?.type ?: if (prefillIsFile) SourceType.M3U_FILE else if (prefillUrl != null) SourceType.M3U_URL else SourceType.XTREAM)
    }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var server by rememberSaveable { mutableStateOf(if (existing?.type == SourceType.XTREAM) existing?.url.orEmpty() else "") }
    var username by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var m3uUrl by rememberSaveable { mutableStateOf(if (existing?.type == SourceType.M3U_URL) existing?.url.orEmpty() else if (!prefillIsFile) prefillUrl ?: "" else "") }
    var fileUri by rememberSaveable { mutableStateOf(if (prefillIsFile) prefillUrl else null) }
    var epgUrl by rememberSaveable { mutableStateOf(existing?.epgUrl ?: "") }
    var userAgent by rememberSaveable { mutableStateOf(existing?.userAgent ?: "") }
    var showAdvanced by rememberSaveable { mutableStateOf(existing?.epgUrl?.isNotBlank() == true || existing?.userAgent?.isNotBlank() == true) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            fileUri = uri.toString()
            if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "My playlist"
        }
    }

    val detected = remember(m3uUrl) { if (type == SourceType.M3U_URL) XtreamClient.detectFromM3uUrl(m3uUrl) else null }

    fun finished() {
        vm.navigate(com.lumen.tv.ui.Screen.Home)
        // An extra playlist gets the category picker too (first-run setup covers the very first one).
        if (existing == null && vm.store.settings.value.setupDone) vm.navigate(com.lumen.tv.ui.Screen.CategorySetup)
    }

    fun submit() {
        error = null
        val finalName = name.trim().ifBlank {
            when (type) {
                SourceType.XTREAM -> runCatching { Uri.parse(XtreamClient.normalizeBase(server)).host }.getOrNull() ?: "My IPTV"
                SourceType.M3U_URL -> runCatching { Uri.parse(m3uUrl.trim()).host }.getOrNull() ?: "My playlist"
                SourceType.M3U_FILE -> "My playlist"
            }
        }
        val base = Source(
            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
            name = finalName, type = type, url = "",
            epgUrl = epgUrl.trim(), userAgent = userAgent.trim(),
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
        )
        when (type) {
            SourceType.XTREAM -> {
                if (server.isBlank() || username.isBlank() || password.isBlank()) { error = "Enter the server address, username and password."; return }
                val s = base.copy(url = XtreamClient.normalizeBase(server), username = username.trim(), password = password.trim())
                busy = true
                scope.launch {
                    runCatching { vm.repo.testXtream(s) }
                        .onSuccess { vm.saveSource(s) { busy = false; finished() } }
                        .onFailure {
                            busy = false
                            error = when (it) {
                                is java.net.UnknownHostException -> "Can't find that server. Check the address."
                                is java.net.SocketTimeoutException -> "The server didn't respond in time."
                                is java.net.ConnectException -> "Couldn't connect. Check the address and port."
                                else -> it.message ?: "Couldn't sign in."
                            }
                        }
                }
            }
            SourceType.M3U_URL -> {
                val u = m3uUrl.trim()
                if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) { error = "Enter a full link starting with http:// or https://"; return }
                busy = true
                vm.saveSource(base.copy(url = u)) { busy = false; finished() }
            }
            SourceType.M3U_FILE -> {
                val f = fileUri
                if (f == null && existing == null) { error = "Choose a playlist file first."; return }
                busy = true
                vm.saveSource(base.copy(url = f ?: existing!!.url), fileUri = f?.let(Uri::parse)) {
                    busy = false; finished()
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isTv || maxWidth >= 840.dp
        AmbientBackground()
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(start = 64.dp, end = 32.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandMark(76.dp)
                        Spacer(Modifier.width(16.dp))
                        Wordmark(fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(if (firstRun) "Welcome to ZapBackTV" else if (existing != null) "Edit playlist" else "Add a playlist", style = MaterialTheme.typography.displayLarge, color = Hue.TextHi)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Bring your own IPTV service. ZapBackTV supports Xtream Codes logins and M3U playlists, with a full TV guide, catch-up, movies and series.",
                        style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid, modifier = Modifier.widthIn(max = 460.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                    Feature(Icons.Rounded.LiveTv, "Live TV with instant previews and channel zapping")
                    Feature(Icons.Rounded.CalendarViewWeek, "Full-screen TV guide with catch-up")
                    Feature(Icons.Rounded.Movie, "Movies & series with resume and up-next")
                    Feature(Icons.Rounded.VideoLibrary, "Favorites, history and global search")
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding().navigationBarsPadding().imePadding()
                    .padding(horizontal = if (wide) 48.dp else 22.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (!wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!firstRun) { RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { vm.back() }); Spacer(Modifier.width(12.dp)) }
                        BrandMark(44.dp)
                        Spacer(Modifier.width(12.dp))
                        Wordmark(fontSize = 17.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(if (firstRun) "Welcome to ZapBackTV" else if (existing != null) "Edit playlist" else "Add a playlist", style = MaterialTheme.typography.displayMedium, color = Hue.TextHi)
                    Text("Connect your IPTV service to get started.", style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid)
                    Spacer(Modifier.height(22.dp))
                }
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 560.dp)
                        .glass(RoundedCornerShape(26.dp), fill = Hue.Surface1.copy(alpha = 0.72f)).padding(22.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Xtream Codes", type == SourceType.XTREAM, { type = SourceType.XTREAM; error = null }, Modifier.initialFocus(), icon = Icons.Rounded.Dns)
                        Pill("M3U link", type == SourceType.M3U_URL, { type = SourceType.M3U_URL; error = null }, icon = Icons.Rounded.Link)
                        Pill("File", type == SourceType.M3U_FILE, { type = SourceType.M3U_FILE; error = null }, icon = Icons.Rounded.FolderOpen)
                    }
                    Spacer(Modifier.height(20.dp))
                    Field(name, { name = it }, "Playlist name", "e.g. Living room")
                    when (type) {
                        SourceType.XTREAM -> {
                            Field(server, { server = it }, "Server URL", "http://example.com:8080", KeyboardType.Uri)
                            Field(username, { username = it }, "Username", "")
                            Field(
                                password, { password = it }, "Password", "",
                                KeyboardType.Password,
                                visual = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailing = {
                                    IconButton({ showPassword = !showPassword }) {
                                        Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show password", tint = Hue.TextMid)
                                    }
                                },
                            )
                        }
                        SourceType.M3U_URL -> {
                            Field(m3uUrl, { m3uUrl = it }, "Playlist URL", "https://…/playlist.m3u", KeyboardType.Uri)
                            AnimatedVisibility(detected != null) {
                                Row(
                                    Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(14.dp))
                                        .background(accent.soft).padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = accent.main)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "This is an Xtream link. Switch to get movie posters, series seasons and catch-up.",
                                        style = MaterialTheme.typography.bodyMedium, color = Hue.TextHi, modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    LumenButton("Switch", onClick = {
                                        detected?.let { (b, u, p) -> server = b; username = u; password = p; type = SourceType.XTREAM }
                                    })
                                }
                            }
                        }
                        SourceType.M3U_FILE -> {
                            val label = fileUri?.let { Uri.parse(it).lastPathSegment?.substringAfterLast('/') }
                                ?: if (existing?.type == SourceType.M3U_FILE) "Current file (choose to replace)" else "No file selected"
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                                LumenButton("Choose file", onClick = {
                                    try {
                                        picker.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "application/octet-stream", "*/*"))
                                    } catch (e: ActivityNotFoundException) {
                                        error = "No file picker is available on this device. Use an M3U link instead."
                                    }
                                }, icon = Icons.Rounded.FolderOpen, primary = false)
                                Spacer(Modifier.width(14.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = Hue.TextMid, maxLines = 1)
                            }
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(if (showAdvanced) "Hide advanced" else "Advanced: EPG & user agent", showAdvanced, { showAdvanced = !showAdvanced }, icon = Icons.Rounded.Tune)
                    }
                    AnimatedVisibility(showAdvanced) {
                        Column(Modifier.padding(top = 12.dp)) {
                            Field(epgUrl, { epgUrl = it }, "EPG / XMLTV URL (optional)", if (type == SourceType.XTREAM) "Uses the provider's guide by default" else "Uses url-tvg from the playlist by default", KeyboardType.Uri)
                            Field(userAgent, { userAgent = it }, "User agent (optional)", "Some providers require a specific one")
                        }
                    }
                    AnimatedVisibility(error != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                                .background(Hue.Live.copy(alpha = 0.14f)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = Hue.Live)
                            Spacer(Modifier.width(10.dp))
                            Text(error ?: "", style = MaterialTheme.typography.bodyMedium, color = Hue.TextHi)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (busy) {
                            CircularProgressIndicator(color = accent.main, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(if (type == SourceType.XTREAM) "Signing in…" else "Loading playlist…", style = MaterialTheme.typography.bodyLarge, color = Hue.TextMid)
                        } else {
                            LumenButton(if (existing != null) "Save changes" else "Connect", onClick = ::submit)
                            if (!firstRun) {
                                Spacer(Modifier.width(12.dp))
                                LumenButton("Cancel", onClick = { vm.back() }, primary = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "ZapBackTV is a player only and doesn't provide any channels or content.",
                    style = MaterialTheme.typography.bodySmall, color = Hue.TextLo,
                )
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: String) {
    val accent = LocalAccent.current
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.soft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent.main, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Hue.TextHi)
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, color = Hue.TextLo) }) else null,
        singleLine = true,
        visualTransformation = visual,
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent.main,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            focusedLabelColor = accent.main,
            unfocusedLabelColor = Hue.TextLo,
            cursorColor = accent.main,
            focusedTextColor = Hue.TextHi,
            unfocusedTextColor = Hue.TextHi,
            focusedContainerColor = Hue.Surface2,
            unfocusedContainerColor = Hue.Surface2.copy(alpha = 0.6f),
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
}
