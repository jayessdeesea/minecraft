#!/bin/sh
# Deploys origin/main as a garrison run: every commit there passed its verify (commit.sh), so that - not the tree here,
# which may be mid-change or a commit ahead - is what goes out. It's checked out and built in a worktree of its own
# (DEPLOY_WORKTREE, default C:/mc-worktrees/deploy): the modules installed and the app packaged without tests, the fat
# jar checked. Then the running garrison is stopped by its PID (never all java), the jar copied to $S/garrison-app.jar
# and the new run started from the copy - so the troops stay in the field through the build. The garrison runs from
# this tree's root, where its state is (minecraft.state-dir).
# $S is GARRISON_RUNS, default C:/mc-state/garrison, outside any cloud-sync folder, which holds files open. The
# garrison writes its PID to $S/garrison.pid and its log to $S/logs, a file an hour (start-garrison.sh). Each run is a
# line of $S/runs.log - "<name> <started, UTC> <commit> <PID>" - which run-log.sh picks a run's lines out of the log by.
# With no name, the run is the next number after the last in runs.log (field688, so field689).
# Usage: deploy.sh [<run-name>]
set -e
S="${GARRISON_RUNS:-C:/mc-state/garrison}"
W="${DEPLOY_WORKTREE:-C:/mc-worktrees/deploy}"
mkdir -p "$S"
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
ROOT=$(pwd)
LAST=$(tail -1 "$S/runs.log" 2>/dev/null | cut -d' ' -f1)
RUN="${1:-$(echo "$LAST" | sed -E 's/[0-9]+$//')$(( $(echo "$LAST" | grep -oE '[0-9]+$') + 1 ))}"
if ! echo "$RUN" | grep -qE '^[A-Za-z]+[0-9]+$' || grep -q "^$RUN " "$S/runs.log" 2>/dev/null; then echo "no run name to deploy as ('$RUN'): name a new one - deploy.sh <run-name>" >&2; exit 2; fi
git fetch -q origin main
COMMIT=$(git rev-parse --short origin/main)
if [ -e "$W/.git" ]; then git -C "$W" checkout -q --detach origin/main; else git worktree prune; git worktree add -q --detach "$W" origin/main; fi
echo "deploying $COMMIT as $RUN: $(git log -1 --format=%s origin/main)"
cd "$W"
# The IDE's Java build writes into the same target/classes: a build that meets one of its writes fails
# to assemble a jar (field368, field374). Tried again, from clean, up to three times; then not deployed.
built=no
for try in 1 2 3; do
  rm -rf */target/classes */target/test-classes 2>/dev/null || echo "build try $try: target/classes not all removed (the IDE holds a file)" >&2
  out=$( { mvn -o -q -T 4 install -pl protocol,command,command-ai,bot -DskipTests && mvn -o -q package -pl app -DskipTests; } 2>&1 )
  if ! echo "$out" | grep -q "ERROR"; then built=yes; break; fi
  echo "build try $try failed: $(echo "$out" | grep ERROR | head -1 | cut -c1-160)" >&2
  sleep 5
done
if [ "$built" != yes ]; then echo "the build failed three times - not deploying" >&2; exit 1; fi
SIZE=$(stat -c %s app/target/app.jar)
if [ "$SIZE" -lt 90000000 ]; then
  echo "app.jar is $SIZE bytes - not the fat jar; not starting" >&2
  exit 1
fi
# The bot module's registry tables in the fat jar: a build racing the IDE's writes into target/classes
# once packaged a jar without them, and every soldier failed to connect (field368).
python - <<'PY' || { echo "app.jar lacks the bot's registry tables - not starting" >&2; exit 1; }
import io, sys, zipfile
z = zipfile.ZipFile('app/target/app.jar')
bot = [n for n in z.namelist() if '/bot-' in n and n.endswith('.jar')]
names = zipfile.ZipFile(io.BytesIO(z.read(bot[0]))).namelist() if bot else []
want = ['block-classes-777.txt', 'item-classes-777.txt', 'hostile-entity-types-777.txt', 'weapon-stats-777.txt']
missing = [w for w in want if not any(n.endswith(w) for n in names)]
if missing:
    print('missing', missing, file=sys.stderr)
    sys.exit(1)
PY
# The app's own config in the fat jar: field655 went out without application.yml and never started.
python - <<'PY' || { echo "app.jar lacks BOOT-INF/classes/application.yml - not starting" >&2; exit 1; }
import zipfile, sys
sys.exit(0 if 'BOOT-INF/classes/application.yml' in zipfile.ZipFile('app/target/app.jar').namelist() else 1)
PY
# The run before, by the PID it wrote - and only while that's still a java process: a run long dead may have left
# its number to something else.
PID=$(cat "$S/garrison.pid" 2>/dev/null || true)
if [ -n "$PID" ] && tasklist //FI "PID eq $PID" //FO CSV //NH 2>/dev/null | grep -qi '^"java'; then
  taskkill //PID "$PID" //F > /dev/null 2>&1 || true
  sleep 3
fi
rm -f "$S/garrison.pid"
cp app/target/app.jar "$S/garrison-app.jar"
cd "$ROOT"
STARTED=$(date -u +%Y-%m-%dT%H:%M:%SZ)
echo "app.jar $SIZE bytes; starting $RUN"
nohup sh tools/ops/start-garrison.sh > /dev/null 2>&1 &
# Its PID file is written once the app is up; a start that never gets there says why on its console.
for wait in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
  [ -s "$S/garrison.pid" ] && break
  sleep 2
done
if [ ! -s "$S/garrison.pid" ]; then echo "$RUN didn't start - see $S/garrison-console.log and the newest file under $S/logs" >&2; exit 1; fi
echo "$RUN $STARTED $COMMIT $(cat "$S/garrison.pid")" >> "$S/runs.log"
tail -1 "$S/runs.log"
