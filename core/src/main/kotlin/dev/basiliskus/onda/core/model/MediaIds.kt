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

    fun resolveStation(lastPlayedId: String?, playlists: List<Playlist>): Station? =
        lastPlayedId?.let { stationIndex(playlists)[it] }

    /**
     * Where a station that [resolveStation] can no longer find has moved to: the first
     * station carrying [lastPlayedUrl], in playlist order.
     *
     * [station] hashes playlist and group alongside url, so moving or regrouping a station
     * changes its id and the stored one stops resolving. Url is the only component a move
     * leaves alone, which is why it is persisted next to the id and consulted here.
     *
     * Only station ids are followed. A Discover (`rb|`) id must keep failing even when its
     * url also sits in a playlist — Discover-only stations deliberately do not resume, and
     * matching on url alone would silently start resuming them.
     * enforced by: MediaIdsTest."a Discover id is not followed by url even when the url is in a playlist"
     *
     * First in playlist order, not last: urls are shared across playlists in the real
     * station set, so this tie-break decides a live case, not a hypothetical one.
     * [stationIndex] uses associateBy, which would pick the last.
     * enforced by: MediaIdsTest."a url in two playlists resolves to the first in playlist order"
     * enforced by: MediaIdsTest."the committed playlists really do share urls across files"
     */
    fun movedStation(lastPlayedId: String?, lastPlayedUrl: String?, playlists: List<Playlist>): Station? {
        if (parse(lastPlayedId ?: return null) !is Parsed.StationNode) return null
        // No null guard on lastPlayedUrl: Station.url is non-null, so a null simply matches
        // nothing. An explicit check read well but no test could tell it apart — it survived
        // deletion during the mutation pass, which is the definition of dead weight here.
        return playlists.flatMap { it.stations }.firstOrNull { it.url == lastPlayedUrl }
    }

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
