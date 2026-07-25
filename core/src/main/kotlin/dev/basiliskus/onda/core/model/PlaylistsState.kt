package dev.basiliskus.onda.core.model

sealed interface PlaylistsState {
    data object NeedsFolder : PlaylistsState
    data object Loading : PlaylistsState
    data class Ready(val playlists: List<Playlist>) : PlaylistsState
}
