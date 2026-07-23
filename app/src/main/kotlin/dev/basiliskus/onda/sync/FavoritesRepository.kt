package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.favorites.PendingCsv
import dev.basiliskus.onda.core.favorites.PendingStation
import dev.basiliskus.onda.core.radiobrowser.RbStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors
    private val mutex = Mutex()

    init {
        refresh()
        scope.launch { playlists.folderTicks.collect { refresh() } }
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val text = playlists.readPendingText().getOrElse {
                    _errors.tryEmit(UNAVAILABLE_MESSAGE)
                    return@withLock
                }
                val current = text?.let(PendingCsv::parse) ?: emptyList()
                _pendingUrls.value = current.map { it.url }.toSet()
            }
        }
    }

    fun toggle(s: RbStation) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val text = playlists.readPendingText().getOrElse {
                    _errors.tryEmit(UNAVAILABLE_MESSAGE)
                    return@withLock
                }
                val current = text?.let(PendingCsv::parse) ?: emptyList()
                val updated = PendingCsv.toggle(current, PendingStation(
                    name = s.name, url = s.url, icon = s.favicon,
                    stationuuid = s.stationuuid, tags = s.tags, country = s.country, votes = s.votes,
                ))
                val wrote = playlists.writePendingText(PendingCsv.serialize(updated))
                if (!wrote) {
                    _errors.tryEmit(UNAVAILABLE_MESSAGE)
                    return@withLock
                }
                _pendingUrls.value = updated.map { it.url }.toSet()
            }
        }
    }

    private companion object {
        const val UNAVAILABLE_MESSAGE = "Favorites unavailable — synced folder unreachable"
    }
}
