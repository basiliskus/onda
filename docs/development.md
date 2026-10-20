# Development checklist

Use this checklist for coordinated changes. Read [AGENTS.md](../AGENTS.md)
and the [product spec](spec.md) first; use [the release checklist](release.md)
for a release. You need JDK 17 and an Android SDK discoverable through
`ANDROID_HOME` or an ignored `local.properties` file containing `sdk.dir`.
Use the checked-in Gradle wrapper.

## Plan and implement

- [ ] Fetch `origin`, verify the task against current code, and record the base
  SHA. Use one branch and worktree per task. Write down the diagnosis or plan,
  affected files, risks, and verification before implementation; new features
  also need a design conversation and a written design doc. State planned
  omissions as open review questions, not settled rationale.
- [ ] For bugs, reproduce the user-visible failure before fixing its root cause
  and add a regression test where feasible. For refactors, preserve behavior
  and account for the plan's tests. Map each enumerated rule or case to a test.
- [ ] Check null, empty, boundary, concurrent and in-flight cases. For identity
  changes, measure collisions in real data; compare candidate rules on inputs
  where they diverge. Sweep sibling consumers and exit paths for the same bug.
- [ ] Keep suspend/timeout/flow logic testable in `:core`, or explain the platform
  dependency. Check API stability annotations, justify composition-time side
  effects, and verify new public APIs have production or framework callers.
  Keep symbols internal unless a public boundary needs them.
- [ ] For undo/rollback, confirm referenced identities survive compaction and
  deduplication; use snapshots when they don't. State dispatcher assumptions in
  ordering tests, and cover repeated triggers before a fetch completes.
- [ ] Review errors shown in UI, notifications and logs for leaked stream URLs,
  paths and tree URIs. Check changed code and doc examples for secrets and
  injection risks. Record unrelated defects separately.
- [ ] For cross-repo changes, check the real producer/consumer and update the
  [data contract](data-contract.md). Record warnings and the receiving owner;
  don't claim a handoff, issue or downstream change exists before verifying it.

## Verify and document

- [ ] Run the repository checks from the task worktree. Force fresh test execution
  when claiming a new suite pass; derive counts from JUnit XML, not console text.

  ```sh
  ./gradlew :core:test :app:testDebugUnitTest --rerun-tasks
  ./gradlew :app:lintDebug
  ./gradlew :app:assembleDebug
  python3 scripts/check_enforced_by.py
  git diff --check
  ```

- [ ] Save each command, its actual exit code and full raw output for the
  reviewer. Verify the harness itself: a pipe's last command can hide failure;
  timestamps alone can't distinguish a write from a sync touch. Evidence files
  must exist and contain the claimed result before a checklist item is complete.
- [ ] Give each manual test a `Requires` value (device, emulator, desktop script
  or none) and record pass/fail/skip(reason). Execute script-only rows here.
  Cover algebra rules composed with undo/retry/refresh, not just gestures.
  Automate whole gesture chains before checking files and UI, avoiding animation
  races. If a row is the sole mitigation for a risk, name its unresolved carry
  and acceptance condition; don't silently skip it. See [smoke checks](smoke-checklist.md)
  for release hardware verification and [release policy](adr/0001-release-at-milestone-end.md)
  for the distinction between unverified behavior and a release blocker.
- [ ] Update affected docs, inventories and the changelog; resolve links and
  search for copies of any corrected factual claim. Derive counts at the commit
  being described. Check broad prose claims against evidence or narrow them;
  cite the SHA and command for cross-repo historical claims. Follow the
  [`enforced by:` convention](canon.md) for testable source guarantees.
- [ ] For roadmap work, prepare the struck priority-matrix row and shipped note
  in its milestone section, effective when merged. Preserve the table and
  heading shapes [the gate reads](release.md#readiness-gate).

## Review and hand off

- [ ] Compare the final diff with the plan and account for every deliverable and
  planned test. Commit on the task branch with an appropriate conventional
  prefix (`feat:`, `fix:`, `refactor:` or `chore:`). Give a fresh reviewer from a different
  model family the base/head SHAs, changed files, raw check output and known gaps.
- [ ] Reviewers declare their method (for example mutation, differential execution
  or driving the real consumer). For tests claiming a production mechanism is
  enforced, remove/invert that mechanism and confirm failure. Use
  `scripts/mutation-run.sh` in an isolated worktree; it clears generated test
  results and rejects stale XML. Persist the mutation table with APPLIED results
  distinguished from PREDICTED claims; sample predictions and classify survivors
  as coverage gaps or equivalent mutants. Don't leave mutations in the task tree.
- [ ] Resolve findings and rerun affected checks. Re-review behavior-changing fix
  deltas with a different method from the preceding round. Verify PR prose matches
  the final diff and CI passes on its exact head; get user approval before merge.
  Draft comments/reviews for the user: don't post on their behalf without approval
  for the specific message.
- [ ] Complete any planned post-merge observation and retain its evidence. Record
  useful lessons in the maintained checklist or [canon](canon.md), and track
  unfinished work explicitly rather than claiming it happened.
