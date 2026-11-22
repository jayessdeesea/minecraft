#!/bin/sh
# Deploys the working tree as garrison run $1: cleans stale classes, installs the modules and
# packages the app without tests (run the suite first), checks the fat jar, then stops the running
# garrison by its PID (never all java), copies the jar to $S/garrison-app.jar and starts the new
# run from the copy - so the troops stay in the field through the build.
# $S is GARRISON_RUNS, default C:/mc-state/garrison - off OneDrive, which holds files open; each run logs to
# $S/garrison-<run>.log, and the previous run's PID is read from its log's "with PID" line.
# With no names, the previous run is the newest log there and the new run the next number after it (field686, so
# field687).
# Usage: deploy.sh [<run-name> [<previous-run-name>]]
set -e
S="${GARRISON_RUNS:-C:/mc-state/garrison}"
mkdir -p "$S"
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
LAST=$(ls -t "$S"/garrison-*.log 2>/dev/null | head -1 | sed -E 's#.*/garrison-(.*)\.log#\1#')
set -- "${1:-$(echo "$LAST" | sed -E 's/[0-9]+$//')$(( $(echo "$LAST" | grep -oE '[0-9]+$') + 1 ))}" "${2:-$LAST}"
if ! echo "$1" | grep -qE '^[A-Za-z]+[0-9]+$' || [ -f "$S/garrison-$1.log" ]; then echo "no run name to deploy as ('$1'): name it - deploy.sh <run-name> <previous-run-name>" >&2; exit 2; fi
# Only what's committed and pushed is deployed: commit.sh pushes a commit only once its verify passes, so what's on
# origin/main is verified, and a tree with uncommitted changes to tracked files (field556 went out so, its verify
# failed) or a commit still being verified is not.
if ! git diff --quiet HEAD -- . ':!Claude outputs'; then echo "the tree has uncommitted changes - verify and commit them first; not deploying" >&2; exit 1; fi
if ! git merge-base --is-ancestor HEAD origin/main; then echo "HEAD isn't on origin/main - not verified and pushed yet (commit.sh); not deploying" >&2; exit 1; fi
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
PREV="$S/garrison-$2.log"
if [ -f "$PREV" ]; then
  PID=$(grep -o "with PID [0-9]*" "$PREV" | head -1 | grep -o "[0-9]*")
  # Only while that PID is still a java process: a run long dead may have left its number to something else.
  if [ -n "$PID" ] && tasklist //FI "PID eq $PID" //FO CSV //NH 2>/dev/null | grep -qi '^"java'; then taskkill //PID "$PID" //F > /dev/null 2>&1 || true; fi
  sleep 3
fi
cp app/target/app.jar "$S/garrison-app.jar"
echo "app.jar $SIZE bytes; starting $1"
nohup sh tools/ops/start-garrison.sh "$1" > /dev/null 2>&1 &
sleep 8
grep -m1 "Started Application" "$S/garrison-$1.log" || echo "not started yet - see $S/garrison-$1.log"
