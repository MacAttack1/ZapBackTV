package com.lumen.tv.data

import java.util.UUID

enum class SourceType { XTREAM, M3U_URL, M3U_FILE }

enum class Kind { LIVE, MOVIE, SERIES }

data class Source(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val userAgent: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

data class Category(
    val id: String,
    val name: String,
    val kind: Kind,
    val count: Int = 0,
)

/** A playable (or browsable, for series) item from any source. */
data class Stream(
    val id: String,
    val name: String,
    val kind: Kind,
    val categoryId: String,
    val image: String? = null,
    val url: String = "",
    val epgId: String? = null,
    val number: Int = 0,
    val rating: Double? = null,
    val year: String? = null,
    val added: Long = 0,
    val plot: String? = null,
    val catchupDays: Int = 0,
    val ext: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val adult: Boolean = false,
) {
    val key: String get() = "${kind.name}:$id"

    /** Unique within any list, even when a provider files one stream under several categories. */
    val listKey: String get() = "$key|$categoryId"
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val image: String? = null,
    val plot: String? = null,
    val durationSecs: Int = 0,
    val rating: Double? = null,
) {
    val watchKey: String get() = "EP:$id"
    val label: String get() = "S${season} · E${number}"
}

data class SeriesInfo(
    val series: Stream,
    val plot: String? = null,
    val cast: String? = null,
    val genre: String? = null,
    val director: String? = null,
    val backdrop: String? = null,
    val releaseDate: String? = null,
    val seasons: Map<Int, List<Episode>> = emptyMap(),
) {
    val allEpisodes: List<Episode> by lazy { seasons.toSortedMap().values.flatten() }
}

data class VodInfo(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val durationSecs: Int = 0,
    val backdrop: String? = null,
    val releaseDate: String? = null,
    val rating: Double? = null,
    val trailer: String? = null,
)

data class Programme(
    val start: Long,
    val end: Long,
    val title: String,
    val desc: String? = null,
) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in start until end
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

data class AccountInfo(
    val status: String,
    val expiresAt: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean,
    val createdAt: Long?,
    val serverTimezone: String? = null,
)

class Library(
    val sourceId: String,
    val categories: Map<Kind, List<Category>>,
    val streams: Map<Kind, List<Stream>>,
    val epgUrl: String? = null,
    val localSeries: Map<String, SeriesInfo> = emptyMap(),
) {
    private val byCategory: Map<Kind, Map<String, List<Stream>>> =
        streams.mapValues { (_, list) -> list.groupBy { it.categoryId } }
    private val byKey: Map<String, Stream> by lazy {
        HashMap<String, Stream>().apply { streams.values.forEach { l -> l.forEach { put(it.key, it) } } }
    }

    fun streams(kind: Kind): List<Stream> = streams[kind].orEmpty()
    fun categories(kind: Kind): List<Category> = categories[kind].orEmpty()
    fun inCategory(kind: Kind, categoryId: String?): List<Stream> =
        if (categoryId == null || categoryId == ALL) streams(kind)
        else byCategory[kind]?.get(categoryId).orEmpty()

    fun find(key: String): Stream? = byKey[key]
    val isEmpty: Boolean get() = streams.values.all { it.isEmpty() }

    companion object {
        const val ALL = "__all__"
        const val FAVORITES = "__fav__"
        const val RECENT = "__recent__"
    }
}

sealed interface LibState {
    data object Idle : LibState
    data class Loading(val message: String) : LibState
    data class Ready(val library: Library) : LibState
    data class Error(val message: String) : LibState
}

// ---------------------------------------------------------------- load progress

enum class StepState { WAITING, RUNNING, DONE, FAILED }

data class LoadStep(
    val id: String,
    val label: String,
    val state: StepState = StepState.WAITING,
    val detail: String? = null,
    val fraction: Float? = null,
)

data class LoadProgress(val title: String, val steps: List<LoadStep>) {
    /** 0..1 across all steps; running steps contribute their own fraction when known. */
    val overall: Float
        get() = if (steps.isEmpty()) 0f else steps.sumOf {
            when (it.state) {
                StepState.DONE, StepState.FAILED -> 1.0
                StepState.RUNNING -> (it.fraction ?: 0.15f).toDouble()
                StepState.WAITING -> 0.0
            }
        }.toFloat() / steps.size
    val current: LoadStep? get() = steps.firstOrNull { it.state == StepState.RUNNING }
}
