package dev.basiliskus.onda.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val stationIndex: Map<String, Station>
        get() = ((application as OndaApp).graph.playlistRepository.state.value as? PlaylistsState.Ready)
            ?.let { MediaIds.stationIndex(it.playlists) } ?: emptyMap()

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
        // A browser (e.g. Android Auto) can connect and call onGetChildren before the
        // repository's background rescan finishes — onGetChildren answers from whatever
        // state is current, so without this a root browsed too early comes back empty and
        // stays that way. Push a change notification whenever the repository's state moves,
        // so subscribed browsers re-fetch once playlists are actually loaded.
        serviceScope.launch {
            (application as OndaApp).graph.playlistRepository.state.collect {
                session?.notifyChildrenChanged(MediaIds.ROOT, Int.MAX_VALUE, null)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        serviceScope.cancel()
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
