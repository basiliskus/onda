package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.favorites.PendingCsv
import dev.basiliskus.onda.core.favorites.PendingStation
import dev.basiliskus.onda.core.radiobrowser.RbStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavoritesRepository(
    private val playlists: PlaylistRepository,
    private val scope: CoroutineScope,
) {
    private val _pendingUrls = MutableStateFlow<Set<String>>(emptySet())
    val pendingUrls: StateFlow<Set<String>> = _pendingUrls
    private val mutex = Mutex()

    init {
        refresh()
        scope.launch { playlists.folderTicks.collect { refresh() } }
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val current = playlists.readPendingText()?.let(PendingCsv::parse) ?: emptyList()
                _pendingUrls.value = current.map { it.url }.toSet()
            }
        }
    }

    fun toggle(s: RbStation) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val current = playlists.readPendingText()?.let(PendingCsv::parse) ?: emptyList()
                val updated = PendingCsv.toggle(current, PendingStation(
                    name = s.name, url = s.url, icon = s.favicon,
                    stationuuid = s.stationuuid, tags = s.tags, country = s.country, votes = s.votes,
                ))
                playlists.writePendingText(PendingCsv.serialize(updated))
                _pendingUrls.value = updated.map { it.url }.toSet()
            }
        }
    }
}
