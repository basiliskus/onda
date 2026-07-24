# RETRO

## Action Tracker

Items from retrospectives that need resolution. Every item must have a GitHub
issue or be explicitly closed with a reason.

| # | Action | Source | GitHub | Status | Notes |
|---|--------|--------|--------|--------|-------|
| 1 | :app-module ViewModel test harness (banner state machine first) | PR #6, PR #8 | #9 | Open | Top carry-forward; second occurrence of the untested-:app-logic gap |
| 2 | Extract resumption wait-and-resolve seam into :core | PR #6 | #10 | Open | Service-layer logic → extraction; ViewModels → harness (#1) |
| 3 | Persist Discover search text (`query` rememberSaveable) | PR #8 | #11 | Open | Polish pass |
| 4 | Stations route missing `launchSingleTop` | PR #8 | #12 | Open | Pre-existing 🔵 |
| 5 | Browse fetches catch only `IOException` | PR #8 | #13 | Open | Pre-existing 🔵; `SerializationException` would crash |
| 6 | Confirm tag↔versionName guard ran when v0.2.0 is tagged | PR #7 | — | Open | One-time release-step; note added to `docs/smoke-checklist.md` |

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
- [ ] Confirm release guard on v0.2.0 tag — Action Tracker #6 (one-time)

<!-- Milestone retro entry template:
## <milestone> — <date>
**Quality**: N/5 · **What went well**: … · **Lessons**: … · **Actions**: → tracker
-->
