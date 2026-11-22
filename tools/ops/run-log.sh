#!/bin/sh
# Prints one run's lines of the garrison's log: the hourly files from the hour the run started to the hour the next one
# did ($S/logs, UTC), kept to the lines of its process - each line carries its PID. A line carried on over several - a
# SITREP's soldiers, a stack trace - gives its first only. The runs are deploy.sh's ledger, $S/runs.log: a line a run,
# "<name> <started, UTC> <commit> <PID>".
# Usage: run-log.sh [run-name, default the last]
S="${GARRISON_RUNS:-C:/mc-state/garrison}"; R="$S/runs.log"
[ -f "$R" ] || { echo "no runs recorded in $R (deploy.sh writes it)" >&2; exit 2; }
run="${1:-$(tail -1 "$R" | cut -d' ' -f1)}"
line=$(grep "^$run " "$R" | tail -1)
[ -n "$line" ] || { echo "no run '$run' in $R" >&2; exit 2; }
pid=$(echo "$line" | cut -d' ' -f4)
hour() { echo "$1" | sed -E 's/^([0-9-]{10})T([0-9]{2}).*/\1-\2/'; }
from=$(hour "$(echo "$line" | cut -d' ' -f2)")
next=$(awk -v r="$run" 'found { print $2; exit } $1 == r { found = 1 }' "$R")
to=$([ -n "$next" ] && hour "$next" || echo 9999)
find "$S/logs" -name '*_application.log' | sort | while IFS= read -r file; do
  at=$(basename "$file" _application.log)
  if [ ! "$at" \< "$from" ] && [ ! "$at" \> "$to" ]; then grep -h " $pid --- " "$file"; fi
done
