#!/usr/bin/env bash
#
# Answers one question: is it time to tag a release?
#
#   scripts/release-ready.sh            # target = oldest pending release; last milestone fallback
#   scripts/release-ready.sh v0.5.0     # target = an explicit version
#
# Exit 0 = READY, 1 = BLOCKED, 2 = the script could not decide.
#
# READY means every check below passed. It does NOT mean "release now" — it
# means nothing known is in the way, so release preparation may start.
# Checklist: docs/release.md. Policy: docs/adr/0001-release-at-milestone-end.md.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 2

ROADMAP=ROADMAP.md
CHANGELOG=CHANGELOG.md
GRADLE=app/build.gradle.kts

blocked=0
pass() { printf '  \033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$1"; blocked=1; }
note() { printf '      %s\n' "$1"; }
die()  { printf '\033[31mCANNOT DECIDE:\033[0m %s\n' "$1" >&2; exit 2; }

[ -f "$ROADMAP" ]   || die "no $ROADMAP"
[ -f "$CHANGELOG" ] || die "no $CHANGELOG"
[ -f "$GRADLE" ]    || die "no $GRADLE"
command -v gh >/dev/null || die "gh not on PATH — the blocker check needs it"

# ---------------------------------------------------------------- target
if [ $# -ge 1 ]; then
    TARGET="$1"
else
    # The OLDEST milestone whose release row is still unstruck — the next tag to cut, not
    # the furthest-out one planned. Defaulting to the newest heading answered about a
    # milestone two releases away the moment v0.5.1 was opened while v0.5.0 was being cut.
    TARGET=$(awk '
        # FS is "|" for the matrix rows, so a heading has no usable fields — match on $0.
        /^### Milestone: v[0-9]+\.[0-9]+\.[0-9]+/ {
            if (match($0, /v[0-9]+\.[0-9]+\.[0-9]+/)) order[++n] = substr($0, RSTART, RLENGTH)
        }
        /^\|/ {
            if (NF < 5) next
            ms = $(NF-1); gsub(/^[ \t]+|[ \t]+$/, "", ms)
            feature = $3; gsub(/^[ \t]+|[ \t]+$/, "", feature)
            bare = ms; sub(/^v/, "", bare)
            if (tolower(feature) ~ ("release[ ]+v?" bare) && feature !~ /~~/) pending[ms] = 1
        }
        END { for (i = 1; i <= n; i++) if (order[i] in pending) { print order[i]; exit } }
    ' FS='|' "$ROADMAP")
    [ -n "$TARGET" ] || TARGET=$(grep -oE '^### Milestone: v[0-9]+\.[0-9]+\.[0-9]+' "$ROADMAP" | tail -1 | awk '{print $3}')
    [ -n "$TARGET" ] || die "no '### Milestone: vX.Y.Z' heading in $ROADMAP — pass a version explicitly"
    echo "(no version given — targeting $TARGET, the oldest milestone with an unshipped release row)"
fi
case "$TARGET" in
    v[0-9]*.[0-9]*.[0-9]*) ;;
    *) die "target '$TARGET' is not vX.Y.Z" ;;
esac
BARE="${TARGET#v}"

echo
echo "Release readiness — $TARGET"
echo

# ------------------------------------------------- 1. release blockers
# The only severity signal this repo has. Anything that must not ship gets
# the label at file time, so a bug found today blocks today rather than
# waiting for a strategy session to roadmap it.
#
# Deliberately NOT `gh issue list --label`: that reads GitHub's search index,
# which was measured lagging ~60s behind a freshly applied label — the gate
# would report READY for a bug just marked as a blocker, failing open in
# exactly the case it exists to catch. The REST endpoint was correct
# immediately, so filter its output locally. It also returns pull requests,
# hence the `.pull_request == null` guard.
if ! blockers=$(gh api --paginate 'repos/{owner}/{repo}/issues?state=open&per_page=100' \
                    -q '.[] | select(.pull_request == null)
                           | select([.labels[].name] | index("release-blocker"))
                           | "#\(.number) \(.title)"' 2>&1); then
    die "gh api issues failed: $blockers"
fi
if [ -n "$blockers" ]; then
    fail "open release-blocker issues"
    while IFS= read -r line; do note "$line"; done <<<"$blockers"
else
    pass "no open release-blocker issues"
fi

# ------------------------------------------------- 2. milestone complete
# Priority-matrix rows are `| **PN** | feature | why | milestone |`. A row is
# done when its feature cell is struck through. The release row is the one
# this script is about, so it is expected to be unstruck.
unstruck=$(awk -F'|' -v ms="$TARGET" -v bare="$BARE" '
    /^\|/ {
        if (NF < 5) next
        milestone = $(NF-1); gsub(/^[ \t]+|[ \t]+$/, "", milestone)
        if (milestone != ms) next
        feature = $3; gsub(/^[ \t]+|[ \t]+$/, "", feature)
        if (feature ~ /~~/) next                       # struck = shipped
        if (tolower(feature) ~ ("release[ ]+v?" bare)) next   # the release row itself
        priority = $2; gsub(/[ *\t]/, "", priority)
        print priority " " feature
    }' "$ROADMAP")
if [ -n "$unstruck" ]; then
    fail "$TARGET has unfinished rows"
    while IFS= read -r line; do note "$line"; done <<<"$unstruck"
else
    pass "every $TARGET row is shipped"
fi

# ---------------------------------------- 3. release row is the last row
# Convention from ADR-0001: the release is the final row of its milestone,
# so "all rows above it are struck" and "ready to tag" are the same fact.
last_feature=$(awk -F'|' -v ms="$TARGET" '
    /^\|/ {
        if (NF < 5) next
        milestone = $(NF-1); gsub(/^[ \t]+|[ \t]+$/, "", milestone)
        if (milestone != ms) next
        feature = $3; gsub(/^[ \t]+|[ \t]+$/, "", feature)
        line = feature
    }
    END { print line }' "$ROADMAP")
if printf '%s' "$last_feature" | grep -qiE "release +v?$BARE"; then
    pass "release row is last in the $TARGET matrix"
else
    fail "release row is not last in the $TARGET matrix (last is: ${last_feature:-none})"
    note "ADR-0001: the release ships a milestone, so it goes at the end of one."
fi

# ------------------------------------------------ 4. something to ship
unreleased=$(awk '/^## \[Unreleased\]/{f=1;next} /^## \[/{f=0} f' "$CHANGELOG" | grep -c '[^[:space:]]')
if [ "$unreleased" -gt 0 ]; then
    pass "CHANGELOG [Unreleased] has content ($unreleased lines)"
else
    fail "CHANGELOG [Unreleased] is empty — nothing to release"
fi

# ------------------------------------------------------- 5. tag is free
if git rev-parse -q --verify "refs/tags/$TARGET" >/dev/null; then
    fail "tag $TARGET already exists locally"
elif git ls-remote --exit-code --tags origin "$TARGET" >/dev/null 2>&1; then
    fail "tag $TARGET already exists on origin"
else
    pass "tag $TARGET is free"
fi

# ------------------------------------------------------ 6. repo state
current=$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' "$GRADLE")
[ -n "$current" ] || die "could not parse versionName from $GRADLE"
if [ "$current" = "$BARE" ]; then
    note "versionName is already $BARE — the bump has landed"
else
    note "versionName is $current; the release checklist bumps it to $BARE"
fi

branch=$(git rev-parse --abbrev-ref HEAD)
if [ "$branch" = "main" ]; then
    git fetch -q origin main 2>/dev/null
    behind=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo 0)
    if [ "$behind" -gt 0 ]; then
        fail "main is $behind commits behind origin/main"
    elif [ -n "$(git status --porcelain)" ]; then
        fail "working tree is dirty"
    else
        pass "on main, clean, synced with origin"
    fi
else
    note "on branch '$branch' — the tag must be cut from main"
fi

# ------------------------------------------------------------- verdict
echo
if [ "$blocked" -eq 0 ]; then
    printf '\033[32mREADY\033[0m — nothing known blocks %s.\n' "$TARGET"
    printf 'Follow the release checklist: docs/release.md.\n\n'
    exit 0
fi
printf '\033[31mBLOCKED\033[0m — resolve the ✗ rows above before tagging %s.\n\n' "$TARGET"
exit 1
