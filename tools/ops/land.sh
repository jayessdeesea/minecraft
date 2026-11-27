#!/bin/sh
# Lands this worktree's change through the queue: the installed tool's `land`, run from any branch, those that predate
# the tool included - install-landing.sh copies this file to C:/mc-state/landing/land.sh for them. Its exits are
# landing's own table. A tool not installed, or a java that does not start, is 6 - landing itself failed - never 1,
# which is an entry's FAILED.
# Usage: land.sh <message file> [--timeout <minutes>]
JAR="${LANDING_JAR:-C:/mc-state/landing/landing.jar}"
JAVA="${LANDING_JAVA:-java}"
if [ ! -f "$JAR" ]; then
  echo "landing is not installed: no $JAR (sh tools/ops/install-landing.sh)" >&2
  exit 6
fi
# with no arguments the tool answers usage, 2, once it has entered its job and read its config: any other answer is a
# tool that did not start
"$JAVA" -jar "$JAR" > /dev/null 2>&1
started=$?
if [ "$started" -ne 2 ]; then
  echo "landing did not start: $JAVA -jar $JAR exited $started" >&2
  exit 6
fi
exec "$JAVA" -jar "$JAR" land "$@"
