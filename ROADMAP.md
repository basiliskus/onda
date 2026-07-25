# Onda ROADMAP

## Priority Matrix

| Priority | Feature | Why | Milestone |
|----------|---------|-----|-----------|
| **P2** | ✅ ~~app-module test harness~~ | :app state machines (async bridge, banner VM, undo/compaction) shipped untested three milestones running; the v0.3.0 🔴 was a five-line test away. Action Tracker #1, GitHub #9 | v0.4.0 |
| **P2** | ✅ ~~song capture~~ | Like the playing track from the Now Playing sheet or the media notification; feeds the existing desktop titles-log → catalog pipeline. Contract pinned 2026-07-23 | v0.4.0 |
| **P3** | cross-playlist move | "Move to playlist…" in the long-press sheet via the existing delete + targeted-add op pair; closes the only phone-side gap for stations not on radio-browser | v0.4.0 |

## Milestones

### Milestone: v0.4.0 — Harness and capture

**app-module test harness**
Stand up `app/src/test` (JUnit + kotlinx-coroutines-test + a fake
`PlaylistRepository`). First targets, in order: `OpsRepository` undo/compaction
semantics (delete-pending-then-undo, delete-after-move-then-undo, generation-CAS
voiding — the PR #17 🔴 class), the browse-banner state machine in
`DiscoverViewModel` (load, partial failure keeps banner, clear-on-both-loaded,
in-flight guards — the PR #8 🟡 class), and the resumption wait-and-resolve logic
(extract the :core seam per GitHub #10 or test in place — decide at plan time).
SAF write-failure rollback becomes testable here (fake repository failure
injection), retiring the standing matrix skip. (Action Tracker #1, GitHub #9;
third-occurrence escalation from the v0.3.0 retro.) Runs FIRST in the milestone
so the two features below get harness coverage from day one.
✅ Shipped 2026-07-25: 29 :app tests + 6 :core, every compaction-algebra rule
covered under undo; resumption extracted to :core (closes GitHub #10); SAF
fault injection retires the manual-matrix skip; CI runs `:app:testDebugUnitTest`.

**song capture**
Like the currently playing track from the ICY `streamTitle` already surfaced in
`NowPlaying`. Reach: a like button in the Now Playing sheet AND a custom action on
the media notification (Android Auto custom action deferred — Future). Disabled
when no `streamTitle` is available (no-metadata stations like FIP cannot capture —
shown disabled, never a silent no-op). Appends liked-only lines to a SIBLING file
`~/Sync/Radio/recordings/onda-titles.log` (never pyradio's own log — single writer
per file), formatted exactly `MMM d (EEE) HH:mm |     <raw streamTitle> (LIKED)`:
English locale explicitly (device locale would break the parse), no year in the
timestamp, four spaces after the pipe, raw title never parsed (contract pinned
2026-07-23 from pyradio 0.9.3 `log.py`). Capture logic gets harness tests
(formatter is :core-able; writer uses the synced-file choreography). Cross-repo:
the data-repo harvester needs its one-line `*-titles.log` glob change — hand off
when this lands.
✅ Shipped 2026-07-25 (PR #29): 13 formatter + 16 capture rows. Two corrections
from reading the real consumer — the day is zero-padded (`MMM dd`, matching
pyradio's `%b %d`), and the pipe separator is `" | "` plus a 4-space liked
indent. The title is untrusted ICY input, so all ten characters Python's
`splitlines()` splits on are neutralized; the 4-space indent is what makes a
forged `Station:` line unreachable. **Still owed: the harvester glob change —
until it widens, nothing Onda writes is ingested.**

**cross-playlist move**
"Move to playlist…" entry in the existing long-press station sheet: playlist
picker → group picker (existing + free-text, reusing `GroupPickerSheet`), emitting
the already-supported delete + targeted-add op pair — no new op type, no journal
contract change, no desktop work (`stations-merge-ops.py` handles both ops
today). Undo must restore the pair atomically (snapshot undo already covers
multi-op restoration — verify with a harness test). Playlist-origin stations
carry empty extras (stationuuid/tags/country/votes) in the add op; the desktop
drops them anyway. Closes the gap for stations not on radio-browser, which
currently have no phone-side path between playlists.

## Completed

- v0.3.0 — Playlist editing round-trip (2026-07-24, PR #17): ops.jsonl journal with
  compaction algebra and optimistic overlay in :core, OpsRepository with
  snapshot/generation-CAS undo, swipe-delete + move sheet + targeted adds,
  pending.csv migration, single derived flow feeding UI/media session/Auto.
  Desktop consumer (`stations-merge-ops.py` + watcher) landed in parallel in the
  dotfiles repo — round-trip live end to end. Retro: `RETRO.md`.

- v0.2.0 — Daily-driver polish (2026-07-24, PRs #6–#8): playback resumption after
  process death (`onPlaybackResumption` + persisted last-played id +
  `MediaButtonReceiver`), release pipeline guards (tag↔versionName check, post-merge
  main builds), discover polish (self-healing error banner, segment selection
  survives back-navigation). Retro: `RETRO.md`.
- v0.1.0 — MVP (2026-07-23, PR #3): watched playlists with hot reload, Media3
  playback with quirk-aware data sources, radio-browser discovery, pending.csv
  favorites round-trip, Android Auto browse tree, Catppuccin theme, signed-release
  pipeline for Obtainium. Spec: `docs/superpowers/specs/2026-07-23-onda-design.md`.

## Future

- Android Auto polish (unscheduled, was v0.2.0): folder-pick hint at the Auto root on
  `NeedsFolder`; honor `page`/`pageSize` in `onGetChildren` if a group ever exceeds
  head-unit limits. Build when felt.
- Sleep timer (unscheduled, was v0.2.0): cut from MVP scope 2026-07-23; build when
  actually missed.
- Android Auto custom action for song capture (deferred from v0.4.0): like a song
  from the car — the highest-value capture moment; adds Auto custom-action scope
  and DHU verification. Build after v0.4.0's sheet + notification reach ships.
- Snapshot-reorder op and drag-to-adjacent-group gesture: future candidates noted in
  the ops-journal design. Note from the v0.3.0 retro: any new op type must design
  undo with snapshot semantics — id-references die under compaction.
- ~~Upstream: fix shell-escaped URL on `stations.m3u` line 74~~ — fixed at source in
  the dotfiles repo (7fcb2d9 there) during the v0.3.0 parallel deliverable.
- Escrow decision follow-up: record in Bitwarden whether an escrow copy of the
  release keystore exists (see "Onda release keystore" vault entry).

## Contributing

Personal project; not accepting external contributions. Roadmap entries follow the
pipeline format: matrix row + byte-identical `**bold**` milestone entry, greppable
hooks in spec text, no bold lead-ins inside specs.
