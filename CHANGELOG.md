# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

### Added

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
