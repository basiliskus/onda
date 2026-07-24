# RETRO

## Action Tracker

Items from retrospectives that need resolution. Every item must have a GitHub
issue or be explicitly closed with a reason.

| # | Action | Source | GitHub | Status | Notes |
|---|--------|--------|--------|--------|-------|
| 1 | :app-module test harness (undo/compaction, banner VM, resumption) | PR #6, PR #8, PR #17 | #9 | Open | THIRD occurrence (v0.3.0 🔴 was this class) — escalated to scheduled v0.4.0 milestone work |
| 2 | Extract resumption wait-and-resolve seam into :core | PR #6 | #10 | Open | Service-layer logic → extraction; ViewModels → harness (#1) |
| 3 | Persist Discover search text (`query` rememberSaveable) | PR #8 | #11 | Open | Polish pass |
| 4 | Stations route missing `launchSingleTop` | PR #8 | #12 | Open | Pre-existing 🔵 |
| 5 | Browse fetches catch only `IOException` | PR #8 | #13 | Open | Pre-existing 🔵; `SerializationException` would crash |
| 6 | Confirm tag↔versionName guard ran when v0.2.0 is tagged | PR #7 | — | Closed | Guard ran green on the v0.2.0 tag (run 30107921024, step "Verify tag matches versionName": success) |
| 7 | Ops-journal UX polish: voided-undo feedback, sheet rotation survival | PR #17 | #18 | Open | One polish pass with #3 (#11) |
| 8 | Relay desktop contract notes (fresh-UUID/url-dedup backstop; move collapses dup urls) | PR #17 | — | Closed | Relayed to the dotfiles agent during the run; url-dedup kept mandatory in stations-merge-ops.py |

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

- [ ] :app test harness (v0.4.0 milestone) — Action Tracker #1 (GitHub #9)
- [ ] Ops-journal UX polish — Action Tracker #7 (GitHub #18), with #3 (GitHub #11)
- [ ] :core resumption seam decision folds into the harness plan — Action Tracker #2 (GitHub #10)

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

- [ ] :app ViewModel test harness — Action Tracker #1 (GitHub #9)
- [ ] :core resumption seam — Action Tracker #2 (GitHub #10)
- [ ] Discover `query` persistence — Action Tracker #3 (GitHub #11)
- [ ] `launchSingleTop` on stations route — Action Tracker #4 (GitHub #12)
- [ ] Widen browse-fetch error handling — Action Tracker #5 (GitHub #13)
- [x] Confirm release guard on v0.2.0 tag — Action Tracker #6 — resolved: guard ran
  green on the v0.2.0 tag (run 30107921024; closed via PR #16)

<!-- Milestone retro entry template:
## <milestone> — <date>
**Quality**: N/5 · **What went well**: … · **Lessons**: … · **Actions**: → tracker
-->
