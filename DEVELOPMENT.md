# Development Setup

## What this project does

A from-scratch Minecraft Java Edition client — our own Netty-based protocol implementation, no
Minecraft protocol/NBT/world libraries — built as the substrate for a chain-of-command hierarchy
of AI agents: a **General** (infrequent, strategic LLM calls) issues objectives to **Squad
Leaders** (more frequent, tactical LLM calls), who command **Soldiers** — actual Minecraft
player-bots running a deterministic, non-LLM control loop every game tick. See
[AGENTS.md](AGENTS.md) for the full rationale and module layout.

## Current status

`protocol` can log all the way in: Handshake → Login (with compression) → Configuration (Client
Information, Known Packs, registry/tag sync, Finish Configuration) → Play. Confirmed against the
real local dev server, including the server's own log:
```
ScoutBot[/127.0.0.1:59369] logged in with entity id 19 at (4.5, 81.0, 42.5)
ScoutBot joined the game
```
See `MinecraftClient.observeJoin` and docs/protocol-spec.md § 3.4 for how the Configuration
packet IDs were derived empirically rather than from a written spec. What this *isn't* yet: a
persistent connection. `JoinClientHandler` disconnects a couple of seconds after reaching Play
without responding to anything (no Keep Alive, no movement) — it's a diagnostic tool that proves
the join sequence works, not a Soldier. `command`, `command-ai`, and `bot` are still
`package-info.java` placeholders; Play's actual packet catalog (needed for a bot that does
anything once joined) isn't implemented.

## Prerequisites

- Java 25 (Amazon Corretto or another distribution) — matches [AGENTS.md](AGENTS.md#stack)
- Maven
- An Anthropic API key, only once `command-ai` actually calls Claude (not needed for the
  connectivity smoke test in this doc)

## 1. Download and run a local dev server

We target a single pinned Minecraft version — see
[AGENTS.md § Target environment](AGENTS.md#target-environment) for the current pin (it's bumped
deliberately, so treat that section as the source of truth rather than anything below). Get the
server jar from Mojang's own version manifest rather than a third-party mirror, so it's guaranteed
to match:

1. Fetch `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json` and find the entry
   under `versions` whose `id` matches the pinned version.
2. Fetch that entry's own `url` — a per-version JSON document.
3. In that document, `downloads.server.url` is the server jar's download URL and
   `downloads.server.sha1` is its checksum. The server's own `javaVersion.majorVersion` field
   tells you the minimum JRE it needs.

```bash
SERVER_JAR_URL="<downloads.server.url from step 3>"
SERVER_JAR_SHA1="<downloads.server.sha1 from step 3>"

mkdir -p ~/mc-dev-server && cd ~/mc-dev-server   # or e.g. C:\mc-dev-server on Windows
curl -Lo server.jar "$SERVER_JAR_URL"

# verify the download
echo "$SERVER_JAR_SHA1  server.jar" | sha1sum -c -

# first run: generates eula.txt and server.properties, then exits
java -jar server.jar nogui
```

Keep the server directory **outside this repo, and outside any cloud-sync client** (OneDrive,
Dropbox, etc., if your working copy happens to be under one — see AGENTS.md's note on `target/`
for why). It's a large third-party binary with its own world save that gets rewritten
continuously while the server runs; a live server's region files under active cloud sync is a
worse version of the same file-locking problem, up to save corruption.

Edit the two generated files before the second run:
- `eula.txt` — change `eula=false` to `eula=true`
- `server.properties` — set `online-mode=false` (this is the offline-mode dev setup AGENTS.md
  commits to; skips Mojang/Xbox auth entirely)

```bash
java -jar server.jar nogui
```

Server listens on `localhost:25565` by default.

## 2. Connect with the real Minecraft Java client

Multiplayer → Add Server → `localhost:25565`. Your client must be on the same version as the
server — offline mode means any username works, no Microsoft login needed for this server.

This is today's actual smoke test: confirms the server runs and is reachable, independent of
anything in this repo.

## 3. Ping the server from this project's client

Today this only exercises Status state (no Login, no joining the world yet):

```bash
mvn -pl protocol test -Dtest=StatusPingLiveIT
```

This connects to `localhost:25565`, runs a real Handshake/Status/Ping exchange, and prints the
server's JSON status response plus round-trip time. It's named `*IT` (not `*Test`) specifically so
`mvn verify` never runs it by default — it needs a live server this project doesn't start for you,
so it must not be able to fail a plain build on a machine without one running (see the class's own
javadoc in `protocol/src/test/java/.../StatusPingLiveIT.java`).

## 4. Watch this project's client actually join

```bash
mvn -pl protocol test -Dtest=JoinObservationLiveIT
```

Logs in as `ScoutBot`, drives the full join sequence through Play, prints every Configuration/Play
packet ID/length/hex it observed, then disconnects after ~1 second of quiet. Check the server's
own console/log for `ScoutBot joined the game` to confirm it from the server's side too. Same
`*IT` naming reasoning as step 3 — needs a live server, so `mvn verify` skips it by default.

## 5. Run a bot that stays connected and does something (not yet possible)

Once `bot` wires up a persistent `Soldier` (Keep Alive replies, movement, actually reading Play
packets instead of just logging them) this becomes: build with `mvn verify`, run
`app/target/app.jar`, point it at `localhost:25565`, and watch the bot stick around in the world
alongside the native client from step 2. Fill this in when that code exists — don't let it go
stale as a TODO.
