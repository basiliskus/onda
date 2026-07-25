package dev.basiliskus.onda.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Playback-resumption wait-and-resolve: waits (bounded, so a missing folder or stuck scan
 * fails well inside the ~5s foreground-service/ANR budget) for the first settled playlists
 * state, then resolves [lastPlayedId] against it. Throws IllegalStateException on a missing
 * id, unavailable playlists, or a stale id; a stuck-Loading flow lets the timeout's
 * TimeoutCancellationException propagate — the caller treats any failure the same.
 */
suspend fun resolveResumptionStation(
    lastPlayedId: String?,
    states: Flow<PlaylistsState>,
    timeoutMs: Long = 3_000,
): Station {
    lastPlayedId ?: throw IllegalStateException("no last played station")
    val playlists = withTimeout(timeoutMs) {
        states.first { it !is PlaylistsState.Loading }.let { it as? PlaylistsState.Ready }
    }?.playlists ?: throw IllegalStateException("playlists unavailable")
    return MediaIds.resolveStation(lastPlayedId, playlists)
        ?: throw IllegalStateException("last played station no longer in playlists")
}
