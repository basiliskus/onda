# ADR-0001 — The release is the last row of its milestone, gated on `release-blocker`

**Status**: Accepted
**Date**: 2026-07-26
**Supersedes**: the release ordering chosen in `docs/strategy-sessions/2026-07-26-v0.5.0.md`

## Context

v0.4.0 shipped three features across PRs #23, #29 and #34, all merged — and
was never released. `versionName` sat at `0.3.0` for two days with nobody
noticing, because nothing in the process was responsible for noticing. It
surfaced only when a *different* task (the owed hardware smoke rows, GitHub
#33) turned out to be blocked on an APK that did not exist.

Three separate gaps produced that:

1. **No skill owns releasing.** The pipeline-* family stops at a merged PR by
   design — `pipeline-drain` ends with a comment telling you to "cut a release
   with your repo's own release tooling". `pipeline-ship`, despite the name,
   ships working-tree changes to a merged PR; it has no version bump and no
   tag. So the release was the one recurring task with no checklist behind it,
   in a project whose entire process thesis is that mandatory checklist items
   are the strongest enforcement venue.

2. **The release had no clear trigger.** The v0.5.0 strategy session placed it
   *mid-milestone* — after the correctness fixes, before the ops-journal work
   — for the good reason that the tag should carry the fixes. But a release in
   the middle of a milestone has no observable "now" condition: the reader has
   to know that rows above it are done and rows below it are not, and that
   distinction lives only in whoever last read the ROADMAP.

3. **No severity signal exists on issues.** Priority (P0–P3) is written only
   into the ROADMAP priority matrix, and only at strategy sessions. A critical
   bug filed on a Tuesday could not block a Wednesday release, because nothing
   connected it to the release decision until the next planning session.

Gap 3 is the one that matters most: shipping a version with a known critical
bug was possible not through carelessness but because the process had no
representation for "known critical".

## Decision

**1. The release is the last row of its milestone.** Always. This makes
"every row above it is struck" and "ready to tag" the same fact, so readiness
is observable from the ROADMAP rather than inferred. It satisfies the strategy
session's reasoning strictly harder than mid-milestone placement did: the tag
now carries *everything* in the milestone, not just the rows that happened to
precede it.

**2. `release-blocker` is the sole blocking severity signal.** One GitHub
label. Anything that must not ship gets it at file time — not at the next
strategy session. An open `release-blocker` issue blocks the tag,
unconditionally. The label costs one decision per filed issue and buys the
property that a bug found today blocks today.

Note what this deliberately does *not* do: it does not add a priority-label
scheme. `tech-debt` and the ROADMAP matrix already carry ordering. The only
new information is binary and release-scoped.

**Amended 2026-07-26 — the label means *known* must-not-ship, not *unverified*.**
The first thing labeled under this ADR was #33, the owed hardware smoke rows.
That was a mistake, and an instructive one: nothing in #33 is known broken, and
three of its rows (M1, M5, M11) had been owed *precisely because no build was
ever installed to run them against*. Blocking the release on them made the
dependency circular — the rows need an APK the release produces.

The distinction the label must carry:

| State | Blocks a tag? |
|---|---|
| Known defect that must not reach users | **yes** — label it |
| Unverified, nothing known wrong | no — verify against the release build |
| Known defect judged acceptable to ship | no — but say so on the issue |

Verification belongs *after* the tag anyway, because the release APK is the
artifact users get: a sideloaded debug build exercises neither `minifyEnabled`
nor release signing, so verifying on one leaves the ProGuard-sensitive surface
unproven — and costs an uninstall plus a SAF re-grant. If a post-tag row fails,
what it surfaces gets the label and the next patch release fixes it.

Conflating the two states would have devalued the label for real defects, which
is the only job it has.

**3. Readiness is executable, not a document.** `scripts/release-ready.sh`
answers "is it time to tag?" with a verdict and per-check evidence: open
blockers, unstruck milestone rows, release-row position, non-empty
`[Unreleased]`, tag availability, repo state. Exit 0 READY / 1 BLOCKED.
A checklist you have to remember to read is what produced the v0.4.0 miss.

**4. The release runs as its own pipeline.**
`.pipeline-templates/release-state.json` — 12 stages, gate → bump → changelog →
build → PR → review → merge → tag → verify → retro. It is not selected by
`/pipeline-next` (no ROADMAP row type maps to it); it is copied into
`.pipeline-state/` by hand and run with `/pipeline-run`. Stage 1 refuses to
proceed unless the gate script exits READY, and stage 11 refuses to call the
release done until the GitHub release carries an actual APK asset — a green
workflow with no asset is a failed release.

## Consequences

- The tag moves later in every milestone. For v0.5.0 that means #35 and #38
  both land before it, which is what "don't ship known-broken" implies anyway.
- Someone must decide blocker-or-not when filing an issue. Getting it wrong is
  cheap and reversible in both directions: label it later, or remove it.
- The gate can be wrong. It reads a markdown table and a label, so a ROADMAP
  formatting change can silently break the milestone-completeness check. The
  release template's retro stage asks explicitly whether the gate's verdict
  matched reality, so a wrong verdict is a finding about the gate.
- The gate is only as good as the label discipline. It cannot detect a critical
  bug nobody filed. It replaces "did anyone remember?" with "did anyone label
  it?", which is a weaker question but an auditable one.
- Two milestones now fold into one tag (v0.4.0 was never released, so v0.5.0
  carries both). v0.4.0 will never exist as a version. Deliberate: an
  intermediate tag would publish a build nobody would ever install.

## Alternatives considered

**ROADMAP P0/P1 rows as the blocking signal** — no new machinery, reuses the
existing matrix. Rejected because it reproduces gap 3 exactly: a bug filed
between strategy sessions cannot block anything until someone roadmaps it.

**A `docs/release.md` checklist, no executable** — cheapest and honest about
being manual. Rejected because the signal would be something you go and read,
which is the failure mode that lost v0.4.0.

**Gate script with no release template** — the release would run as an ordinary
feature pipeline reading a documented procedure. Rejected because the two steps
that actually got dropped (pushing the tag, confirming the published artifact)
happen *after* a normal pipeline's last stage, so no ordinary template can own
them.
