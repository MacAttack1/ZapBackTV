package com.lumen.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val liveFormat: String = "ts",          // "ts" or "m3u8" for Xtream live streams
    val bufferSeconds: Int = 15,
    val autoplayNext: Boolean = true,
    val aspect: Int = 0,                    // 0 fit, 1 zoom/crop, 2 stretch
    val accent: Int = 0,
    val epgShiftHours: Int = 0,
    val showNumbers: Boolean = true,
    val previewOnFocus: Boolean = true,
    val pin: String = "",
    val hiddenCategories: Set<String> = emptySet(), // "$sourceId|KIND|$categoryId"
    val defaultUserAgent: String = "",
    val reduceMotion: Boolean = false,
    val preferredRegion: String = "US",
    val countryOnly: Boolean = false,
    val setupVersion: Int = 0,                      // bumped when setup gains a step, so it runs again
    val showAdult: Boolean = false,                 // adult categories stay hidden unless this is on
    val approvedForeign: Set<String> = emptySet(),  // other-country categories kept in setup ("$sourceId|KIND|$categoryId")
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val skippedVersion: String = "",
    val whatsNewVersion: String = "",               // notes of a downloaded update, shown once after it installs
    val whatsNewNotes: String = "",
) {
    val setupDone: Boolean get() = setupVersion >= SETUP_VERSION

    fun toJson() = JSONObject().apply {
        put("liveFormat", liveFormat); put("bufferSeconds", bufferSeconds); put("autoplayNext", autoplayNext)
        put("aspect", aspect); put("accent", accent); put("epgShiftHours", epgShiftHours)
        put("showNumbers", showNumbers); put("previewOnFocus", previewOnFocus); put("pin", pin)
        put("hiddenCategories", JSONArray(hiddenCategories.toList())); put("defaultUserAgent", defaultUserAgent)
        put("reduceMotion", reduceMotion)
        put("preferredRegion", preferredRegion)
        put("countryOnly", countryOnly)
        put("setupVersion", setupVersion)
        put("showAdult", showAdult)
        put("approvedForeign", JSONArray(approvedForeign.toList()))
        put("autoCheckUpdates", autoCheckUpdates); put("lastUpdateCheck", lastUpdateCheck); put("skippedVersion", skippedVersion)
        put("whatsNewVersion", whatsNewVersion); put("whatsNewNotes", whatsNewNotes)
    }

    companion object {
        const val SETUP_VERSION = 2

        fun fromJson(o: JSONObject) = Settings(
            liveFormat = o.optString("liveFormat", "ts"),
            bufferSeconds = o.optInt("bufferSeconds", 15),
            autoplayNext = o.optBoolean("autoplayNext", true),
            aspect = o.optInt("aspect", 0),
            accent = o.optInt("accent", 0),
            epgShiftHours = o.optInt("epgShiftHours", 0),
            showNumbers = o.optBoolean("showNumbers", true),
            previewOnFocus = o.optBoolean("previewOnFocus", true),
            pin = o.optString("pin", ""),
            hiddenCategories = o.optJSONArray("hiddenCategories")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            defaultUserAgent = o.optString("defaultUserAgent", ""),
            reduceMotion = o.optBoolean("reduceMotion", false),
            preferredRegion = o.optString("preferredRegion", "US"),
            countryOnly = o.optBoolean("countryOnly", false),
            // Builds before the category step saved "setupDone": treat that as step one finished.
            setupVersion = o.optInt("setupVersion", if (o.optBoolean("setupDone", false)) 1 else 0),
            showAdult = o.optBoolean("showAdult", false),
            approvedForeign = o.optJSONArray("approvedForeign")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            autoCheckUpdates = o.optBoolean("autoCheckUpdates", true),
            lastUpdateCheck = o.optLong("lastUpdateCheck", 0),
            skippedVersion = o.optString("skippedVersion", ""),
            whatsNewVersion = o.optString("whatsNewVersion", ""),
            whatsNewNotes = o.optString("whatsNewNotes", ""),
        )
    }
}

/** A watch-history entry: drives "Continue watching", recent channels and resume points. */
data class WatchEntry(
    val key: String,          // Stream.key for live/movies, Episode.watchKey for episodes
    val sourceId: String,
    val kind: Kind,
    val streamId: String,
    val title: String,
    val subtitle: String?,
    val image: String?,
    val url: String,
    val position: Long,
    val duration: Long,
    val updated: Long,
    val seriesKey: String? = null,
) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val isResumable: Boolean get() = kind != Kind.LIVE && position > 30_000 && (duration <= 0 || fraction < 0.94f)

    fun toJson() = JSONObject().apply {
        put("key", key); put("sourceId", sourceId); put("kind", kind.name); put("streamId", streamId)
        put("title", title); put("subtitle", subtitle ?: ""); put("image", image ?: ""); put("url", url)
        put("position", position); put("duration", duration); put("updated", updated); put("seriesKey", seriesKey ?: "")
    }

    companion object {
        fun fromJson(o: JSONObject) = WatchEntry(
            key = o.getString("key"), sourceId = o.getString("sourceId"), kind = Kind.valueOf(o.getString("kind")),
            streamId = o.optString("streamId"), title = o.optString("title"),
            subtitle = o.optString("subtitle").ifBlank { null }, image = o.optString("image").ifBlank { null },
            url = o.optString("url"), position = o.optLong("position"), duration = o.optLong("duration"),
            updated = o.optLong("updated"), seriesKey = o.optString("seriesKey").ifBlank { null },
        )
    }
}

class Store(context: Context) {
    // The file keeps its original name so playlists and settings survive the app's rename.
    private val sp = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    private val _sources = MutableStateFlow(readSources())
    val sources: StateFlow<List<Source>> = _sources
    private val _activeId = MutableStateFlow(sp.getString("active", null))
    val activeId: StateFlow<String?> = _activeId
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings
    private val _favorites = MutableStateFlow(sp.getStringSet("favorites", emptySet())!!.toSet())
    val favorites: StateFlow<Set<String>> = _favorites
    private val _history = MutableStateFlow(readHistory())
    private val _searches = MutableStateFlow(readSearches())
    /** Recent searches, newest first. */
    val searches: StateFlow<List<String>> = _searches
    val history: StateFlow<List<WatchEntry>> = _history

    // ------------------------------------------------------------ sources
    fun upsertSource(s: Source) {
        val list = _sources.value.toMutableList()
        val i = list.indexOfFirst { it.id == s.id }
        if (i >= 0) list[i] = s else list += s
        _sources.value = list
        writeSources()
    }

    fun deleteSource(id: String) {
        _sources.value = _sources.value.filterNot { it.id == id }
        writeSources()
        if (_activeId.value == id) setActive(_sources.value.firstOrNull()?.id)
        _favorites.value = _favorites.value.filterNot { it.startsWith("$id|") }.toSet()
        sp.edit().putStringSet("favorites", _favorites.value).apply()
        _history.value = _history.value.filterNot { it.sourceId == id }
        writeHistory()
        updateSettings { st ->
            st.copy(
                hiddenCategories = st.hiddenCategories.filterNotTo(HashSet()) { it.startsWith("$id|") },
                approvedForeign = st.approvedForeign.filterNotTo(HashSet()) { it.startsWith("$id|") },
            )
        }
    }

    fun setActive(id: String?) {
        _activeId.value = id
        sp.edit().putString("active", id).apply()
    }

    // ------------------------------------------------------------ settings
    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.value = transform(_settings.value)
        sp.edit().putString("settings", _settings.value.toJson().toString()).apply()
    }

    // ------------------------------------------------------------ favorites
    fun favKey(sourceId: String, streamKey: String) = "$sourceId|$streamKey"
    fun isFavorite(sourceId: String, streamKey: String) = favKey(sourceId, streamKey) in _favorites.value
    fun toggleFavorite(sourceId: String, streamKey: String): Boolean {
        val k = favKey(sourceId, streamKey)
        val now = k !in _favorites.value
        _favorites.value = if (now) _favorites.value + k else _favorites.value - k
        sp.edit().putStringSet("favorites", _favorites.value).apply()
        return now
    }

    // ------------------------------------------------------------ history
    fun record(entry: WatchEntry) {
        val rest = _history.value.filterNot { it.key == entry.key && it.sourceId == entry.sourceId }
        // For series keep only the latest episode per show in the list.
        val dedup = if (entry.seriesKey != null) rest.filterNot { it.seriesKey == entry.seriesKey && it.sourceId == entry.sourceId } else rest
        _history.value = (listOf(entry) + dedup).take(150)
        writeHistory()
    }

    /** Resume positions are kept per episode even after the series row moves on. */
    fun savePosition(sourceId: String, key: String, position: Long, duration: Long) {
        sp.edit().putString("pos|$sourceId|$key", "$position/$duration").apply()
    }

    fun position(sourceId: String, key: String): Pair<Long, Long>? =
        sp.getString("pos|$sourceId|$key", null)?.split('/')?.let {
            val p = it.getOrNull(0)?.toLongOrNull() ?: return null
            p to (it.getOrNull(1)?.toLongOrNull() ?: 0L)
        }

    fun removeHistory(entry: WatchEntry) {
        _history.value = _history.value.filterNot { it.key == entry.key && it.sourceId == entry.sourceId }
        writeHistory()
    }

    // ------------------------------------------------------------ search history
    fun addSearch(query: String) {
        val q = query.trim().replace(Regex("\\s+"), " ")
        if (q.length < 2) return
        _searches.value = (listOf(q) + _searches.value.filterNot { it.equals(q, ignoreCase = true) }).take(15)
        writeSearches()
    }

    fun removeSearch(query: String) {
        _searches.value = _searches.value.filterNot { it == query }
        writeSearches()
    }

    fun clearSearches() {
        _searches.value = emptyList()
        writeSearches()
    }

    private fun readSearches(): List<String> = runCatching {
        val a = JSONArray(sp.getString("searchHistory", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    private fun writeSearches() {
        sp.edit().putString("searchHistory", JSONArray(_searches.value).toString()).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        writeHistory()
        val e = sp.edit()
        sp.all.keys.filter { it.startsWith("pos|") }.forEach { e.remove(it) }
        e.apply()
    }

    // ------------------------------------------------------------ persistence
    private fun readSources(): List<Source> = runCatching {
        val a = JSONArray(sp.getString("sources", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Source(
                id = o.getString("id"), name = o.getString("name"), type = SourceType.valueOf(o.getString("type")),
                url = o.optString("url"), username = o.optString("username"), password = o.optString("password"),
                epgUrl = o.optString("epgUrl"), userAgent = o.optString("userAgent"), addedAt = o.optLong("addedAt"),
            )
        }
    }.getOrDefault(emptyList())

    private fun writeSources() {
        val a = JSONArray()
        _sources.value.forEach { s ->
            a.put(JSONObject().apply {
                put("id", s.id); put("name", s.name); put("type", s.type.name); put("url", s.url)
                put("username", s.username); put("password", s.password); put("epgUrl", s.epgUrl)
                put("userAgent", s.userAgent); put("addedAt", s.addedAt)
            })
        }
        sp.edit().putString("sources", a.toString()).apply()
    }

    private fun readSettings(): Settings =
        runCatching { Settings.fromJson(JSONObject(sp.getString("settings", "{}")!!)) }.getOrDefault(Settings())

    private fun readHistory(): List<WatchEntry> = runCatching {
        val a = JSONArray(sp.getString("history", "[]"))
        (0 until a.length()).mapNotNull { runCatching { WatchEntry.fromJson(a.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun writeHistory() {
        val a = JSONArray(); _history.value.forEach { a.put(it.toJson()) }
        sp.edit().putString("history", a.toString()).apply()
    }
}
