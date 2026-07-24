package dev.basiliskus.onda.core.ops

import dev.basiliskus.onda.core.model.Group
import dev.basiliskus.onda.core.model.Playlist
import dev.basiliskus.onda.core.model.Station

fun applyOps(playlists: List<Playlist>, ops: List<Op>): List<Playlist> =
    ops.fold(playlists) { acc, op ->
        when (op) {
            is Op.Delete -> acc.mapPlaylist(op.playlist) { it.removeUrl(op.url) }
            is Op.Move -> acc.mapPlaylist(op.playlist) { p ->
                val station = p.stations.firstOrNull { it.url == op.url } ?: return@mapPlaylist p
                p.removeUrl(op.url)
                    .appendToGroup(op.toGroup, station.copy(group = op.toGroup, pending = true))
            }
            is Op.Add -> {
                if (op.playlist == null || op.group == null) acc // quick-star adds are invisible in playlists
                else acc.mapPlaylist(op.playlist) { p ->
                    if (p.stations.any { it.url == op.station.url }) p // dup-add backstop
                    else p.appendToGroup(
                        op.group,
                        Station(
                            name = op.station.name, url = op.station.url,
                            group = op.group, playlist = op.playlist,
                            logo = op.station.icon.ifBlank { null }, pending = true,
                        ),
                    )
                }
            }
        }
    }

private fun List<Playlist>.mapPlaylist(name: String, f: (Playlist) -> Playlist): List<Playlist> =
    map { if (it.name == name) f(it) else it }

private fun Playlist.removeUrl(url: String): Playlist =
    copy(groups = groups.mapNotNull { g ->
        g.copy(stations = g.stations.filterNot { it.url == url }).takeIf { it.stations.isNotEmpty() }
    })

private fun Playlist.appendToGroup(title: String, station: Station): Playlist =
    if (groups.any { it.title == title })
        copy(groups = groups.map { if (it.title == title) it.copy(stations = it.stations + station) else it })
    else copy(groups = groups + Group(title, listOf(station)))
