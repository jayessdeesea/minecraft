#!/bin/sh
# Where the code base is hard to change, measured: what a refactor pass reads before it chooses what to simplify
# (docs/conventions.md, "Refactor every sixteenth commit, between profiles"). Nothing here is a judgement - each list is a count.
#   1. churn x size: main sources changed most often in the last N commits, weighted by their length - a long file
#      everything passes through is where a change costs most to make and to check.
#   2. returned to: sources changed in three or more of the last eight commits - a fix that wanted a fix.
#   3. changed together: pairs of main sources that changed in the same commit four times or more in the last N -
#      one decision kept in two places.
#   4. long lines: main sources by lines over 200 characters.
#   5. the slowest tests of the last verify, from its surefire reports.
# Usage: hotspots.sh [commits, default 64]
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
N="${1:-64}"
# The worktree whose surefire reports are read: REPORTS_WORKTREE when set, else commit.sh's verify worktree.
W="${REPORTS_WORKTREE:-${VERIFY_WORKTREE:-C:/mc-worktrees/verify}}"
main='^(protocol|command|command-ai|bot|landing|app)/src/main/.*[.]java$'

echo "== churn x size, the last $N commits (changes, lines, changes x lines)"
git log -n "$N" --name-only --pretty=format: | grep -E "$main" | sort | uniq -c | while read -r changes file; do
  [ -f "$file" ] && echo "$changes $(wc -l < "$file") $file"
done | awk '{printf "%8d  %3d x %5d  %s\n", $1 * $2, $1, $2, $3}' | sort -rn | head -12

echo "== returned to: changed in 3 or more of the last 8 commits"
git log -n 8 --name-only --pretty=format: | grep -E "$main" | sort | uniq -c | awk '$1 >= 3 {printf "%3d of 8  %s\n", $1, $2}' | sort -rn

echo "== changed together 4 times or more in the last $N commits"
git log -n "$N" --name-only --pretty=format:@ | awk -v main="$main" '
  /^@/ { for (i = 1; i <= n; i++) for (j = i + 1; j <= n; j++) pair[f[i] " + " f[j]]++; n = 0; next }
  $0 ~ main { sub(/.*\//, ""); f[++n] = $0 }
  END { for (i = 1; i <= n; i++) for (j = i + 1; j <= n; j++) pair[f[i] " + " f[j]]++
        for (p in pair) if (pair[p] >= 4) printf "%3d  %s\n", pair[p], p }' | sort -rn | head -12

echo "== lines over 200 characters"
git ls-files | grep -E "$main" | while read -r file; do
  long=$(awk 'length($0) > 200 { n++ } END { print n + 0 }' "$file")
  [ "$long" -gt 0 ] && echo "$long $file"
done | sort -rn | head -8

echo "== the slowest tests of the last verify"
if ls "$W"/*/target/surefire-reports/TEST-*.xml > /dev/null 2>&1; then
  grep -ho '<testcase name="[^"]*" classname="[^"]*" time="[0-9.]*"' "$W"/*/target/surefire-reports/TEST-*.xml \
    | sed -E 's/<testcase name="([^"]*)" classname="[^"]*\.([^".]*)" time="([0-9.]*)"/\3 \2.\1/' | sort -rn | head -15
  grep -ho '<testcase [^>]*time="[0-9.]*"' "$W"/*/target/surefire-reports/TEST-*.xml | sed -E 's/.*time="([0-9.]*)"/\1/' \
    | awk '{ s += $1; n++ } END { printf "%d tests, %.0f s summed\n", n, s }'
else
  echo "no surefire reports under $W"
fi
