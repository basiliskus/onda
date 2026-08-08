# Canon: the unverified-claim defect class

**Verdict on the `enforced by:` trial (GitHub #31, decided 2026-08-08):
promoted.** Every comment asserting a guarantee that a test could enforce
names the row that enforces it, as `ClassName."row name"` on one line, and
`scripts/check_enforced_by.py` (run in CI) fails the build when a citation
names a class or row that no longer exists. This converts the convention's
one observed failure — the citation map going stale inside a single commit
on PR #47 — from a vigilance failure into a build failure.

This document is #31's permanent form. The issue closes with it; new
instances of the class get filed against their own PRs and, when they earn
a new instrument, extend this file.

## The class

A claim about verified behavior, sitting anywhere nothing checks it. The
defining property: the claim *reads* like the output of verification, so a
reader (or the author, one commit later) treats it as one. Volume, not
carelessness, is the signal — five instances landed on one branch (PR #29)
written in good faith.

## Substrates observed

Each entry names the PR that caught the class on that substrate.

1. **Source comments** — the origin. PR #23's vacuous U9 row; PR #29's five
   (a "golden copy" that wasn't, a false locale rationale, a false
   class-init claim two agents "verified" with opposite results, a
   harmless-for-one-caller claim outlived by a second caller, an ownership
   test passing for the wrong reason).
2. **PR prose and review claims** — PR #57 ("filed on data" written before
   filing), PR #59 ("both halves verified", caught by the release reviewer),
   PR #64's strategy doc ("~4 pool", transcribed from conversation instead
   of derived). Each example cites only its own PR: this sentence's first
   two drafts each carried a collective cross-reference that was itself
   misattributed — caught by two successive primary-record reviews.
3. **API shapes** — a signature implying a guarantee the implementation
   scopes narrower (PR #29's `mimeFor`).
4. **Release notes / CHANGELOG** — PR #65 (suppression scope, channel
   semantics — corrected across three review rounds), PR #66 ("all sheets
   URL-derived" while one is a snapshot by design).
5. **Forward-dated claims** — plans stated as completed facts (PR #57;
   canonized as the external-existence check, Action Tracker #30).
6. **Comments asserting the just-fixed bug** — prose describing the pre-fix
   world surviving the fix commit (PR #65's stage-11 review).
7. **Instrument self-corruption** — the verification tool lying: a
   comparison script clobbering its own input (v0.5.1 release, Action
   Tracker #32), a mutation driver reading stale XML (PR #34, GitHub #36).

## Instruments

| Instrument | Catches | Lives in |
|---|---|---|
| `enforced by:` citation + `scripts/check_enforced_by.py` | renamed/deleted rows behind a guarantee comment | CI + templates |
| Quantifier audit on prose diffs | "never/always/every/only" broader than any test pins | template checklists |
| Derive-never-transcribe | numbers in prose that no command reproduces | template checklists |
| Mutation testing, APPLIED vs PREDICTED, table persisted | rows not enforcing what their names claim | Code Review stage |
| `scripts/mutation-run.sh` | the driver itself misattributing stale results (#36) | Code Review stage |
| External-existence check (AT #30) | claims that a filed/shipped/verified thing exists | template checklists |
| Contradiction rule + fresh paths + XML mtime (AT #32) | an instrument's output contradicting an already-passed check | release template |
| Instrument declaration | re-review rounds re-measuring with the same blind spot | Code Review / Re-Review |
| Doc sweep after factual corrections | a corrected claim's copies surviving in docs/CHANGELOG | template checklists |

## Boundaries, stated plainly

- The dangling-reference check covers the src trees only; prose history
  (ROADMAP, RETRO, PR feedback) cites rows as they were at the time.
- A citation wrapped across lines is invisible to the checker — citations
  stay on one line, however long.
- The check proves a cited row *exists*, not that it *enforces* the claim;
  existence is the cheap half, mutation testing is the expensive half, and
  the two are complements, not substitutes.
