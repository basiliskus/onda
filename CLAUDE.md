# Onda

Personal Android radio player — a Transistor replacement fitted to one
user's workflow. Full product spec, prior research, and accumulated
stream-quirk knowledge: `docs/spec.md`. Read it before any design or
implementation work.

## Development process

**Phase: DESIGN/MVP — superpowers drives all work.**

- Design conversations → `superpowers:brainstorming`; outcomes become
  documents in `docs/`.
- Implementation plans → `superpowers:writing-plans`, checked into
  `docs/plans/`; execute via `superpowers:subagent-driven-development`.
- **pipeline-skills is deliberately NOT initialized** — no
  `.pipeline-templates/` exists, and that absence is the phase guard.
  Do not run `/pipeline-init` or any `/pipeline-*` command in this
  phase.
- **The phase flip is the MVP plan's final task** (verbatim, include it
  in the plan): run `/pipeline-init`; rewrite this section to the
  OWNERSHIP phase — milestones and bugfixes run as pipeline
  feature/bugfix runs, retros graduate lessons into template canon,
  superpowers demoted to brainstorming-only for new-feature design;
  state the direct-to-main threshold (trivial one-liners direct,
  multi-file work through pipeline); commit.
- Cherry-pick `mattpocock-skills` `domain-modeling` during design if
  useful. Do not adopt compound-engineering here.

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
