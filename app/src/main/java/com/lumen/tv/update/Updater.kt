package com.lumen.tv.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.lumen.tv.BuildConfig
import com.lumen.tv.data.Http
import com.lumen.tv.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A release published on GitHub, reduced to what the app needs. */
data class Release(
    val version: String,     // "2.1.0" (tag without the leading "v")
    val title: String,
    val notes: String,       // the release description (Markdown)
    val publishedAt: Long,
    val apkUrl: String?,
    val apkSize: Long,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val message: String) : UpdateState
    /** [prompt] = show the changelog dialog. False after "Later", so Settings can still offer it. */
    data class Available(val release: Release, val prompt: Boolean) : UpdateState
    data class Downloading(val release: Release, val fraction: Float?) : UpdateState
    data class Ready(val release: Release, val apk: File, val needsPermission: Boolean) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String, val release: Release?) : UpdateState
}

/**
 * Checks GitHub Releases for a newer version, shows its changelog, and installs the APK
 * attached to the release once the user approves it.
 *
 * Releases are read from https://api.github.com/repos/<owner>/<repo>/releases/latest.
 * A release counts as an update when its tag (e.g. "v2.1.0") is a higher version than the
 * installed one and it has an .apk attached.
 */
class Updater(private val context: Context, private val store: Store) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val currentVersion: String = BuildConfig.VERSION_NAME
    val repo: String = BuildConfig.GITHUB_REPO
    val releasesPage: String get() = "https://github.com/$repo/releases"

    /** Background check on launch, at most every few hours, quietly ignoring errors. */
    fun checkOnLaunch() {
        val s = store.settings.value
        if (!s.autoCheckUpdates || repo.isBlank()) return
        if (System.currentTimeMillis() - s.lastUpdateCheck < 6 * 3600_000L) return
        check(manual = false)
    }

    /** [manual] checks ignore "skip this version" and report errors and "up to date". */
    fun check(manual: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Checking
            val result = try {
                val release = fetchLatest()
                store.updateSettings { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
                when {
                    release == null -> UpdateState.UpToDate("No releases have been published yet.")
                    compareVersions(release.version, currentVersion) <= 0 ->
                        UpdateState.UpToDate("You're on the latest version ($currentVersion).")
                    release.apkUrl == null -> UpdateState.UpToDate("Version ${release.version} is out, but it has no APK attached yet.")
                    !manual && release.version == store.settings.value.skippedVersion -> UpdateState.Available(release, prompt = false)
                    else -> UpdateState.Available(release, prompt = true)
                }
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't check for updates: ${friendly(e)}", null)
            }
            // Background checks stay silent unless there's something to offer.
            _state.value = if (!manual && (result is UpdateState.UpToDate || result is UpdateState.Failed)) UpdateState.Idle else result
        }
    }

    /** "Later": hide the dialog for now; the update stays listed in Settings. */
    fun later() {
        when (val s = _state.value) {
            is UpdateState.Available -> _state.value = s.copy(prompt = false)
            is UpdateState.Ready -> _state.value = UpdateState.Available(s.release, prompt = false)
            is UpdateState.Failed -> _state.value = s.release?.let { UpdateState.Available(it, prompt = false) } ?: UpdateState.Idle
            is UpdateState.UpToDate -> _state.value = UpdateState.Idle
            else -> Unit
        }
    }

    /** "Skip this version": don't offer it again automatically. */
    fun skip(release: Release) {
        store.updateSettings { it.copy(skippedVersion = release.version) }
        _state.value = UpdateState.Idle
    }

    /** Re-opens the dialog for an update that was put off with "Later". */
    fun reopen() {
        val s = _state.value
        if (s is UpdateState.Available) _state.value = s.copy(prompt = true)
    }

    fun download(release: Release) {
        val url = release.apkUrl ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, null)
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val apk = File(dir, "LumenTV-${release.version}.apk")
                Http.download(url, null, apk) { read, total ->
                    val size = if (total > 0) total else release.apkSize
                    _state.value = UpdateState.Downloading(release, if (size > 0) (read.toFloat() / size).coerceIn(0f, 1f) else null)
                }
                verify(apk)
                // Keep the notes so the next launch can show "What's new".
                store.updateSettings { it.copy(whatsNewVersion = release.version, whatsNewNotes = release.notes) }
                _state.value = UpdateState.Ready(release, apk, needsPermission = !canInstall())
                if (canInstall()) install()
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Download failed: ${friendly(e)}", release)
            }
        }
    }

    /** Hands the downloaded APK to Android's installer, which asks the user to confirm. */
    fun install() {
        val s = _state.value as? UpdateState.Ready ?: return
        if (!canInstall()) { _state.value = s.copy(needsPermission = true); return }
        _state.value = UpdateState.Installing(s.release)
        scope.launch {
            try {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                params.setAppPackageName(context.packageName)
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, s.apk.length()).use { out ->
                        s.apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(context, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Couldn't start the installer: ${friendly(e)}", s.release)
            }
        }
    }

    /** Opens the system screen where "install unknown apps" is allowed for Lumen TV. */
    fun openInstallPermission() {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 26) add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        }
        for (i in intents) {
            try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        }
    }

    /** After returning from the permission screen. */
    fun recheckPermission() {
        val s = _state.value
        if (s is UpdateState.Ready && s.needsPermission && canInstall()) _state.value = s.copy(needsPermission = false)
    }

    internal fun onInstallStatus(status: Int, message: String?) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> release?.let { UpdateState.Available(it, prompt = false) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> UpdateState.Failed(
                "This update is signed with a different key than the installed app. Uninstall Lumen TV once, then install " +
                    "the update from the GitHub releases page. Future updates will install normally.", release,
            )
            PackageInstaller.STATUS_FAILURE_STORAGE -> UpdateState.Failed("Not enough storage to install the update.", release)
            else -> UpdateState.Failed("The update wasn't installed${message?.let { ": $it" } ?: "."}", release)
        }
    }

    // ------------------------------------------------------------------ internals

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    private fun fetchLatest(): Release? {
        val json = try {
            Http.getString("https://api.github.com/repos/$repo/releases/latest")
        } catch (e: IOException) {
            if (e.message?.contains("404") == true) return null // no releases yet
            throw e
        }
        val o = JSONObject(json)
        val tag = o.optString("tag_name").trim()
        if (tag.isEmpty()) return null
        val assets = o.optJSONArray("assets")
        var apkUrl: String? = null
        var apkSize = 0L
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = a.optString("browser_download_url").ifBlank { null }
                apkSize = a.optLong("size")
                break
            }
        }
        return Release(
            version = tag.removePrefix("v").removePrefix("V"),
            title = o.optString("name").ifBlank { tag },
            notes = o.optString("body").replace("\r\n", "\n").trim(),
            publishedAt = parseDate(o.optString("published_at")),
            apkUrl = apkUrl,
            apkSize = apkSize,
            pageUrl = o.optString("html_url").ifBlank { releasesPage },
        )
    }

    /** Refuses files that aren't a newer Lumen TV, so a wrong asset can't be installed by mistake. */
    private fun verify(apk: File) {
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: throw IOException("the downloaded file isn't a valid APK")
        if (info.packageName != context.packageName) throw IOException("the downloaded APK is a different app (${info.packageName})")
        val newCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        if (newCode <= BuildConfig.VERSION_CODE) throw IOException("the downloaded APK isn't newer than the installed app")
    }

    private fun parseDate(s: String): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)!!.time
    }.getOrDefault(0L)

    private fun friendly(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "no internet connection"
        is java.net.SocketTimeoutException -> "the connection timed out"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        /** Compares dotted versions numerically: "2.10.0" > "2.9.3". Ignores suffixes like "-beta". */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val x = parts(a); val y = parts(b)
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}

/** Receives the installer's result: asks the user to confirm, or reports a failure. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        (context.applicationContext as? com.lumen.tv.LumenApp)?.updater?.onInstallStatus(
            status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
        )
    }

    companion object { const val ACTION = "com.lumen.tv.action.INSTALL_STATUS" }
}
