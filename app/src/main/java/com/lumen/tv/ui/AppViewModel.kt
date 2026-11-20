package com.lumen.tv.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumen.tv.LumenApp
import com.lumen.tv.data.Catalog
import com.lumen.tv.data.SearchIndex
import com.lumen.tv.data.SearchResults
import com.lumen.tv.data.CategoryInfo
import com.lumen.tv.data.ContentRules
import com.lumen.tv.data.PickCategory
import com.lumen.tv.data.PickGroup
import com.lumen.tv.data.Episode
import com.lumen.tv.data.Kind
import com.lumen.tv.data.LibState
import com.lumen.tv.data.Library
import com.lumen.tv.data.Repository
import com.lumen.tv.data.SeriesInfo
import com.lumen.tv.data.Source
import com.lumen.tv.data.SourceType
import com.lumen.tv.data.Store
import com.lumen.tv.data.Stream
import com.lumen.tv.data.WatchEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    val route: String

    data object Home : Screen { override val route = "home" }
    data object Live : Screen { override val route = "live" }
    data object Guide : Screen { override val route = "guide" }
    data object Movies : Screen { override val route = "movies" }
    data object Series : Screen { override val route = "series" }
    data object Search : Screen { override val route = "search" }
    data object Favorites : Screen { override val route = "favorites" }
    data object Settings : Screen { override val route = "settings" }
    data class MovieDetail(val movie: Stream) : Screen { override val route = "movie/${movie.id}" }
    data class SeriesDetail(val series: Stream) : Screen { override val route = "series/${series.id}" }
    data object CountrySetup : Screen { override val route = "setup" }
    data object CategorySetup : Screen { override val route = "setup/categories" }
    data class EditSource(val source: Source?, val prefillUrl: String? = null) : Screen { override val route = "source/${source?.id}" }
    data class Player(val request: PlayRequest) : Screen { override val route = "player" }

    val isTopLevel: Boolean get() = this in topLevel

    companion object {
        val topLevel = listOf(Search, Home, Live, Guide, Movies, Series, Favorites, Settings)
    }
}

data class PlayRequest(
    val title: String,
    val subtitle: String? = null,
    val url: String,
    val kind: Kind,
    val streamId: String,
    val watchKey: String,
    val image: String? = null,
    val startMs: Long = 0,
    val userAgent: String? = null,
    val referrer: String? = null,
    // Live: the list we can zap through
    val channels: List<Stream> = emptyList(),
    val channelIndex: Int = -1,
    // Series: the queue for "next episode"
    val series: Stream? = null,
    val episodes: List<Episode> = emptyList(),
    val episodeIndex: Int = -1,
    val isCatchup: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store: Store = (app as LumenApp).store
    val repo = Repository(app, store)
    val updater = (app as LumenApp).updater

    val backStack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = backStack.last()

    var inPip by mutableStateOf(false)

    // ---- search: kept here (not in the screen) so it's still there after watching something
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<Pair<String, SearchResults>?>(null)
    private var searchIndexFor: Library? = null
    private var searchIndex: Map<Kind, SearchIndex>? = null

    /** Builds the search index once per library and reuses it. */
    fun searchIndexFor(lib: Library): Map<Kind, SearchIndex> {
        searchIndex?.let { if (searchIndexFor === lib) return it }
        return SearchIndex.forLibrary(lib).also { searchIndex = it; searchIndexFor = lib }
    }

    /** Back presses arriving right after the player closes are ignored, so a double press
     *  (or a remote that repeats Back) doesn't skip past the screen you came from. */
    var lastPlayerExit = 0L
        private set
    var toast by mutableStateOf<String?>(null)

    val activeSource: StateFlow<Source?> = combine(store.sources, store.activeId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val rules = store.settings.map { it.contentRules }.distinctUntilChanged()

    /**
     * The library every screen uses. Categories the user turned off, adult content and (optionally)
     * other countries are filtered out here once, so live TV, movies, series, home and search all agree.
     */
    val library: StateFlow<Library?> = combine(repo.state, rules) { state, r ->
        (state as? LibState.Ready)?.library?.let { Catalog.applyRules(it, r) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Countries detected in the *unfiltered* playlist, for the setup screen. */
    val countries: StateFlow<List<Pair<com.lumen.tv.data.Region, Int>>?> = repo.state.map { st ->
        (st as? LibState.Ready)?.library?.let { Catalog.countriesIn(it) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Saves the setup choices for the active playlist. [checked] holds the keys of the categories
     * left on, out of everything [offered].
     *  - A category that was switched off is hidden — except other countries' categories while
     *    "only my country" is on, which that rule already filters (so turning the rule off later
     *    brings them back).
     *  - Other-country categories that were switched on are remembered, so the rule keeps them.
     */
    fun finishSetup(region: String, countryOnly: Boolean, offered: List<PickCategory>?, checked: Set<String>?) {
        val sid = activeSource.value?.id
        val only = countryOnly && region.isNotBlank()
        store.updateSettings { st ->
            var hidden = st.hiddenCategories
            var approved = st.approvedForeign
            if (sid != null && offered != null && checked != null) {
                val offeredKeys = HashSet<String>()
                val hide = HashSet<String>()
                val keepForeign = HashSet<String>()
                for (p in offered) {
                    val key = Catalog.categoryKey(sid, p.kind, p.category.id)
                    offeredKeys += key
                    val foreign = p.group == PickGroup.OTHER
                    if (key in checked) { if (foreign) keepForeign += key }
                    else if (!(foreign && only)) hide += key
                }
                hidden = hidden.filterNotTo(HashSet()) { it in offeredKeys } + hide
                approved = approved.filterNotTo(HashSet()) { it.startsWith("$sid|") } + keepForeign
            }
            st.copy(
                preferredRegion = region, countryOnly = only, hiddenCategories = hidden, approvedForeign = approved,
                setupVersion = com.lumen.tv.data.Settings.SETUP_VERSION,
            )
        }
        if (current is Screen.CountrySetup || current is Screen.CategorySetup) back()
    }

    /** Live categories tagged with country + genre, preferred country first. */
    val liveCatalog: StateFlow<List<CategoryInfo>> = combine(
        library, store.settings.map { it.preferredRegion }.distinctUntilChanged(),
    ) { lib, pref ->
        lib?.let { Catalog.classify(it.categories(Kind.LIVE), pref) } ?: emptyList()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // The guide only keeps listings for channels you can see, so re-read it (from the saved
        // file, no download) when that changes.
        viewModelScope.launch { rules.drop(1).collect { repo.refreshEpg(force = false) } }
    }

    fun checkPin(pin: String): Boolean = pin == store.settings.value.pin

    init {
        updater.checkOnLaunch()
        viewModelScope.launch {
            activeSource.distinctUntilChanged { a, b -> a?.id == b?.id }.collect { s ->
                if (s != null) repo.load(s) else repo.clear()
            }
        }
        viewModelScope.launch {
            // Stream format is part of every channel's address, so the library is rebuilt (from the
            // saved files, no download). Time shift only affects the guide.
            store.settings.map { it.liveFormat }.distinctUntilChanged().drop(1).collect {
                activeSource.value?.let { s -> if (repo.current?.id == s.id) repo.load(s) }
            }
        }
        viewModelScope.launch {
            store.settings.map { it.epgShiftHours }.distinctUntilChanged().drop(1).collect { repo.refreshEpg(force = false) }
        }
        viewModelScope.launch {
            repo.notice.collect { msg -> if (msg != null) { toast = msg; repo.noticeShown() } }
        }
    }

    // ------------------------------------------------------------ navigation

    fun navigate(screen: Screen) {
        if (screen.isTopLevel) {
            backStack.clear()
            if (screen != Screen.Home) backStack.add(Screen.Home)
            backStack.add(screen)
        } else backStack.add(screen)
    }

    /** @return false when there's nothing left to pop (the activity should finish). */
    fun back(): Boolean {
        if (current is Screen.Player) lastPlayerExit = android.os.SystemClock.uptimeMillis()
        if (backStack.size > 1) { backStack.removeAt(backStack.lastIndex); return true }
        return false
    }

    fun replaceTop(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }

    // ------------------------------------------------------------ sources

    fun saveSource(source: Source, fileUri: Uri? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            val isEdit = store.sources.value.any { it.id == source.id }
            if (fileUri != null) runCatching { repo.importFile(source, fileUri) }.onFailure {
                toast = "Couldn't read that file: ${it.message}"; return@launch
            }
            if (isEdit) repo.invalidate(source.id)
            store.upsertSource(source)
            store.setActive(source.id)
            repo.load(source, force = true)
            onDone()
        }
    }

    // ------------------------------------------------------------ background picture

    /** Copies a picked photo into the app's own storage (the picker's permission doesn't last) and uses it. */
    fun setBackgroundPhoto(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(ctx.filesDir, "backgrounds").apply { mkdirs() }
                    // A new name each time, so the image cache never shows the old picture.
                    val file = java.io.File(dir, "bg-${System.currentTimeMillis()}.img")
                    ctx.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
                    file.absolutePath
                }.getOrNull()
            }
            if (path == null) toast = "Couldn't open that picture" else {
                store.updateSettings { it.copy(background = "file:$path") }
                toast = "Background updated"
            }
        }
    }

    fun setBackground(value: String) {
        store.updateSettings { it.copy(background = value) }
        if (!value.startsWith("file:")) viewModelScope.launch(Dispatchers.IO) {
            java.io.File(getApplication<Application>().filesDir, "backgrounds").listFiles()?.forEach { it.delete() }
        }
    }

    fun switchSource(source: Source) {
        if (activeSource.value?.id != source.id) store.setActive(source.id)
    }

    fun deleteSource(source: Source) = store.deleteSource(source.id)

    fun refresh() { activeSource.value?.let { repo.load(it, force = true) } }

    /** Long-press "hide this category" — reversible under Settings › Categories. */
    fun hideCategory(kind: Kind, categoryId: String, name: String) {
        val sid = activeSource.value?.id ?: return
        if (categoryId.startsWith("__")) return
        store.updateSettings { it.copy(hiddenCategories = it.hiddenCategories + "$sid|${kind.name}|$categoryId") }
        toast = "Hid “$name”. Undo in Settings › Categories."
    }

    // ------------------------------------------------------------ favorites

    fun isFavorite(stream: Stream): Boolean =
        activeSource.value?.let { store.isFavorite(it.id, stream.key) } ?: false

    fun toggleFavorite(stream: Stream) {
        val s = activeSource.value ?: return
        val added = store.toggleFavorite(s.id, stream.key)
        toast = if (added) "Added “${stream.name}” to favorites" else "Removed from favorites"
    }

    fun favoritesOf(kind: Kind, lib: Library, favs: Set<String>): List<Stream> {
        val sid = lib.sourceId
        return favs.asSequence().filter { it.startsWith("$sid|${kind.name}:") }
            .mapNotNull { lib.find(it.substringAfter('|')) }.toList()
    }

    // ------------------------------------------------------------ playback

    fun playChannel(channel: Stream, list: List<Stream>) {
        val idx = list.indexOfFirst { it.key == channel.key }
        navigate(Screen.Player(liveRequest(channel, if (idx >= 0) list else listOf(channel), maxOf(idx, 0))))
    }

    fun liveRequest(channel: Stream, list: List<Stream>, index: Int) = PlayRequest(
        title = channel.name, url = channel.url, kind = Kind.LIVE, streamId = channel.id,
        watchKey = channel.key, image = channel.image, userAgent = channel.userAgent ?: sourceUa(),
        referrer = channel.referrer, channels = list, channelIndex = index,
    )

    fun playMovie(movie: Stream, fromStart: Boolean = false) {
        val sid = activeSource.value?.id ?: return
        val pos: Long = if (fromStart) 0L else store.position(sid, movie.key)?.first ?: 0L
        navigate(
            Screen.Player(
                PlayRequest(
                    title = movie.name, subtitle = movie.year, url = movie.url, kind = Kind.MOVIE,
                    streamId = movie.id, watchKey = movie.key, image = movie.image, startMs = pos,
                    userAgent = movie.userAgent ?: sourceUa(), referrer = movie.referrer,
                )
            )
        )
    }

    fun playEpisode(info: SeriesInfo, episode: Episode, fromStart: Boolean = false) {
        val sid = activeSource.value?.id ?: return
        val all = info.allEpisodes
        val pos: Long = if (fromStart) 0L else store.position(sid, episode.watchKey)?.first ?: 0L
        navigate(Screen.Player(episodeRequest(info.series, all, all.indexOfFirst { it.id == episode.id }.coerceAtLeast(0), pos)))
    }

    fun episodeRequest(series: Stream, episodes: List<Episode>, index: Int, startMs: Long = 0): PlayRequest {
        val ep = episodes[index]
        return PlayRequest(
            title = series.name, subtitle = "${ep.label} · ${ep.title}", url = ep.url, kind = Kind.SERIES,
            streamId = ep.id, watchKey = ep.watchKey, image = ep.image ?: series.image, startMs = startMs,
            userAgent = series.userAgent ?: sourceUa(), series = series, episodes = episodes, episodeIndex = index,
        )
    }

    fun resume(entry: WatchEntry) {
        val lib = library.value
        when (entry.kind) {
            Kind.LIVE -> {
                val ch = lib?.find(entry.key) ?: return
                playChannel(ch, lib.inCategory(Kind.LIVE, ch.categoryId))
            }
            Kind.MOVIE -> lib?.find(entry.key)?.let { playMovie(it) } ?: navigate(
                Screen.Player(PlayRequest(entry.title, entry.subtitle, entry.url, Kind.MOVIE, entry.streamId, entry.key, entry.image, entry.position, sourceUa()))
            )
            Kind.SERIES -> {
                val series = entry.seriesKey?.let { lib?.find(it) }
                viewModelScope.launch {
                    val info = series?.let { runCatching { repo.seriesInfo(it) }.getOrNull() }
                    val idx = info?.allEpisodes?.indexOfFirst { it.watchKey == entry.key } ?: -1
                    if (info != null && idx >= 0) {
                        navigate(Screen.Player(episodeRequest(info.series, info.allEpisodes, idx, entry.position)))
                    } else navigate(
                        Screen.Player(PlayRequest(entry.title, entry.subtitle, entry.url, Kind.SERIES, entry.streamId, entry.key, entry.image, entry.position, sourceUa()))
                    )
                }
            }
        }
    }

    fun recordWatch(req: PlayRequest, position: Long, duration: Long) {
        val s = activeSource.value ?: return
        if (req.isCatchup) return
        if (req.kind != Kind.LIVE) store.savePosition(s.id, req.watchKey, position, duration)
        store.record(
            WatchEntry(
                key = req.watchKey, sourceId = s.id, kind = req.kind, streamId = req.streamId,
                title = req.title, subtitle = req.subtitle, image = req.image, url = req.url,
                position = position, duration = duration, updated = System.currentTimeMillis(),
                seriesKey = req.series?.key,
            )
        )
    }

    fun positionOf(key: String): Pair<Long, Long>? = activeSource.value?.let { store.position(it.id, key) }

    private fun sourceUa(): String? =
        activeSource.value?.userAgent?.ifBlank { null } ?: store.settings.value.defaultUserAgent.ifBlank { null }

    companion object {
        fun sourceLabel(t: SourceType) = when (t) {
            SourceType.XTREAM -> "Xtream Codes"
            SourceType.M3U_URL -> "M3U playlist"
            SourceType.M3U_FILE -> "M3U file"
        }
    }
}
