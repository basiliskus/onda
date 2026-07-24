# Onda ROADMAP

## Priority Matrix

| Priority | Feature | Why | Milestone |
|----------|---------|-----|-----------|
| **P1** | playback resumption after process death | Media-button/notification resume from a dead process silently fails; `onPlaybackResumption` (#PlaybackService) unimplemented | v0.2.0 |
| **P2** | discover polish | Stale "radio-browser unreachable" banner never clears on success (`DiscoverViewModel.browseError`); Top segment resets to Tags on back-navigation | v0.2.0 |
| **P2** | release pipeline guards | No tag↔versionCode consistency check in release.yml; CI never builds main post-merge (pull_request trigger only) | v0.2.0 |
| **P3** | android auto polish | No head-unit hint when the folder isn't picked yet (empty tree); Auto pagination ignored (fine at 205 stations) | v0.2.0 |
| **P3** | pending.csv edge hardening | Multi-line-field parse asymmetry, `pending.csv (1).bak` collision variant in `recoverOrphanedBak`, consumed-entry resurrection window — all logged in PR #3 review | v0.2.0 |
| **P3** | sleep timer | Deliberately cut from MVP scope; revisit on demand | v0.2.0 |
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

**android auto polish**
Show a "open Onda on your phone to pick the synced folder" browsable hint item at the
Auto root when `PlaylistsState.NeedsFolder`. Respect `page`/`pageSize` in
`onGetChildren` if any group ever exceeds head-unit limits. (was Task 14 review notes)

**pending.csv edge hardening**
Make `PendingCsv.parse` tolerant of quoted multi-line fields (or keep the serialize-side
sanitize as the contract and document it in `docs/spec.md`); widen `recoverOrphanedBak`
matching to provider-mangled `pending.csv (1).bak` variants; add a spec line on the
consumed-entry resurrection window when desktop sync lands mid-toggle. (was PR #3
review nits, on the record)

**sleep timer**
Stop playback after N minutes; coroutine delay + `player.stop()` plus a Now Playing
sheet control. Cut from MVP scope 2026-07-23; build when actually missed.

### Milestone: v0.3.0 — Playlist editing round-trip

**playlist editing round-trip**
Ops journal per `docs/superpowers/specs/2026-07-23-ops-journal-design.md`:
`OpsJournal` + `applyOps` overlay in :core, `OpsRepository` evolving
`FavoritesRepository`, swipe-to-delete with undo snackbar, long-press move-to-group
sheet, targeted adds from Discover, `ops.jsonl` replacing `pending.csv` with
first-launch migration, contract docs updated in `docs/spec.md`. Desktop half
(`stations-merge-ops.py` + watcher) is a parallel dotfiles-repo deliverable —
coordinate landing. All journal/overlay logic tested in :core against the real
playlist fixtures.

## Completed

- v0.1.0 — MVP (2026-07-23, PR #3): watched playlists with hot reload, Media3
  playback with quirk-aware data sources, radio-browser discovery, pending.csv
  favorites round-trip, Android Auto browse tree, Catppuccin theme, signed-release
  pipeline for Obtainium. Spec: `docs/superpowers/specs/2026-07-23-onda-design.md`.

## Future

- Upstream (dotfiles repo, not this repo): fix shell-escaped URL on `stations.m3u`
  line 74 (`stations-to-m3u.py` or stations.csv) — it defeats indirection detection.
- Escrow decision follow-up: record in Bitwarden whether an escrow copy of the
  release keystore exists (see "Onda release keystore" vault entry).

## Contributing

Personal project; not accepting external contributions. Roadmap entries follow the
pipeline format: matrix row + byte-identical `**bold**` milestone entry, greppable
hooks in spec text, no bold lead-ins inside specs.
