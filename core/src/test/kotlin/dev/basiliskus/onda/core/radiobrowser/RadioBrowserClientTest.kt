package dev.basiliskus.onda.core.radiobrowser

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import kotlin.test.*

class RadioBrowserClientTest {
    private val stationJson = """
        [{"stationuuid":"u1","name":"FIP","url":"http://icecast.radiofrance.fr/fip-midfi.mp3",
          "url_resolved":"http://resolved.example/x","favicon":"https://fip.fr/fav.png",
          "tags":"eclectic,public radio","country":"France","countrycode":"FR",
          "codec":"MP3","bitrate":128,"votes":1500,"lastcheckok":1,"unknown_field":"ignored"}]
    """.trimIndent()

    private fun client(vararg servers: MockWebServer) =
        RadioBrowserClient(OkHttpClient(), servers.map { it.url("/").toString().trimEnd('/') })

    @Test fun `searchStations builds query and parses response`() = runBlocking {
        val server = MockWebServer().also { it.start() }
        server.enqueue(MockResponse().setBody(stationJson))
        val stations = client(server).searchStations(StationFilter(tag = "jazz"), offset = 30, limit = 30)
        val req = server.takeRequest()
        assertEquals("/json/stations/search", req.requestUrl!!.encodedPath)
        assertEquals("jazz", req.requestUrl!!.queryParameter("tag"))
        assertEquals("votes", req.requestUrl!!.queryParameter("order"))
        assertEquals("true", req.requestUrl!!.queryParameter("reverse"))
        assertEquals("true", req.requestUrl!!.queryParameter("hidebroken"))
        assertEquals("30", req.requestUrl!!.queryParameter("offset"))
        assertTrue(req.getHeader("User-Agent")!!.startsWith("Onda/"))
        assertEquals("u1", stations.single().stationuuid)
        assertEquals("http://icecast.radiofrance.fr/fip-midfi.mp3", stations.single().url) // url, never url_resolved
        server.shutdown()
    }

    @Test fun `fails over to next mirror on 500 and remembers it`() = runBlocking {
        val bad = MockWebServer().also { it.start() }
        val good = MockWebServer().also { it.start() }
        bad.enqueue(MockResponse().setResponseCode(500))
        good.enqueue(MockResponse().setBody(stationJson))
        good.enqueue(MockResponse().setBody(stationJson))
        val c = client(bad, good)
        assertEquals(1, c.searchStations(StationFilter()).size)
        // second call goes straight to the good mirror
        assertEquals(1, c.searchStations(StationFilter()).size)
        assertEquals(1, bad.requestCount)
        assertEquals(2, good.requestCount)
        bad.shutdown(); good.shutdown()
    }

    @Test fun `throws IOException when all mirrors fail`(): Unit = runBlocking {
        val bad = MockWebServer().also { it.start() }
        bad.enqueue(MockResponse().setResponseCode(503))
        assertFailsWith<IOException> { client(bad).topTags() }
        bad.shutdown()
    }

    @Test fun `topTags and countries parse`() = runBlocking {
        val server = MockWebServer().also { it.start() }
        server.enqueue(MockResponse().setBody("""[{"name":"jazz","stationcount":2000}]"""))
        server.enqueue(MockResponse().setBody("""[{"name":"France","iso_3166_1":"FR","stationcount":900}]"""))
        val c = client(server)
        assertEquals("jazz", c.topTags().single().name)
        assertEquals("FR", c.countries().single().iso_3166_1)
        server.shutdown()
    }
}
