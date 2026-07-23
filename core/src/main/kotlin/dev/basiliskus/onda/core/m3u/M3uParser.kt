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
        // Attribute values are matched over the whole string (not just up to the first comma)
        // so a comma inside a quoted value (e.g. group-title="News, Talk") doesn't truncate the
        // match. The display name then starts at the first comma AFTER the last attribute match
        // — everything before it is duration + attributes, everything after is the name (which
        // may itself contain commas).
        val matches = attr.findAll(afterColon).toList()
        val searchFrom = matches.lastOrNull()?.range?.last?.plus(1) ?: 0
        val commaIndex = afterColon.indexOf(',', searchFrom)
        if (commaIndex == -1) return null
        val name = afterColon.substring(commaIndex + 1).trim()
        if (name.isEmpty()) return null
        val attrs = matches.associate { it.groupValues[1] to it.groupValues[2] }
        return Triple(name, attrs["group-title"], attrs["tvg-logo"])
    }
}
