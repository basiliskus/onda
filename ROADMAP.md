# Onda ROADMAP

## Priority Matrix

The release row is always the **last** row of its milestone, so "every row
above it is struck" and "ready to tag" are the same fact. `scripts/release-ready.sh`
reads that, plus the `release-blocker` label, and answers whether it is time.
Policy: `docs/adr/0001-release-at-milestone-end.md`.

| Priority | Feature | Why | Milestone |
|----------|---------|-----|-----------|
| **P2** | ✅ ~~app-module test harness~~ | :app state machines (async bridge, banner VM, undo/compaction) shipped untested three milestones running; the v0.3.0 🔴 was a five-line test away. GitHub #9 | v0.4.0 |
| **P2** | ✅ ~~song capture~~ | Like the playing track from the Now Playing sheet or the media notification; feeds the existing desktop titles-log → catalog pipeline. Contract pinned 2026-07-23 | v0.4.0 |
| **P3** | ✅ ~~cross-playlist move~~ | "Move to playlist…" in the long-press sheet via the existing delete + targeted-add op pair; closes the only phone-side gap for stations not on radio-browser | v0.4.0 |

| **P1** | ✅ ~~browse fetch catches only IOException~~ | Premise stale — `RadioBrowserClient` already converts decode failures to `IOException` (469aaf3) and two rows pin it. Closed on evidence, no code needed. GitHub #13 | v0.5.0 |
| **P1** | ✅ ~~harden M3uParser against quotes in EXTINF names~~ | A quote forges a `tvg-logo` (Coil fetches it) or silently drops the station from the playlist. GitHub #39 | v0.5.0 |
| **P2** | ✅ ~~resumption survives move and regroup~~ | `MediaIds` hashes playlist+group, so a station moved by v0.4.0's own feature stops resuming after process death. GitHub #35 | v0.5.0 |
| **P0** | ✅ ~~release v0.5.0~~ | v0.4.0 merged but never shipped — versionName is still 0.3.0. Unblocks #33's owed hardware rows (which now run against the release APK, not a debug build), the first real `library/onda.json`, and Onda's first real input to `basiliskus/data`. Moved from mid-milestone to last row 2026-07-26 per ADR-0001 | v0.5.0 |
| **P1** | ✅ ~~song capture: emit `>>> Station:` lines~~ | Liked lines travel with a `>>> Station:` line as one untearable append (single line when the item names no station); verified against the real harvester (attribution byte-equal + negative control). GitHub #51 | v0.5.1 |
| **P2** | ✅ ~~ops journal: shared batch id + strictly-increasing ts~~ | Every op of one journal write shares the write's uuid with strictly-increasing ts, stamped at the single write point; verified against the real desktop merge (3 applied, 0 rejected, pair order kept). GitHub #38 | v0.5.1 |
| **P3** | ✅ ~~annotated release tag instructions~~ | Documented `git tag -m` with the one-line-summary convention. GitHub #52 | v0.5.1 |
| **P0** | ✅ ~~release v0.5.1~~ | Shipped 2026-08-07 with #51, #38, #52. The #33 hardware pass runs against this APK (release-first per ADR-0001 precedent — unverified is not known-broken) | v0.5.1 |
| **P1** | ✅ ~~undo integrity: #25 + #24 + #30~~ | Intent flag closes the tap race structurally (the losing interleaving cannot exist); migration self-bumps; buffered channels. 4 reproductions written failing at diagnosis, all green post-fix, control unchanged | v0.5.2 |
| **P2** | ✅ ~~polish sweep (grouped): #11 #12 #18 #28~~ | Saveable query + sheets, singleTop content routes, "Nothing to undo" on a voided tap (new row failing pre-fix), MediaIds/Resumption → core.playback, PlaylistsSource extracted, catalog version.ref. Suspend conversion declined by decision | v0.5.2 |
| **P2** | ✅ ~~canon closure: #31 decision + #36~~ | `enforced by:` promoted with a CI dangling-reference check (26 citations resolve; injected-dangling run shown failing); mutation hygiene is `scripts/mutation-run.sh` (stale/clean/killed-by-build paths all exercised); the class's canon is `docs/canon.md`. #31 #36 closed | v0.5.2 |
| **P0** | ✅ ~~release v0.5.2~~ | Shipped 2026-08-08 with #25 #24 #30, #11 #12 #18 #28, #31 #36 (PRs #60–#67). Outstanding PR #66 device checks and playback are tracked in docs/smoke-checklist.md; R1 is closed as not hardware-observable | v0.5.2 |

## Milestones

### Milestone: v0.4.0 — Harness and capture

Completed the app-module test harness, song capture and cross-playlist moves.
These changes shipped with v0.5.0; no v0.4.0 tag was published. Product contracts
live in [the spec](docs/spec.md) and [data contract](docs/data-contract.md).

### Milestone: v0.5.0 — Correct, then ship

Shipped 2026-07-26: M3U attribute parsing hardening (#39), resumption after moves
and regrouping (#35), and the v0.4.0 work. #13's premise was already fixed.
Added the executable release gate and [ADR-0001](docs/adr/0001-release-at-milestone-end.md).

### Milestone: v0.5.1 — Contract hardening

Shipped 2026-08-07: station attribution for liked tracks (#51), shared batch IDs
and strictly increasing timestamps for journal writes (#38), and annotated-tag
instructions (#52). The hardware pass tracked in #33 was completed.

### Milestone: v0.5.2 — Integrity and sweep

Shipped 2026-08-08: undo integrity and feedback (#25, #24, #30), search and sheet
state survival, singleTop navigation and voided-undo feedback (#11, #12, #18,
#28), and verification tooling (#31, #36). The remaining device checks are
tracked in [the smoke checklist](docs/smoke-checklist.md#outstanding-verification-carried-from-v052).

## Completed

Historical test totals below are retained from the release records and reproduced
by counting `@Test` declarations at the named tags; they aren't new suite runs.

- v0.5.2 — Integrity and sweep (2026-08-08, PRs #60–#68 including the release PR).
  #60–#63 close out v0.5.1 documentation; #64 plans this milestone; #65–#67
  deliver undo integrity, UI polish and verification tooling. `:core` 132
  unchanged; `:app` 75 → 84. The contract-source diff below is empty even though
  app-side journal writers changed. Outstanding device checks remain in the
  [smoke checklist](docs/smoke-checklist.md#outstanding-verification-carried-from-v052).
- v0.5.1 — Contract hardening (2026-08-07, PRs #54–#59 through the release tag).
  Station attribution (#56), shared batch IDs and monotone timestamps (#57),
  annotated-tag instructions (#58). `:core` 117 → 132; `:app` 68 → 75.
  #60's smoke-row clarification followed the tag and ships in v0.5.2; the
  hardware pass tracked by #33 was discharged on 2026-08-07/08.
- v0.5.0 — Correct, then ship (2026-07-26, PRs #21–#50 including the release PR).
  Includes untagged v0.4.0 work: app harness (#23), song capture (#29),
  cross-playlist moves (#34), plus M3U parsing (#43), resumption (#47) and the
  release gate (#45). `:core` 68 → 117; `:app` 0 → 68.
- v0.3.0 — Playlist editing round-trip (2026-07-24, PR #17): ops journal,
  optimistic overlay, snapshot undo, moves and targeted adds; desktop consumer
  coordinated in parallel. [Design](docs/superpowers/specs/2026-07-23-ops-journal-design.md).
- v0.2.0 — Daily-driver polish (2026-07-24, PRs #6–#8): playback resumption,
  release workflow guards and Discover state handling.
- v0.1.0 — MVP (2026-07-23, PR #3): watched playlists, playback, discovery,
  Android Auto and signed releases. [Design](docs/superpowers/specs/2026-07-23-onda-design.md).

Contract-source evidence, bounded to the released tags rather than a moving HEAD:

```sh
git diff --exit-code v0.5.1..v0.5.2 -- \
  core/src/main/kotlin/dev/basiliskus/onda/core/ops \
  core/src/main/kotlin/dev/basiliskus/onda/core/capture \
  core/src/main/kotlin/dev/basiliskus/onda/core/m3u
```

Expected: empty output, exit 0. The tag commits are
[v0.5.1 (`4979a01`)](https://github.com/basiliskus/onda/commit/4979a01acbc51bd97a69e3b798b2e418232b607e)
and [v0.5.2 (`ce6e8b4`)](https://github.com/basiliskus/onda/commit/ce6e8b44be51bccf0317b6c100dec33d1e25c246).
This proves those source directories match; it doesn't prove unchanged app-side
behavior or replace the device checks.

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
[release gate format](docs/release.md#readiness-gate): priority-matrix row and
versioned milestone heading. Keep the release row last and strike shipped work.
