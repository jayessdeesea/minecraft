#!/bin/sh
# Starts the garrison from its own copy of the app jar ($S/garrison-app.jar, put there by deploy.sh), so a build can
# rewrite app/target/app.jar while it runs. Its log goes to $S/logs in a file an hour, UTC
# (app's log4j2-spring.xml: logs/yyyy/MM/dd/yyyy-MM-dd-HH_application.log), and its process id to $S/garrison.pid; what
# the JVM itself prints - a crash, a failure before the log is up - to $S/garrison-console.log, begun again each start.
# Its arguments - the operator's name, the camp, the mission, the flags - are the operator's, kept outside the repo in
# $S/garrison.args, one argument a line.
S="${GARRISON_RUNS:-C:/mc-state/garrison}"
[ -f "$S/garrison.args" ] || { echo "usage: start-garrison.sh, with $S/garrison.args" >&2; exit 2; }
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
set --
while IFS= read -r arg || [ -n "$arg" ]; do
  [ -n "$arg" ] && set -- "$@" "$arg"
done < "$S/garrison.args"
exec java -jar "$S/garrison-app.jar" "$@" "--logging.file.path=$S/logs" "--spring.pid.file=$S/garrison.pid" > "$S/garrison-console.log" 2>&1
