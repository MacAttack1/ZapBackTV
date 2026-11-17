package com.lumen.tv.data

import java.text.Normalizer

private val marks = Regex("\\p{Mn}+")

/** Lower-cases and strips accents; skips the (slow) Unicode normalizer for plain ASCII names. */
fun foldForSearch(s: String): String {
    val lower = s.lowercase()
    return if (lower.all { it.code < 128 }) lower else Normalizer.normalize(lower, Normalizer.Form.NFD).replace(marks, "")
}

data class SearchResults(val live: List<Stream>, val movies: List<Stream>, val series: List<Stream>) {
    val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/** Names pre-folded once per library so each keystroke is a quick scan. */
class SearchIndex(private val items: List<Stream>) {
    private val names: Array<String> = Array(items.size) { foldForSearch(items[it].name) }

    fun find(q: String, terms: List<String>, limit: Int, check: () -> Unit): List<Stream> {
        val first = ArrayList<Stream>()
        val rest = ArrayList<Stream>()
        for (i in names.indices) {
            if (i and 1023 == 0) check()
            val n = names[i]
            if (terms.all { it in n }) {
                if (n.startsWith(q)) first += items[i] else if (rest.size < limit) rest += items[i]
                if (first.size >= limit) break
            }
        }
        return (first + rest).take(limit)
    }

    companion object {
        fun forLibrary(lib: Library): Map<Kind, SearchIndex> = Kind.entries.associateWith { SearchIndex(lib.streams(it)) }
    }
}
