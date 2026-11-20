package com.lumen.tv.data

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * The organized library saved in a compact binary file, so the app opens straight into it instead
 * of re-reading the provider's huge JSON/M3U files on every launch. [fingerprint] identifies the
 * downloaded files it was built from: if a fresh download has the same fingerprint, nothing changed.
 */
object Snapshot {
    private const val MAGIC = 0x5A42_4C42 // "ZBLB"
    /** Bump when parsing or the format changes, so older snapshots are rebuilt. */
    private const val VERSION = 1

    class Saved(val library: Library, val fingerprint: String)

    fun write(file: File, lib: Library, fingerprint: String, config: String) {
        val tmp = File(file.path + ".part")
        DataOutputStream(BufferedOutputStream(DeflaterOutputStream(tmp.outputStream(), Deflater(1)), 64 * 1024)).use { o ->
            o.writeInt(MAGIC); o.writeInt(VERSION)
            o.str(config); o.str(fingerprint)
            o.str(lib.sourceId); o.optStr(lib.epgUrl)
            for (kind in Kind.entries) {
                val cats = lib.categories(kind)
                o.writeInt(cats.size)
                for (c in cats) { o.str(c.id); o.str(c.name); o.writeInt(c.count) }
                val streams = lib.streams(kind)
                o.writeInt(streams.size)
                for (s in streams) o.stream(s)
            }
            o.writeInt(lib.localSeries.size)
            for ((id, info) in lib.localSeries) {
                o.str(id); o.stream(info.series)
                o.optStr(info.plot); o.optStr(info.cast); o.optStr(info.genre); o.optStr(info.director)
                o.optStr(info.backdrop); o.optStr(info.releaseDate)
                o.writeInt(info.seasons.size)
                for ((season, eps) in info.seasons) {
                    o.writeInt(season); o.writeInt(eps.size)
                    for (e in eps) {
                        o.str(e.id); o.str(e.title); o.writeInt(e.season); o.writeInt(e.number); o.str(e.url)
                        o.optStr(e.image); o.optStr(e.plot); o.writeInt(e.durationSecs); o.optDouble(e.rating)
                    }
                }
            }
        }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    /** The saved library, or null if there is none, it's from an older version, or [config] changed. */
    fun read(file: File, config: String): Saved? {
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(InflaterInputStream(file.inputStream()), 64 * 1024)).use { i ->
                if (i.readInt() != MAGIC || i.readInt() != VERSION) return null
                if (i.str() != config) return null
                val fingerprint = i.str()
                val sourceId = i.str(); val epgUrl = i.optStr()
                val categories = HashMap<Kind, List<Category>>()
                val streams = HashMap<Kind, List<Stream>>()
                for (kind in Kind.entries) {
                    categories[kind] = List(i.readInt()) { Category(i.str(), i.str(), kind, i.readInt()) }
                    streams[kind] = List(i.readInt()) { i.stream() }
                }
                val series = HashMap<String, SeriesInfo>()
                repeat(i.readInt()) {
                    val id = i.str(); val s = i.stream()
                    val plot = i.optStr(); val cast = i.optStr(); val genre = i.optStr(); val director = i.optStr()
                    val backdrop = i.optStr(); val release = i.optStr()
                    val seasons = HashMap<Int, List<Episode>>()
                    repeat(i.readInt()) {
                        val season = i.readInt()
                        seasons[season] = List(i.readInt()) {
                            Episode(i.str(), i.str(), i.readInt(), i.readInt(), i.str(), i.optStr(), i.optStr(), i.readInt(), i.optDouble())
                        }
                    }
                    series[id] = SeriesInfo(s, plot, cast, genre, director, backdrop, release, seasons)
                }
                Saved(Library(sourceId, categories, streams, epgUrl, series), fingerprint)
            }
        } catch (e: Exception) {
            null // damaged or partial: rebuild from the downloaded files
        }
    }

    /** Just the fingerprint of the saved library (reads only the file's header). */
    fun readFingerprint(file: File, config: String): String? {
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(InflaterInputStream(file.inputStream()), 8 * 1024)).use { i ->
                if (i.readInt() != MAGIC || i.readInt() != VERSION || i.str() != config) null else i.str()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Size + checksum of each file, cheap enough to run after every download. */
    fun fingerprint(files: List<File>): String = files.joinToString(";") { f ->
        if (!f.exists()) return@joinToString "${f.name}:-"
        val crc = CRC32()
        val buf = ByteArray(64 * 1024)
        f.inputStream().use { input -> while (true) { val n = input.read(buf); if (n < 0) break; crc.update(buf, 0, n) } }
        "${f.name}:${f.length()}:${java.lang.Long.toHexString(crc.value)}"
    }

    // ---------------------------------------------------------------- encoding

    private fun DataOutputStream.str(s: String) {
        val b = s.toByteArray(Charsets.UTF_8); writeInt(b.size); write(b)
    }
    private fun DataOutputStream.optStr(s: String?) = if (s == null) writeInt(-1) else str(s)
    private fun DataOutputStream.optDouble(d: Double?) { writeBoolean(d != null); if (d != null) writeDouble(d) }

    private fun DataInputStream.str(): String = optStr() ?: ""
    private fun DataInputStream.optStr(): String? {
        val n = readInt()
        if (n < 0) return null
        if (n == 0) return ""
        val b = ByteArray(n); readFully(b); return String(b, Charsets.UTF_8)
    }
    private fun DataInputStream.optDouble(): Double? = if (readBoolean()) readDouble() else null

    private fun DataOutputStream.stream(s: Stream) {
        str(s.id); str(s.name); writeByte(s.kind.ordinal); str(s.categoryId)
        optStr(s.image); str(s.url); optStr(s.epgId); writeInt(s.number); optDouble(s.rating); optStr(s.year)
        writeLong(s.added); optStr(s.plot); writeInt(s.catchupDays); optStr(s.ext); optStr(s.userAgent)
        optStr(s.referrer); writeBoolean(s.adult)
    }

    private fun DataInputStream.stream(): Stream = Stream(
        id = str(), name = str(), kind = Kind.entries[readByte().toInt()], categoryId = str(),
        image = optStr(), url = str(), epgId = optStr(), number = readInt(), rating = optDouble(), year = optStr(),
        added = readLong(), plot = optStr(), catchupDays = readInt(), ext = optStr(), userAgent = optStr(),
        referrer = optStr(), adult = readBoolean(),
    )
}
