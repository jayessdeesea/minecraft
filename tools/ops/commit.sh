#!/bin/sh
# Commits the tracked changes, verifies that commit in a worktree of its own, and pushes it only on BUILD SUCCESS with
# no BUILD FAILURE. What's verified is the commit, not the tree, so the tree is free to edit while this runs. A commit
# that fails is taken back (its changes left staged) while it's still HEAD; main is only ever pushed verified, which
# deploy.sh holds a deploy to. It adds with `git add -u`, so `git add` new files first.
# The worktree is VERIFY_WORKTREE, default C:/mc-worktrees/verify, kept between runs.
# Usage: commit.sh <message file, outside the repo>
[ -f "$1" ] || { echo "usage: commit.sh <message file>" >&2; exit 2; }
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
W="${VERIFY_WORKTREE:-C:/mc-worktrees/verify}"
git add -u && git commit -q -F "$1" || exit 1
commit=$(git rev-parse HEAD)
if [ -e "$W/.git" ]; then
  git -C "$W" checkout -q --detach "$commit" || exit 1
else
  git worktree prune && git worktree add -q --detach "$W" "$commit" || exit 1
fi
out=$(sh "$W/tools/ops/verify.sh"); echo "$out"
if echo "$out" | grep -q "BUILD SUCCESS" && ! echo "$out" | grep -q "BUILD FAILURE"; then
  git push -q origin "$commit:main" || exit 1
  git log --oneline -1 "$commit"
  count=$(git rev-list --count "$commit"); echo "$count"
  [ $((count % 16)) -eq 0 ] && echo "commit $count: a profile is due (docs/conventions.md, \"Profile every sixteenth commit\")"
  [ $((count % 16)) -eq 8 ] && echo "commit $count: a refactor pass is due (docs/conventions.md, \"Refactor every sixteenth commit, between profiles\"; sh tools/ops/hotspots.sh)"
  sh tools/ops/hotspots.sh | sed -n '/the slowest tests/,$p'
  exit 0
fi
if [ "$(git rev-parse HEAD)" = "$commit" ]; then
  git reset -q --soft HEAD~1
  echo "the verify failed: the commit is taken back, its changes left staged" >&2
else
  echo "the verify failed, and HEAD has moved on from $commit: not pushed - take it out by hand" >&2
fi
exit 1
