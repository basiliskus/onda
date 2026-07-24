# Onda ROADMAP

## Priority Matrix

| Priority | Feature | Why | Milestone |
|----------|---------|-----|-----------|
| **P2** | app-module test harness | :app state machines (async bridge, banner VM, undo/compaction) shipped untested three milestones running; the v0.3.0 🔴 was a five-line test away. Action Tracker #1, GitHub #9 | v0.4.0 |

## Milestones

### Milestone: v0.4.0 — Test the platform layer

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
third-occurrence escalation from the v0.3.0 retro.)

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
- Song capture (candidate v0.4.0, brainstorm before scheduling): favorite the
  currently playing track from the ICY `streamTitle` already surfaced in
  `NowPlaying`, feeding the EXISTING desktop pipeline (pyradio titles log →
  `~/Code/Personal/data/music/scripts/pyradio-to-json.py` → catalog). Contract
  pinned 2026-07-23 from pyradio 0.9.3 source (`log.py`: title + `' (LIKED)'`):
  Onda appends liked-only lines to a SIBLING file
  `~/Sync/Radio/recordings/onda-titles.log` (never pyradio's own log — single
  writer per file) formatted exactly
  `MMM d (EEE) HH:mm |     <raw streamTitle> (LIKED)` — English locale
  explicitly (device locale would break the parse), no year in timestamp, four
  spaces after the pipe, raw title never parsed. Harvester change: read
  `*-titles.log` glob (one-line edit, data repo). Remaining design questions:
  capture UI reach (Now Playing sheet button trivial; notification/Android Auto
  custom action is the real value) and coverage honesty (no-metadata stations
  like FIP can't capture).
- Move station to a different playlist (candidate, post-v0.4.0): "Move to
  playlist…" entry in the existing long-press sheet, emitting the already-supported
  delete + targeted-add op pair — no new op type, no journal contract change, no
  desktop work (`stations-merge-ops.py` handles both today). Phone UI affordance
  only; playlist-origin stations carry empty extras (stationuuid/votes) in the add
  op, which the desktop drops anyway. Undo must restore the pair atomically
  (snapshot undo already does). Closes the gap for stations not on radio-browser,
  which currently have no phone-side path between playlists.
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
