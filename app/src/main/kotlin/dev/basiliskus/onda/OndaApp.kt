package dev.basiliskus.onda

import android.app.Application
import android.util.Log
import dev.basiliskus.onda.core.radiobrowser.RadioBrowserClient
import dev.basiliskus.onda.playback.PlayerConnection
import dev.basiliskus.onda.sync.FolderPrefs
import dev.basiliskus.onda.sync.OpsRepository
import dev.basiliskus.onda.sync.PlaylistRepository
import dev.basiliskus.onda.sync.SongCaptureRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

class AppGraph(val app: Application) {
    val httpClient: OkHttpClient by lazy { OkHttpClient() }
    val radioBrowser: RadioBrowserClient by lazy { RadioBrowserClient(httpClient) }
    // Last-resort net: targeted try/catch at each I/O boundary is the real fix, this just
    // keeps an unanticipated throw from taking down the whole process.
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        Log.e("Onda", "Unhandled exception in appScope", e)
    }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
    val folderPrefs: FolderPrefs by lazy { FolderPrefs(app) }
    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(app, folderPrefs, appScope) }
    val playerConnection: PlayerConnection by lazy { PlayerConnection(app, appScope) }
    val opsRepository: OpsRepository by lazy { OpsRepository(playlistRepository, appScope) }
    internal val songCapture: SongCaptureRepository by lazy { SongCaptureRepository(playlistRepository, appScope) }
}

class OndaApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
