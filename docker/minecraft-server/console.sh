#!/bin/sh
# Sends one command to the running server's console - see entrypoint.sh.
printf '%s\n' "$*" > /tmp/console
