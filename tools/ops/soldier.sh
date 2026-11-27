#!/bin/sh
# One soldier's run, read from the garrison's log: what it is doing now, and what it kept doing - its changes of
# activity, its tasks taken on and off, its CANTCOs, failed steps and alerts, each grouped with a count and the first
# and last time (UTC) it was seen, so a loop reads as one line with a big count. A soldier's lines are its tick
# thread's ([<name>-tick]) and every other line naming it as a word. Reads only; the lines are gathered into
# $S/soldier-<name>-<run>.log (run-log.sh) for looking further.
# Usage: soldier.sh <name> [run-name, default the last; or a log file]
S="${GARRISON_RUNS:-C:/mc-state/garrison}"
NAME="$1"; RUN="$2"
echo "$NAME" | grep -Eq '^[A-Za-z]+$' || { echo "usage: soldier.sh <name> [run | log file]" >&2; exit 2; }
if [ -f "$RUN" ]; then
  SRC="$RUN"; L="$S/soldier-$NAME-$(basename "$RUN" .log).log"
  grep -E "\[ *$NAME-tick\]|\b$NAME\b" "$SRC" > "$L"
else
  [ -n "$RUN" ] || RUN=$(tail -1 "$S/runs.log" 2>/dev/null | cut -d' ' -f1)
  L="$S/soldier-$NAME-$RUN.log"
  sh "$(dirname "$0")/run-log.sh" "$RUN" | grep -E "\[ *$NAME-tick\]|\b$NAME\b" > "$L" || true
fi
[ -s "$L" ] || { echo "no lines for $NAME" >&2; exit 1; }

# A line as "<time>\t<message>": the message is what follows the logger's name.
tm() { sed -E 's/^([^ ]+) +[A-Z]+ [0-9]+ --- \[[^]]*\] [^ ]+ +: /\1\t/'; }
# Places and counts out of a message, so one cause in many places groups as one.
blur() { sed -E 's/BlockPos\[x=-?[0-9]+, y=-?[0-9]+, z=-?[0-9]+\]/<pos>/g; s/\(-?[0-9]+, -?[0-9]+, -?[0-9]+\)/<pos>/g; s/-?[0-9]+ -?[0-9]+ -?[0-9]+/<pos>/g; s/after [0-9]+ s/after N s/g'; }
# "<time>\t<key>" lines grouped: count, first and last time, key; the biggest first.
group() {
  awk -F'\t' '{ k = $2; n[k]++; if (!(k in f)) f[k] = $1; l[k] = $1 }
    END { for (k in n) printf "%6d  %s .. %s  %s\n", n[k], substr(f[k], 12, 8), substr(l[k], 12, 8), k }' |
    sort -rn | head -"${1:-12}" | cut -c1-220
}
section() { echo; echo "== $1"; }

echo "$NAME in $(basename "$L" .log): $(head -1 "$L" | cut -d' ' -f1) to $(tail -1 "$L" | cut -d' ' -f1), $(grep -c "" "$L") lines"

section "Now: the last change of activity, and the last lines"
grep "\[State\] $NAME:" "$L" | tail -1 | tm | cut -f2 | cut -c1-220
tail -5 "$L" | tm | sed -E 's/^.{11}(.{8})[^\t]*\t/\1  /' | cut -c1-220

section "Changes of activity: $(grep -c "\[State\] $NAME:" "$L"), by from -> to"
grep "\[State\] $NAME:" "$L" | tm | sed -E "s/\t\[State\] $NAME: (.*) after [0-9]+ s.*/\t\1/" | group 12

section "Tasks: $(grep -cE "\[Register\] $NAME on " "$L") put on, $(grep -cE "\[Register\] $NAME taken off " "$L") taken off"
grep -E "\[Register\] $NAME (on|taken off) " "$L" | tm |
  sed -E "s/\t\[Register\] $NAME (on|taken off) ([^,]*).*\(([A-Z_]+)\)\$/\t\1 \2 (\3)/; s/\t\[Register\] $NAME taken off (.*) for now:.*/\ttaken off \1 for now (barred)/; s/\t\[Register\] $NAME on ([^,]*).*/\ton \1/; s/\t\[Register\] $NAME taken off ([^,]*),.*/\ttaken off \1/" |
  group 16

section "CANTCO: $(grep -c "\] $NAME CANTCO on " "$L"), by task and reason"
grep "\[Register\] $NAME CANTCO on " "$L" | tm | sed -E "s/\t\[Register\] $NAME CANTCO on /\t/" | group 10

section "Failed steps: $(grep -c ": $NAME: step '.*' failed" "$L"), by step and reason"
grep ": $NAME: step '.*' failed" "$L" | tm | sed -E "s/\t$NAME: step /\t/" | blur | group 10

section "[Alert]: $(grep -c "\[Alert\] $NAME " "$L"), by kind"
grep "\[Alert\] $NAME " "$L" | tm | sed -E "s/\t\[Alert\] $NAME ([a-z]+( [a-z]+)?).*/\t\1/" | group 10

section "[Retry], [Loop], [Request], [Police]"
grep -E "\[(Retry|Loop|Request|Police)\]" "$L" | tm | sed -E 's/\t(\[[A-Za-z]+\]).*/\t\1/' | group 8
grep -E "\[(Retry|Loop|Request|Police)\]" "$L" | tm | blur | cut -f2 | awk '!seen[$0]++' | head -6 | cut -c1-220

section "Deaths: $(grep -cE "\b$NAME is dead - respawning" "$L"); disconnects: $(grep -cE "\b$NAME disconnected unexpectedly" "$L")"
grep -E "\[Casualty\].*\b$NAME\b|\b$NAME disconnected unexpectedly" "$L" | tm | cut -f2 | tail -5 | cut -c1-220
