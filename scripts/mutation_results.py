#!/usr/bin/env python3
"""Parse one mutation evaluation's test results, refusing stale XML (GitHub #36).

Usage: mutation_results.py <run-start-epoch> <gradle-exit> <results-dir>...

`./gradlew :core:test :app:testDebugUnitTest` stops at the first failing task,
leaving the other module's previous XML on disk; a driver that parses the
results dirs afterwards silently attributes stale results to the current
mutation (PR #34's reviewer saw three different mutations report identical
failure sets). Every XML parsed here must be newer than the run's start, or
the whole evaluation is rejected.

Verdicts: KILLED (failing rows), KILLED-BY-BUILD (gradle failed with zero
failing rows — a compile-error mutation kills before any row runs, including
the one-module-compiles case), SURVIVED (clean run, zero failures).
Exit 0 = trustworthy result; exit 1 = stale XML, discard and re-run.
"""

import glob
import os
import sys
import xml.etree.ElementTree as ET


def main():
    start = int(sys.argv[1])
    gradle_exit = int(sys.argv[2])
    files = [f for d in sys.argv[3:] for f in glob.glob(os.path.join(d, "*.xml"))]

    stale = [f for f in files if os.path.getmtime(f) < start]
    for f in stale:
        print(f"mutation-run: STALE XML (predates this run): {f}")

    tests = failed = 0
    for f in files:
        root = ET.parse(f).getroot()
        tests += int(root.get("tests", 0))
        failed += int(root.get("failures", 0)) + int(root.get("errors", 0))

    if failed:
        verdict = "KILLED"
    elif gradle_exit != 0:
        verdict = "KILLED-BY-BUILD (no failing rows — compile error or task failure)"
    else:
        verdict = "SURVIVED"
    print(f"mutation-run: {tests} tests, {failed} failed -> {verdict}")
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
