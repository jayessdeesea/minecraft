#!/bin/sh
# Commits the tracked changes behind a full verify: committed and pushed only on BUILD SUCCESS with no BUILD FAILURE,
# else nothing. It adds with `git add -u`, so `git add` new files first. Don't edit the tree or run mvn in it while
# this runs: its add comes after the verify and would sweep up changes the verify never saw.
# Usage: commit.sh <message file, outside the repo>
[ -f "$1" ] || { echo "usage: commit.sh <message file>" >&2; exit 2; }
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
out=$(sh tools/ops/verify.sh); echo "$out"
if echo "$out" | grep -q "BUILD SUCCESS" && ! echo "$out" | grep -q "BUILD FAILURE"; then
  git add -u && git commit -q -F "$1" && git push -q origin main && git log --oneline -1 && git rev-list --count HEAD
else
  exit 1
fi
