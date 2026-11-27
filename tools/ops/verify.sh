#!/bin/sh
# The whole suite as the commit gate runs it - from clean classes, every module, offline - printing only failures and
# the build result. Four test threads (junit-platform.properties), so the dev server and the garrison keep their CPU.
# Maven writes its whole output to $VERIFY_LOG and runs to its end - no reader of its output can exit early and leave
# it running on, its classes deleted under it by the next verify's clean. What is printed is read from the log after:
# the first 20 failure lines and the BUILD line. The exit is Maven's: the landing queue's verifier reads the log and the
# exit, never this printing. With no VERIFY_LOG the log is a temp file, gone when this exits.
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
if [ -n "$VERIFY_LOG" ]; then
  log="$VERIFY_LOG"
else
  log=$(mktemp) || exit 1
  trap 'rm -f "$log"' EXIT
fi
rm -rf */target/classes */target/test-classes
mvn -o verify -fae > "$log" 2>&1; status=$?
grep -E "Tests run:.*(Fail|Err)[a-z]*: [1-9]|BUILD|ERROR\]" "$log" | awk 'NR <= 20 || /BUILD/'
exit "$status"
