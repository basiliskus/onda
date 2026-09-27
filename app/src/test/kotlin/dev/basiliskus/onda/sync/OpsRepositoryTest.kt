package dev.basiliskus.onda.sync

import dev.basiliskus.onda.core.ops.OpsJournal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Overlay, refresh, and SAF write-failure injection — the orchestration around :core's
 *  ops algebra (compaction/apply are canon there). */
@OptIn(ExperimentalCoroutinesApi::class)
class OpsRepositoryTest {
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

    /**
     * GitHub #27 — the one-time pending.csv migration re-ran on every refresh whenever its
     * csv delete failed, re-starring stations the user had since unstarred. It is removed:
     * a leftover pending.csv is not Onda's file and must be neither read nor deleted.
     * The shape is the field one: the station was migrated long ago (already starred in the
     * journal), the csv still lists it, and the user unstars it.
     */
    @Test fun `a leftover pending csv is ignored across refreshes`() = runTest {
        val csv = "# name,url,encoding,icon,profile,buffering,force-http,volume,referer,player,stationuuid,tags,country,votes\n" +
            "NTS,http://q,,,,,,,,,uuid-1,eclectic,United Kingdom,812\n"
        val fake = FakePlaylistsSource(ready())
        fake.seedJournal(addOp("http://q"))
        fake.files[LEGACY_CSV] = csv
        val r = repo(fake)
        advanceUntilIdle()

        r.toggle(rbStation("http://q")) // unstar — the csv still lists it
        advanceUntilIdle()
        r.refresh()
        advanceUntilIdle()
        r.refresh()
        advanceUntilIdle()

        assertEquals(emptyList(), fake.journalOps(), "a refresh re-added the unstarred station")
        assertEquals(emptySet(), r.starredUrls.value)
        assertEquals(csv, fake.files[LEGACY_CSV], "pending.csv was modified or deleted")
        assertEquals(0, fake.readsOf(LEGACY_CSV), "pending.csv was read")
    }

    private companion object {
        /** The retired favorites file — written by v0.1.0–v0.2.0, replaced by ops.jsonl in v0.3.0. */
        const val LEGACY_CSV = "pending.csv"
    }
}
