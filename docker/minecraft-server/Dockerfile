# The pinned dev server (AGENTS.md "Target environment"): Mojang's own 26.3 server jar, verified
# against the version manifest's SHA-1 - the same steps DEVELOPMENT.md used to do by hand.
FROM eclipse-temurin:25.0.4_7-jre-noble

ARG SERVER_JAR_URL=https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar
ARG SERVER_JAR_SHA1=33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt/minecraft \
 && curl -fsSL -o /opt/minecraft/server.jar "$SERVER_JAR_URL" \
 && echo "$SERVER_JAR_SHA1  /opt/minecraft/server.jar" | sha1sum -c -

COPY entrypoint.sh /opt/minecraft/entrypoint.sh
COPY console.sh /usr/local/bin/console
RUN chmod +x /opt/minecraft/entrypoint.sh /usr/local/bin/console

# World, logs, and generated config live here - mount a volume to keep them across restarts.
WORKDIR /data
EXPOSE 25565
ENTRYPOINT ["/opt/minecraft/entrypoint.sh"]
