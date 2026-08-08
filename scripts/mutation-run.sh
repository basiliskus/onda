#!/usr/bin/env bash
# One hygienic mutation evaluation (GitHub #36): clear both per-module
# results dirs, run with --continue, refuse XML older than the run started.
# Use this instead of hand-rolling the gradle invocation — the hand-rolled
# shape misattributes a failing module's STALE sibling XML to the current
# mutation (see scripts/mutation_results.py for the full story).
set -u
cd "$(dirname "$0")/.." || exit 1

CORE=core/build/test-results/test
APP=app/build/test-results/testDebugUnitTest

rm -rf "$CORE" "$APP"
START=$(date +%s)
./gradlew --continue :core:test :app:testDebugUnitTest -q
python3 scripts/mutation_results.py "$START" "$?" "$CORE" "$APP"
