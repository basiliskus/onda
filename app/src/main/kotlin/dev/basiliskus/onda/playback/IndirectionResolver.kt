package dev.basiliskus.onda.playback

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import dev.basiliskus.onda.core.m3u.IndirectionParser
import dev.basiliskus.onda.core.radiobrowser.RadioBrowserClient
import java.net.HttpURLConnection
import java.net.URL

/** Play-time resolution of .pls/.m3u indirection. Runs on Media3's loading thread (blocking IO is expected). */
class IndirectionResolver : ResolvingDataSource.Resolver {
    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        var uri = dataSpec.uri
        var hops = 0
        while (IndirectionParser.looksLikeIndirection(uri.toString()) && hops < 3) {
            val body = fetch(uri.toString()) ?: break
            val next = IndirectionParser.firstStreamUrl(body) ?: break
            uri = Uri.parse(next)
            hops++
        }
        return if (uri == dataSpec.uri) dataSpec else dataSpec.withUri(uri)
    }

    private fun fetch(url: String): String? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", RadioBrowserClient.USER_AGENT)
        conn.inputStream.use { String(it.readNBytes(64 * 1024)) }
    } catch (_: Exception) { null }
}
