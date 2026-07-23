package dev.basiliskus.onda.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.basiliskus.onda.OndaApp
import dev.basiliskus.onda.core.model.MediaIds
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.radiobrowser.RadioBrowserClient
import dev.basiliskus.onda.sync.PlaylistsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var lastRetriedMediaId: String? = null
    private var connectivityManager: ConnectivityManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Single retry when connectivity returns while the session is active. Distinct from the
    // immediate single-retry-per-mediaId in onPlayerError below — this one fires when a
    // network becomes available again while the player is sitting in an error state.
    // NetworkCallback methods run on an internal ConnectivityThread, not main — ExoPlayer
    // must only ever be touched from the thread it was created on, so hop back to main.
    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                val player = session?.player ?: return@post
                if (player.playerError != null && player.currentMediaItem != null) {
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private val stationIndex: Map<String, Station>
        get() = ((application as OndaApp).graph.playlistRepository.state.value as? PlaylistsState.Ready)
            ?.let { MediaIds.stationIndex(it.playlists) } ?: emptyMap()

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        // DefaultHttpDataSource (HttpURLConnection) is REQUIRED here: it tolerates
        // shoutcast v1 "ICY 200 OK" status lines that OkHttp rejects. Do not swap.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(RadioBrowserClient.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            IndirectionResolver(),
        )
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { lastRetriedMediaId = null }
            override fun onPlayerError(error: PlaybackException) {
                val id = player.currentMediaItem?.mediaId ?: return
                if (lastRetriedMediaId != id) {
                    lastRetriedMediaId = id
                    player.prepare(); player.play()
                } // else: stay idle; controllers observe the error state
            }
            // Media3 extracts ICY StreamTitle updates as IcyInfo but does not merge them into
            // Player.mediaMetadata on its own (androidx/media/issues/153) — do it explicitly so
            // PlayerConnection's `c.mediaMetadata` reflects the current track. replaceMediaItem
            // with an unchanged URI updates metadata without reloading/interrupting the stream.
            @UnstableApi
            override fun onMetadata(metadata: Metadata) {
                val current = player.currentMediaItem ?: return
                for (i in 0 until metadata.length()) {
                    val title = (metadata.get(i) as? IcyInfo)?.title?.takeIf { it.isNotBlank() } ?: continue
                    if (current.mediaMetadata.title?.toString() == title) continue
                    player.replaceMediaItem(
                        player.currentMediaItemIndex,
                        current.buildUpon()
                            .setMediaMetadata(current.mediaMetadata.buildUpon().setTitle(title).build())
                            .build(),
                    )
                }
            }
        })
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        // A browser (e.g. Android Auto) can connect and call onGetChildren before the
        // repository's background rescan finishes — onGetChildren answers from whatever
        // state is current, so without this a root (or any already-browsed playlist/group)
        // fetched too early comes back empty and stays that way. Push a change notification
        // for every node a browser could currently be subscribed to whenever the
        // repository's state moves, so it re-fetches once playlists are actually loaded.
        serviceScope.launch {
            (application as OndaApp).graph.playlistRepository.state.collect { state ->
                session?.notifyChildrenChanged(MediaIds.ROOT, Int.MAX_VALUE, null)
                (state as? PlaylistsState.Ready)?.playlists?.forEach { playlist ->
                    session?.notifyChildrenChanged(MediaIds.playlist(playlist.name), Int.MAX_VALUE, null)
                    playlist.groups.forEach { group ->
                        session?.notifyChildrenChanged(
                            MediaIds.group(playlist.name, group.title), Int.MAX_VALUE, null,
                        )
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        serviceScope.cancel()
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        connectivityManager = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                stationIndex[item.mediaId]?.let { s -> playableItem(item.mediaId, s.name, s.url, s.logo) }
                    ?: item.requestMetadata.mediaUri?.let { uri ->
                        item.buildUpon().setUri(uri).build()
                    } ?: item
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsableItem(MediaIds.ROOT, "Onda"), params))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            parentId: String, page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val playlists = ((application as OndaApp).graph.playlistRepository.state.value as? PlaylistsState.Ready)
                ?.playlists ?: emptyList()
            val children: List<MediaItem> = when (val node = MediaIds.parse(parentId)) {
                is MediaIds.Parsed.Root ->
                    playlists.map { browsableItem(MediaIds.playlist(it.name), it.name) }
                is MediaIds.Parsed.PlaylistNode ->
                    playlists.firstOrNull { it.name == node.playlist }?.groups
                        ?.map { g -> browsableItem(MediaIds.group(node.playlist, g.title), g.title) } ?: emptyList()
                is MediaIds.Parsed.GroupNode ->
                    playlists.firstOrNull { it.name == node.playlist }?.groups
                        ?.firstOrNull { MediaIds.groupMatches(node, node.playlist, it.title) }?.stations
                        ?.map { s -> playableItem(MediaIds.station(s), s.name, s.url, s.logo) } ?: emptyList()
                else -> emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
        }

        private fun browsableItem(id: String, title: String): MediaItem =
            MediaItem.Builder().setMediaId(id).setMediaMetadata(
                MediaMetadata.Builder().setTitle(title)
                    .setIsBrowsable(true).setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
            ).build()
    }

    companion object {
        fun playableItem(mediaId: String, title: String, url: String, artworkUrl: String?): MediaItem =
            MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(url)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder().setMediaUri(android.net.Uri.parse(url)).build()
                )
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setStation(title)
                        .setArtworkUri(artworkUrl?.let(android.net.Uri::parse))
                        .setIsPlayable(true).setIsBrowsable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                        .build()
                )
                .build()
    }
}
