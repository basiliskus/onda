# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

- Move a station to a different playlist: "Move to playlist…" in the long-press
  sheet opens a playlist picker, then the existing group picker for the target
  playlist (existing group or a new one). The move lands as a `Delete` on the
  source plus a targeted `Add` on the target, emitted from a **single** journal
  mutation — one compaction pass, one generation bump, one write, one Undo token
  that undoes both — and a failed write rolls the whole pair back and voids the
  token. Picking the station's current playlist routes to a single `Move` op
  instead. No new op type and no journal-contract change: the desktop round-trip
  is verified against the real `stations-merge-ops.py` (2 applied, 0 rejected, no
  `ops-rejected.jsonl`). **Known loss:** the desktop rebuilds an added row from
  name, url and icon, so a cross-playlist move — and an A→B→A round trip, which
  nets to a delete plus a re-add in A — blanks the `encoding` column and drops any
  pyradio column past `icon`, appending the row at the end of its target group
  instead of a curated position.
  One live row is affected today: `KALX 90.7FM Berkeley` in `public-radio.csv`,
  which carries `volume=100`. Contract: `docs/spec.md`.
- Song capture: a like button in the Now Playing sheet and a Media3 notification
  custom action append the current ICY track title to
  `recordings/onda-titles.log` in the synced folder, one line per like, in
  pyradio's own liked-line format (`MMM dd (EEE) HH:mm |     <raw title>
  (LIKED)`, English locale, no year) so the desktop harvester reads both logs
  with one parser. Onda appends to its own log only, never pyradio's. Stations
  without ICY metadata cannot be captured: the sheet button is disabled and the
  notification action is absent (the platform drops disabled custom actions).
  Android Auto shows neither — still deferred. Contract: `docs/spec.md`.
- `:app` JVM unit-test harness (JUnit 4 + kotlinx-coroutines-test, no Robolectric):
  29 tests over the state machines that shipped untested — undo/compaction
  composition, optimistic overlay, SAF write-failure rollback, `pending.csv`
  migration, and the Discover browse-banner in-flight guards. Retires the standing
  "SAF fault injection is manual-only" gap.

### Changed

- Playback resumption wait-and-resolve moved out of `PlaybackService` into `:core`
  (`resolveResumptionStation`, 6 tests); `PlaylistsState` moved to `:core` with it.
  Same semantics, including the 3s timeout and the three failure exits.
- `PlaylistsSource` gained one append seam, `appendTitlesLogLine(line)` (SAF `"wa"`
  append, no read-modify-write). It takes no filename, so appending to any file
  but Onda's own titles log is unrepresentable rather than merely discouraged.
- Test seams for the harness: `PlaylistsSource` (keeps SAF/DocumentFile behind a
  fakeable boundary), `RadioBrowserApi` (same for OkHttp), and an injectable IO
  dispatcher on `OpsRepository`. Production wiring unchanged.
- CI and `test_command` run `:app:testDebugUnitTest` alongside `:core:test`.

### Fixed

- Playback resumption survives a move or regroup. `MediaIds.station` hashes
  playlist and group alongside url, so moving a station — a two-tap gesture since
  v0.4.0's cross-playlist move — changed its media id, the persisted last-played id
  stopped resolving, and resumption after process death failed for that station
  until it was played again. The url is now persisted next to the id and consulted
  **only when the id misses**, so the id path — which `onAddMediaItems` also uses to
  turn a controller-supplied id back into a station, as Android Auto's browse tree
  does — keeps its exact previous semantics. Discover-only stations still do not
  resume: the fallback follows `station|` ids only, so an `rb|` id whose url also
  sits in a playlist stays unresolvable. Where one url appears in more than one
  playlist, which the real station set does, the first in playlist order wins.
  A move is not the only way the id can miss: a station **deleted** from the
  playlists whose url still exists elsewhere now resumes that copy — same stream,
  possibly a different name and logo — rather than failing, and the next transition
  makes the new copy the remembered one. Existing installs need no migration — they
  carry an id and no url, which behaves exactly as before until the station is next
  played. `:core` 107 → 117 tests.
- M3U parsing: a `word="value"` substring inside a station name is no longer read
  as an EXTINF attribute. `parseExtinf` splits the line at the first comma at
  **quote-depth zero** — everything left is duration + attributes, everything right
  is the display name — so such a name is kept **verbatim**, neither stripped nor
  escaped. Three failures, each reproduced before the fix: a station whose name
  carried an attribute-shaped token vanished from the playlist with no log line
  (radio-browser names carry scraped HTML, and `<img src="logo.png">` is exactly
  that shape); a name could forge `tvg-logo`, handing Coil an arbitrary URL to
  fetch; and a name could forge `group-title`, relocating the station and putting a
  group that exists in no playlist into the UI group list — unreported until now,
  and reachable without an attacker by any `key="value"` followed by a comma. A
  comma inside a quoted value (`group-title="News, Talk"`) still does not truncate
  the attribute region. A line whose quotes do not balance has no usable quote
  depth, so it falls back to the pre-fix rule — the first comma after the last
  well-formed `key="value"` match — and every line reaching that fallback parses
  exactly as it always has. (A quote that opens *after* the separator never
  reaches it, and those lines do change — that change is the fix.) The balanced
  case never takes the fallback, so an attribute-only line is still dropped
  rather than scavenged for a name. `:core` 89 → 107 tests.

## [0.3.0] - 2026-07-24

### Added

- Playlist editing round-trip: swipe-to-delete with undo, long-press move-to-group
  sheet (existing or new group), star long-press targeted add from Discover. Edits
  append to an `ops.jsonl` journal in the synced folder and render as an optimistic
  overlay (pending rows italic) until the desktop merge consumes the journal;
  playlists, media session, and Android Auto all reflect edits immediately.

### Changed

- `ops.jsonl` replaces `pending.csv`; a legacy `pending.csv` is migrated to journal
  ops on first launch and deleted.

## [0.2.0] - 2026-07-24

### Added

- Playback resumption after process death: media-button and system media-resumption
  starts now replay the last played playlist station (`onPlaybackResumption` +
  persisted last-played id). Discover-only stations deliberately do not resume.
- Release pipeline guards: the release workflow refuses a tag that does not match
  `versionName` (before any build work or keystore decode), and CI now builds
  `main` after every merge.

### Fixed

- Discover: the "radio-browser unreachable" banner now clears once both browse
  lists load after a retry, and the Tags/Countries/Top selection survives
  back-navigation (Top no longer auto-reopens its list when restored).

## [0.1.0] - 2026-07-23

### Added

- Watched-playlist player: SAF folder onboarding, hot reload (foreground rescan +
  pull-to-refresh), six playlists with sticky group headers, letter-tile logos.
- Media3 playback with quirk-aware data sources: playlist indirection (.pls/.m3u)
  resolved at play time, HLS, cross-protocol redirect self-heal, shoutcast ICY
  tolerance, ICY now-playing metadata, single retry per item plus auto-retry on
  connectivity return (foreground-reliable).
- Discover: radio-browser tags/countries/top/search browse trees with votes
  ordering, infinite scroll, tap-to-play, mirror failover.
- Favorites round-trip: star ↔ pending.csv (pyradio-compatible schema), crash-safe
  atomic writes with orphaned-backup recovery, refresh on desktop consumption.
- Android Auto browse tree (playlists → groups → stations).
- Catppuccin Latte/Frappé theme, adaptive launcher icon.
- Tag-triggered signed-release workflow for Obtainium distribution.
