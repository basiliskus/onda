package dev.basiliskus.onda.core.m3u

import dev.basiliskus.onda.core.model.Group
import dev.basiliskus.onda.core.model.Playlist
import dev.basiliskus.onda.core.model.Station

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parsePlaylist(fileBaseName: String, text: String): Playlist {
        val lines = text.removePrefix("﻿").lines().map { it.trim() }
        val stations = mutableListOf<Station>()
        var pending: Triple<String, String?, String?>? = null // name, group, logo
        for (line in lines) {
            when {
                line.isEmpty() -> {}
                line.startsWith("#EXTINF:") -> {
                    pending = parseExtinf(line)
                }
                line.startsWith("#") -> {} // other directives ignored
                else -> {
                    val p = pending
                    if (p != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                        stations += Station(
                            name = p.first,
                            url = line,
                            group = p.second?.takeIf { it.isNotBlank() } ?: "Ungrouped",
                            playlist = fileBaseName,
                            logo = p.third,
                        )
                    }
                    pending = null
                }
            }
        }
        // groupBy already orders keys by first occurrence, so this is already in
        // first-encounter order — no extra sort needed.
        val groups = stations.groupBy { it.group }.map { (title, ss) -> Group(title, ss) }
        return Playlist(fileBaseName, groups)
    }

    private fun parseExtinf(line: String): Triple<String, String?, String?>? {
        val afterColon = line.substringAfter("#EXTINF:")
        // The separator is the first comma at quote-depth zero: everything left of it is
        // duration + attributes, everything right of it is the display name, verbatim. Tracking
        // quote depth is what keeps a comma inside a quoted value (group-title="News, Talk")
        // from truncating the attribute region, and matching attributes only left of the
        // separator is what keeps a word="value" substring inside a name from being read as an
        // attribute. Names may themselves contain commas and quotes; neither is interpreted.
        val separator = separatorIndex(afterColon)
        if (separator == -1) return null
        val name = afterColon.substring(separator + 1).trim()
        if (name.isEmpty()) return null
        val attrs = attr.findAll(afterColon.substring(0, separator))
            .associate { it.groupValues[1] to it.groupValues[2] }
        return Triple(name, attrs["group-title"], attrs["tvg-logo"])
    }

    /**
     * Index of the first comma at quote-depth zero, or — when an unbalanced quote leaves the
     * scan permanently inside quotes and no such comma exists — the first comma of any kind.
     * The fallback keeps a line like `#EXTINF:-1 "weird,Name` parsing as it always has instead
     * of trading one silent drop for another. Returns -1 when there is no comma at all.
     */
    private fun separatorIndex(afterColon: String): Int {
        var inQuotes = false
        var firstQuotedComma = -1
        afterColon.forEachIndexed { i, c ->
            when (c) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i else if (firstQuotedComma == -1) firstQuotedComma = i
            }
        }
        return firstQuotedComma
    }
}
