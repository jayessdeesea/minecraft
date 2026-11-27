#!/bin/sh
# Lands the change of the worktree this is run in through the landing queue: the installed land.sh, whose exit is this
# script's (docs/conventions.md, "Landing a change"). The tool commits the tracked changes with the message - `git add`
# new files first - squashes the branch onto main, and waits while the queue verifies and pushes it. Landed with nothing
# more here (0), this then says the commit count on main, what that count makes due, and the last verify's slowest
# tests. No landing installed is 6, as land.sh's own.
# Usage: commit.sh <message file, outside the repo> [--timeout <minutes>]
LAND="${LANDING_LAND:-C:/mc-state/landing/land.sh}"
if [ ! -f "$LAND" ]; then
  echo "landing is not installed: no $LAND (sh tools/ops/install-landing.sh)" >&2
  exit 6
fi
[ -z "${VERIFY_WORKTREE:-}" ] || echo "VERIFY_WORKTREE is not read: the queue verifies in a worktree of its own" >&2
sh "$LAND" "$@"; landed=$?
[ "$landed" -eq 0 ] || exit "$landed"
count=$(git rev-list --count HEAD) && {
  echo "$count"
  [ $((count % 16)) -eq 0 ] && echo "commit $count: a profile is due (docs/conventions.md, \"Profile every sixteenth commit\")"
  [ $((count % 16)) -eq 8 ] && echo "commit $count: a refactor pass is due (docs/conventions.md, \"Refactor every sixteenth commit, between profiles\"; sh tools/ops/hotspots.sh)"
}
sh "$(dirname "$0")/hotspots.sh" --slowest
exit 0
