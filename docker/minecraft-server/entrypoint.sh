#!/bin/sh
# First run: accept the EULA and seed server.properties with the offline-mode dev setup AGENTS.md
# commits to, and no spawn protection - the soldiers aren't operators, and they dig and build. Later runs leave both alone, so hand edits inside the volume survive restarts.
set -e
[ -f eula.txt ] || echo "eula=true" > eula.txt
if [ ! -f server.properties ]; then
    cat > server.properties <<PROPS
online-mode=false
spawn-protection=0
PROPS
fi

# The server console reads from a FIFO, so `docker compose exec minecraft console <command>` can
# drive it non-interactively (e.g. `time set night` to force a live combat test). Holding fd 3
# open read-write keeps the FIFO from hitting EOF each time a writer closes.
rm -f /tmp/console && mkfifo /tmp/console
exec 3<>/tmp/console
exec java ${JAVA_OPTS:--Xmx2G} -jar /opt/minecraft/server.jar nogui "$@" < /tmp/console
