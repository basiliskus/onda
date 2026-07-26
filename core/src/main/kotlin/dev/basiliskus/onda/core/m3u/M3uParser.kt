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
        // duration + attributes, everything right of it is the display name, verbatim apart
        // from surrounding whitespace (see the trim below and the row pinning it). Tracking
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
     * Index of the first comma at quote-depth zero.
     *
     * An unbalanced quote leaves the scan permanently inside quotes, so quote depth stops
     * meaning anything and no such comma can be found. That case falls back to the pre-fix
     * rule — the first comma after the last well-formed `key="value"` match — which anchors on
     * something the broken quoting cannot invalidate, and which by construction makes every
     * unbalanced line parse exactly as it did before this parser tracked quotes at all.
     *
     * With quotes balanced the fallback must NOT fire: a line whose only comma sits inside a
     * quoted value has no display name, and scavenging one out of the middle of an attribute
     * value would invent a station the file never declared.
     *
     * Returns -1 when no separator exists under either rule.
     */
    private fun separatorIndex(afterColon: String): Int {
        var inQuotes = false
        afterColon.forEachIndexed { i, c ->
            when (c) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return i
            }
        }
        if (!inQuotes) return -1
        val afterLastAttr = attr.findAll(afterColon).lastOrNull()?.range?.last?.plus(1) ?: 0
        return afterColon.indexOf(',', afterLastAttr)
    }
}
