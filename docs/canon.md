# Canon: the unverified-claim defect class

**Verdict on the `enforced by:` trial (GitHub #31, decided 2026-08-08):
promoted.** Every comment asserting a guarantee that a test could enforce
names the row that enforces it, as `ClassName."row name"` on one line, and
`scripts/check_enforced_by.py` (run in CI) fails the build when a citation
names a class or row that no longer exists. This converts the convention's
one observed failure — the citation map going stale inside a single commit
on PR #47 — from a vigilance failure into a build failure.

This document records the verification rules from #31. Apply them through
the [development checklist](development.md) and [release checklist](release.md);
add new instruments here when evidence warrants them.

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
   now covered by the external-existence check).
6. **Comments asserting the just-fixed bug** — prose describing the pre-fix
   world surviving the fix commit (PR #65's review).
7. **Instrument self-corruption** — the verification tool lying: a
   comparison script clobbering its own input (v0.5.1 release), a mutation
   driver reading stale XML (PR #34, GitHub #36).
   v0.5.2 added two recurring sub-shapes, four instances in one milestone:
   a harness reporting someone else's exit code (`cmd | tail` twice — the
   canon-closure run and the release gate), and a value used as a
   discriminator that cannot discriminate (file mtime twice — the release
   checklist's freshness check, where the suite necessarily predates the
   commit, and hardware row R1, where `stations.m3u` proved the timestamp
   was a sync touch). Both fail toward *confident* false verification,
   which is worse than no check.

8. **The fix diff** — the code written in response to a finding, which is
   simultaneously the least-reviewed code in a branch and the most
   defect-dense. PR #65's round-2 fix introduced the regressions round 3
   caught; PR #66's round-2 fix carried four bugs; PR #67's re-review
   refuted a claim its own fix commit had just added. Reviewing the fix
   delta with a rotated instrument is now a development checklist item.

## Instruments

| Instrument | Catches | Procedure |
|---|---|---|
| `enforced by:` citation + `scripts/check_enforced_by.py` | renamed/deleted rows behind a guarantee comment | CI and [development](development.md) |
| Prose audit and derived counts | quantifiers broader than tests; numbers no command reproduces | [development](development.md) |
| Mutation testing with `scripts/mutation-run.sh` | tests that don't enforce their claims; stale XML | [development review](development.md#review-and-hand-off) |
| External-existence check | claims that a filed/shipped/verified thing exists | [development](development.md) |
| Fresh downloads and contradiction stop | an artifact check contradicting an earlier result | [release verification](release.md#publish-and-verify) |
| Different review method and fix-delta review | repeated blind spots; defects introduced by fixes | [development review](development.md#review-and-hand-off) |
| Sibling sweep | a fix missing another consumer or exit path | [development](development.md) |
| Harness verification | wrong exit code; observations that can't distinguish cases | [development](development.md) |
| Doc sweep after factual corrections | stale copies in docs or changelog | [development](development.md) |

## Boundaries, stated plainly

- The dangling-reference check covers the src trees only; prose history
  (roadmaps and PR discussions) cites rows as they were at the time.
- A citation wrapped across lines is invisible to the checker — citations
  stay on one line, however long.
- The check proves a cited row *exists*, not that it *enforces* the claim;
  existence is the cheap half, mutation testing is the expensive half, and
  the two are complements, not substitutes.
