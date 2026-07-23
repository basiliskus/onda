package dev.basiliskus.onda

import android.app.Application
import dev.basiliskus.onda.core.radiobrowser.RadioBrowserClient
import okhttp3.OkHttpClient

class AppGraph(val app: Application) {
    val httpClient: OkHttpClient by lazy { OkHttpClient() }
    val radioBrowser: RadioBrowserClient by lazy { RadioBrowserClient(httpClient) }
    // Task 8 adds: playlistRepository. Task 13 adds: favoritesRepository.
}

class OndaApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
