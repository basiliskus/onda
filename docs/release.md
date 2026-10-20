# Release checklist

Use this checklist to publish a milestone. The [release policy](adr/0001-release-at-milestone-end.md)
requires an executable readiness verdict and verification of the published APK.
A merged version bump alone is not a release.

## Readiness gate

Run from a checkout with GitHub CLI access to the repository:

```sh
scripts/release-ready.sh vX.Y.Z
```

Replace `vX.Y.Z` with the target version. Without an argument, the script picks
the oldest milestone heading with an unstruck release row; if none remains,
it falls back to the last milestone heading. An explicit version is preferable
for release work. Exit codes are **0 READY**, **1 BLOCKED**, **2 cannot decide**.
Stop on either nonzero exit and preserve the output; fix incorrect gate behavior
rather than bypassing it. READY means nothing known blocks preparation, not
permission to merge or tag.

The gate reads [ROADMAP.md](../ROADMAP.md) headings shaped
`### Milestone: vX.Y.Z — title` and matrix rows shaped
`| **P1** | feature | reason | vX.Y.Z |`. Keep the feature in the second cell
and the milestone in the final cell. Strike shipped features with `~~`; the
`release vX.Y.Z` row must be the last row of its milestone. The gate also checks
open `release-blocker` issues, nonempty changelog `[Unreleased]`, tag availability,
and repository state. It doesn't check CI or the published artifact.

## Prepare and review

1. Fetch `origin` and run the gate for the target against current `main` before
   moving changelog entries. Save the full output and verify passing `ci.yml`
   results for that exact main SHA. Create a `release-vX.Y.Z` branch and worktree.
2. Set `versionName` in `app/build.gradle.kts` to `X.Y.Z` and increment
   `versionCode` by one. Check the parser used by [the release workflow](../.github/workflows/release.yml):

   ```sh
   sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' app/build.gradle.kts
   ```

   Its output must match the target without `v`.
3. Move `[Unreleased]` into `## [X.Y.Z] - YYYY-MM-DD` and leave a fresh empty
   `[Unreleased]` above it. Account for merged PRs since the previous tag;
   user-visible changes need an entry. Derive numbers and verify broad claims.
   Prepare the struck release row, dated milestone note and Completed entry in
   the roadmap; publication is still pending until the checks below pass.
4. Run the [development checks](development.md#verify-and-document), forcing
   fresh tests and recording JUnit counts and raw outputs. The debug build
   doesn't verify release signing or minification; CI produces the signed APK.
5. Commit only `app/build.gradle.kts`, `CHANGELOG.md` and `ROADMAP.md` as
   `chore: release vX.Y.Z`. Open a PR with the release notes and evidence. Get
   independent review, resolve findings, and verify CI on the exact PR head.
   Obtain the user's approval before merging.

## Publish and verify

1. After merge, update `main` with a fast-forward pull. Confirm its HEAD is the
   release PR's merge commit, the workflow parser yields the target version,
   and `ci.yml` passed on this exact commit. Don't tag the old branch head.
2. Obtain the user's authorization to tag and publish that commit. Use an
   annotated tag with a message matching the style of `git tag -n1`:

   ```sh
   git tag -m "<one-line release summary>" vX.Y.Z
   git push origin vX.Y.Z
   ```

3. Identify the `release.yml` run by tag and commit SHA, then watch that run to
   completion. Inspect the GitHub release for an actual, nonempty
   `app-release.apk` asset. A green run without that asset is a failed release.
4. Download the APK into a fresh directory. Verify its signature with the SDK's
   `apksigner verify --verbose --print-certs`, compare the signing certificate
   with a separately downloaded known-good release, and use `apkanalyzer
   manifest version-name` and `apkanalyzer manifest version-code` to confirm
   the intended version. Keep each downloaded artifact at its own path.
5. Install the published APK using Obtainium or adb and record launch, synced
   folder loading and playback results. Run the applicable [hardware smoke
   checks](smoke-checklist.md), with pass/fail/skip(reason), device, version and
   evidence. Never report an unrun check as passed. A debug build can't replace
   this check of the signed, minified artifact.
6. Confirm the tag, release asset, workflow and exact-commit CI evidence agree
   before reporting completion. If observations contradict, stop and re-establish
   both checks with fresh downloads. For local test evidence, freshness is
   relative to the newest source mtime, not the later commit time.

If publication fails, preserve the run and artifact evidence and agree a recovery
plan with the user; don't automatically delete or retarget a published tag.
Label a known must-not-ship defect `release-blocker` when filing it. Unverified
hardware behavior alone does not block the tag: run it against the release APK
and record any resulting defect, as the policy requires. Close a hardware row
that cannot distinguish the cases as not hardware-observable, naming the automated
assertion that covers it. Record whether the gate's verdict matched reality;
a false READY is a defect in the gate.
