package com.lumen.tv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

class EpgData(
    private val programmes: Map<String, List<Programme>>,
    private val nameIndex: Map<String, String>,
    /** True when the guide was shortened (fewer days) so it would fit in this device's memory. */
    val trimmed: Boolean = false,
) {
    val isEmpty: Boolean get() = programmes.isEmpty()
    val channelCount: Int get() = programmes.size

    fun forStream(s: Stream): List<Programme> {
        s.epgId?.let { id -> programmes[id]?.let { return it } }
        val byName = nameIndex[normalizeName(s.name)] ?: return emptyList()
        return programmes[byName].orEmpty()
    }

    fun now(s: Stream, t: Long = System.currentTimeMillis()): Programme? {
        val list = forStream(s)
        if (list.isEmpty()) return null
        var lo = 0; var hi = list.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val p = list[mid]
            when {
                t < p.start -> hi = mid - 1
                t >= p.end -> lo = mid + 1
                else -> return p
            }
        }
        return null
    }

    fun upcoming(s: Stream, count: Int = 4, t: Long = System.currentTimeMillis()): List<Programme> =
        forStream(s).asSequence().filter { it.end > t }.take(count).toList()

    fun between(s: Stream, from: Long, to: Long): List<Programme> =
        forStream(s).filter { it.end > from && it.start < to }

    companion object {
        val EMPTY = EpgData(emptyMap(), emptyMap())

        private val prefix = Regex("""^[a-z]{2,4}\s*[:|\-]\s*""")
        private val noise = Regex("""\b(fhd|uhd|hd|sd|4k|hevc|h265|backup|raw)\b""")
        fun normalizeName(name: String): String =
            name.lowercase(Locale.ROOT)
                .replace(prefix, "")
                .replace(Regex("""\[.*?]|\(.*?\)"""), "")
                .replace(noise, "")
                .filter { it.isLetterOrDigit() }
    }
}

/** Streaming XMLTV parser. Handles gzip transparently and keeps only channels the library uses. */
object EpgParser {
    private val formats = ThreadLocal.withInitial {
        listOf(
            SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US),
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        )
    }

    fun parse(
        input: InputStream,
        wantedIds: Set<String>,
        wantedNames: Set<String>,
        shiftMs: Long = 0,
        now: Long = System.currentTimeMillis(),
    ): EpgData {
        // Big guides (thousands of channels, a week of listings) can fill the whole app's memory,
        // which crashes it wherever the next allocation happens. So the window shrinks as memory
        // fills: 2 days back / 4 ahead, then 6 hours back / 1 day ahead, then stop adding.
        var windowStart = now - 2 * 24 * 3600_000L
        var windowEnd = now + 4 * 24 * 3600_000L
        var stage = 0
        var added = 0
        // Titles and descriptions repeat a lot (reruns, "Paid Programming"), so share one copy.
        val strings = HashMap<String, String>()
        fun shared(v: String): String = strings.getOrPut(v) { v }

        val buffered = BufferedInputStream(input, 64 * 1024)
        buffered.mark(4)
        val b1 = buffered.read(); val b2 = buffered.read()
        buffered.reset()
        val stream: InputStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered, 64 * 1024) else buffered

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, null)

        val keep = HashSet<String>(wantedIds)
        val nameIndex = HashMap<String, String>()
        val result = HashMap<String, ArrayList<Programme>>()

        var channelId: String? = null
        var progChannel: String? = null
        var progStart = 0L
        var progEnd = 0L
        var title: String? = null
        var desc: String? = null
        var inProgramme = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> channelId = parser.getAttributeValue(null, "id")
                    "display-name" -> if (channelId != null && !inProgramme) {
                        val n = EpgData.normalizeName(safeText(parser))
                        if (n.isNotEmpty() && n in wantedNames) {
                            keep += channelId!!
                            nameIndex.putIfAbsent(n, channelId!!)
                        }
                    }
                    "programme" -> {
                        inProgramme = true
                        progChannel = parser.getAttributeValue(null, "channel")
                        progStart = parseTime(parser.getAttributeValue(null, "start")) + shiftMs
                        progEnd = parseTime(parser.getAttributeValue(null, "stop")) + shiftMs
                        title = null; desc = null
                    }
                    "title" -> if (inProgramme && title == null) title = safeText(parser)
                    "desc" -> if (inProgramme && desc == null) desc = safeText(parser).let { if (it.length > MAX_DESC) it.take(MAX_DESC - 1) + "…" else it }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name) {
                    "channel" -> channelId = null
                    "programme" -> {
                        inProgramme = false
                        val ch = progChannel
                        if (stage < 2 && ch != null && ch in keep && progStart > 0 &&
                            progEnd > windowStart && progStart < windowEnd
                        ) {
                            val end = if (progEnd > progStart) progEnd else progStart + 30 * 60_000L
                            result.getOrPut(ch) { ArrayList() } +=
                                Programme(progStart, end, shared(title ?: "No title"), desc?.takeIf { it.isNotEmpty() }?.let(::shared))
                            if (++added % 4000 == 0) {
                                var used = heapUsed()
                                // Parsing leaves garbage behind; only count what's really still in use.
                                if (used > 0.45f) { System.gc(); used = heapUsed() }
                                if (stage == 0 && used > 0.45f) {
                                    stage = 1
                                    windowStart = now - 6 * 3600_000L
                                    windowEnd = now + 24 * 3600_000L
                                    for (list in result.values) list.removeAll { it.end <= windowStart || it.start >= windowEnd }
                                    strings.clear()
                                } else if (stage == 1 && used > 0.6f) {
                                    stage = 2
                                }
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        result.values.forEach { it.sortBy { p -> p.start }; it.trimToSize() }
        return EpgData(result, nameIndex, trimmed = stage > 0)
    }

    private const val MAX_DESC = 360

    /** Share of the app's memory limit in use right now. */
    private fun heapUsed(): Float {
        val rt = Runtime.getRuntime()
        return (rt.totalMemory() - rt.freeMemory()).toFloat() / rt.maxMemory()
    }

    private fun safeText(p: XmlPullParser): String = runCatching { p.nextText().trim() }.getOrDefault("")

    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        val v = s.trim()
        for (f in formats.get()!!) {
            runCatching { return f.parse(v)!!.time }
        }
        return 0
    }
}
