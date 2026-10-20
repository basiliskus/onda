#!/usr/bin/env python3
"""Dangling-reference check for the `enforced by:` convention (GitHub #31).

A comment asserting a testable guarantee names the row that enforces it as
ClassName."row name". This walks the src trees, collects every such citation,
and fails when one names a test class or row that does not exist — a renamed
or deleted row then breaks CI instead of leaving a lying comment (the shape
PR #47 hit: the citation map went stale inside a single commit).

Scope is the src trees only, deliberately: prose history (roadmaps and PR discussions) cites rows as they were at the time and must not rot the build.
Citations must sit on one line — a wrapped citation is invisible here, which
is why the convention keeps them unwrapped however long the line gets.

Stdlib-only, so it runs on a bare CI runner. Exit 0 = every citation resolves.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIRS = ["core/src", "app/src"]
CITATION = re.compile(r'([A-Z][A-Za-z0-9]*Test)\."([^"]+)"')
# @Test-gated so a backticked helper fun can never satisfy the existence check;
# every row in this repo declares as a same-line `@Test fun `row``.
ROW_DECL = re.compile(r"@Test fun `([^`]+)`")


def kt_files():
    for d in SRC_DIRS:
        for base, _, names in os.walk(os.path.join(ROOT, d)):
            for n in names:
                if n.endswith(".kt"):
                    yield os.path.join(base, n)


def main():
    test_rows = {}
    for path in kt_files():
        name = os.path.basename(path)[:-3]
        if name.endswith("Test"):
            with open(path, encoding="utf-8") as f:
                # Union, not assignment: two files sharing a basename must not
                # shadow each other's rows (would fail toward false danglings).
                test_rows.setdefault(name, set()).update(ROW_DECL.findall(f.read()))

    checked = 0
    dangling = []
    for path in kt_files():
        rel = os.path.relpath(path, ROOT)
        with open(path, encoding="utf-8") as f:
            for lineno, line in enumerate(f, 1):
                for cls, row in CITATION.findall(line):
                    checked += 1
                    if cls not in test_rows:
                        dangling.append(f"{rel}:{lineno}: no test class {cls}")
                    elif row not in test_rows[cls]:
                        dangling.append(f'{rel}:{lineno}: {cls} has no row "{row}"')

    for d in dangling:
        print(f"DANGLING: {d}")
    print(f"enforced-by: {checked} citations checked, {len(dangling)} dangling")
    return 1 if dangling else 0


if __name__ == "__main__":
    sys.exit(main())
