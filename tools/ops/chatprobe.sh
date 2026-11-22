#!/bin/sh
# Question-only probe of the operator's chat (POST 127.0.0.1:8125/chat): each question, how many register reads it
# made (counted from the garrison log's "[Chat] the platoon leader reads" lines) and its reply - which must name what's
# true, with "done" empty: a question gives no command. Questions only, so the probe never changes an order.
# Appends to $S/chatprobe.out. Usage: chatprobe.sh <garrison log>
L="$1"; S="${GARRISON_RUNS:-C:/mc-state/garrison}"; OUT="$S/chatprobe.out"
[ -f "$L" ] || { echo "usage: chatprobe.sh <garrison log>" >&2; exit 2; }
echo "== $(date -Iseconds) $(basename "$L")" >> "$OUT"
for q in "list the missions" "what is at chunk (1, 3)?" "what chunk is x=20 z=50 in?" "what tasks does Charlie have?"; do
  before=$(grep -c "\[Chat\] the platoon leader reads" "$L")
  r=$(curl -s -m 240 -X POST -H "Content-Type: application/json" --data "{\"message\":\"$q\",\"turns\":[]}" http://127.0.0.1:8125/chat)
  after=$(grep -c "\[Chat\] the platoon leader reads" "$L")
  echo "Q: $q | reads: $((after - before)) | $r" >> "$OUT"
done
tail -5 "$OUT"
