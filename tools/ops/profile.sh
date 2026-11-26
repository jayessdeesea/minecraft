#!/bin/sh
# Where the running garrison spends its CPU, counted (docs/conventions.md, "Profile every sixteenth commit"): records it
# with Java Flight Recorder, which attaches to the live JVM at about 1% cost, then prints the execution samples' total,
# the top frames by self time, and each sample put to the project's own frame nearest the top - the JDK frame on top (a
# map lookup, a sort) is rarely where the fix goes; "(none)", always printed, counts the samples with no project frame
# in their top 25. A recording with almost no samples is a garrison doing nothing; one jfr can't read is an error.
# Usage: profile.sh [seconds, default 180]   record the live garrison, then read it (takes that long plus 10 s)
#        profile.sh --read <file.jfr>          read a recording already made
# The recording is GARRISON_RUNS/profiles/prof-<run>-<UTC time>.jfr, its run the last line of runs.log.
S="${GARRISON_RUNS:-C:/mc-state/garrison}"
OWN='io[.]github[.]jayessdeesea[.]minecraft[.]'
if [ "$1" = --read ]; then
  f="$2"; [ -f "$f" ] || { echo "usage: profile.sh --read <file.jfr>" >&2; exit 2; }
else
  secs="${1:-180}"
  pid=$(cat "$S/garrison.pid" 2>/dev/null) && [ -n "$pid" ] || { echo "no garrison.pid under $S" >&2; exit 1; }
  run=$(tail -1 "$S/runs.log" 2>/dev/null | cut -d' ' -f1); run=${run:-unknown}
  mkdir -p "$S/profiles"
  f="$S/profiles/prof-$run-$(date -u +%Y%m%dT%H%M%SZ).jfr"
  out=$(jcmd "$pid" JFR.start name="prof-$$" duration="${secs}s" settings=profile \
    filename="$(cygpath -w "$f" 2>/dev/null || echo "$f")" 2>&1)
  echo "$out"
  echo "$out" | grep -q "Started recording" || { echo "JFR didn't start a recording" >&2; exit 1; }
  sleep $((secs + 10))
  [ -s "$f" ] || { echo "no recording at $f" >&2; exit 1; }
fi
jfr summary "$f" > /dev/null || { echo "jfr can't read $f" >&2; exit 1; }
echo "recording $f"
frames() { jfr print --events jdk.ExecutionSample --stack-depth "$1" "$f" |
  awk '/stackTrace = \[/ { n = 1; next } n && /^[[:space:]]*\]/ { n = 0 }
       n { sub(/^[[:space:]]+/, ""); if ($0 == "...") next; sub(/[(].*/, ""); print n "\t" $0; n++ }'; }
self=$(mktemp); owners=$(mktemp); trap 'rm -f "$self" "$owners"' EXIT
frames 1 > "$self"
echo "== $(wc -l < "$self") execution samples"
echo "== self time, the top 15 frames"
cut -f2 "$self" | sort | uniq -c | sort -rn | head -15
echo "== each sample put to the project's frame nearest the top, the top 15, then (none)"
frames 25 | awk -F'\t' -v own="$OWN" '
  $1 == 1 { if (seen && !taken) print "(none)"; taken = 0; seen = 1 }
  !taken && $2 ~ own { print $2; taken = 1 }
  END { if (seen && !taken) print "(none)" }' | sort | uniq -c | sort -rn > "$owners"
grep -v ' (none)$' "$owners" | head -15
grep ' (none)$' "$owners" || echo "      0 (none)"
