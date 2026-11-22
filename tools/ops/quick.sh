#!/bin/sh
# The quick tier while working on a change: every module's tests but the slow ones - the simulations, the stuck evals
# and the combat matrices, tagged "slow", nearly all of the suite's time - through the reactor, offline. The gate is
# still the whole suite (commit.sh).
# Usage: quick.sh [mvn arguments, e.g. -Dtest=FarmMissionsTest -Dsurefire.failIfNoSpecifiedTests=false]
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
out=$(mvn -o -q test -DexcludedGroups=slow "$@" 2>&1); result=$?
echo "$out" | grep -E "Tests run:.*(Fail|Err)[a-z]*: [1-9]|ERROR\]" | head -30
if [ "$result" -eq 0 ]; then echo "quick tier: passed"; else echo "quick tier: FAILED"; fi
exit "$result"
