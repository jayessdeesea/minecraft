#!/bin/sh
# The whole suite as the commit gate runs it - from clean classes, every module, offline - printing only failures and
# the build result. Four test threads (junit-platform.properties), so the dev server and the garrison keep their CPU.
# The first 20 lines are kept by a reader that reads to the end: one that exits early leaves Maven running on, its
# classes deleted under it by the next verify's clean, and the BUILD line never read.
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 1
rm -rf */target/classes */target/test-classes
mvn -o verify -fae 2>&1 | grep -E "Tests run:.*(Fail|Err)[a-z]*: [1-9]|BUILD|ERROR\]" | awk 'NR <= 20 || /BUILD/'
