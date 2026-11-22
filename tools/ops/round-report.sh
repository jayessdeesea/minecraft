#!/bin/sh
# A live round's digest of the garrison's log: what to dig into first, counted, with the first few of each - the
# alerts by kind, the deaths, blocks the tables don't know, the missions and objectives, who was taken off a task most
# often, the changes of activity, and what was thrown away - then the chat probe (chatprobe.sh). Reads only; the run's
# lines are gathered into $S/round-<run>.log (run-log.sh) for looking further.
# Usage: round-report.sh [run-name, default the last; or a log file] [--no-chat]
S="${GARRISON_RUNS:-C:/mc-state/garrison}"
RUN=""; CHAT=yes
for a in "$@"; do
  case "$a" in --no-chat) CHAT=no ;; *) RUN="$a" ;; esac
done
if [ -f "$RUN" ]; then
  L="$RUN"
else
  [ -n "$RUN" ] || RUN=$(tail -1 "$S/runs.log" 2>/dev/null | cut -d' ' -f1)
  L="$S/round-$RUN.log"
  sh "$(dirname "$0")/run-log.sh" "$RUN" > "$L" || exit 2
fi
# The message of a log line: what follows the logger's name.
msg() { sed -E 's/^[^ ]+ +[A-Z]+ [0-9]+ --- \[[^]]*\] [^ ]+ +: //' | cut -c1-200; }
section() { echo; echo "== $1"; }
echo "$(basename "$L" .log): $(head -1 "$L" | cut -d' ' -f1) to $(tail -1 "$L" | cut -d' ' -f1), $(grep -c "" "$L") lines"

section "[Alert]: $(grep -c "\[Alert\]" "$L"), by soldier and kind"
grep "\[Alert\]" "$L" | grep -v "has room again" | msg | sed -E 's/^\[Alert\] ([A-Za-z]+) ([a-z]+).*/\1 \2/' | sort | uniq -c | sort -rn | head -15
grep "\[Alert\]" "$L" | grep -v "has room again" | msg | awk '{k=$2" "$3} !seen[k]++' | head -8

section "Deaths: $(grep -c "is dead - respawning" "$L")"
grep "\[Casualty\]" "$L" | msg | tail -8

section "[Alert] unknown block: add each to the block tables"
grep "\[Alert\] unknown block" "$L" | msg | sort | uniq -c | sort -rn | head -20

section "[Mission]"
grep "\[Mission\]" "$L" | msg | tail -10
section "[Objectives]"
grep "\[Objectives\]" "$L" | msg | tail -10

section "[Register] taken off: $(grep -c "\[Register\].*taken off" "$L"), by soldier and task"
grep "\[Register\].*taken off" "$L" | msg | sed -E 's/^\[Register\] ([A-Za-z]+) taken off (.*), after.*/\1: \2/' | sort | uniq -c | sort -rn | head -12

section "[State] changes of activity: $(grep -c "\[State\]" "$L"), returns $(grep -c "\[State\].*(back again)" "$L"); by soldier, then the commonest"
grep "\[State\]" "$L" | msg | sed -E 's/^\[State\] ([A-Za-z]+): .*/\1/' | sort | uniq -c | sort -rn | head -8
grep "\[State\]" "$L" | msg | sed -E 's/^\[State\] [A-Za-z]+: (.*) after [0-9]+ s( \(back again\))?.*/\1\2/' | sort | uniq -c | sort -rn | head -10

section "throws away: $(grep -c "throws away" "$L"), by soldier and item"
grep "throws away" "$L" | msg | sed -E 's/^([A-Za-z]+) throws away [0-9]+ of (item [0-9]+).*/\1 \2/' | sort | uniq -c | sort -rn | head -10

if [ "$CHAT" = yes ]; then
  section "chat probe: each answer reads above 0, names what's true, gives no command"
  sh "$(dirname "$0")/chatprobe.sh"
fi
