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
        val groups = stations.groupBy { it.group }.map { (title, ss) -> Group(title, ss) }
        val order = stations.map { it.group }.distinct()
        return Playlist(fileBaseName, groups.sortedBy { order.indexOf(it.title) })
    }

    private fun parseExtinf(line: String): Triple<String, String?, String?>? {
        val afterColon = line.substringAfter("#EXTINF:")
        val name = afterColon.substringAfter(',', "").trim()
        if (name.isEmpty()) return null
        val attrsPart = afterColon.substringBefore(',')
        val attrs = attr.findAll(attrsPart).associate { it.groupValues[1] to it.groupValues[2] }
        return Triple(name, attrs["group-title"], attrs["tvg-logo"])
    }
}
