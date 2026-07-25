# Onda

Personal Android radio player — a Transistor replacement fitted to one
user's workflow. Full product spec, prior research, and accumulated
stream-quirk knowledge: `docs/spec.md`. Read it before any design or
implementation work.

## Development process

**Phase: OWNERSHIP — pipeline-skills drives all work.**

- The MVP shipped as v0.1.0 (2026-07-23). `/pipeline-init` has run:
  `.pipeline-templates/` exists and `ROADMAP.md` is the work queue.
- Milestones and bugfixes run as pipeline feature/bugfix runs
  (`/pipeline-next` → `/pipeline-run`); retrospectives
  (`/pipeline-retro`) graduate lessons into the template canon
  (`.pipeline-templates/*.json` — project-local templates are
  customized deliberately and never regenerated blindly).
- superpowers is demoted to brainstorming-only: use
  `superpowers:brainstorming` for new-feature design conversations;
  everything after the design doc goes through the pipeline.
- Direct-to-main threshold, reconciled with the Git workflow section
  (which always wins): nothing is ever committed directly to main.
  Trivial one-liners go as a single-commit PR without a pipeline run;
  multi-file work goes through a pipeline run + PR.

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
`stations.csv` (+ five sibling playlists) in the dotfiles repo
(`~/.dotfiles/home/.config/pyradio/`), exported by
`~/.dotfiles/scripts/tools/stations-to-m3u.py` to `~/Sync/Radio/*.m3u`
(EXTM3U with `group-title` on every entry), which Synology Drive syncs
to the phone. The app watches that synced folder and hot-reloads —
importing is not a concept. Six playlists today: stations, nts, fip,
bbc, public-radio, radio-garden (~205 stations).

Phone-side edits flow back through `ops.jsonl` in the same folder
(identity-anchored add/delete/move ops; optimistic overlay until the
desktop merge script consumes-and-clears; the phone owns the journal).
Replaced `pending.csv` in v0.3.0 (first launch migrates). Contract:
`docs/superpowers/specs/2026-07-23-ops-journal-design.md`.

<!-- pipeline-config: managed by /pipeline-init -->
## Pipeline configuration

- default_branch: main
- pr_target_branch: main
- test_command: ./gradlew :core:test :app:testDebugUnitTest
- lint_command: ./gradlew :app:lintDebug
- build_command: ./gradlew :app:assembleDebug
- venv_path:
- changelog: keep-a-changelog
<!-- /pipeline-config -->
