#!/bin/sh
# Starts the garrison as run $1 from its own copy of the app jar ($S/garrison-app.jar, put there by deploy.sh), so a
# build can rewrite app/target/app.jar while it runs; logs to $S/garrison-$1.log. Its arguments - the operator's name,
# the camp, the mission, the flags - are the operator's, kept outside the repo in $S/garrison.args, one argument a line.
S="${GARRISON_RUNS:-C:/mc-state/garrison}"
RUN="$1"
[ -n "$RUN" ] && [ -f "$S/garrison.args" ] || { echo "usage: start-garrison.sh <run-name>, with $S/garrison.args" >&2; exit 2; }
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
set --
while IFS= read -r arg || [ -n "$arg" ]; do
  [ -n "$arg" ] && set -- "$@" "$arg"
done < "$S/garrison.args"
exec java -jar "$S/garrison-app.jar" "$@" > "$S/garrison-$RUN.log" 2>&1
