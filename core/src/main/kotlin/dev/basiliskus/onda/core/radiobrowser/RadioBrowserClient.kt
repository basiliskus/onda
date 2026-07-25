package dev.basiliskus.onda.core.radiobrowser

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class RbStation(
    val stationuuid: String,
    val name: String,
    val url: String,
    val favicon: String = "",
    val tags: String = "",
    val country: String = "",
    val countrycode: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val votes: Int = 0,
)

@Serializable data class RbTag(val name: String, val stationcount: Int)
@Serializable data class RbCountry(val name: String, val iso_3166_1: String, val stationcount: Int)

data class StationFilter(
    val name: String? = null,
    val tag: String? = null,
    val countrycode: String? = null,
)

/** What the Discover UI consumes of the radio-browser client — the seam that keeps OkHttp
 *  behind a fakeable boundary for JVM tests. */
interface RadioBrowserApi {
    suspend fun searchStations(filter: StationFilter, offset: Int = 0, limit: Int = 30): List<RbStation>
    suspend fun topTags(limit: Int = 100): List<RbTag>
    suspend fun countries(): List<RbCountry>
}

class RadioBrowserClient(
    private val http: OkHttpClient,
    private val mirrors: List<String> = DEFAULT_MIRRORS,
) : RadioBrowserApi {
    companion object {
        val DEFAULT_MIRRORS = listOf(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
        )
        const val USER_AGENT = "Onda/0.1 (github.com/basiliskus/onda)"
    }

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var preferredMirror = 0

    override suspend fun searchStations(filter: StationFilter, offset: Int, limit: Int): List<RbStation> =
        get("/json/stations/search", buildMap {
            filter.name?.let { put("name", it) }
            filter.tag?.let { put("tag", it); put("tagExact", "true") }
            filter.countrycode?.let { put("countrycode", it) }
            put("order", "votes"); put("reverse", "true"); put("hidebroken", "true")
            put("offset", offset.toString()); put("limit", limit.toString())
        })

    override suspend fun topTags(limit: Int): List<RbTag> =
        get("/json/tags", mapOf("order" to "stationcount", "reverse" to "true", "limit" to limit.toString(), "hidebroken" to "true"))

    override suspend fun countries(): List<RbCountry> =
        get("/json/countries", mapOf("order" to "name", "hidebroken" to "true"))

    private suspend inline fun <reified T> get(path: String, params: Map<String, String>): T =
        withContext(Dispatchers.IO) {
            var lastError: IOException? = null
            val start = preferredMirror
            for (i in mirrors.indices) {
                val idx = (start + i) % mirrors.size
                val url = mirrors[idx].toHttpUrl().newBuilder().encodedPath(path).apply {
                    params.forEach { (k, v) -> addQueryParameter(k, v) }
                }.build()
                try {
                    http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
                        .execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from ${mirrors[idx]}")
                            val body = resp.body!!.string()
                            val decoded = try {
                                json.decodeFromString<T>(body)
                            } catch (e: SerializationException) {
                                throw IOException("bad payload from ${mirrors[idx]}", e)
                            } catch (e: IllegalArgumentException) {
                                throw IOException("bad payload from ${mirrors[idx]}", e)
                            }
                            // Only trust this mirror as "preferred" once we've actually decoded
                            // a valid payload from it — a mirror that serves 200-with-garbage
                            // must not become sticky.
                            preferredMirror = idx
                            return@withContext decoded
                        }
                } catch (e: IOException) { lastError = e }
            }
            throw lastError ?: IOException("no mirrors configured")
        }
}
