package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.ops.Op
import dev.basiliskus.onda.core.ops.OpsJournal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Overlay, refresh, SAF write-failure injection, and pending.csv migration choreography —
 *  the orchestration around :core's ops algebra (compaction/apply are canon there). */
@OptIn(ExperimentalCoroutinesApi::class)
class OpsRepositoryTest {
    private val pendingCsvHeader =
        "# name,url,encoding,icon,profile,buffering,force-http,volume,referer,player,stationuuid,tags,country,votes"
    private val pendingCsvRow =
        "NTS,https://nts.example/stream,,https://nts.example/icon.png,,,,,,,uuid-1,eclectic,United Kingdom,812"

    @Test fun `effectivePlaylists overlays targeted add as pending row`() = runTest {
        val fake = FakePlaylistsSource(ready(station("http://synced")))
        val r = repo(fake)
        advanceUntilIdle()

        r.addTargeted(rbStation("http://t"), "stations", "B")
        r.toggle(rbStation("http://q"))
        advanceUntilIdle()
        val row = r.effectiveStations().single { it.url == "http://t" }
        assertEquals("stations", row.playlist)
        assertEquals("B", row.group)
        assertTrue(row.pending)
        assertTrue(r.effectiveStations().none { it.url == "http://q" }) // quick-star is invisible
    }

    @Test fun `starredUrls tracks quick-star toggles only`() = runTest {
        val fake = FakePlaylistsSource(ready())
        val r = repo(fake)
        advanceUntilIdle()

        r.toggle(rbStation("http://q"))
        advanceUntilIdle()
        assertEquals(setOf("http://q"), r.starredUrls.value)

        r.addTargeted(rbStation("http://t"), "stations", "A")
        advanceUntilIdle()
        assertEquals(setOf("http://q"), r.starredUrls.value) // targeted add never lights the star

        r.toggle(rbStation("http://q"))
        advanceUntilIdle()
        assertEquals(emptySet(), r.starredUrls.value)
    }

    @Test fun `toggle write failure rolls back the star and emits unavailable`() = runTest {
        val fake = FakePlaylistsSource(ready())
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()

        fake.failWrites = true
        r.toggle(rbStation("http://q"))
        advanceUntilIdle()
        assertEquals(emptySet(), r.starredUrls.value)
        assertEquals(listOf(UNAVAILABLE_MESSAGE), errors)
        assertNull(fake.files[JOURNAL])
    }

    @Test fun `move write failure rolls back the overlay`() = runTest {
        val s = station("http://u")
        val fake = FakePlaylistsSource(ready(s))
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()

        fake.failWrites = true
        r.moveToPlaylist(s, s.playlist, "B")
        advanceUntilIdle()
        val row = r.effectiveStations().single { it.url == "http://u" }
        assertEquals("A", row.group) // back in the original group
        assertFalse(row.pending)
        assertEquals(listOf(UNAVAILABLE_MESSAGE), errors)
    }

    @Test fun `refresh reports malformed journal lines and keeps good ops`() = runTest {
        val add = addOp("http://u", "stations", "A")
        val fake = FakePlaylistsSource(ready())
        fake.files[JOURNAL] = OpsJournal.serialize(listOf(add)) + "not json\n"
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()

        assertEquals(listOf("Skipped 1 malformed journal line(s)"), errors)
        assertEquals("http://u", r.effectiveStations().single().url) // good op kept
    }

    @Test fun `refresh with unreachable folder emits unavailable and keeps ops`() = runTest {
        val fake = FakePlaylistsSource(ready())
        fake.seedJournal(addOp("http://u", "stations", "A"))
        val r = repo(fake)
        val errors = collectErrors(r)
        advanceUntilIdle()
        assertEquals(1, r.effectiveStations().size)

        fake.failReads = true
        r.refresh()
        advanceUntilIdle()
        assertEquals(listOf(UNAVAILABLE_MESSAGE), errors)
        assertEquals(1, r.effectiveStations().size) // ops unchanged
    }

    @Test fun `folderTick re-reads the journal`() = runTest {
        val fake = FakePlaylistsSource(ready())
        val r = repo(fake)
        advanceUntilIdle()
        assertTrue(r.effectiveStations().isEmpty())

        fake.files[JOURNAL] = OpsJournal.serialize(listOf(addOp("http://u", "stations", "A")))
        fake.folderTicks.tryEmit(Unit)
        advanceUntilIdle()
        assertEquals("http://u", r.effectiveStations().single().url)
    }

    @Test fun `pending csv migrates to journal adds and csv is deleted`() = runTest {
        val fake = FakePlaylistsSource(ready())
        fake.files[LEGACY_CSV] = "$pendingCsvHeader\n$pendingCsvRow\n"
        val r = repo(fake)
        advanceUntilIdle()

        assertFalse(LEGACY_CSV in fake.files)
        val add = fake.journalOps().single() as Op.Add
        assertNull(add.playlist)
        assertNull(add.group)
        assertEquals("https://nts.example/stream", add.station.url)
        assertEquals(setOf("https://nts.example/stream"), r.starredUrls.value)
    }

    @Test fun `pending csv migration aborts on write failure keeping the csv`() = runTest {
        val fake = FakePlaylistsSource(ready())
        fake.files[LEGACY_CSV] = "$pendingCsvHeader\n$pendingCsvRow\n"
        fake.failWrites = true
        val r = repo(fake)
        advanceUntilIdle()

        assertTrue(LEGACY_CSV in fake.files)
        assertNull(fake.files[JOURNAL])
        assertEquals(emptySet(), r.starredUrls.value)
    }
}
