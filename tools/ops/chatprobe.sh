#!/bin/sh
# Question-only probe of the operator's chat (POST 127.0.0.1:8125/chat): each question, how many register reads it
# made (counted from the garrison log's "[Chat] the platoon leader reads" lines, in its last two hourly files - so an
# hour's turn mid-probe loses none) and its reply - which must name what's true, with "done" empty: a question gives no
# command. Questions only, so the probe never changes an order. Appends to $S/chatprobe.out.
# Usage: chatprobe.sh
S="${GARRISON_RUNS:-C:/mc-state/garrison}"; OUT="$S/chatprobe.out"
reads() { find "$S/logs" -name '*_application.log' | sort | tail -2 | xargs cat | grep -c "\[Chat\] the platoon leader reads"; }
[ -d "$S/logs" ] || { echo "no garrison log in $S/logs" >&2; exit 2; }
echo "== $(date -u +%Y-%m-%dT%H:%M:%SZ) $(tail -1 "$S/runs.log" 2>/dev/null | cut -d' ' -f1)" >> "$OUT"
for q in "list the missions" "what is at chunk (1, 3)?" "what chunk is x=20 z=50 in?" "what tasks does Charlie have?"; do
  before=$(reads)
  r=$(curl -s -m 240 -X POST -H "Content-Type: application/json" --data "{\"message\":\"$q\",\"turns\":[]}" http://127.0.0.1:8125/chat)
  after=$(reads)
  echo "Q: $q | reads: $((after - before)) | $r" >> "$OUT"
done
tail -5 "$OUT"
