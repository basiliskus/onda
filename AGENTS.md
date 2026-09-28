# Onda

Personal Android radio player — a Transistor replacement fitted to one
user's workflow. Full product spec, prior research, and accumulated
stream-quirk knowledge: `docs/spec.md`. Read it before any design or
implementation work.

## Development process

**Phase: OWNERSHIP — coordinated agent work (Paseo, since 2026-09-27).**
Replaces the pipeline-skills runs used through v0.5.2.

- Work queue: `ROADMAP.md` for milestones; GitHub issues for bugs and
  tech debt. Verify an item against current code before starting it —
  issue text goes stale.
- One task = one branch = one worktree. A coordinator delegates
  implementation to one agent and review to a fresh agent from a
  different model family; nobody reviews their own work.
- Use the matching `.pipeline-templates/{feature,bugfix,refactor}-state.json`
  as the task checklist. Its stages hold lessons graduated from past
  retrospectives; reading them needs no pipeline skill. Template changes
  stay deliberate.
- Report the raw output of every check in **Checks**. A completion claim
  without output isn't evidence.
- New features start with a design conversation and a written design
  doc before implementation.
- Nothing is committed directly to main (Git workflow wins). Trivial
  one-liners go as a single-commit PR; everything else follows the flow
  above.

## Releases

This repo owns the release step:

- **Is it time?** `scripts/release-ready.sh` answers, with evidence.
  Never decide by reading the ROADMAP.
- **What blocks a tag:** any open issue labeled `release-blocker`.
  Label it when you file it, not at the next strategy session.
- **The release is the last row of its milestone**, always.
- **How:** on a `release-vX.Y.Z` branch, walk
  `.pipeline-templates/release-state.json` as a checklist with
  `target_version` set. Tagging is the only manual act; CI does the rest.

Policy and the reasoning behind it:
`docs/adr/0001-release-at-milestone-end.md`.

## Git workflow

- Never commit directly to main. All work goes through a branch and a
  PR with cohesive scope, reviewed and approved by the user before
  merge.
- Large PRs are acceptable early in the project; as it matures, PRs
  are expected to become smaller and more focused.

## Decisions already made — do not relitigate

- Kotlin + Jetpack Compose + Media3 (`MediaLibraryService` for
  background playback, notification, Android Auto).
- Core logic (M3U parsing, radio-browser client, favorites/pending
  model) in Android-free Kotlin modules; thin platform layer.
  KMP-shaped, without buying KMP.
- Distribution: sideload via Obtainium pointed at this repo's GitHub
  releases. No Play Store.
- UI theme: Catppuccin Frappé (matches the rest of the user's tooling;
  named semantic colors, no ad-hoc hex).

## Data contract

The app renders playlists it does not own. Source of truth is
`stations.csv` (+ five sibling playlists) in the data repo
(`~/Code/Personal/data/radio/` — moved from `.dotfiles` by dotfiles#35;
`.dotfiles` keeps only the launchd wrappers), exported by
`radio/scripts/stations-to-m3u.py` to `~/Sync/Radio/*.m3u`
(EXTM3U with `group-title` on every entry), which Synology Drive syncs
to the phone. The app watches that synced folder and hot-reloads —
importing is not a concept. Six playlists today: stations, nts, fip,
bbc, public-radio, radio-garden (~205 stations).

The canonical statement of every cross-repo invariant — producing
side, consuming side, and the test pinning each — is the **"Contract
with Onda"** section of the data repo's `radio/README.md`. When this
file and that section disagree, that section wins.

Phone-side edits flow back through `ops.jsonl` in the same folder
(identity-anchored add/delete/move ops; optimistic overlay until the
desktop merge script consumes-and-clears; the phone owns the journal).
Replaced `pending.csv` in v0.3.0; the one-time migration was removed
(#27) and a leftover `pending.csv` is ignored. Contract:
`docs/superpowers/specs/2026-07-23-ops-journal-design.md`.

## Checks

- Tests: `./gradlew :core:test :app:testDebugUnitTest`
- Lint: `./gradlew :app:lintDebug`
- Build: `./gradlew :app:assembleDebug`
- Default and PR branch: `main`. Changelog: Keep a Changelog.
