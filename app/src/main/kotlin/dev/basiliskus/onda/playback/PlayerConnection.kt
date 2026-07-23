package dev.basiliskus.onda.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.basiliskus.onda.core.model.MediaIds
import dev.basiliskus.onda.core.model.Station
import dev.basiliskus.onda.core.radiobrowser.RbStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

data class NowPlaying(
    val mediaId: String,
    val stationName: String,
    val streamTitle: String?,
    val artworkUrl: String?,
    val playingUrl: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val error: String?,
)

class PlayerConnection(private val context: Context, private val scope: CoroutineScope) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying
    private var controller: MediaController? = null

    init {
        scope.launch(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            controller = MediaController.Builder(context, token).buildAsync().await().also { c ->
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) = publish(c)
                })
                publish(c)
            }
        }
    }

    private fun publish(c: MediaController) {
        val item = c.currentMediaItem ?: run { _nowPlaying.value = null; return }
        val meta: MediaMetadata = c.mediaMetadata
        val requested = item.requestMetadata.mediaUri?.toString() ?: ""
        _nowPlaying.value = NowPlaying(
            mediaId = item.mediaId,
            stationName = (meta.station ?: meta.title ?: "Unknown station").toString(),
            streamTitle = meta.title?.toString()?.takeIf { it != (meta.station ?: "").toString() },
            artworkUrl = meta.artworkUri?.toString(),
            playingUrl = requested,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            error = c.playerError?.let { humanize(it) },
        )
    }

    private fun humanize(e: PlaybackException) = "Station unreachable"

    private fun play(item: MediaItem) {
        val c = controller ?: return
        c.setMediaItem(item); c.prepare(); c.play()
    }

    fun playStation(s: Station) =
        play(PlaybackService.playableItem(MediaIds.station(s), s.name, s.url, s.logo))

    fun playRb(s: RbStation) =
        play(PlaybackService.playableItem(MediaIds.rb(s.stationuuid), s.name, s.url, s.favicon.ifBlank { null }))

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.stop() else { c.prepare(); c.play() }
    }

    fun stop() { controller?.stop() }
}
