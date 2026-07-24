# Pipeline templates

Project-local state templates for the pipeline-* skill family. These OVERRIDE the
skill-default templates in the pipeline-skill's `templates/` directory (resolved via
`PIPELINE_SKILL_DIR` or the directory containing `pipeline.py`); `pipeline-next`
checks project-local first.

Installed by `/pipeline-init` 2026-07-23 from the canonical templates, with
`pr_target_branch`/`default_branch` set to `main` and the Gradle test/lint commands
injected into the relevant stages. Customizations made here are deliberate and are
never regenerated blindly — re-copying from canonical requires an explicit
`--force-templates` (with backup).

This directory is committed. `.pipeline-state/` contents and `.pipeline-log.md` are
per-checkout ephemera and stay gitignored.
