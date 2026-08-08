# RETRO

## Action Tracker

Items from retrospectives that need resolution. Every item must have a GitHub
issue or be explicitly closed with a reason.

| # | Action | Source | GitHub | Status | Notes |
|---|--------|--------|--------|--------|-------|
| 1 | :app-module test harness (undo/compaction, banner VM, resumption) | PR #6, PR #8, PR #17 | #9 | Closed | Shipped in PR #23; :app 0 → 68 rows by milestone close. Class narrowed, not closed — see row 15 |
| 2 | Extract resumption wait-and-resolve seam into :core | PR #6 | #10 | Closed | Extracted as `resolveResumptionStation` in PR #23 (6 :core rows); semantics preserved incl. raw TimeoutCancellationException |
| 3 | Persist Discover search text (`query` rememberSaveable) | PR #8 | #11 | Open | Polish pass |
| 4 | Stations route missing `launchSingleTop` | PR #8 | #12 | Open | Pre-existing 🔵 |
| 5 | Browse fetches catch only `IOException` | PR #8 | #13 | Closed | Premise stale — `RadioBrowserClient` already converts decode failures to `IOException` (469aaf3), pinned by two `RadioBrowserClientTest` rows. Found by `/pipeline-next`'s staleness check, closed without code |
| 6 | Confirm tag↔versionName guard ran when v0.2.0 is tagged | PR #7 | — | Closed | Guard ran green on the v0.2.0 tag (run 30107921024, step "Verify tag matches versionName": success) |
| 7 | Ops-journal UX polish: voided-undo feedback, sheet rotation survival | PR #17 | #18 | Open | One polish pass with #3 (#11) |
| 8 | Relay desktop contract notes (fresh-UUID/url-dedup backstop; move collapses dup urls) | PR #17 | — | Closed | Relayed to the dotfiles agent during the run; url-dedup kept mandatory in stations-merge-ops.py |
| 9 | Comments asserting a testable guarantee must name the row enforcing it | PR #23, PR #29, PR #34, PR #43, PR #47, PR #50 | #31 | Open | Scope extended at v0.5.0 retro: the class migrated to PR prose, CHANGELOG, an API shape, and a release note — any artifact asserting a testable fact. `enforced by:` promote-or-drop now has a negative data point (map went stale inside one commit, PR #47) |
| 10 | Mutation tables must mark each entry APPLIED vs PREDICTED, and a sample of PREDICTED must be executed | PR #34 | — | Closed | Applied to `.pipeline-templates/{feature,bugfix,refactor}-state.json` Code Review stage this commit. Both v0.4.0 coverage gaps were hiding in the PREDICTED column |
| 11 | Verification stages must not silently replay: `--rerun-tasks` + XML counts; mutation drivers need `--continue` + cleared results dirs | PR #23, PR #34 | #36 | Open | Template item applied this commit; #36 tracks the driver-hygiene half, which can misattribute results rather than merely omit them |
| 12 | Manual-matrix rows carry a `Requires:` field; a plan's sole-mitigation row becomes a blocking carry | PR #23, PR #29, PR #34 | #33 | Closed | Template item applied at v0.4.0 retro; PR #43's matrix came back empty because of it. Hardware session 2026-07-26 (recorded on #33) discharged part of the debt against the release APK; nine rows remained; ALL discharged 2026-08-07/08 against the v0.5.1 release APK (full record on #33, closed). Row 17's creation half survives as a standing checklist row |
| 13 | Planning: mechanical self-join — every enumerated case gets a test row | PR #34 | — | Closed | Applied to the feature template's Planning stage this commit. Case 5c was named in one table and absent from the next, and shipped uncovered |
| 14 | Planning: do not pre-litigate a simplification | PR #34 | — | Closed | Applied to the feature template's Planning stage this commit. Four layers ratified "group move keeps no Undo"; the user's reversal deleted three things and closed half of #18 |
| 15 | Residual :app risk migrated into Compose — the harness bought coverage where it was cheapest | PR #23, PR #29, PR #34 | #26 | Open | #26 (pagination in-flight) survived the whole milestone uncovered; both user-found defects in PR #29 were in the layer with zero JVM reach |
| 16 | Desktop-side defects found by Onda review | PR #34 | .dotfiles#38–42 | Closed | All five fixed and closed upstream (`.dotfiles` a0aac52, dc544e1). #40 bounced back a threat-model correction — station names are third-party radio-browser data, not user-typed — tracked here as #39 |
| 17 | Titles-log glob must widen before song capture is ingested | PR #29 | data#4 | Closed | data#4 closed; `data` PRs #5/#6 merged. First real ingestion succeeded 2026-07-26: Onda's likes from `onda-titles.log` landed in `library/onda.json`. Successor defects tracked in row 24 |
| 18 | Stage agents must persist artifacts a later stage will audit | PR #23, PR #34 | — | Closed | Applied to the Code Review stage of all three templates this commit. PR #34's mutation table existed only in a subagent report, so Test Execution had to re-derive it |
| 19 | Docs re-check when a run corrects a factual claim | PR #29 | — | Closed | Applied to the Documentation stage of all three templates this commit. The forced-locale rationale was fixed in code while its copy in `docs/spec.md` survived a full fix pass |
| 20 | Emit a shared batch id on ops written together | .dotfiles#41 | #38 | Closed | Shipped in PR #57 (v0.5.1): batch stamped centrally, monotone fixed-width ts. Receiving issue landed as data#16 (topology had moved) and the desktop implemented drop-batch the same day. Field-verified incl. the mixed-batch caveat shape |
| 21 | Harden `M3uParser` against quotes in EXTINF names | .dotfiles#40 | #39 | Closed | Shipped in PR #43: attributes parsed only before the first quote-depth-zero comma; hostile names kept verbatim. Three bugs fixed (silent drop, forged `tvg-logo`, forged `group-title` — the third unreported until diagnosis reproduced it). `:core` 89 → 107 |
| 22 | Song capture: emit `>>> Station:` lines so likes can be attributed | Retro v0.5.0 | #51 | Closed | Shipped in PR #56 (v0.5.1); field-verified end-to-end 2026-08-07: real like attributed byte-equal through a station switch, negative control clean |
| 23 | Release template's Tag & Publish documents a `git tag` command that fails | PR #50 | #52 | Closed | Fixed in PR #58 and consumed the same day by the v0.5.1 release run — the tag step worked first try for the first time |
| 24 | Titles-harvester defects: artist/title split reversed, entity anomaly | Retro v0.5.0 | data#8 | Closed | All resolved data-side by 2026-08-08: `_fields` fix committed, KCRW no-space layout pinned with a real-line doctest (`cfa` cfc6aa89), data#8 closed, `library/onda.json` regenerated (5/5 entries carry `raw_title`). Verified from this side by inspection |
| 25 | Prose-diff verification: quantifier audit, derive-not-transcribe, cross-repo claims cite sha+command | PR #43, PR #50 | — | Closed | Applied to the Documentation stage of all three templates at the v0.5.0 retro. Catches 100% of the milestone's eight #31-class prose defects by two mechanical rules |
| 26 | Review stages declare their instrument; a re-review must change it | PR #43, PR #47 | — | Closed | Applied to the Code Review stage of all three templates at the v0.5.0 retro. `lastOrNull` survived five suite-measuring stages; the write-path defect was reachable only by reading the type |
| 27 | Diagnosis prototypes run on discriminating inputs; key changes count collisions in real data | PR #43, PR #47 | — | Closed | Applied to the bugfix Diagnosis stage at the v0.5.0 retro. PR #43 prototyped on the one input where all candidate rules agree and paid with three rewrites; PR #47's collision count (21/205) killed the obvious fix before it was written |
| 28 | Bugfix Fix stage said "update CHANGELOG.md"; Gate 1 forbids it | PR #47 | — | Closed | Contradictory item removed from the bugfix template at the v0.5.0 retro; CHANGELOG belongs to the Documentation stage (Gate 2) |
| 29 | Run `/pipeline-roadmap-audit` at strategy time, not one task at a time | PR #42, PR #43 | — | Closed | Applied to pipeline-strategy's survey stage (`~/.claude/skills/pipeline-strategy/SKILL.md`) at the v0.5.0 retro. Two stale-premise closures were both caught downstream by `/pipeline-next` |
| 30 | External-existence claims ("filed on X", "exists in Y") verified by command at commit time | PR #57, PR #59, Retro v0.5.1 | — | Closed | Applied to the Documentation stage of all three templates at the v0.5.1 retro. Three forward-dated claims in one milestone; the quantifier audit is structurally blind to the shape |
| 31 | Undo silent no-op — field-sighted, byte-proven | Retro v0.5.1 | #25 | Open | First field evidence posted on #25 (mixed-batch journal pair, 2026-08-07). Unreproduced under controlled re-runs; v0.5.2 triage priority — user-visible and data-affecting |
| 32 | Release verification: fresh download paths, contradiction rule, XML-mtime check | PR #59 | — | Closed | Applied to the release template's Release Verification stage at the v0.5.1 retro. The stage-11 near-miss compared the old release against itself after the comparison script clobbered its own input |

## v0.5.1 — Contract hardening (PRs #54–#60)

**Forward link**: v0.5.2 planned in a deep `/pipeline-strategy` session on
2026-08-08 — see `docs/strategy-sessions/2026-08-08-v0.5.2.md`. Chosen path:
debt burn (undo-integrity cluster led by the field-sighted #25, grouped polish
sweep, canon closure), retiring the 2026-07-24/25 issue cohort except its
coverage tail (#26, #27), which the P1 row's test seams partially absorb.

**Date**: 2026-08-08
**Scope**: Both outbound files gained the field their desktop consumer was
waiting for — station attribution on likes (#51, PR #56) and batch atomicity
on journal ops (#38, PR #57) — plus the release-template tag fix (#52, PR
#58), the release itself (PR #59), and the row-17 rewrite (PR #60). Released
2026-08-07 and **fully field-verified the same day**: every owed hardware row
(#33, eleven rows two milestones deep) discharged against the release APK,
both cross-repo contracts exercised against their real consumers, and the
desktop's drop-batch half (data#16) implemented hours after being filed.
**Tests at close**: 207 (`:core` 132, `:app` 75) — derived at retro time via
`--rerun-tasks` + XML count, both suites green.

### What We Learned

**1. The #31 class's newest substrate: the forward-dated claim — a plan stated as fact.**
Three instances in one milestone, all caught by a *different* instrument than
the audits that exist: "The desktop drop-batch half is filed on `data`"
written while the filing was deliberately deferred (PR #57 — caught by a
workflow reviewer running `gh issue list` against the claimed repo); "both
halves verified against the real consumer" while the hardware half was
explicitly owed (PR #59 — caught by the release reviewer); and the ROADMAP's
".dotfiles refile" instruction aimed at a repo that had stopped owning the
script (caught by plan-time premise verification). The quantifier audit is
structurally blind to all three: none contains a quantifier. The claim shape
is "X exists in an external system", and its evidence is a command.
**Action taken**: Closed — external-existence-claim verification item added to
the Documentation stage of all three templates this commit (Action Tracker
#30).

**2. Instrument rotation is now confirmed by a controlled sequence, not just argued.**
PR #57 ran three review layers, each catching what the previous structurally
could not: self-review (doc-vs-code) produced the test-strengthening rows;
the mutation round's M6/M7 were killed by *exactly those rows* and would have
survived the original suite, while its own extra probe (M8) found the
Add-only byte pin; the workflow round then found six verified findings after
two green rounds — including the false forward-dated claim and two
ts-contract gaps — using instruments (gh-verification, contract stress-read)
neither prior round had used. The v0.5.0 retro's instrument-declaration canon
(AT #26) predicted this; v0.5.1 measured it.
**Action taken**: Closed — AT #26's template item is the mechanism, unchanged;
this milestone is its confirming evidence (PRs #56–#57, review rounds cited).

**3. Field verification found things in both directions no desk instrument reaches.**
The hardware pass validated two defenses against their real threats — the
consumed-ids ledger discarded a genuine Synology sync-echo replay (the
crash-window defense meeting an unplanned real replay), and the mixed-batch
compaction shape (data#16 caveat (a)) occurred in the wild on day one and
applied correctly — and it produced the first field sighting of #25: one undo
silently failed to reach the journal, provable only from captured journal
bytes (a mixed-batch pair no pure phone state could produce). The
enabling practice was **desktop-side journal-transition watching during the
device pass**, which turned UI observations into byte evidence.
**Action taken**: Open — #25 carries the full field evidence (comment posted
2026-08-07) and a new Action Tracker row #31 for v0.5.2 triage; the
journal-watcher practice added to `docs/smoke-checklist.md`'s preamble this
commit.

**4. A verification instrument can corrupt its own evidence.**
Release stage 11: the new APK is byte-count-identical to v0.5.0's (padding
absorbs small dex deltas), which correctly triggered a hash comparison — and
the comparison script's fallback branch silently overwrote the artifact under
test with the old release's asset, producing "identical" from comparing
v0.5.0 against itself. The already-passed manifest check contradicted the
result, which is what exposed it. Two rules: verification downloads go to
fresh paths, and a result that contradicts an earlier check in the same pass
means a broken instrument, not a wrong earlier check.
**Action taken**: Closed — both rules added to the release template's Release
Verification stage this commit (Action Tracker #32); the reviewer's
XML-mtime-vs-commit-time check added alongside (it had no template row).

### Insights

- **A receiving issue that carries the full contract gets implemented fast and
  right.** data#16 was filed with the compaction caveats, the hygiene rule,
  and the consumer's own code-comment expectations quoted back at it; the
  data agent shipped drop-batch enforcement plus review fixes within hours.
  Contrast the `.dotfiles#41` era, where the ask travelled as prose and
  landed as inference.
- **The undo-authority observation**: with auto-sync, an undo's real window is
  sync latency + the merge's 5s debounce — shorter than its snackbar. Benign
  under manual sync (this phone's setup); recorded on #33 for the day sync
  goes automatic.
- **The quantifier-production rate still hasn't moved** — third milestone.
  The audits catch what the writer keeps writing. Two data points say the
  gates are load-bearing infrastructure, not training wheels.
- **KCRW broadcasts unspaced `Title-Artist-Album`** — a second real-world ICY
  convention variant, captured with its station name attached, exactly the
  input the data repo's deferred station-aware pass needs.
- **v0.5.1 is the first release with nothing owed against it** — every JVM
  row, every hardware row, both cross-repo contracts live-verified.

### Review Stats

| Metric | PR #56 | PR #57 | PR #59 | Total |
|--------|--------|--------|--------|-------|
| Tests added | 10 | 12 | 0 | 22 |
| Review findings (all layers) | 8 | 17 | 3 | 28 |
| Findings fixed pre-merge | 5 | 12 | 1 | 18 |
| Mutations applied / survivors | 6 / 0 | 8 / 1 (fixed) | — | 14 / 1 |
| #31-class prose/claim defects | 1 | 3 | 1 | 5 |
| CI failures | 0 | 0 | 0 | 0 |

Unfixed findings were accepted-as-recorded bounds (severity 🔵), each stated
in its PR body. Tests-added derived from the per-PR XML chains
(117→123→132 / 68→72→75).

### Process Improvements Applied

**During the milestone**: release template's tag command fixed (#52, PR #58);
smoke row 17 rewritten around consumer ingestion (PR #60, user decision).
**This commit**: external-existence-claim item (Documentation stage, all three
templates); fresh-paths + contradiction-rule + XML-mtime items (release
template, Release Verification); journal-watcher note (smoke-checklist
preamble).
**Cross-repo**: data#16 filed and implemented; data#8 and the harvester's
`_fields` fix remain OUT-OF-REPO (Action Tracker #24).

### Open Items

- [ ] #25 undo silent no-op — now field-sighted, Action Tracker #31 (v0.5.2
  triage priority)
- [ ] Row 17 creation half (fresh-folder SAF ladder) — standing checklist row,
  not owed debt
- [x] Verify data's drop-batch implementation against data#16's caveats —
  verified 2026-08-08 by reading `merge()`: torn-line batch poisoning,
  membership-based abort (drop the members present, never expect N),
  split-batch partner awareness, batch-value hygiene in `is_wellformed`.
  All caveats honored; data#16 and data#19 both closed
- [ ] Remaining tech debt: GitHub #11, #12, #18, #24, #26, #27, #28, #30, #31,
  #36

## v0.5.0 — Correct, then ship (PRs #40–#50)

**Date**: 2026-08-07
**Scope**: Two correctness fixes — `M3uParser` quote hardening (PR #43, closing
#39) and resumption surviving a move (PR #47, closing #35) — plus the executable
release gate with ADR-0001 (PR #45) and the first pipeline-run release (PR #50):
v0.5.0 tagged, signed, APK verified on the GitHub release. Seven docs PRs
(#40–#42, #44, #46, #48, #49) carried the strategy session, two stale-premise
closures, and the gate's own corrections. First milestone that actually
*shipped* since v0.3.0 — v0.4.0 was merged but never tagged, which is what the
gate exists to prevent.
**Tests at close**: 185 (`:core` 117, `:app` 68) — derived at retro time via
`--rerun-tasks` + XML count, both suites green.

### What We Learned

**1. The #31 defect class lives wherever verified work gets described — and it moved substrates four times.**
v0.4.0 counted six instances, all in source comments. This milestone produced
eight more, none of them in source: four false claims in PR #43's prose and
CHANGELOG (an inverted cross-repo history claim, an over-broad equivalence
claim, an invented `ftp://` justification, a transcribed test count), an API
shape in PR #47 (`FolderPrefs`' id/url pair, whose must-describe-the-same-station
invariant was held by a KDoc), and a correct-somewhere baseline in PR #50's
release notes (`:core` 89 — right in PR #43's context, wrong two PRs later).
In every case the underlying engineering was correct; the sentence about it was
not. Mutation testing reaches none of these surfaces. The countermeasures are
mechanical: audit the *prose diff* for quantifiers (`always`, `never`, `all`,
`cannot`, `since`), derive rather than transcribe any number a command can
produce, and cite a sha + command for any claim about another repo's history.
**Action taken**: Open — Action Tracker #9 (GitHub #31) scope extended from
source comments to any artifact asserting a testable fact (comment posted);
quantifier-audit, derive-not-transcribe, and cross-repo-citation items added to
the Documentation stage of all three templates this commit (Action Tracker #25).

**2. A defect survives exactly as long as the stages hunting it share an instrument.**
PR #43's chain — reproduce, invert-and-observe, read-doc-against-code, apply
mutations, differentially execute — produced five disjoint finding classes,
each invisible to every instrument upstream of it. `lastOrNull` survived five
stages because all of them measured against the test suite, and the mutant
passes the suite; only comparing against the other parser could see it. PR #47
is the converse: the write path has no JVM reach, so no mutation could ever
find the `setLastPlayed` pairing risk — a human reading the type was the only
instrument left, and it was the reviewer's. A re-review that repeats the
previous round's instrument is nearly free of information.
**Action taken**: Closed — instrument-declaration item added to the Code Review
stage of all three templates this commit: state the instrument used, and a
re-review round must change it (Action Tracker #26).

**3. The release pipeline's first execution falsified two of its own documents.**
Stage 10's `git tag vX.Y.Z` fails as written — `tag.gpgsign true` forces
annotated tags, discovered by running it (GitHub #52). Stage 11's
"rows runnable only once this APK is installed" claim was never true and was
corrected before first use (PR #46). The gate's default-target selector shipped
with a silent awk fallback, caught only by executing it against the real
ROADMAP (PR #49). Meanwhile the one piece of the milestone that shipped
*outside* a pipeline run — PR #45, the gate itself — is the one PR with no
retro artifact (backfilled this retro), and PR #47 surfaced a template
contradiction: the bugfix Fix stage said "update CHANGELOG.md" while Gate 1
forbids exactly that. Processes are code; only execution verifies them.
**Action taken**: Open — Action Tracker #23 (GitHub #52) for the tag command;
Fix-stage CHANGELOG item removed from the bugfix template this commit (Action
Tracker #28); PR #45 retrospective backfilled via `gh pr comment` this retro.

**4. A gate encodes a judgement, and the judgement needs stating.**
The gate as first built blocked the tag on #33's hardware rows — conflating
*unverified* with *known must-not-ship* and making the dependency circular,
since those rows were owed precisely because no release build existed to run
them against. The user's reversal — release first, then verify — was right and
produced strictly better evidence: the rows now run against a minified,
release-signed APK installed over Obtainium, not a sideloaded debug build.
`release-blocker` keeps its meaning only because ADR-0001 now states the three
states (known defect: label it; unverified: verify against the release build;
known-acceptable: say so on the issue).
**Action taken**: Closed — ADR-0001 amended with the release-blocker semantics
table (PRs #46, #49).

**5. A hypothesis is only tested by an input that could refute it.**
PR #43's diagnosis prototyped the fallback on the one input where every
candidate rule returns the same index — the single shape incapable of
discriminating — and all three subsequent rewrites were about inputs where the
rules diverge. The positive twin is PR #47: counting url collisions in the
real fixtures (21 of 205 shared) killed the obvious one-line fix before it was
written. Same lesson at plan scale: the strategy session triaged eight
candidates from issue titles without opening a file, and item one was already
fixed — the second stale-premise closure caught downstream by
`/pipeline-next`'s staleness check rather than at planning time.
**Action taken**: Closed — discriminating-input and collision-count items added
to the bugfix Diagnosis stage this commit (Action Tracker #27);
`/pipeline-roadmap-audit` added to pipeline-strategy's survey stage
(`~/.claude/skills/pipeline-strategy/SKILL.md`, Action Tracker #29).

### Insights

- **The manual matrix came back empty for the first time in three milestones**
  (PR #43) — not by deferral but because the `Requires:` field forced the
  question per row and every answer was "nothing", including a cross-repo row
  discharged by driving the real desktop exporter with its sanitizer neutered.
- **The best process call of the milestone was the user's**, and it beat the
  gate its own author built: release first, then verify, produced better
  evidence than verify-then-release ever could.
- **A surviving mutation was resolved by deleting the code, not adding a test**
  (PR #47's M4): a guard no test can distinguish is #31 wearing an `if`.
- **Correct-somewhere numbers are the ones that travel.** The `:core` 89
  baseline survived review precisely because it was genuinely right in the
  artifact it was copied from.
- **The million-input differential harness was deliberately discarded.** The
  shipped fallback is pinned by 26 `M3uParserTest` rows; a maintained Python
  model of the parser would itself be an unverified artifact. Simplification
  noted, not an omission.
- **Verification kept finding things outside the diff**: PR #50's stage 11
  established that v2-only signing is invisible in the zip listing
  (`META-INF/*.RSA` absent ≠ unsigned), and the harvester's swapped
  artist/title split was caught by the user reading five lines of JSON that
  three prior format checks had passed over.

### Review Stats

| Metric | PR #43 | PR #45 | PR #47 | PR #50 | Total |
|--------|--------|--------|--------|--------|-------|
| Tests added | 18 | 0 | 10 | 0 | 28 |
| Review findings | 12 (7 🟡, 5 🔵) | — (no pipeline run) | 9 | 1 | 22 |
| Findings fixed pre-merge | 12 | — | 9 | 1 | 22 |
| #31-class prose/shape defects | 5 | — | 2 | 1 | 8 |
| CI failures | 0 | 0 | 0 | 0 | 0 |

Tests-added counts derived from `gh pr diff N | rg -c '^\+\s*@Test'`; suite
totals from fresh `--rerun-tasks` XML.

### Process Improvements Applied

**During the milestone**: ADR-0001 (release at milestone end) +
`.pipeline-templates/release-state.json` (12 stages) + `scripts/release-ready.sh`
(PR #45); gate default-target selector fixed by running it (PR #49); mandatory
ROADMAP-strike item in the Documentation stage of all three templates (PR #48);
JVM crash dumps gitignored (PR #44); release stage-11 claim corrected to a
signed-artifact check (PR #46).
**This commit**: Documentation-stage quantifier-audit / derive-not-transcribe /
cross-repo-citation items (all three templates); Code Review
instrument-declaration item (all three); bugfix Diagnosis discriminating-input
+ collision-count items; bugfix Fix-stage CHANGELOG item removed (Gate 1
conflict); `pipeline-strategy` survey stage gains `/pipeline-roadmap-audit`.
**GitHub**: #52 (tag command), #51 (station-attribution lines) filed at
milestone close; #31 scope extended.

### Open Items

- [x] Nine owed hardware rows (13, 16, 19, 20–25) — Action Tracker #12 (GitHub
  #33) — resolved in v0.5.1: all discharged 2026-08-07/08 against the release
  APK, #33 closed
- [x] Ops batch id + refile the receiving issue on `.dotfiles` — Action
  Tracker #20 (GitHub #38) — resolved in v0.5.1 (PR #57); receiving issue
  became data#16 (topology had moved), implemented same day
- [x] Song capture station-attribution lines — Action Tracker #22 (GitHub
  #51) — resolved in v0.5.1 (PR #56), field-verified
- [x] Release-template tag command — Action Tracker #23 (GitHub #52) —
  resolved in v0.5.1 (PR #58), consumed by the v0.5.1 release run
- [ ] `enforced by:` promote-or-drop, now with a negative data point — Action
  Tracker #9 (GitHub #31)
- [ ] Titles-harvester defects (artist/title split fix uncommitted, entity
  anomaly) — Action Tracker #24 (basiliskus/data#8)

## v0.4.0 — Harness and capture (PRs #23, #29, #34)

**Forward link**: planning for the next milestone was done in a deep
`/pipeline-strategy` session on 2026-07-26 — see
`docs/strategy-sessions/2026-07-26-v0.5.0.md`. It surfaced that this milestone
shipped nothing: v0.4.0 was merged but never released, which blocked the owed
hardware rows, the first real `library/onda.json`, and the live test of
basiliskus/data#5. The chosen path (B) fixes correctness first, then releases.

**Date**: 2026-07-25
**Scope**: The `:app` JVM test harness plus the seams that made it possible
(PR #23, closing GitHub #9 and #10); song capture to the synced titles log
(PR #29); and cross-playlist move (PR #34). Ordered harness-first deliberately
so the two features after it would inherit coverage from day one.
**Tests at close**: 157 (`:core` 89, `:app` 68 — `:app` was 0 at milestone open)

### What We Learned

**1. Comments asserting a guarantee are unverified claims sitting where nothing checks them.**
Six instances across three PRs: the vacuous U9 row (#23); the fake's "golden copy"
claim, the forced-locale-implies-ASCII-digits claim that reached `docs/spec.md`, the
per-call-formatter rationale, and `mimeFor`'s "mangling is harmless" (#29); and an
inverted claim about the desktop's `apply_move` (#34). Detection improved
monotonically — everything escaped in #23, four of five were caught in #29, and #34's
was caught pre-merge — but the production rate did not move. The two highest-risk
instances both asserted something about an *external consumer*, where the local
harness exercises only the branch the feature takes, not the branch the comment
describes.
**Action taken**: Open — tracked in Action Tracker #9 (GitHub #31); Self-Review
template item added to `.pipeline-templates/{feature,bugfix,refactor}-state.json`
this commit.

**2. A predicted mutation is a claim; only an executed one is evidence.**
PR #34 split its mutation table into APPLIED and PREDICTED, and both of the
milestone's coverage gaps were in or beside the PREDICTED column: two `compact()`
branches invertible with all 155 tests green, one of them the plan's own case 5c.
The plan's own C1 row also could not fail under the mutation the plan named for it —
found by running it, not reading it. Executing a mutation also produces facts a
prediction cannot: `M-DELEG` killing nothing identified it as an equivalent mutant.
**Action taken**: Closed — mandatory item added to the "Code Review" stage checklist of
`.pipeline-templates/{feature,bugfix,refactor}-state.json` this commit, requiring the
APPLIED/PREDICTED split and execution of a PREDICTED sample (Action Tracker #10).

**3. Verification tooling degrades silently, in two different ways.**
Gradle served UP-TO-DATE results and turned Test Execution into a replay (#23,
surfaced only because two subagents died mid-run). A failing `:core:test` aborts
before `:app:testDebugUnitTest` and leaves stale `:app` XML, which a mutation driver
misattributes to the current mutation (#34). The second is worse than the first: it
reports *wrong* answers rather than none, and mutation testing is now a mandatory
gate.
**Action taken**: Open — tracked in Action Tracker #11 (GitHub #36); `--rerun-tasks`
and the driver-hygiene requirement applied to all three templates this commit.

**4. Before deferring a manual row, ask what it actually requires.**
PRs #23 and #29 each deferred a row that then mattered — #23's M2 (resumption smoke)
and #29's M6 (sheet like button, the surface where both user-found defects lived).
PR #34 discharged its desktop round-trip *inside the pipeline* because the plan wrote
"no device needed" into the row: the real `stations-merge-ops.py` ran against a
scratch corpus with production journal bytes. First time in three PRs. The
distinction is not device-vs-not; it is that nobody had asked.
**Action taken**: Open — tracked in Action Tracker #12 (GitHub #33); `Requires:`
field and the sole-mitigation blocking-carry rule applied to all three templates
this commit. This IDEA was filed in #23, repeated in #29, and repeated again in #34
before being implemented.

**5. Plans lose things between their own tables, and settle questions review should ask.**
PR #34's case 5c was enumerated in the plan's compaction table and absent from its
test-list table; it shipped uncovered. Separately, a plan paragraph arguing why group
move needed no Undo was ratified by four review layers — the user's one-line reversal
deleted `OpsRepository.move()`, a screen parameter, and a discarded token, and closed
half of GitHub #18. Pre-litigating an omission removes review pressure from it exactly
as a false comment does.
**Action taken**: Closed — two items added to the "Planning" stage checklist of
`.pipeline-templates/feature-state.json` this commit: a mandatory mechanical
self-join, and "do not pre-litigate a simplification" (Action Tracker #13, #14).

**6. Harness-first ordering paid off where JVM reach exists, and the residual risk moved.**
`:app` went 0 → 68 rows. The atomicity crux of PR #34 was only checkable because the
harness existed; the algebra-rule gate found real holes in two consecutive PRs; and
song capture used the harness on day one exactly as the ordering intended. But the
harness bought coverage where it was cheapest, and both defects the user found in
PR #29 were in Compose, which has zero JVM reach. #26 survived the entire milestone
uncovered, and owed hardware rows grew from 1 to 10.
**Action taken**: Open — tracked in Action Tracker #15 (GitHub #26, #33).

### Insights

- **Three disjoint review layers kept earning their slots.** Self-Review, a
  fresh-context Code Review subagent, and the user each found things the others
  missed, in all three PRs. The user's findings clustered exactly where automated
  reach is zero.
- **Verifying a consumer beats reading its description.** Planning read
  `pyradio-to-json.py` and corrected the ROADMAP's format (#29); read
  `stations-merge-ops.py` and confirmed no desktop work (#34); transcribed
  `compact()` into Python and ran 13 compositions rather than trusting the algebra
  table (#34). Every one of those found something the requirement had wrong.
- **Adversarial security analysis found a real vulnerability**, not a checklist
  sweep: the ICY title is attacker-controlled input written into a file a desktop
  script parses, and forged records were demonstrated end to end before the fix.
- **A bug fix produced a better architecture than the design** — moving capture to
  the app scope with a `messages` flow (#29) also made the two feedback channels
  structurally disjoint, which the original design achieved only by discipline.
- **The best change of the milestone was a deletion** (#34), and it came from the user.

### Review Stats

| Metric | PR #23 | PR #29 | PR #34 | Total |
|--------|--------|--------|--------|-------|
| Tests added | 35 | 24 | 23 | 82 |
| 🔴 Findings | 0 | 0 | 0 | 0 |
| 🟡 Findings | 2 | 4 | 3 | 9 |
| Findings fixed pre-merge | 2 | 4 | 3 | 9 |
| #31-class comment defects | 1 | 4 | 1 | 6 |
| CI failures | 0 | 0 | 0 | 0 |

### Process Improvements Applied

**Pipeline template**: `--rerun-tasks` + XML-count requirement (Test Execution);
`Requires:` field and sole-mitigation blocking carry (Test Execution); mutation table
APPLIED/PREDICTED split plus driver hygiene (Code Review); comments-asserting-
guarantees rule and dispatcher-assumption check (Self-Review); mechanical self-join
and no-pre-litigation (Planning). Applied to all three templates this commit.
**CLAUDE.md**: `test_command` updated to run `:app:testDebugUnitTest` (PR #23).
**CI**: `ci.yml` runs the `:app` suite (PR #23) — before this, CI ran no `:app` tests.
**Docs**: `docs/spec.md` gained the synced-folder contract, titles-log format with its
line-boundary rationale, and the cross-playlist move contract.

### Open Items

- [ ] Comment-guarantee rule + `enforced by:` promote-or-drop — Action Tracker #9 (GitHub #31)
- [ ] Mutation-driver hygiene — Action Tracker #11 (GitHub #36)
- [ ] Ten owed hardware rows (smoke 13, 16–24) — Action Tracker #12 (GitHub #33)
  — partially discharged in v0.5.0 (2026-07-26 session vs the release APK,
  recorded on #33); nine rows remain
- [ ] Compose-layer coverage gap, pagination in-flight — Action Tracker #15 (GitHub #26)
- [ ] Desktop defects — Action Tracker #16 (basiliskus/.dotfiles#38–42)
- [x] Titles-log glob — Action Tracker #17 (basiliskus/data#4) — resolved in
  v0.5.0: data PRs #5/#6 merged, first ingestion 2026-07-26 (successor defects
  in Action Tracker #24)
- [ ] Remaining tech debt: GitHub #11, #12, #18, #24, #25, #27, #28, #30 —
  #13 closed stale-premise in v0.5.0 (PR #42); #35 fixed in v0.5.0 (PR #47)

## v0.3.0 — Playlist editing round-trip (PR #17)

**Date**: 2026-07-24
**Scope**: Phone-side playlist editing through an `ops.jsonl` journal (swipe-delete
with undo, move-to-group sheet, targeted adds), optimistic overlay, pending.csv
migration; desktop consumer landed in parallel in the dotfiles repo — the full
round-trip is live. Largest run since the MVP: 12 commits, 26+ files, three
serialized implementer agents.
**Tests at close**: 68 (:core), 0 in :app.

### What We Learned

**1. The 🔴 lived in the twice-flagged gap — untested :app state machines, third
occurrence.**
Undo referenced ops by id, but compaction consumes ids: swiping a pending add
netted `[add]+delete → []` and Undo silently lost the edit. Plan, three
implementers, Self-Review, and a 13-row emulator matrix all missed it; the
fresh-context Code Review caught it. The same class escaped as PR #6's untested
async bridge and PR #8's untested banner VM. A fake-PlaylistRepository harness
would have caught this as a five-line JVM test — and the snapshot/CAS fix itself
is still uncovered.
**Action taken**: scheduled as the v0.4.0 milestone in `ROADMAP.md` (this commit);
Action Tracker #1 (GitHub #9) escalated with PR #17 as third source.

**2. The verification matrix inherited the plan's blind spot.**
The matrix had an Undo row — it passed against a plain delete, the one case where
id-based undo is sound. For algebra-core features, gestures aren't the unit of
coverage; algebra rules composed with user-visible operations are
(undo-after-move and undo-of-pending were enumerable straight from the compaction
table). Same lesson from the adb side: per-step tap-verify loops raced sheet
animations and snackbar timers; atomic interaction chains with end-state
verification held up.
**Action taken**: `.pipeline-templates/{feature,bugfix,refactor}-state.json` (this
commit) — Test Execution gains the algebra-rule-row requirement and the
atomic-chains guidance; Self-Review gains a mandatory id-survival check for
undo/rollback paths; Planning gains a cross-side contract-warning relay item.

**3. Accepted review leftovers are felt-UX debt, not noise.**
Two of PR #17's accepted 🔵s degrade daily use once editing is habitual: a voided
Undo (generation mismatch) gives no feedback, and mid-flow sheets vanish on
rotation. Same polish class as the Discover query nit from v0.2.0.
**Action taken**: Open — tracked in Action Tracker #7 (GitHub #18), grouped with
#3 (GitHub #11) as one polish pass.

**4. Deferred verification now has named homes instead of matrix skips.**
The three reasoned matrix skips (DHU, SAF fault injection, TalkBack) and the
reviewer's IME watch-item each needed a landing place, or they'd silently vanish.
**Action taken**: `docs/smoke-checklist.md` rows 14–15 added (TalkBack, New-group
IME) in this commit; DHU was already row 10; SAF fault injection becomes testable
inside the v0.4.0 harness (named in the ROADMAP milestone spec).

### Insights

- Three review layers, three disjoint real findings: subagent review (🔴 undo/
  compaction, 🟡 stale journal on refresh), re-review (demanded post-fix evidence,
  verified CAS ABA-proof), user review (star-scope semantics, token-arming race).
  None was redundant; keep all three for feature-scale PRs.
- Behavioral semantics at feature seams (who owns an add: Discover star vs
  playlist?) are owner decisions — two agent layers accepted the design's literal
  wording; only the user could rule quick-stars-only. Route seam semantics to user
  review explicitly.
- Pre-litigating risky deviations in the plan (derived-flow placement,
  plain-remember swipe state) turned the two most contestable calls into review
  non-events — both ratified, the second one catching an upstream API's
  rememberSaveable trap in the process.
- Pinning the wire contract in the design doc before either side built let the
  phone and desktop halves land independently with zero drift; the one contract
  subtlety discovered late (fresh-UUID resurrection vs url-dedup) crossed repos
  because it was relayed deliberately.
- v0.2.0's error-banner work absorbed this run's transient radio-browser failures
  mid-E2E — recovery on retry, exactly as built.
- Accepted-by-design residue (refresh voids live token on identical content,
  mimeFor octet-stream, empty-csv redundant rewrite) is documented in the PR
  thread; deliberately not tracked — conservative-safe behaviors, not debt.

### Review Stats

| Metric | PR #17 |
|--------|--------|
| Tests added | 33 net (:core 35→68) |
| 🔴 Findings | 1 (fixed pre-merge, 35059dc) |
| 🟡 Findings | 1 (fixed pre-merge, same commit) |
| 🔵 Findings | 11 across three layers (3 fixed, 2 tracked as #18, rest accepted) |
| User-review findings | 2 fixed (8700a18) + 1 owner decision (star scope, doc amended 400f807) |
| CI failures | 0 |

### Process Improvements Applied

**Pipeline templates**: 9 edits across the three templates (finding 2).
**ROADMAP**: v0.3.0 → Completed; v0.4.0 harness milestone scheduled (finding 1);
line-74 upstream item struck (fixed at source, dotfiles 7fcb2d9).
**Docs**: smoke rows 14–15 (finding 4); ops-journal design doc gained an
Amendments section during the run (star scope, 400f807).

### Open Items

- [x] :app test harness (v0.4.0 milestone) — Action Tracker #1 (GitHub #9) — resolved in v0.4.0 (PR #23)
- [ ] Ops-journal UX polish — Action Tracker #7 (GitHub #18), with #3 (GitHub #11)
- [x] :core resumption seam decision folds into the harness plan — Action Tracker #2 (GitHub #10) — resolved in v0.4.0 (PR #23)

## v0.2.0 — Daily-driver polish (PRs #6–#8)

**Date**: 2026-07-24
**Scope**: Playback resumption after process death; release pipeline guards
(tag↔versionName, post-merge main builds); Discover polish (self-healing error
banner, persistent segment selection). First milestone run through the pipeline.
**Tests at close**: 35 (:core), 0 in :app (see finding 1).

### What We Learned

**1. :app-side logic with real state machines shipped untested — twice.**
PR #6 left the resumption async bridge (timeout, scan-wait, three exceptional
exits) in `PlaybackService` with only manual coverage; PR #8 left the browse-banner
state machine in `DiscoverViewModel` the same way — and that's exactly where the
milestone's only 🟡 (in-flight duplicate fetch → permanently stale banner) lived.
Two shapes, two remedies: ViewModels get a test harness, service-layer logic gets
extracted to :core.
**Action taken**: Open — tracked in Action Tracker #1 (GitHub #9) and #2 (GitHub #10).

**2. Inline stage execution silently broke the state-file-as-program invariant.**
PR #7's run reached `current_stage: 14` with Implementation still `pending` —
the work happened, the ledger was wrong, and the retro caught it rather than the
run. Inline execution must carry the same bookkeeping duty as subagent collection.
**Action taken**: Updated `~/.claude/skills/pipeline-run/SKILL.md` — section
"Inline-verify shortcut for low-risk changes" (inline bookkeeping mandate; Gate
Check runs even when all prior work was inline).

**3. The project-local templates were still speaking Python and LiveView.**
Stage 1 checked off "verify python/venv" against a Gradle project; WIRING_CHECK
cited another project's PRs and hot paths; the leak checklist hunted ARNs in an
app whose real leak vectors are stream URLs and tree URIs.
**Action taken**: `.pipeline-templates/{feature,bugfix,refactor}-state.json`
modernized in this commit (32 edits): JDK/Gradle environment checks, Android
WIRING_CHECK (:core API needs a real :app/service call site), Kotlin visibility
review, app-specific leak vectors, CONFIG_ONLY change class.

**4. The gates that would have caught this milestone's misses didn't exist yet.**
The 🟡 escaped Self-Review because nothing forced enumerating the in-flight window
of state-gated fetches; PR #6's partially-executed manual matrix was invisible
because pass/skip wasn't recorded; composition-time navigation (the back-trap) is
a plannable smell.
**Action taken**: same templates commit — plan-stage items (:core seam proposal,
stability annotations, composition-time side-effect smell, manual matrix in plan),
mandatory Self-Review in-flight-window enumeration, Test-Execution pass/skip(reason)
rows with evidence-as-PR-comment, merge-stage post-merge-observation item, and the
retro prompt now requires a consolidated carry-forward on milestone-closing PRs.

**5. The release guard carries named verification debt.**
The tag↔versionName check has never seen a real tag push; its first live run is
the v0.2.0 release itself.
**Action taken**: release-pipeline note added to `docs/smoke-checklist.md` in this
commit (confirm the guard step ran on the v0.2.0 tag, then remove the note); also
Action Tracker #6.

### Insights

- Plan accuracy was the milestone's step change vs the MVP: plans pinned to the
  exact Media3 version in `libs.versions.toml` produced zero architectural
  deviations across three PRs (sole deviation: one lint-mandated annotation).
- Pre-writing design decisions into the plan made review cheap: PR #6's four 🔵
  findings were all accepted-by-design with the rationale already on paper.
- The fresh-context Code Review subagent earned its keep both times it ran against
  inline-executed work (PR #7 injection probing; PR #8's 🟡) — right-sized ceremony
  is inline stages + subagent review, never inline review alone.
- Evidence-as-PR-comment (screenshots, logcat, run URLs) settled every reviewer
  question without a round-trip; now templated.
- "Persist the key, re-derive the value" (resumption id → live station index) is
  the house default for state layered on synced data.
- User review contributed materially each time (RC-tag decision, parse-error
  hardening, TOP_INDEX extraction, both harness follow-ups).

### Review Stats

| Metric | PR #6 | PR #7 | PR #8 | Total |
|--------|-------|-------|-------|-------|
| Tests added | 4 | 0 | 0 | 4 |
| 🔴 Findings | 0 | 0 | 0 | 0 |
| 🟡 Findings | 0 | 0 | 1 | 1 |
| 🔵 Findings | 4 | 2 | 2 | 8 |
| Findings fixed pre-merge | 0 (all accepted-by-design) | 1 | 2 | 3 |
| CI failures | 0 | 0 | 0 | 0 |

### Process Improvements Applied

**Pipeline templates**: 32 edits across feature/bugfix/refactor (findings 3–4).
**Skills**: `pipeline-run` inline-bookkeeping mandate (finding 2).
**Docs**: smoke-checklist row 13 (resumption) added during PR #6; release-guard
note (finding 5).
**ROADMAP**: v0.2.0 moved to Completed; matrix pruned.

### Open Items

- [x] :app ViewModel test harness — Action Tracker #1 (GitHub #9) — resolved in v0.4.0 (PR #23)
- [x] :core resumption seam — Action Tracker #2 (GitHub #10) — resolved in v0.4.0 (PR #23)
- [ ] Discover `query` persistence — Action Tracker #3 (GitHub #11)
- [ ] `launchSingleTop` on stations route — Action Tracker #4 (GitHub #12)
- [x] Widen browse-fetch error handling — Action Tracker #5 (GitHub #13) —
  closed stale-premise in v0.5.0 (PR #42): `RadioBrowserClient` already
  converts decode failures to `IOException`
- [x] Confirm release guard on v0.2.0 tag — Action Tracker #6 — resolved: guard ran
  green on the v0.2.0 tag (run 30107921024; closed via PR #16)

<!-- Milestone retro entry template:
## <milestone> — <date>
**Quality**: N/5 · **What went well**: … · **Lessons**: … · **Actions**: → tracker
-->
