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

    fun load(source: Source, force: Boolean = false) {
        loadJob?.cancel(); epgJob?.cancel()
        if (current?.id != source.id) {
            _epg.value = EpgData.EMPTY; _account.value = null
            seriesCache.clear(); vodCache.clear()
        }
        current = source
        loadJob = scope.launch {
            val previous = (_state.value as? LibState.Ready)?.library?.takeIf { it.sourceId == source.id }
            if (previous == null) _state.value = LibState.Loading("Connecting to ${source.name}…")
            try {
                val lib = when (source.type) {
                    SourceType.XTREAM -> loadXtream(source, force)
                    SourceType.M3U_URL, SourceType.M3U_FILE -> loadM3u(source, force)
                }
                if (lib.isEmpty) throw IOException("This playlist doesn't contain any channels or videos.")
                _state.value = LibState.Ready(lib)
                _progress.value = null
                loadEpg(source, lib, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                System.gc()
                failRunning()
                if (previous == null) _state.value = LibState.Error("This playlist is too large for this device's memory.")
            } catch (e: Throwable) {
                // Never let a bad playlist take the whole app down.
                failRunning()
                if (previous == null) _state.value = LibState.Error(friendly(e)) else _progress.value = null
            }
        }
    }

    fun refreshEpg() {
        val s = current ?: return
        val lib = (_state.value as? LibState.Ready)?.library ?: return
        epgJob?.cancel()
        epgJob = scope.launch { loadEpg(s, lib, force = true) }
    }

    // ------------------------------------------------------------------ progress

    private val _progress = MutableStateFlow<LoadProgress?>(null)
    /** Step-by-step progress of the current library load (null when idle). */
    val progress: StateFlow<LoadProgress?> = _progress
    private val _epgProgress = MutableStateFlow<Float?>(null)
    /** 0..1 while the TV guide downloads/parses in the background, else null. */
    val epgProgress: StateFlow<Float?> = _epgProgress

    private fun startProgress(title: String, vararg steps: Pair<String, String>) {
        _progress.value = LoadProgress(title, steps.map { (id, label) -> LoadStep(id, label) })
    }

    private fun step(id: String, transform: (LoadStep) -> LoadStep) {
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

    private suspend fun loadXtream(source: Source, force: Boolean): Library = coroutineScope {
        val client = XtreamClient(source)
        val d = dir(source)
        val format = store.settings.value.liveFormat
        val ua = source.userAgent.ifBlank { null }

        startProgress(
            "Loading ${source.name}",
            "account" to "Signing in", "live" to "Live channels", "movies" to "Movies",
            "series" to "Series", "organize" to "Organizing your library",
        )
        status("Signing in…")
        running("account")
        val accountJson = cached(File(d, "account.json"), force, ttlHours = 1) { client.accountRaw() }
        _account.value = client.parseAccount(accountJson)
        done("account", _account.value?.status)

        status("Loading channels, movies and series…")
        val liveCats = async { cached(File(d, "live_cats.json"), force) { client.raw("get_live_categories") } }
        val vodCats = async { runCatching { cached(File(d, "vod_cats.json"), force) { client.raw("get_vod_categories") } }.getOrDefault("[]") }
        val seriesCats = async { runCatching { cached(File(d, "series_cats.json"), force) { client.raw("get_series_categories") } }.getOrDefault("[]") }

        // Big catalogs are downloaded straight to disk and parsed as a stream,
        // never held in memory as one giant string. Each one reports its own progress.
        val live = async {
            running("live", "Connecting…")
            val f = cachedFile(File(d, "live.json"), force) { Http.download(client.apiUrl("get_live_streams"), ua, it, downloading("live")) }
            client.parseLive(f, format, parsing("live", "channels")).also { done("live", count(it.size, "channels")) }
        }
        val vod = async {
            running("movies", "Connecting…")
            runCatching {
                val f = cachedFile(File(d, "vod.json"), force) { Http.download(client.apiUrl("get_vod_streams"), ua, it, downloading("movies")) }
                client.parseVod(f, parsing("movies", "movies"))
            }.getOrElse { emptyList() }.also { done("movies", count(it.size, "movies")) }
        }
        val series = async {
            running("series", "Connecting…")
            runCatching {
                val f = cachedFile(File(d, "series.json"), force) { Http.download(client.apiUrl("get_series"), ua, it, downloading("series")) }
                client.parseSeries(f, parsing("series", "series"))
            }.getOrElse { emptyList() }.also { done("series", count(it.size, "series")) }
        }

        val streams = mapOf(Kind.LIVE to live.await(), Kind.MOVIE to vod.await(), Kind.SERIES to series.await())
        status("Organizing your library…")
        running("organize")
        val cats = mapOf(
            Kind.LIVE to client.parseCategories(liveCats.await(), Kind.LIVE),
            Kind.MOVIE to client.parseCategories(vodCats.await(), Kind.MOVIE),
            Kind.SERIES to client.parseCategories(seriesCats.await(), Kind.SERIES),
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

    private fun loadM3u(source: Source, force: Boolean): Library {
        startProgress("Loading ${source.name}", "download" to "Playlist", "read" to "Channels & videos", "organize" to "Organizing your library")
        val file = File(dir(source), "playlist.m3u")
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 12 * 3600_000L
        if (force || !fresh) {
            status(if (file.exists()) "Refreshing playlist…" else "Downloading playlist…")
            running("download", "Connecting…")
            try {
                when (source.type) {
                    SourceType.M3U_URL -> Http.download(source.url, source.userAgent.ifBlank { null }, file) { read, total ->
                        running("download", if (total > 0) "${formatBytes(read)} of ${formatBytes(total)}" else formatBytes(read),
                            if (total > 0) read.toFloat() / total else null)
                    }
                    else -> copyUri(Uri.parse(source.url), file)
                }
            } catch (e: Exception) {
                if (!file.exists()) throw e
            }
        }
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
        val live = lib.streams(Kind.LIVE)
        if (live.isEmpty()) return
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
        if (_state.value !is LibState.Ready) _state.value = LibState.Loading(msg)
    }

    private fun cached(file: File, force: Boolean, ttlHours: Int = 12, fetch: () -> String): String {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ttlHours * 3600_000L
        if (!force && fresh) return file.readText()
        return try {
            fetch().also { file.writeText(it) }
        } catch (e: Exception) {
            if (file.exists()) file.readText() else throw e
        }
    }

    private fun cachedFile(file: File, force: Boolean, ttlHours: Int = 12, fetch: (File) -> Unit): File {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < ttlHours * 3600_000L
        if (!force && fresh) return file
        return try {
            fetch(file); file
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
