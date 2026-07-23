package dev.basiliskus.onda.core.model

import java.security.MessageDigest

object MediaIds {
    const val ROOT = "root"

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(12)

    fun playlist(name: String) = "playlist|$name"
    fun group(playlist: String, group: String) = "group|$playlist|${hash(group)}"
    fun station(s: Station) = "station|${hash("${s.playlist}|${s.group}|${s.url}")}"
    fun rb(uuid: String) = "rb|$uuid"

    fun stationIndex(playlists: List<Playlist>): Map<String, Station> =
        playlists.flatMap { it.stations }.associateBy { station(it) }

    sealed interface Parsed {
        data object Root : Parsed
        data class PlaylistNode(val playlist: String) : Parsed
        data class GroupNode(val playlist: String, val groupHash: String) : Parsed
        data class StationNode(val key: String) : Parsed
        data class RbNode(val uuid: String) : Parsed
    }

    fun parse(id: String): Parsed? {
        if (id == ROOT) return Parsed.Root
        val parts = id.split('|')
        return when {
            parts.size == 2 && parts[0] == "playlist" -> Parsed.PlaylistNode(parts[1])
            parts.size == 3 && parts[0] == "group" -> Parsed.GroupNode(parts[1], parts[2])
            parts.size == 2 && parts[0] == "station" -> Parsed.StationNode(parts[1])
            parts.size == 2 && parts[0] == "rb" -> Parsed.RbNode(parts[1])
            else -> null
        }
    }

    fun groupMatches(parsed: Parsed.GroupNode, playlist: String, group: String) =
        parsed.playlist == playlist && parsed.groupHash == hash(group)
}
