package com.lumen.tv.data

import java.io.InputStream

/**
 * Streaming M3U / M3U8 (extended) playlist parser.
 *
 * Supports #EXTM3U header attributes (url-tvg / x-tvg-url), #EXTINF attributes
 * (tvg-id, tvg-name, tvg-logo, group-title, tvg-chno, catchup-days, tvg-rec),
 * #EXTGRP and #EXTVLCOPT (user agent / referrer). Entries are classified into
 * live channels, movies and series; episodes named like "Show S01E02" are
 * grouped into series with seasons.
 */
object M3uParser {
    private val attrRegex = Regex("""([A-Za-z0-9_\-]+)\s*=\s*"([^"]*)"""")
    private val episodeRegex = Regex("""^(.*?)[\s._\-|:]*[Ss](\d{1,2})[\s._\-]*[Ee](\d{1,3})\b(.*)$""")
    private val vodExtensions = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm", "mpg", "mpeg", "divx", "3gp")
    private val yearRegex = Regex("""\((\d{4})\)""")

    private class Pending(
        val name: String,
        val attrs: Map<String, String>,
        var group: String?,
        var userAgent: String? = null,
        var referrer: String? = null,
    )

    private class SeriesBuilder(val name: String, val group: String, var image: String?) {
        val episodes = ArrayList<Episode>()
    }

    fun parse(sourceId: String, input: InputStream): Library {
        val live = ArrayList<Stream>()
        val movies = ArrayList<Stream>()
        val series = LinkedHashMap<String, SeriesBuilder>()
        var epgUrl: String? = null
        var pending: Pending? = null
        var index = 0

        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim().removePrefix("﻿")
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        val a = attributes(line)
                        epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: a["tvg-url"])
                            ?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    }
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val a = attributes(line)
                        pending = Pending(name = titleOf(line, a), attrs = a, group = a["group-title"])
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        pending?.let { if (it.group.isNullOrBlank()) it.group = line.substringAfter(':').trim() }
                    }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val opt = line.substringAfter(':')
                        val k = opt.substringBefore('=').trim().lowercase()
                        val v = opt.substringAfter('=', "").trim()
                        when (k) {
                            "http-user-agent" -> pending?.userAgent = v
                            "http-referrer", "http-referer" -> pending?.referrer = v
                        }
                    }
                    line.startsWith("#") -> Unit // #KODIPROP, #EXT-X-*, comments
                    else -> {
                        val p = pending ?: Pending(name = line.substringAfterLast('/'), attrs = emptyMap(), group = null)
                        pending = null
                        index++
                        val url = line
                        val group = p.group?.takeIf { it.isNotBlank() } ?: "Uncategorized"
                        val logo = p.attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        val name = p.name.ifBlank { p.attrs["tvg-name"] ?: "Channel $index" }
                        val id = "m$index"
                        when (classify(url, group, name)) {
                            Kind.LIVE -> live += Stream(
                                id = id, name = name, kind = Kind.LIVE, categoryId = group,
                                image = logo, url = url,
                                epgId = p.attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                                number = p.attrs["tvg-chno"]?.toIntOrNull() ?: 0,
                                catchupDays = (p.attrs["catchup-days"] ?: p.attrs["tvg-rec"])?.toIntOrNull() ?: 0,
                                userAgent = p.userAgent, referrer = p.referrer,
                            )
                            Kind.MOVIE -> {
                                val m = episodeRegex.find(name)
                                if (m != null) addEpisode(series, group, m, url, logo, id) else movies += Stream(
                                    id = id, name = name, kind = Kind.MOVIE, categoryId = group,
                                    image = logo, url = url, year = yearRegex.find(name)?.groupValues?.get(1),
                                    added = (Long.MAX_VALUE - index), // preserve playlist order for "latest"
                                    userAgent = p.userAgent, referrer = p.referrer,
                                )
                            }
                            Kind.SERIES -> {
                                val m = episodeRegex.find(name)
                                if (m != null) addEpisode(series, group, m, url, logo, id) else movies += Stream(
                                    id = id, name = name, kind = Kind.MOVIE, categoryId = group,
                                    image = logo, url = url, userAgent = p.userAgent, referrer = p.referrer,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Number live channels that have no tvg-chno, continuing after the highest explicit number.
        var next = (live.maxOfOrNull { it.number } ?: 0) + 1
        val numberedLive = live.map { if (it.number > 0) it else it.copy(number = next++) }

        val localSeries = LinkedHashMap<String, SeriesInfo>()
        val seriesStreams = series.values.mapIndexed { i, b ->
            val sid = "ms${i + 1}"
            val stream = Stream(
                id = sid, name = b.name, kind = Kind.SERIES, categoryId = b.group, image = b.image,
                added = Long.MAX_VALUE - i,
            )
            val seasons = b.episodes.groupBy { it.season }
                .mapValues { (_, eps) -> eps.sortedBy { it.number } }
                .toSortedMap()
            localSeries[sid] = SeriesInfo(series = stream, seasons = seasons)
            stream
        }

        val streams = mapOf(Kind.LIVE to numberedLive, Kind.MOVIE to movies, Kind.SERIES to seriesStreams)
        return Library(
            sourceId = sourceId,
            categories = streams.mapValues { (kind, list) -> categoriesOf(kind, list) },
            streams = streams,
            epgUrl = epgUrl,
            localSeries = localSeries,
        )
    }

    private fun addEpisode(
        series: MutableMap<String, SeriesBuilder>, group: String, m: MatchResult,
        url: String, logo: String?, id: String,
    ) {
        val show = m.groupValues[1].trim().trimEnd('-', '|', ':', '.').trim().ifBlank { "Unknown series" }
        val season = m.groupValues[2].toIntOrNull() ?: 1
        val ep = m.groupValues[3].toIntOrNull() ?: 0
        val rest = m.groupValues[4].trim().trimStart('-', ':', '|', '.').trim()
        val b = series.getOrPut("$group|${show.lowercase()}") { SeriesBuilder(show, group, logo) }
        if (b.image == null) b.image = logo
        b.episodes += Episode(
            id = id, title = rest.ifBlank { "Episode $ep" }, season = season, number = ep,
            url = url, image = logo,
        )
    }

    fun categoriesOf(kind: Kind, list: List<Stream>): List<Category> {
        val counts = LinkedHashMap<String, Int>()
        list.forEach { counts[it.categoryId] = (counts[it.categoryId] ?: 0) + 1 }
        return counts.map { (g, c) -> Category(id = g, name = g, kind = kind, count = c) }
    }

    private fun classify(url: String, group: String, name: String): Kind {
        val lower = url.lowercase()
        if ("/series/" in lower) return Kind.SERIES
        if ("/movie/" in lower || "/movies/" in lower) return Kind.MOVIE
        val path = lower.substringBefore('?')
        val ext = path.substringAfterLast('.', "")
        val g = group.lowercase()
        if (ext in vodExtensions) {
            return if ("series" in g || "show" in g || episodeRegex.containsMatchIn(name)) Kind.SERIES else Kind.MOVIE
        }
        return Kind.LIVE
    }

    private fun attributes(line: String): Map<String, String> {
        val map = HashMap<String, String>()
        attrRegex.findAll(line).forEach { map[it.groupValues[1].lowercase()] = it.groupValues[2] }
        return map
    }

    /** Title is everything after the comma that follows the attribute list. */
    private fun titleOf(line: String, attrs: Map<String, String>): String {
        val lastQuote = line.lastIndexOf('"')
        val comma = line.indexOf(',', startIndex = if (lastQuote >= 0) lastQuote else 0)
        val t = if (comma >= 0) line.substring(comma + 1).trim() else ""
        return t.ifBlank { attrs["tvg-name"].orEmpty() }
    }
}
