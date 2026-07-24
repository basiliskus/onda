# Onda ROADMAP

## Priority Matrix

| Priority | Feature | Why | Milestone |
|----------|---------|-----|-----------|
| **P1** | playback resumption after process death | Media-button/notification resume from a dead process silently fails; `onPlaybackResumption` (#PlaybackService) unimplemented — felt daily | v0.2.0 |
| **P2** | release pipeline guards | No tag↔versionCode consistency check in release.yml (Obtainium silently misses updates); CI never builds main post-merge | v0.2.0 |
| **P2** | discover polish | Stale "radio-browser unreachable" banner never clears on success (`DiscoverViewModel.browseError`); Top segment resets to Tags on back-navigation | v0.2.0 |
| **P2** | playlist editing round-trip | Phone-side add/delete/move-between-groups via an ops.jsonl journal replacing pending.csv; optimistic overlay; desktop merge closes the loop. Design: `docs/superpowers/specs/2026-07-23-ops-journal-design.md` | v0.3.0 |

## Milestones

### Milestone: v0.2.0 — Daily-driver polish

**playback resumption after process death**
Implement `MediaLibrarySession.Callback.onPlaybackResumption` in `PlaybackService` so
media-button/notification resume works after the process is killed. Resolve the last
played media id via `MediaIds` and the station index; persist the last id in
`FolderPrefs` (was final-review known-future-work item).

**discover polish**
Clear `browseError` in `DiscoverViewModel` when a later `topTags()`/`countries()` call
succeeds. Make the Tags/Countries/Top segmented selection survive back-navigation
(`rememberSaveable`). (was PR #3 review nits)

**release pipeline guards**
release.yml: fail the build if the pushed tag does not match `versionName` in
`app/build.gradle.kts` (Obtainium won't see an upgrade otherwise). ci.yml: add a
`push: branches: [main]` trigger so main is built post-merge. (was final-review
known-future-work items)

### Milestone: v0.3.0 — Playlist editing round-trip

**playlist editing round-trip**
Ops journal per `docs/superpowers/specs/2026-07-23-ops-journal-design.md`:
`OpsJournal` + `applyOps` overlay in :core, `OpsRepository` evolving
`FavoritesRepository`, swipe-to-delete with undo snackbar, long-press move-to-group
sheet, targeted adds from Discover, `ops.jsonl` replacing `pending.csv` with
first-launch migration, contract docs updated in `docs/spec.md`. Desktop half
(`stations-merge-ops.py` + watcher) is a parallel dotfiles-repo deliverable —
coordinate landing. All journal/overlay logic tested in :core against the real
playlist fixtures. Supersedes the retired "pending.csv edge hardening" item: the
journal replaces pending.csv, and the bak-collision + consumed-entry-resurrection
concerns from the PR #3 review carry into this design's file-safety and
conditional-clear semantics.

## Completed

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
  `NowPlaying` — capture raw title + station + timestamp to a synced append-only
  file the desktop can consume. Open design questions: destination format and
  desktop consumption, notification/Android Auto capture action, and coverage
  honesty (no-metadata stations like FIP can't capture). Store raw strings; don't
  parse "Artist - Title".
- Snapshot-reorder op and drag-to-adjacent-group gesture: future candidates noted in
  the ops-journal design.
- Upstream (dotfiles repo, not this repo): fix shell-escaped URL on `stations.m3u`
  line 74 (`stations-to-m3u.py` or stations.csv) — it defeats indirection detection.
- Escrow decision follow-up: record in Bitwarden whether an escrow copy of the
  release keystore exists (see "Onda release keystore" vault entry).

## Contributing

Personal project; not accepting external contributions. Roadmap entries follow the
pipeline format: matrix row + byte-identical `**bold**` milestone entry, greppable
hooks in spec text, no bold lead-ins inside specs.
