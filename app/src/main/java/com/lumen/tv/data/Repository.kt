package com.lumen.tv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class Repository(private val context: Context, private val store: Store) {
    companion object {
        /** How often the provider is asked for a new copy of the playlist. */
        const val CHECK_HOURS = 12
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<LibState>(LibState.Idle)
    val state: StateFlow<LibState> = _state
    private val _epg = MutableStateFlow(EpgData.EMPTY)
    val epg: StateFlow<EpgData> = _epg
    private val _epgStatus = MutableStateFlow<String?>(null)
    val epgStatus: StateFlow<String?> = _epgStatus
    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account

    private var loadJob: Job? = null
    private var epgJob: Job? = null
    var current: Source? = null
        private set

    private val seriesCache = ConcurrentHashMap<String, SeriesInfo>()
    private val vodCache = ConcurrentHashMap<String, VodInfo>()

    private fun dir(s: Source) = File(context.filesDir, "sources/${s.id}").apply { mkdirs() }

    fun clear() {
        loadJob?.cancel(); epgJob?.cancel()
        current = null
        _state.value = LibState.Idle; _epg.value = EpgData.EMPTY; _account.value = null
    }

    /** A short message for the user after a background check (e.g. "Playlist updated"). */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    fun noticeShown() { _notice.value = null }

    /** True while a background check for playlist changes is running. */
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** Background checks don't show the full loading screen. */
    @Volatile private var quiet = false

    /**
     * Opens [source]'s library. The organized library saved last time opens right away; the
     * provider is only asked for a new copy when it's over [CHECK_HOURS] old (or [force], from the
     * Refresh button), and the list is only rebuilt when what comes back is actually different.
     */
    fun load(source: Source, force: Boolean = false) {
        loadJob?.cancel(); epgJob?.cancel()
        if (current?.id != source.id) {
            _epg.value = EpgData.EMPTY; _account.value = null
            seriesCache.clear(); vodCache.clear()
        }
        current = source
        loadJob = scope.launch {
            val d = dir(source)
            val snapFile = File(d, "library.bin")
            val config = configKey(source)
            var shown = (_state.value as? LibState.Ready)?.library?.takeIf { it.sourceId == source.id }
            var shownFp: String? = null

            // 1. The saved library, instantly.
            if (shown == null) {
                val saved = runCatching { Snapshot.read(snapFile, config) }.getOrNull()
                if (saved != null) {
                    shown = saved.library
                    shownFp = saved.fingerprint
                    _state.value = LibState.Ready(saved.library)
                    _progress.value = null
                    startEpg(source, saved.library)
                }
            } else {
                // Already on screen: only the saved copy's fingerprint is needed.
                shownFp = Snapshot.readFingerprint(snapFile, config)
            }
            if (source.type == SourceType.XTREAM) refreshAccount(source, force)

            val inputs = inputFiles(source)
            val fpFile = File(d, "inputs.fp")
            val due = force || !fpFile.exists() || System.currentTimeMillis() - fpFile.lastModified() > CHECK_HOURS * 3600_000L
            if (shown != null && shownFp != null && !due) return@launch // up to date, nothing to do

            // 2. Fetch new copies (quietly when a library is already on screen) and compare.
            // Automatic checks are invisible; the Refresh button still shows the slim progress bar.
            quiet = shown != null && !force
            _syncing.value = shown != null
            try {
                if (due || inputs.any { !it.exists() }) {
                    when (source.type) {
                        SourceType.XTREAM -> downloadXtream(source, force = due)
                        SourceType.M3U_URL, SourceType.M3U_FILE -> downloadM3u(source)
                    }
                }
                val fp = Snapshot.fingerprint(inputs)
                fpFile.writeText(fp); fpFile.setLastModified(System.currentTimeMillis())
                if (shown != null && fp == shownFp) {
                    if (force) _notice.value = "${source.name} is up to date"
                    return@launch
                }

                // 3. Something changed (or nothing was saved yet): rebuild the library.
                val lib = when (source.type) {
                    SourceType.XTREAM -> parseXtream(source)
                    SourceType.M3U_URL, SourceType.M3U_FILE -> parseM3u(source)
                }
                if (lib.isEmpty) throw IOException("This playlist doesn't contain any channels or videos.")
                val before = shown
                _state.value = LibState.Ready(lib)
                _progress.value = null
                runCatching { Snapshot.write(snapFile, lib, fp, config) }
                // Only announce real content changes (not a rebuild after a setting changed).
                if (before != null && shownFp != null) _notice.value = changeSummary(source, before, lib)
                startEpg(source, lib)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                System.gc()
                failRunning()
                if (shown == null) _state.value = LibState.Error("This playlist is too large for this device's memory.")
            } catch (e: Throwable) {
                // Never let a bad playlist take the whole app down; a library on screen stays.
                failRunning()
                if (shown == null) _state.value = LibState.Error(friendly(e))
                else { _progress.value = null; if (force) _notice.value = "Couldn't check ${source.name}: ${friendly(e)}" }
            } finally {
                _syncing.value = false
                quiet = false
            }
        }
    }

    private fun startEpg(source: Source, lib: Library) {
        epgJob?.cancel()
        epgJob = scope.launch { loadEpg(source, lib, force = false) }
    }

    private fun refreshAccount(source: Source, force: Boolean) {
        scope.launch {
            runCatching {
                val client = XtreamClient(source)
                _account.value = client.parseAccount(cached(File(dir(source), "account.json"), force, ttlHours = 1) { client.accountRaw() })
            }
        }
    }

    /** Settings that change how the library is built; a different value means rebuilding it. */
    private fun configKey(source: Source) =
        "${source.type}|${source.url}|${source.username}|${store.settings.value.liveFormat}"

    private fun inputFiles(source: Source): List<File> {
        val d = dir(source)
        return when (source.type) {
            SourceType.XTREAM -> listOf("live_cats.json", "vod_cats.json", "series_cats.json", "live.json", "vod.json", "series.json").map { File(d, it) }
            else -> listOf(File(d, "playlist.m3u"))
        }
    }

    private fun changeSummary(source: Source, old: Library, new: Library): String {
        val parts = listOf(Kind.LIVE to "channels", Kind.MOVIE to "movies", Kind.SERIES to "series").mapNotNull { (k, noun) ->
            val diff = new.streams(k).size - old.streams(k).size
            when {
                diff > 0 -> "$diff new $noun"
                diff < 0 -> "${-diff} fewer $noun"
                else -> null
            }
        }
        return "${source.name} updated" + if (parts.isEmpty()) "" else ": " + parts.joinToString(", ")
    }

    fun refreshEpg(force: Boolean = true) {
        val s = current ?: return
        val lib = (_state.value as? LibState.Ready)?.library ?: return
        epgJob?.cancel()
        epgJob = scope.launch { loadEpg(s, lib, force = force) }
    }

    // ------------------------------------------------------------------ progress

    private val _progress = MutableStateFlow<LoadProgress?>(null)
    /** Step-by-step progress of the current library load (null when idle). */
    val progress: StateFlow<LoadProgress?> = _progress
    private val _epgProgress = MutableStateFlow<Float?>(null)
    /** 0..1 while the TV guide downloads/parses in the background, else null. */
    val epgProgress: StateFlow<Float?> = _epgProgress

    private fun startProgress(title: String, vararg steps: Pair<String, String>) {
        if (quiet) return
        _progress.value = LoadProgress(title, steps.map { (id, label) -> LoadStep(id, label) })
    }

    private fun step(id: String, transform: (LoadStep) -> LoadStep) {
        if (quiet) return
        _progress.update { p -> p?.copy(steps = p.steps.map { if (it.id == id) transform(it) else it }) }
    }

    private fun running(id: String, detail: String? = null, fraction: Float? = null) =
        step(id) { it.copy(state = StepState.RUNNING, detail = detail ?: it.detail, fraction = fraction) }

    private fun done(id: String, detail: String? = null) =
        step(id) { it.copy(state = StepState.DONE, detail = detail ?: it.detail, fraction = 1f) }

    private fun failRunning() {
        _progress.update { p -> p?.copy(steps = p.steps.map { if (it.state == StepState.RUNNING) it.copy(state = StepState.FAILED) else it }) }
    }

    /** Download progress callback: shows MB so far, and a real fraction when the size is known. */
    private fun downloading(id: String): (Long, Long) -> Unit = { read, total ->
        running(
            id,
            detail = if (total > 0) "Downloading ${formatBytes(read)} of ${formatBytes(total)}" else "Downloading ${formatBytes(read)}",
            fraction = if (total > 0) 0.6f * read / total else null,
        )
    }

    private fun parsing(id: String, noun: String): (Float) -> Unit = { f ->
        running(id, detail = "Reading $noun… ${(f * 100).toInt()}%", fraction = 0.6f + 0.4f * f)
    }

    private fun count(n: Int, noun: String) = "${String.format(java.util.Locale.US, "%,d", n)} $noun"

    // ------------------------------------------------------------------ Xtream

    private suspend fun downloadXtream(source: Source, force: Boolean) = coroutineScope {
        val client = XtreamClient(source)
        val d = dir(source)
        val ua = source.userAgent.ifBlank { null }
        startProgress(
            "Loading ${source.name}",
            "live" to "Live channels", "movies" to "Movies", "series" to "Series", "organize" to "Organizing your library",
        )
        status("Loading channels, movies and series…")
        val jobs = listOf(
            async { cached(File(d, "live_cats.json"), force) { client.raw("get_live_categories") } },
            async { runCatching { cached(File(d, "vod_cats.json"), force) { client.raw("get_vod_categories") } } },
            async { runCatching { cached(File(d, "series_cats.json"), force) { client.raw("get_series_categories") } } },
            // Big catalogs go straight to disk, never held in memory as one giant string.
            async {
                running("live", "Connecting…")
                cachedFile(File(d, "live.json"), force) { Http.download(client.apiUrl("get_live_streams"), ua, it, downloading("live")) }
            },
            async {
                running("movies", "Connecting…")
                runCatching { cachedFile(File(d, "vod.json"), force) { Http.download(client.apiUrl("get_vod_streams"), ua, it, downloading("movies")) } }
            },
            async {
                running("series", "Connecting…")
                runCatching { cachedFile(File(d, "series.json"), force) { Http.download(client.apiUrl("get_series"), ua, it, downloading("series")) } }
            },
        )
        jobs.forEach { it.await() }
    }

    private suspend fun parseXtream(source: Source): Library = coroutineScope {
        val client = XtreamClient(source)
        val d = dir(source)
        val format = store.settings.value.liveFormat
        if (_progress.value == null) startProgress(
            "Loading ${source.name}",
            "live" to "Live channels", "movies" to "Movies", "series" to "Series", "organize" to "Organizing your library",
        )
        status("Reading channels, movies and series…")
        fun text(name: String) = File(d, name).takeIf { it.exists() }?.readText() ?: "[]"
        val live = async {
            val f = File(d, "live.json")
            if (!f.exists()) throw IOException("Couldn't download the channel list.")
            client.parseLive(f, format, parsing("live", "channels")).also { done("live", count(it.size, "channels")) }
        }
        val vod = async {
            runCatching { File(d, "vod.json").takeIf { it.exists() }?.let { client.parseVod(it, parsing("movies", "movies")) } }
                .getOrNull().orEmpty().also { done("movies", count(it.size, "movies")) }
        }
        val series = async {
            runCatching { File(d, "series.json").takeIf { it.exists() }?.let { client.parseSeries(it, parsing("series", "series")) } }
                .getOrNull().orEmpty().also { done("series", count(it.size, "series")) }
        }
        val streams = mapOf(Kind.LIVE to live.await(), Kind.MOVIE to vod.await(), Kind.SERIES to series.await())
        status("Organizing your library…")
        running("organize")
        val cats = mapOf(
            Kind.LIVE to client.parseCategories(text("live_cats.json"), Kind.LIVE),
            Kind.MOVIE to runCatching { client.parseCategories(text("vod_cats.json"), Kind.MOVIE) }.getOrDefault(emptyList()),
            Kind.SERIES to runCatching { client.parseCategories(text("series_cats.json"), Kind.SERIES) }.getOrDefault(emptyList()),
        )
        val lib = Library(
            sourceId = source.id,
            categories = cats.mapValues { (kind, list) -> withCounts(kind, list, streams[kind].orEmpty()) },
            streams = streams,
            epgUrl = client.epgUrl(),
        )
        done("organize", count(cats.values.sumOf { it.size }, "categories"))
        lib
    }

    /** Adds stream counts, drops empty categories, and adds a bucket for uncategorized items. */
    private fun withCounts(kind: Kind, cats: List<Category>, streams: List<Stream>): List<Category> {
        val counts = streams.groupingBy { it.categoryId }.eachCount()
        val known = cats.distinctBy { it.id }.map { it.copy(count = counts[it.id] ?: 0) }.filter { it.count > 0 }
        val knownIds = known.map { it.id }.toHashSet()
        val orphans = counts.filterKeys { it !in knownIds }
        return if (orphans.isEmpty()) known else known + orphans.map { (id, c) ->
            Category(id = id, name = if (id == "0") "Uncategorized" else "Other", kind = kind, count = c)
        }
    }

    // ------------------------------------------------------------------ M3U

    private fun downloadM3u(source: Source) {
        startProgress("Loading ${source.name}", "download" to "Playlist", "read" to "Channels & videos", "organize" to "Organizing your library")
        val file = File(dir(source), "playlist.m3u")
        status(if (file.exists()) "Refreshing playlist…" else "Downloading playlist…")
        running("download", "Connecting…")
        // Download beside the old copy, so a failed download never leaves half a playlist.
        val part = File(file.path + ".part")
        try {
            when (source.type) {
                SourceType.M3U_URL -> Http.download(source.url, source.userAgent.ifBlank { null }, part) { read, total ->
                    running("download", if (total > 0) "${formatBytes(read)} of ${formatBytes(total)}" else formatBytes(read),
                        if (total > 0) read.toFloat() / total else null)
                }
                else -> copyUri(Uri.parse(source.url), part)
            }
            if (part.length() > 0) { file.delete(); part.renameTo(file) }
        } catch (e: Exception) {
            part.delete()
            if (!file.exists()) throw e
        }
        done("download", formatBytes(file.length()))
    }

    private fun parseM3u(source: Source): Library {
        val file = File(dir(source), "playlist.m3u")
        if (!file.exists()) throw IOException("The playlist hasn't been downloaded yet.")
        if (_progress.value == null) startProgress("Loading ${source.name}", "download" to "Playlist", "read" to "Channels & videos", "organize" to "Organizing your library")
        done("download", formatBytes(file.length()))
        status("Reading playlist…")
        running("read", "Reading… 0%")
        val lib = ProgressInputStream(file.inputStream(), file.length()) { f ->
            running("read", "Reading… ${(f * 100).toInt()}%", f)
        }.use { M3uParser.parse(source.id, it) }
        done("read", "${count(lib.streams(Kind.LIVE).size, "channels")} · ${count(lib.streams(Kind.MOVIE).size, "movies")} · ${count(lib.streams(Kind.SERIES).size, "series")}")
        done("organize")
        return lib
    }

    /** Copies a user-picked playlist file into app storage (called when the source is added). */
    suspend fun importFile(source: Source, uri: Uri) = withContext(Dispatchers.IO) {
        copyUri(uri, File(dir(source), "playlist.m3u"))
    }

    private fun copyUri(uri: Uri, target: File) {
        if (uri.scheme == "file" || uri.scheme == null) {
            File(uri.path ?: throw IOException("Invalid file")).copyTo(target, overwrite = true); return
        }
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Couldn't open the playlist file")
        input.use { i -> target.outputStream().use { o -> i.copyTo(o) } }
    }

    // ------------------------------------------------------------------ EPG

    private suspend fun loadEpg(source: Source, lib: Library, force: Boolean) {
        val url = source.epgUrl.ifBlank { lib.epgUrl ?: "" }
        if (url.isBlank()) { _epgStatus.value = null; return }
        // Only channels that are actually shown (not hidden categories or other countries):
        // a guide for every channel in a big playlist won't fit in a TV box's memory.
        val live = Catalog.applyRules(lib, store.settings.value.contentRules).streams(Kind.LIVE)
        if (live.isEmpty()) { _epg.value = EpgData.EMPTY; return }
        try {
            val file = File(dir(source), "epg.xml")
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > 12 * 3600_000L
            _epgProgress.value = 0f
            if (force || stale) {
                _epgStatus.value = "Downloading TV guide…"
                try {
                    Http.download(url, source.userAgent.ifBlank { null }, file) { read, total ->
                        _epgStatus.value = "Downloading TV guide… ${formatBytes(read)}"
                        _epgProgress.value = if (total > 0) 0.5f * read / total else 0.05f
                    }
                } catch (e: Exception) {
                    if (!file.exists()) throw e
                }
            }
            _epgStatus.value = "Reading TV guide…"
            _epgProgress.value = 0.5f
            // Let go of the old guide first, so two copies are never in memory at once.
            _epg.value = EpgData.EMPTY
            val ids = live.mapNotNullTo(HashSet()) { it.epgId }
            val names = live.mapTo(HashSet()) { EpgData.normalizeName(it.name) }
            val shift = store.settings.value.epgShiftHours * 3600_000L
            val data = ProgressInputStream(file.inputStream(), file.length()) { f ->
                _epgProgress.value = 0.5f + 0.5f * f
                _epgStatus.value = "Reading TV guide… ${(f * 100).toInt()}%"
            }.use { EpgParser.parse(it, ids, names, shift) }
            _epg.value = data
            _epgStatus.value = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            System.gc()
            _epgStatus.value = "Guide is too large for this device"
        } catch (e: Throwable) {
            _epgStatus.value = "Guide unavailable: ${friendly(e)}"
        } finally {
            _epgProgress.value = null
        }
    }

    // ------------------------------------------------------------------ details

    suspend fun seriesInfo(series: Stream): SeriesInfo = withContext(Dispatchers.IO) {
        val lib = (_state.value as? LibState.Ready)?.library
        lib?.localSeries?.get(series.id)?.let { return@withContext it }
        seriesCache[series.id]?.let { return@withContext it }
        val source = current ?: throw IOException("No playlist selected")
        val client = XtreamClient(source)
        val info = client.parseSeriesInfo(client.raw("get_series_info", "&series_id=${series.id}"), series)
        seriesCache[series.id] = info
        info
    }

    suspend fun vodInfo(movie: Stream): VodInfo? = withContext(Dispatchers.IO) {
        vodCache[movie.id]?.let { return@withContext it }
        val source = current ?: return@withContext null
        if (source.type != SourceType.XTREAM) return@withContext null
        runCatching {
            val client = XtreamClient(source)
            client.parseVodInfo(client.raw("get_vod_info", "&vod_id=${movie.id}")).also { vodCache[movie.id] = it }
        }.getOrNull()
    }

    suspend fun shortEpg(channel: Stream): List<Programme> = withContext(Dispatchers.IO) {
        val source = current ?: return@withContext emptyList()
        if (source.type != SourceType.XTREAM) return@withContext emptyList()
        runCatching {
            val client = XtreamClient(source)
            client.parseShortEpg(client.raw("get_short_epg", "&stream_id=${channel.id}&limit=6"))
        }.getOrDefault(emptyList())
    }

    fun catchupUrl(channel: Stream, p: Programme): String? {
        val source = current ?: return null
        if (channel.catchupDays <= 0) return null
        return if (source.type == SourceType.XTREAM) XtreamClient(source).catchupUrl(channel.id, p.start, p.end) else null
    }

    /** Drops cached responses for one source (used after its credentials change). */
    fun invalidate(sourceId: String) {
        File(context.filesDir, "sources/$sourceId").listFiles()?.forEach { if (it.name != "playlist.m3u") it.delete() }
    }

    fun clearCache() {
        File(context.filesDir, "sources").deleteRecursively()
        seriesCache.clear(); vodCache.clear()
        current?.let { load(it, force = true) }
    }

    /** Validates Xtream credentials before a source is saved. */
    suspend fun testXtream(source: Source): AccountInfo = withContext(Dispatchers.IO) {
        val client = XtreamClient(source)
        client.parseAccount(client.accountRaw())
    }

    // ------------------------------------------------------------------ util

    private fun status(msg: String) {
        if (quiet) return
        if (_state.value !is LibState.Ready) _state.value = LibState.Loading(msg)
    }

    private fun cached(file: File, force: Boolean, ttlHours: Int = 12, fetch: () -> String): String {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ttlHours * 3600_000L
        if (!force && fresh) return file.readText()
        return try {
            fetch().also { if (!file.exists() || file.readText() != it) file.writeText(it) else file.setLastModified(System.currentTimeMillis()) }
        } catch (e: Exception) {
            if (file.exists()) file.readText() else throw e
        }
    }

    private fun cachedFile(file: File, force: Boolean, ttlHours: Int = 12, fetch: (File) -> Unit): File {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ttlHours * 3600_000L
        if (!force && fresh) return file
        return try {
            fetch(file) // Http.download writes beside the old copy and swaps it in only when complete
            file
        } catch (e: Exception) {
            if (file.exists()) file else throw e
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException -> "Can't reach the server. Check the address and your internet connection."
        is java.net.SocketTimeoutException -> "The server took too long to respond."
        is java.net.ConnectException -> "Couldn't connect to the server."
        is javax.net.ssl.SSLException -> "Secure connection failed. Try http:// instead of https://."
        else -> e.message ?: e.javaClass.simpleName
    }
}
