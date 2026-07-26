package dev.basiliskus.onda.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.basiliskus.onda.OndaApp
import dev.basiliskus.onda.core.capture.streamTitleOf
import dev.basiliskus.onda.core.model.MediaIds
import dev.basiliskus.onda.core.model.PlaylistsState
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.model.resolveResumptionStation
import dev.basiliskus.onda.core.radiobrowser.RadioBrowserClient
import dev.basiliskus.onda.sync.SongCaptureRepository
import dev.basiliskus.onda.sync.captureMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

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
        get() = ((application as OndaApp).graph.opsRepository.effectivePlaylists.value as? PlaylistsState.Ready)
            ?.let { MediaIds.stationIndex(it.playlists) } ?: emptyMap()

    private val likeCommand = SessionCommand(ACTION_LIKE, Bundle.EMPTY)
    /**
     * Last enabled-ness pushed to the media-notification controller; null when none is known to
     * have landed — before the first push, and again after one is rejected.
     *
     * Confined to the session's application looper (the main thread): written from
     * Player.Listener.onMediaMetadataChanged and from the setCustomLayout listener it posts
     * back to that same looper, read from LibraryCallback.onConnect, all of which Media3
     * dispatches there. Not safe to touch from anywhere else.
     */
    private var likeEnabled: Boolean? = null

    // A disabled CommandButton is dropped from the notification entirely — the platform has no
    // greyed-out NotificationCompat.Action — so on a station without ICY metadata the Like
    // action is simply absent. Absence is the honest analogue of disabled here: an always-shown
    // action that quietly did nothing would be worse. The Now Playing sheet, which can render a
    // real Material disabled state, keeps the affordance visible instead.
    private fun likeButton(enabled: Boolean) =
        CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
            .setSessionCommand(likeCommand)
            .setDisplayName("Like")
            .setEnabled(enabled)
            .build()

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
        // Every likeEnabled write happens here, so the setCustomLayout callback below is posted
        // back to the same looper Media3 dispatches Player.Listener and Callback.onConnect on,
        // rather than running on whichever binder thread completes the future.
        val appLooperExecutor = Handler(player.applicationLooper).let { h -> Executor { h.post(it) } }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                lastRetriedMediaId = null
                // The url is persisted alongside the id because the id hashes playlist and
                // group, so a later move or regroup invalidates it (GitHub #35).
                if (item != null) (application as OndaApp).graph.folderPrefs.let { prefs ->
                    prefs.lastPlayedMediaId = item.mediaId
                    prefs.lastPlayedUrl = item.requestMetadata.mediaUri?.toString()
                }
            }
            // onMetadata below calls replaceMediaItem on every ICY title tick, so this fires
            // several times a song; re-pushing an identical layout would rebuild the
            // notification just as often, hence the likeEnabled guard.
            @UnstableApi
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val enabled = streamTitleOf(
                    mediaMetadata.title?.toString(), mediaMetadata.station?.toString(),
                ) != null
                if (likeEnabled == enabled) return
                likeEnabled = enabled
                // Per-controller, not the session-wide overload: the session-wide push reaches
                // Android Auto too, and Auto's only feedback channel for this action would be
                // a Toast it does not display — a failed write in the car would be a silent
                // no-op. The Auto custom action is deferred to Future by the ROADMAP; until it
                // has real feedback, the button must not render there.
                val s = session ?: return
                // A null controller means the notification isn't up yet; its onConnect reads
                // likeEnabled below, so the state still reaches it.
                val controller = s.mediaNotificationControllerInfo ?: return
                val push = s.setCustomLayout(controller, listOf(likeButton(enabled)))
                // The latch above is optimistic, so it would suppress every later identical
                // push and leave a rejected one stale on the notification until the
                // enabled-ness flips twice. Clear it instead and let the next metadata change
                // re-push. A notification that connects in between renders the button absent —
                // the same unknown-state default onConnect already starts from. The check
                // keeps a newer latch: only roll back what this push itself set.
                push.addListener({
                    val landed = runCatching { push.get().resultCode == SessionResult.RESULT_SUCCESS }
                        .getOrDefault(false)
                    if (!landed && likeEnabled == enabled) likeEnabled = null
                }, appLooperExecutor)
            }
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
            (application as OndaApp).graph.opsRepository.effectivePlaylists.collect { state ->
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
        // The default callback grants no custom commands, and an unavailable command's button
        // is force-disabled — so without this the Like action would never render at all.
        // DEFAULT_SESSION_AND_LIBRARY_COMMANDS (not ..._SESSION_COMMANDS) keeps the Android
        // Auto browse tree working.
        //
        // Both the grant and the layout are scoped to the media-notification controller: the
        // notification is the only surface where a Toast is actually shown, so it is the only
        // one that can report a failed write instead of silently swallowing it. Android Auto
        // and the app's own MediaController get the browse tree and nothing else (the Now
        // Playing sheet calls SongCaptureRepository directly, never this command).
        // isMediaNotificationController is a pure predicate over ControllerInfo's package,
        // version and connection hints in media3 1.6.0, so it is valid this early.
        @UnstableApi
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val forNotification = session.isMediaNotificationController(controller)
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .apply { if (forNotification) add(likeCommand) }
                        .build()
                )
            if (forNotification) builder.setCustomLayout(listOf(likeButton(likeEnabled == true)))
            return builder.build()
        }

        /** The notification's Like action, and nothing else — the Now Playing sheet talks to
         *  SongCaptureRepository directly (same process), so one capture yields exactly one
         *  message: a toast from here, a snackbar from there, never both. */
        @UnstableApi
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != ACTION_LIKE) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            // Read metadata on the calling (main) thread; the repository hops to IO itself.
            val meta = session.player.mediaMetadata
            val title = streamTitleOf(meta.title?.toString(), meta.station?.toString())
            return serviceScope.future {
                val outcome = (application as OndaApp).graph.songCapture.capture(title)
                // The notification has no Activity to host a snackbar, and a capture started
                // here never reaches the sheet — so one outcome still yields one message.
                Toast.makeText(this@PlaybackService, captureMessage(outcome), Toast.LENGTH_SHORT).show()
                SessionResult(outcome.toResultCode())
            }
        }

        /** A title that cleared between notification render and tap resolves to NoTitle, so
         *  the tap reports itself rather than silently doing nothing. */
        @UnstableApi
        private fun SongCaptureRepository.Outcome.toResultCode() = when (this) {
            SongCaptureRepository.Outcome.Saved -> SessionResult.RESULT_SUCCESS
            SongCaptureRepository.Outcome.NoTitle -> SessionError.ERROR_INVALID_STATE
            SongCaptureRepository.Outcome.WriteFailed -> SessionError.ERROR_IO
        }

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
            val playlists = ((application as OndaApp).graph.opsRepository.effectivePlaylists.value as? PlaylistsState.Ready)
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

        @UnstableApi
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            serviceScope.future {
                val graph = (application as OndaApp).graph
                // Named: the two String? parameters are adjacent, so a swap would compile
                // and then fail silently — the id path would miss and a url would never
                // parse as an id, leaving resumption permanently broken.
                val station = resolveResumptionStation(
                    lastPlayedId = graph.folderPrefs.lastPlayedMediaId,
                    lastPlayedUrl = graph.folderPrefs.lastPlayedUrl,
                    states = graph.opsRepository.effectivePlaylists,
                )
                MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.of(playableItem(MediaIds.station(station), station.name, station.url, station.logo)),
                    /* startIndex = */ 0,
                    /* startPositionMs = */ C.TIME_UNSET,
                )
            }

        private fun browsableItem(id: String, title: String): MediaItem =
            MediaItem.Builder().setMediaId(id).setMediaMetadata(
                MediaMetadata.Builder().setTitle(title)
                    .setIsBrowsable(true).setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()
            ).build()
    }

    companion object {
        private const val ACTION_LIKE = "dev.basiliskus.onda.LIKE_TRACK"

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
