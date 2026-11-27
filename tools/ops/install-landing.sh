#!/bin/sh
# Installs the landing queue's tool: the jar built from origin/main in a clean temporary worktree, offline, copied to
# its place under the integrator's lock with the commit it was built from, the last landing/ change there and where
# git, sh and mvn are recorded in the config; the config written by landing install alone, its keys kept; land.sh
# copied beside it, filled with the jar's path; the landing worktree made if absent; the scheduled task registered again
# from tools/ops/mc-landing.xml - javaw on the jar at normal priority, enabled only if it was there and enabled. It then
# asks the five questions the scheduler alone can answer, with a check task made from the same template - job-check for
# integrate, its start a day ahead - that it runs once and deletes. Its 0 means the scheduler can run the tool: every
# question answered yes and landing audit clean; anything else is 1, all installed regardless.
# Every path is the environment's to set: LANDING_STATE, LANDING_WORKTREE, LANDING_TASK, LANDING_REF, LANDING_JAVA,
# LANDING_MVN, LANDING_BUILD (empty or absent: the build worktree only), LANDING_SCHTASKS. With --dry-run it says what it
# would do and makes nothing.
# Usage: install-landing.sh [--dry-run]
case "${1:-}" in
  "") DRY=no ;;
  --dry-run) DRY=yes ;;
  *) echo "usage: install-landing.sh [--dry-run]" >&2; exit 2 ;;
esac
[ $# -le 1 ] || { echo "usage: install-landing.sh [--dry-run]" >&2; exit 2; }
# a config the caller named is never this install's: the installed jar reads the one beside it, and the build's jar is
# given that one by name
unset LANDING_CONFIG
win_path() {
  cygpath -w "$1" 2>/dev/null || printf '%s' "$1"
}
# the programs the task needs, found before anything is made: one missing is exit 1, named
program() {
  command -v "$1" 2>/dev/null || { echo "install-landing.sh: no $1 on this PATH: nothing installed" >&2; exit 1; }
}
GIT=$(program git) || exit 1
SH=$(program sh) || exit 1
MVN=$(program "${LANDING_MVN:-mvn}") || exit 1
JAVA=$(program "${LANDING_JAVA:-java}") || exit 1
JAVAW=""
for w in "$(dirname "$JAVA")/javaw.exe" "$(dirname "$JAVA")/javaw"; do
  [ -f "$w" ] && { JAVAW="$w"; break; }
done
[ -n "$JAVAW" ] || { echo "install-landing.sh: no javaw beside $JAVA: nothing installed" >&2; exit 1; }
echo "programs: git $GIT, sh $SH, mvn $MVN, java $JAVA, javaw $JAVAW"

cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
# the main checkout: the common git dir's parent, whichever worktree this runs from
COMMON=$(cd "$(git rev-parse --git-common-dir)" && pwd) || exit 1
REPO=$(cd "$COMMON/.." && (pwd -W 2>/dev/null || pwd))
STATE="${LANDING_STATE:-C:/mc-state/landing}"
WORKTREE="${LANDING_WORKTREE:-C:/mc-worktrees/landing}"
TASK="${LANDING_TASK:-mc-landing}"
CHECK="$TASK-check"
REF="${LANDING_REF:-origin/main}"
SCHTASKS="${LANDING_SCHTASKS:-schtasks}"
CONFIG="$STATE/config"
INSTALLED="$STATE/landing.jar"
# Windows' own whoami: MSYS has one of its own, which knows no domain and no /groups
WHOAMI="${SystemRoot:-$SYSTEMROOT}/System32/whoami.exe"

# Windows' own commands take /switches, which MSYS would read as paths
win() {
  MSYS_NO_PATHCONV=1 "$@"
}
# a step: with --dry-run only said; else run, and a step that fails ends the install naming it
step() {
  if [ "$DRY" = yes ]; then echo "would: $*"; return 0; fi
  "$@" || { echo "install-landing.sh: failed at: $*" >&2; exit 1; }
}
# a value as sed's replacement takes it literally: a Windows path's backslashes kept
literal() {
  printf '%s' "$1" | sed 's/[\\&|]/\\&/g'
}

if [ "$DRY" = yes ]; then
  BUILD="${LANDING_BUILD:-<a new temp dir, made at install>}"
  XML_DIR="<a temp dir of its own>"
else
  if [ -n "${LANDING_BUILD:-}" ]; then
    # given, it must be new or empty: a jar left there from an earlier build is never installed
    if [ -e "$LANDING_BUILD" ] && [ -n "$(ls -A "$LANDING_BUILD" 2>/dev/null)" ]; then
      echo "install-landing.sh: LANDING_BUILD $LANDING_BUILD is not empty: refused" >&2; exit 1
    fi
    BUILD="$LANDING_BUILD"; mkdir -p "$BUILD" || exit 1
  else
    BUILD=$(mktemp -d) || exit 1
  fi
  XML_DIR=$(mktemp -d) || exit 1
fi
# on any exit, an interrupt included: the build worktree, the filled XML and the check task gone
cleanup() {
  [ "$DRY" = yes ] && return
  [ -e "$BUILD/src" ] && git worktree remove --force "$BUILD/src" 2>/dev/null
  if [ "${CHECK_MADE:-no}" = yes ]; then win "$SCHTASKS" /delete /tn "$CHECK" /f > /dev/null 2>&1; fi
  rm -f "$STATE"/job-check.*
  rm -rf "$XML_DIR"
  [ -z "${LANDING_BUILD:-}" ] && rm -rf "$BUILD"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

echo "== installing landing from $REF into $STATE$( [ "$DRY" = yes ] && echo ' (dry run: nothing made)')"
[ "$DRY" = yes ] || git fetch -q origin || { echo "install-landing.sh: the fetch failed: nothing installed" >&2; exit 1; }
COMMIT=$(git rev-parse --verify --quiet "$REF^{commit}") || { echo "install-landing.sh: no commit $REF" >&2; exit 1; }
CHANGE=$(git log -1 --format=%H "$COMMIT" -- landing)
[ -n "$CHANGE" ] || { echo "install-landing.sh: $REF ($COMMIT) has no landing/: nothing to install" >&2; exit 1; }
echo "built from $COMMIT; its last landing/ change $CHANGE; the main checkout $REPO"

echo "== the jar, built in a clean worktree, offline, in $BUILD"
step git worktree add -q --detach "$BUILD/src" "$COMMIT"
step sh -c "cd '$BUILD/src' && '$MVN' -o -q -pl landing package -DskipTests"
JAR="$BUILD/src/landing/target/landing-<version>-app.jar"
if [ "$DRY" = no ]; then
  JAR=$(ls "$BUILD"/src/landing/target/landing-*-app.jar 2>/dev/null | head -1)
  [ -f "$JAR" ] || { echo "install-landing.sh: the build made no jar" >&2; exit 1; }
fi

echo "== the config $CONFIG: written by landing install, what is there kept"
BUDGET=$( [ -f "$CONFIG" ] && sed -n 's/^run\.budget\.minutes=//p' "$CONFIG" | tail -1)
BUDGET="${BUDGET:-180}"

echo "== the jar into its place, under the integrator's lock, with where git, sh and mvn are"
step env LANDING_CONFIG="$CONFIG" "$JAVA" -jar "$JAR" install "$JAR" "$INSTALLED" "$COMMIT" "$CHANGE" \
  "$(win_path "$(dirname "$GIT")")" "$(win_path "$(dirname "$SH")")" "$(win_path "$(dirname "$MVN")")" \
  "repo=$REPO" "worktree=$WORKTREE" "poke=schtasks /run /tn $TASK" \
  "hook=$(cd "$COMMON" && (pwd -W 2>/dev/null || pwd))/hooks/pre-push"
# land.sh filled with this install's jar, so a land from any branch runs this tool
step sh -c "sed 's|C:/mc-state/landing/landing.jar|$(literal "$INSTALLED")|' '$BUILD/src/tools/ops/land.sh' > '$STATE/land.sh'"

echo "== the landing worktree"
if [ -e "$WORKTREE/.git" ]; then
  echo "$WORKTREE is there: kept"
else
  step git worktree add -q --detach "$WORKTREE" "$COMMIT"
fi

echo "== the task $TASK, registered again from the template"
xml_now=$(win "$SCHTASKS" /query /tn "$TASK" /xml 2>/dev/null); exists=$?
if [ "$exists" -eq 0 ] && ! printf '%s' "$xml_now" | tr -d '\r' | sed -n '/<Settings>/,/<\/Settings>/p' \
    | grep -q '<Enabled>false</Enabled>'; then
  enabled=true; kept=enabled
else
  enabled=false; kept=disabled
fi
xml="$XML_DIR/mc-landing.xml"
user=$("$WHOAMI" 2>/dev/null | tr -d '\r')
action="$(win_path "$JAVAW") -jar \"$(win_path "$INSTALLED")\" integrate"
# the template filled into $1: the action's arguments $2, enabled $3, the trigger's start $4; UTF-16 little-endian with
# its byte-order mark, as Task Scheduler writes its own
fill() {
  sed -e "s|__USER__|$(literal "$user")|g" -e "s|__JAVAW__|$(literal "$(win_path "$JAVAW")")|g" \
      -e "s|__ARGUMENTS__|$(literal "$2")|g" -e "s|__REPO__|$(literal "$(win_path "$REPO")")|g" \
      -e "s|__ENABLED__|$3|g" -e "s|__LIMIT__|PT$((BUDGET + 30))M|g" -e "s|__START__|$4|g" \
      -e 's|encoding="UTF-8"|encoding="UTF-16"|' tools/ops/mc-landing.xml > "$1.utf8" || return 1
  { printf '\377\376'; iconv -f UTF-8 -t UTF-16LE "$1.utf8"; } > "$1"
}
if [ "$DRY" = yes ]; then
  echo "would: fill tools/ops/mc-landing.xml (user $user, $action, in $REPO, $kept,"
  echo "       time limit PT$((BUDGET + 30))M, priority 4) into $xml, UTF-16"
else
  fill "$xml" "-jar \"$(win_path "$INSTALLED")\" integrate" "$enabled" "$(date +%Y-%m-%dT%H:%M:%S)" || exit 1
fi
[ "$DRY" = yes ] && echo "would: register $TASK again, $kept"
step win "$SCHTASKS" /create /tn "$TASK" /xml "$( [ "$DRY" = yes ] && printf '%s' "$xml" || win_path "$xml")" /f
[ "$DRY" = yes ] || echo "registered $TASK again, $kept"

echo "== the five questions, answered on this machine (exit 1 if any is not yes)"
echo "1. schtasks /create /xml takes the file: $( [ "$DRY" = yes ] && echo "asked by the step above" || echo yes)"
said="$STATE/job-check.txt"
check_xml="$XML_DIR/mc-landing-check.xml"
check_args="-jar \"$(win_path "$INSTALLED")\" job-check \"$(win_path "$said")\""
if [ "$DRY" = yes ]; then
  echo "would: say whether this shell is elevated (whoami /groups, the High Mandatory Level)"
  echo "would: fill tools/ops/mc-landing.xml again for the check task $CHECK - the task's own settings, priority"
  echo "       included - with $(win_path "$JAVAW") $check_args for integrate, enabled, its start a day ahead,"
  echo "       into $check_xml"
  echo "would: register it (schtasks /create /tn $CHECK /xml $check_xml /f), run it once, and delete it and its files"
  echo "2. a task of the user's own runs with schtasks /run without elevation: asked by the check task's /run"
  echo "3. landing enters its job when Task Scheduler starts it: asked by the file's first line"
  echo "4. git, sh, mvn and java run under the scheduler: asked by the file's git, sh and java lines"
  echo "5. the task runs at normal priority: asked by the file's priority line"
  echo "would: run landing audit, then exit 1 if any is not yes or the audit is not clean"
  exit 0
fi
answered=yes
if win "$WHOAMI" /groups 2>/dev/null | grep -q S-1-16-12288; then elevated=yes; else elevated=no; fi
rm -f "$said"
# the task itself, from the same template, with job-check in place of integrate - it never takes the lock - and its start
# a day ahead, so only the /run below starts it before it is deleted
if fill "$check_xml" "$check_args" true "$(date -d '+1 day' +%Y-%m-%dT%H:%M:%S)" \
    && win "$SCHTASKS" /create /tn "$CHECK" /xml "$(win_path "$check_xml")" /f > /dev/null 2>&1; then
  CHECK_MADE=yes
  ran=$(win "$SCHTASKS" /run /tn "$CHECK" 2>&1); code=$?
else
  code=1; ran="the check task $CHECK could not be made"
fi
if [ "$elevated" = yes ]; then
  echo "2. a task of the user's own runs with schtasks /run without elevation: unanswered - this shell is elevated; run the install from one that is not"
  answered=no
elif [ "$code" -eq 0 ]; then
  echo "2. a task of the user's own runs with schtasks /run without elevation: yes"
else
  echo "2. a task of the user's own runs with schtasks /run without elevation: NO - $(printf '%s' "$ran" | tr -d '\r')"
  answered=no
fi
if [ "$code" -eq 0 ]; then
  i=0
  while [ ! -f "$said" ] && [ $i -lt 180 ]; do sleep 1; i=$((i + 1)); done
fi
if [ -f "$said" ] && head -1 "$said" | grep -q '^entered its job'; then
  echo "3. landing enters its job when Task Scheduler starts it: yes ($(head -1 "$said" | tr -d '\r'))"
  missing=""
  for name in git sh java; do
    line=$(grep "^$name: " "$said" | tr -d '\r')
    case "$line" in
      "" | "$name: NO"*) missing="$missing ${line:-$name: no answer}" ;;
    esac
  done
  if [ -z "$missing" ]; then
    echo "4. git, sh, mvn and java run under the scheduler: yes"; sed '1d; /^priority: /d; s/^/   /' "$said" | tr -d '\r'
  else
    echo "4. git, sh, mvn and java run under the scheduler: NO -$missing"; answered=no
  fi
  priority=$(grep "^priority: " "$said" | tr -d '\r')
  if [ "$priority" = "priority: NORMAL" ]; then
    echo "5. the task runs at normal priority: yes"
  else
    echo "5. the task runs at normal priority: NO - ${priority:-no answer}"; answered=no
  fi
else
  echo "3. landing enters its job when Task Scheduler starts it: NO - $( [ -f "$said" ] && tr -d '\r' < "$said" || echo "no answer");"
  echo "   the task's log ends: $(tail -3 "$STATE/landing-task.log" 2>/dev/null | tr -d '\r')"
  echo "4. git, sh, mvn and java run under the scheduler: unanswered"
  echo "5. the task runs at normal priority: unanswered"
  answered=no
fi

echo "== the audit"
"$JAVA" -jar "$INSTALLED" audit; audited=$?
echo "== did a window show on the desktop during the check? If one did, the task would show one every five minutes: say so."
if [ "$audited" -ne 0 ]; then
  echo "== installed, the task $kept, but landing audit exited $audited: see above" >&2; exit 1
fi
if [ "$answered" != yes ]; then
  echo "== installed, the task $kept, but a question is not yes: see above" >&2; exit 1
fi
echo "== installed, the task $kept: every question yes, the audit clean"
exit 0
