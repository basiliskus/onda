# Onda ROADMAP

## Priority Matrix

The release row is always the **last** row of its milestone, so "every row
above it is struck" and "ready to tag" are the same fact. `scripts/release-ready.sh`
reads that, plus the `release-blocker` label, and answers whether it is time.
Policy: `docs/adr/0001-release-at-milestone-end.md`.

| Priority | Feature | Why | Milestone |
|----------|---------|-----|-----------|
| **P2** | ✅ ~~app-module test harness~~ | :app state machines (async bridge, banner VM, undo/compaction) shipped untested three milestones running; the v0.3.0 🔴 was a five-line test away. Action Tracker #1, GitHub #9 | v0.4.0 |
| **P2** | ✅ ~~song capture~~ | Like the playing track from the Now Playing sheet or the media notification; feeds the existing desktop titles-log → catalog pipeline. Contract pinned 2026-07-23 | v0.4.0 |
| **P3** | ✅ ~~cross-playlist move~~ | "Move to playlist…" in the long-press sheet via the existing delete + targeted-add op pair; closes the only phone-side gap for stations not on radio-browser | v0.4.0 |

| **P1** | ✅ ~~browse fetch catches only IOException~~ | Premise stale — `RadioBrowserClient` already converts decode failures to `IOException` (469aaf3) and two rows pin it. Closed on evidence, no code needed. GitHub #13 | v0.5.0 |
| **P1** | ✅ ~~harden M3uParser against quotes in EXTINF names~~ | A quote forges a `tvg-logo` (Coil fetches it) or silently drops the station from the playlist. GitHub #39 | v0.5.0 |
| **P2** | ✅ ~~resumption survives move and regroup~~ | `MediaIds` hashes playlist+group, so a station moved by v0.4.0's own feature stops resuming after process death. GitHub #35 | v0.5.0 |
| **P0** | ✅ ~~release v0.5.0~~ | v0.4.0 merged but never shipped — versionName is still 0.3.0. Unblocks #33's owed hardware rows (which now run against the release APK, not a debug build), the first real `library/onda.json`, and Onda's first real input to `basiliskus/data`. Moved from mid-milestone to last row 2026-07-26 per ADR-0001 | v0.5.0 |
| **P1** | ✅ ~~song capture: emit `>>> Station:` lines~~ | Liked lines travel with a `>>> Station:` line as one untearable append (single line when the item names no station); verified against the real harvester (attribution byte-equal + negative control). GitHub #51 | v0.5.1 |
| **P2** | ✅ ~~ops journal: shared batch id + strictly-increasing ts~~ | Every op of one journal write shares the write's uuid with strictly-increasing ts, stamped at the single write point; verified against the real desktop merge (3 applied, 0 rejected, pair order kept). GitHub #38 | v0.5.1 |
| **P3** | ✅ ~~release template: fix the Tag & Publish `git tag` command~~ | Stage 10 now documents `git tag -m` with the one-line-summary convention. GitHub #52 | v0.5.1 |
| **P0** | ✅ ~~release v0.5.1~~ | Shipped 2026-08-07 with #51, #38, #52. The #33 hardware pass runs against this APK (release-first per ADR-0001 precedent — unverified is not known-broken) | v0.5.1 |
| **P1** | undo integrity: #25 + #24 + #30 | One subsystem, three silent failures: the undo-vs-mutate token race is field-sighted (byte-proven on #25, 2026-08-07); #24's skipped generation bump leaves stale tokens live; #30's replay-less messages drop the one report that matters (a lost WriteFailed). Chosen at the 2026-08-08 strategy session | v0.5.2 |
| **P2** | polish sweep (grouped): #11 #12 #18 #28 | Four tiny items that individually lose every triage; one `--group` pipeline run, one PR. Undo-feedback half of #18 lands after the P1 row settles token semantics | v0.5.2 |
| **P2** | canon closure: #31 decision + #36 | Decide `enforced by:` promote-or-drop (one negative data point on record), reframe #31 as a canon doc so it stops absorbing scope; #36's stale-XML guard becomes a script, not vigilance | v0.5.2 |
| **P0** | release v0.5.2 | Last row of its milestone per ADR-0001 | v0.5.2 |

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
✅ Shipped 2026-07-25 (PR #34): the pair emits from one `mutate` — one compaction,
one generation bump, one journal write, one undo token; splitting it reddens 8 of
68 rows. A same-playlist target routes to a single `Op.Move`, and the group-move
sheet now shares that one entry point, so `OpsRepository.move()` is gone and a
group move gained Undo. Desktop round-trip verified live against the real
`stations-merge-ops.py`. Known loss: an add rebuilds the row from name/url/icon,
blanking `encoding` and dropping later columns (one live row: KALX, `volume=100`).

### Milestone: v0.5.0 — Correct, then ship

Chosen at the v0.4.0 boundary via `/pipeline-strategy` (deep mode). The
recommendation was to release first; the chosen path fixes first so the
release carries the fixes. Session record:
`docs/strategy-sessions/2026-07-26-v0.5.0.md`.

~~The release sits in the middle of this milestone, not at the end.~~
Overridden 2026-07-26 by ADR-0001: the release is now the **last** row, after
#38. The strategy session's reasoning — that the tag should carry the
correctness fixes — survives intact; putting the release last satisfies it
strictly harder, and makes "milestone complete" and "ready to tag" the same
condition instead of two the reader has to keep apart.

**browse fetch catches only IOException** — ✅ already fixed, no work needed
Struck 2026-07-26 by `/pipeline-next`'s staleness check before any code was
written. `RadioBrowserClient` converts both `SerializationException` and
`IllegalArgumentException` into `IOException` before rethrowing
(`RadioBrowserClient.kt:91-95`), so the crash is unreachable; `469aaf3` also made
a garbage-serving mirror fail over rather than surface an error, and refuses to
let it become the sticky preferred mirror. Pinned by two rows in
`RadioBrowserClientTest`. The strategy session that scheduled this scored it from
the issue title without checking the code — see the v0.5.0 session record.

**harden M3uParser against quotes in EXTINF names**
The attribute regex has no escape syntax, and `parseExtinf` matches attributes
over the whole line, so a `word="value"` substring inside a station name forges
an attribute or shifts the comma index until the entry parses empty and is
dropped with no log line. Station names come from radio-browser — third-party
data. Not exploitable today (the desktop sanitizes at the writer, and
`IndirectionParser` never reaches this parser), but the safety property lives in
another repository with nothing enforcing it here. GitHub #39.

✅ Shipped 2026-07-26 (PR #43): three bugs, not the two the issue described —
diagnosis also found a forged `group-title` producing a phantom group in the UI
list, reachable without an attacker. Attributes are now matched only left of the
first comma at quote-depth zero; an unbalanced quote falls back to the pre-fix
anchor so those lines parse as they always did. `:core` 89 → 107.

**resumption survives move and regroup**
`MediaIds` hashes playlist + group into the media id, and the last-played id is
persisted for `onPlaybackResumption`. Moving a station — now a two-tap gesture —
changes both components, so it stops resuming. Testable in the `:core` harness
via `resolveResumptionStation`. GitHub #35.

**release v0.5.0** — the last row, per ADR-0001
One tag carrying the unreleased v0.4.0 milestone AND these fixes. Nothing from
v0.4.0 has ever been installed, so a v0.4.0 tag would be superseded within
minutes. Unblocks the eleven owed hardware rows (#33), Onda's first real input
to `basiliskus/data`, and with it the first real `library/onda.json`.

**The hardware rows run after the tag, not before.** ~~#33 gates the tag.~~
Reversed 2026-07-26. `release-blocker` means *known must-not-ship*; #33 is
**unverified**, which is a different state — nothing in it is known broken, and
M1, M5 and M11 have been owed precisely because no build was ever installed to
run them against. Labeling it a blocker made the dependency circular and would
have devalued the label for real defects. The rows now run against the release
APK from Obtainium, which is strictly better evidence than a sideloaded debug
build: `minifyEnabled` and release signing are exercised, and no uninstall or
SAF re-grant is needed. If a row fails, what it surfaces gets `release-blocker`
and v0.5.1 fixes it.

Runs as its own pipeline: copy `.pipeline-templates/release-state.json` to
`.pipeline-state/release-v0.5.0.json`, set `target_version`, then
`/pipeline-run`. Stage 1 refuses to start unless `scripts/release-ready.sh`
exits READY.

### Milestone: v0.5.1 — Contract hardening

**song capture: emit `>>> Station:` lines**
Emit a `>>> Station: <name>` line alongside each liked line, matching
pyradio's shape, so the harvester's station-aware pass can attribute Onda
likes (today: 5 entries, 0 with station — measured against the first real
`library/onda.json`). The name is already read at the like site
(`mediaMetadata.station`); `streamTitleOf` already takes it. Constraints from
GitHub #51: the station name is untrusted ICY input and goes through the same
line-boundary neutralization as the title (log-injection hole PR #29 closed
must stay closed); decide emit-per-like vs on-change; `appendTitlesLogLine`
takes one line, so two lines means either two appends (torn-write window) or
one append with a controlled `\n` — the seam's no-filename property survives
either way. Scoped 2026-08-07: added because the v0.5.0 ingestion made the
gap visible, and the station that motivated data's parser fix is the one
whose name Onda discards.

✅ Shipped 2026-08-07. Chose emit-per-like (stateless, rotation-proof) and
one append with the `\n` controlled by `:core`'s `TitlesLog.likedRecord` —
the pair cannot tear, and a station line never appears without its liked
line. Verified against the real `radio-titles-to-json.py` (attribution
byte-equal, negative control clean). Contract: `docs/spec.md`.

**release template: fix the Tag & Publish `git tag` command**
One-line fix in `.pipeline-templates/release-state.json` stage 10:
`git tag -m "<one-line summary>" vX.Y.Z`, matching the four existing annotated
tags. Loud failure, but it sits in the one stage ADR-0001 exists to protect,
and a release is rare enough that the next executor rediscovers it by failing.
Must merge before this milestone's release run copies the template. Trivial
single-commit PR per the direct-to-main threshold. GitHub #52.

✅ Shipped 2026-08-07: stage 10 documents `git tag -m "<one-line summary>"`
with the existing tags' style (`git tag -n1`) and records why the bare form
fails.

**ops journal: shared batch id + strictly-increasing ts**
`UndoToken` gains an id at construction; every op written under an armed token
carries a `batch` field — uniform, including single-op mutations, so an absent
field has exactly one meaning. Plus strictly-increasing `ts` within a batch,
since the pair currently shares a millisecond and same-key ordering is
load-bearing on the desktop. Contract agreed on GitHub #38.

Moved out of v0.5.0 on 2026-07-26. Two reasons, both checked rather than
assumed: #38 itself records that the gap is "currently unreachable, because the
phone builds its playlist list from the same set the desktop allows", and
`.dotfiles#41` closed as done-as-far-as-it-should-go after shipping the
inference-based warning — so no desktop consumer is waiting, and there is no
open issue there to receive the field. Refiling on `.dotfiles` is part of this
task.

*Topology correction, found at plan time 2026-08-07:* the merge script moved
to `data/radio/scripts/stations-merge-ops.py` (dotfiles#35; `.dotfiles` keeps
only the launchd wrapper), so the receiving issue belongs to
**basiliskus/data**, not `.dotfiles`.

✅ Shipped 2026-08-07. `batch` stamped centrally in `mutate()` before
compaction — uniform across call sites, migration included — with
`@EncodeDefault(NEVER)` so absence is the only spelling of "pre-#38".
Verified against the real merge script over a scratch corpus (3 applied,
0 rejected, order ledger-verified). The desktop drop-batch half is data#16; contract amendments (two compaction caveats) in
`docs/superpowers/specs/2026-07-23-ops-journal-design.md`.

**release v0.5.1** — the last row, per ADR-0001
Ships #51, #38, #52 and anything the v0.5.0 hardware pass turns up. #51 is
the milestone's user-visible value; it is what makes this release worth
cutting rather than waiting.

Correction: the priority matrix and this section previously said
`basiliskus/data` was blocked on #38. That was wrong in both places. `data` is
the music harvester (titles log to JSON catalog); its Onda dependency was song
capture, which shipped — `data#4` is closed and PRs #5 and #6 are merged. The
ops journal is consumed by `stations-merge-ops.py` in `.dotfiles`.

### Milestone: v0.5.2 — Integrity and sweep

Chosen at the 2026-08-08 strategy session (deep mode, three paths presented;
session record: `docs/strategy-sessions/2026-08-08-v0.5.2.md`). The debt-burn
path: the issue pool is 11 items all dated 2026-07-24/25, and this milestone
retires everything except the coverage/process tail (#26, #27), which the P1
row's own test seams partially absorb.

**undo integrity: #25 + #24 + #30**
The token-and-feedback subsystem made honest under races. #25: an undo tap can
beat the mutate coroutine and silently no-op — field-sighted 2026-08-07 with
byte-proof on the issue; the fix must close the arm-before-tap window (or make
the no-op loud), and the design decides between arming the token synchronously
and giving undo a pending-intent semantics. #24: a refresh can skip the
generation bump after migration, leaving a stale token live — the inverse
failure (undo fires when it must not). #30: like/error messages are
best-effort with a replay-less one-slot buffer; a dropped WriteFailed is the
case that matters — `Channel(BUFFERED).receiveAsFlow()` is the named primitive,
for both repositories at once. Design constraint: whatever token semantics
land, the desktop contract is untouched (journal writes stay whole-file,
batch-stamped).

**polish sweep (grouped): #11 #12 #18 #28**
One `--group` run: Discover `query` rememberSaveable (#11), `launchSingleTop`
on the stations route (#12), ops-journal UX polish (#18 — voided-undo
feedback rides the P1 row's semantics, sheet rotation survival stays), PR #23
style follow-ups (#28).

**canon closure: #31 decision + #36**
The `enforced by:` trial gets its verdict (promote with a dangling-reference
check, or drop — one negative data point: the map went stale inside a single
commit on PR #47). #31 then converts to a canon document listing the defect
class's substrates and instruments, and closes as an issue. #36's driver
hygiene becomes `scripts/` tooling.

**release v0.5.2** — the last row, per ADR-0001.

## Completed

- v0.5.1 — Contract hardening (2026-08-07, PRs #54–#58): station attribution
  lines so the harvester can fill `station` for Onda likes (#51, PR #56 —
  emission and ingestion verified against the real consumer with a negative
  control; the on-phone half is #33's), shared batch id + monotone
  fixed-width ts on journal ops (#38, PR #57, desktop drop-batch half filed as
  data#16), and the release-template tag fix (#52, PR #58). `:core` 117 → 132,
  `:app` 68 → 75. Hardware rows (#33) deliberately run AFTER this tag, against
  this APK. Retro: `RETRO.md` (pending milestone retro).

- v0.5.0 — Correct, then ship (2026-07-26, PRs #21–#49): first tag since v0.3.0,
  carrying the never-released v0.4.0 milestone (`:app` test harness, song capture,
  cross-playlist move) plus two correctness fixes — `M3uParser` attribute parsing
  (#39, PR #43) and resumption surviving a move or regroup (#35, PR #47). Also
  landed the release machinery itself: `scripts/release-ready.sh`, the release
  pipeline template, and ADR-0001. `:core` 68 → 117; `:app` 0 → 68, the module
  having had no test source at all at v0.3.0. The eleven owed
  hardware rows (#33) run against this APK, not before it. Retro: `RETRO.md`.

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
