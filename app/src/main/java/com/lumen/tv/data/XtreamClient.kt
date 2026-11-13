package com.lumen.tv.data

import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Xtream Codes "player_api.php" client. All calls are blocking; run them on Dispatchers.IO. */
class XtreamClient(private val source: Source) {
    val base: String = normalizeBase(source.url)
    private val user = source.username.trim()
    private val pass = source.password.trim()
    private val ua = source.userAgent.ifBlank { null }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun pathEnc(s: String) = enc(s).replace("+", "%20")

    private fun api(action: String? = null, extra: String = ""): String = buildString {
        append(base).append("/player_api.php?username=").append(enc(user))
        append("&password=").append(enc(pass))
        if (action != null) append("&action=").append(action)
        append(extra)
    }

    fun accountRaw(): String = Http.getString(api(), ua)
    fun raw(action: String, extra: String = ""): String = Http.getString(api(action, extra), ua)

    fun liveUrl(streamId: String, format: String) = "$base/live/${pathEnc(user)}/${pathEnc(pass)}/$streamId.$format"
    fun movieUrl(streamId: String, ext: String?) = "$base/movie/${pathEnc(user)}/${pathEnc(pass)}/$streamId.${ext ?: "mp4"}"
    fun episodeUrl(episodeId: String, ext: String?) = "$base/series/${pathEnc(user)}/${pathEnc(pass)}/$episodeId.${ext ?: "mp4"}"
    fun epgUrl() = "$base/xmltv.php?username=${enc(user)}&password=${enc(pass)}"

    /** Catch-up / timeshift URL for a programme that has already aired. */
    fun catchupUrl(streamId: String, start: Long, end: Long): String {
        val minutes = ((end - start) / 60_000L).coerceAtLeast(1)
        val ts = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(start))
        return "$base/timeshift/${pathEnc(user)}/${pathEnc(pass)}/$minutes/$ts/$streamId.ts"
    }

    // ---------------------------------------------------------------- parsing

    fun parseAccount(json: String): AccountInfo {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: throw IllegalStateException("The server did not return a valid Xtream response. Check the server URL.")
        val u = root.optJSONObject("user_info")
            ?: throw IllegalStateException("The server did not return account details. Check the server URL.")
        if (u.optInt("auth", 1) == 0 || u.str("status")?.equals("Disabled", true) == true) {
            throw IllegalStateException("Login failed — the username or password was rejected.")
        }
        val server = root.optJSONObject("server_info")
        return AccountInfo(
            status = u.str("status") ?: "Active",
            expiresAt = u.str("exp_date")?.toLongOrNull()?.times(1000),
            maxConnections = u.str("max_connections")?.toIntOrNull(),
            activeConnections = u.str("active_cons")?.toIntOrNull(),
            isTrial = u.str("is_trial") == "1",
            createdAt = u.str("created_at")?.toLongOrNull()?.times(1000),
            serverTimezone = server?.str("timezone"),
        )
    }

    fun parseCategories(json: String, kind: Kind): List<Category> {
        val arr = json.asArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("category_id") ?: return@mapNotNull null
            Category(id = id, name = o.str("category_name") ?: "Category $id", kind = kind)
        }.distinctBy { it.id }
    }

    fun apiUrl(action: String): String = api(action)

    /**
     * Streams a JSON array of objects from disk one object at a time. Values are
     * flattened to strings; nested arrays/objects are skipped. Non-array
     * responses (some panels return {} on error) yield nothing.
     */
    private fun readObjects(file: File, onFraction: ((Float) -> Unit)?, each: (Map<String, String>) -> Unit) {
        val input = file.inputStream().let { if (onFraction != null) ProgressInputStream(it, file.length(), onFraction) else it }
        JsonReader(input.bufferedReader(Charsets.UTF_8)).use { r ->
            r.isLenient = true
            if (r.peek() != JsonToken.BEGIN_ARRAY) return
            r.beginArray()
            while (r.hasNext()) {
                if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                val m = HashMap<String, String>(32)
                r.beginObject()
                while (r.hasNext()) {
                    val name = r.nextName()
                    when (r.peek()) {
                        JsonToken.STRING, JsonToken.NUMBER -> {
                            val v = r.nextString().trim()
                            if (v.isNotEmpty() && v != "null") m[name] = v
                        }
                        JsonToken.BOOLEAN -> m[name] = r.nextBoolean().toString()
                        JsonToken.NULL -> r.nextNull()
                        else -> r.skipValue()
                    }
                }
                r.endObject()
                each(m)
            }
            r.endArray()
        }
    }

    fun parseLive(file: File, format: String, onFraction: ((Float) -> Unit)? = null): List<Stream> {
        val out = ArrayList<Stream>()
        val seen = HashSet<String>()
        var i = 0
        readObjects(file, onFraction) { o ->
            i++
            val id = o["stream_id"] ?: return@readObjects
            val cat = o["category_id"] ?: "0"
            if (!seen.add("$id|$cat")) return@readObjects // panels often list duplicates
            out += Stream(
                id = id,
                name = o["name"] ?: "Channel $id",
                kind = Kind.LIVE,
                categoryId = cat,
                image = o["stream_icon"],
                url = liveUrl(id, format),
                epgId = o["epg_channel_id"],
                number = o["num"]?.toIntOrNull() ?: i,
                added = o["added"]?.toLongOrNull()?.times(1000) ?: 0,
                catchupDays = if (o["tv_archive"] == "1") o["tv_archive_duration"]?.toIntOrNull() ?: 0 else 0,
                adult = o["is_adult"] == "1",
            )
        }
        return out
    }

    fun parseVod(file: File, onFraction: ((Float) -> Unit)? = null): List<Stream> {
        val out = ArrayList<Stream>()
        val seen = HashSet<String>()
        val year = Regex("""\((\d{4})\)""")
        readObjects(file, onFraction) { o ->
            val id = o["stream_id"] ?: return@readObjects
            val cat = o["category_id"] ?: "0"
            if (!seen.add("$id|$cat")) return@readObjects
            val ext = o["container_extension"]
            val name = o["name"] ?: "Movie $id"
            out += Stream(
                id = id,
                name = name,
                kind = Kind.MOVIE,
                categoryId = cat,
                image = o["stream_icon"] ?: o["cover"],
                url = movieUrl(id, ext),
                rating = o["rating"]?.toDoubleOrNull()?.takeIf { it > 0 }
                    ?: o["rating_5based"]?.toDoubleOrNull()?.takeIf { it > 0 }?.times(2),
                year = o["year"] ?: year.find(name)?.groupValues?.get(1),
                added = o["added"]?.toLongOrNull()?.times(1000) ?: 0,
                plot = o["plot"],
                ext = ext,
                adult = o["is_adult"] == "1",
            )
        }
        return out
    }

    fun parseSeries(file: File, onFraction: ((Float) -> Unit)? = null): List<Stream> {
        val out = ArrayList<Stream>()
        val seen = HashSet<String>()
        readObjects(file, onFraction) { o ->
            val id = o["series_id"] ?: return@readObjects
            val cat = o["category_id"] ?: "0"
            if (!seen.add("$id|$cat")) return@readObjects
            out += Stream(
                id = id,
                name = o["name"] ?: "Series $id",
                kind = Kind.SERIES,
                categoryId = cat,
                image = o["cover"],
                rating = o["rating"]?.toDoubleOrNull()?.takeIf { it > 0 }
                    ?: o["rating_5based"]?.toDoubleOrNull()?.takeIf { it > 0 }?.times(2),
                year = (o["releaseDate"] ?: o["release_date"])?.take(4) ?: o["year"],
                added = o["last_modified"]?.toLongOrNull()?.times(1000) ?: 0,
                plot = o["plot"],
            )
        }
        return out
    }

    fun parseSeriesInfo(json: String, series: Stream): SeriesInfo {
        val root = JSONObject(json)
        val info = root.optJSONObject("info") ?: JSONObject()
        val seasons = HashMap<Int, MutableList<Episode>>()
        fun addEpisode(o: JSONObject, fallbackSeason: Int) {
            val id = o.str("id") ?: return
            val einfo = o.optJSONObject("info")
            val season = o.str("season")?.toIntOrNull() ?: fallbackSeason
            val num = o.str("episode_num")?.toIntOrNull() ?: 0
            seasons.getOrPut(season) { ArrayList() } += Episode(
                id = id,
                title = o.str("title")?.let { cleanEpisodeTitle(it, series.name) } ?: "Episode $num",
                season = season,
                number = num,
                url = episodeUrl(id, o.str("container_extension")),
                image = einfo?.str("movie_image") ?: einfo?.str("cover_big"),
                plot = einfo?.str("plot"),
                durationSecs = einfo?.str("duration_secs")?.toIntOrNull() ?: 0,
                rating = einfo?.str("rating")?.toDoubleOrNull()?.takeIf { it > 0 },
            )
        }
        when (val eps = root.opt("episodes")) {
            is JSONObject -> eps.keys().forEach { k ->
                val list = eps.optJSONArray(k) ?: return@forEach
                for (i in 0 until list.length()) list.optJSONObject(i)?.let { addEpisode(it, k.toIntOrNull() ?: 1) }
            }
            is JSONArray -> for (i in 0 until eps.length()) {
                when (val item = eps.opt(i)) {
                    is JSONArray -> for (j in 0 until item.length()) item.optJSONObject(j)?.let { addEpisode(it, i + 1) }
                    is JSONObject -> addEpisode(item, 1)
                }
            }
        }
        return SeriesInfo(
            series = series,
            plot = info.str("plot") ?: series.plot,
            cast = info.str("cast"),
            genre = info.str("genre"),
            director = info.str("director"),
            backdrop = info.firstOf("backdrop_path"),
            releaseDate = info.str("releaseDate") ?: info.str("release_date"),
            seasons = seasons.mapValues { (_, l) -> l.sortedBy { it.number } }.toSortedMap(),
        )
    }

    fun parseVodInfo(json: String): VodInfo {
        val root = JSONObject(json)
        val info = root.optJSONObject("info") ?: JSONObject()
        return VodInfo(
            plot = info.str("plot") ?: info.str("description"),
            cast = info.str("cast") ?: info.str("actors"),
            director = info.str("director"),
            genre = info.str("genre"),
            durationSecs = info.str("duration_secs")?.toIntOrNull() ?: 0,
            backdrop = info.firstOf("backdrop_path"),
            releaseDate = info.str("releasedate") ?: info.str("release_date"),
            rating = info.str("rating")?.toDoubleOrNull()?.takeIf { it > 0 },
            trailer = info.str("youtube_trailer"),
        )
    }

    fun parseShortEpg(json: String): List<Programme> {
        val arr = runCatching { JSONObject(json).optJSONArray("epg_listings") }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.str("start_timestamp")?.toLongOrNull()?.times(1000) ?: return@mapNotNull null
            val end = o.str("stop_timestamp")?.toLongOrNull()?.times(1000) ?: return@mapNotNull null
            Programme(start, end, b64(o.str("title")) ?: "", b64(o.str("description")))
        }.sortedBy { it.start }
    }

    private fun b64(s: String?): String? = s?.let {
        runCatching { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(it)
    }?.takeIf { it.isNotBlank() }

    private fun cleanEpisodeTitle(title: String, seriesName: String): String {
        var t = title.trim()
        if (t.startsWith(seriesName, ignoreCase = true)) t = t.substring(seriesName.length)
        t = t.replace(Regex("""^[\s\-|:._]*[Ss]\d{1,2}[\s._-]*[Ee]\d{1,3}[\s\-|:._]*"""), "")
        return t.trim().ifBlank { title }
    }

    companion object {
        /** Accepts "host:port", "http://host:port/", or a full get.php / player_api.php link. */
        fun normalizeBase(input: String): String {
            var s = input.trim()
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
            s = s.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/xmltv.php")
            s = s.substringBefore('?')
            return s.trimEnd('/')
        }

        /** Detects M3U links generated by Xtream panels (get.php?username=…&password=…). */
        fun detectFromM3uUrl(url: String): Triple<String, String, String>? {
            val parsed = url.trim().toHttpUrlOrNull() ?: return null
            if (!parsed.encodedPath.endsWith("get.php")) return null
            val u = parsed.queryParameter("username") ?: return null
            val p = parsed.queryParameter("password") ?: return null
            val port = if (parsed.port == 80 && parsed.scheme == "http" || parsed.port == 443 && parsed.scheme == "https") "" else ":${parsed.port}"
            return Triple("${parsed.scheme}://${parsed.host}$port", u, p)
        }
    }
}

// ---------------------------------------------------------------- JSON helpers

internal fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = opt(key)
    val s = when (v) {
        is String -> v
        is Number, is Boolean -> v.toString()
        else -> null
    }?.trim()
    return s?.takeIf { it.isNotEmpty() && it != "null" }
}

internal fun JSONObject.firstOf(key: String): String? = when (val v = opt(key)) {
    is JSONArray -> (0 until v.length()).firstNotNullOfOrNull { (v.opt(it) as? String)?.takeIf { s -> s.isNotBlank() } }
    is String -> v.takeIf { it.isNotBlank() }
    else -> null
}

internal fun String.asArray(): JSONArray =
    runCatching { JSONArray(this) }.getOrElse { JSONArray() }
