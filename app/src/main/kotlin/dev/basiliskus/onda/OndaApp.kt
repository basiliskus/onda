package dev.basiliskus.onda

import android.app.Application
import dev.basiliskus.onda.core.radiobrowser.RadioBrowserClient
import dev.basiliskus.onda.playback.PlayerConnection
import dev.basiliskus.onda.sync.FolderPrefs
import dev.basiliskus.onda.sync.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppGraph(val app: Application) {
    val httpClient: OkHttpClient by lazy { OkHttpClient() }
    val radioBrowser: RadioBrowserClient by lazy { RadioBrowserClient(httpClient) }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val folderPrefs: FolderPrefs by lazy { FolderPrefs(app) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(app, folderPrefs, appScope) }
    val playerConnection: PlayerConnection by lazy { PlayerConnection(app, appScope) }
    // Task 13 adds: favoritesRepository.
}

class OndaApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
