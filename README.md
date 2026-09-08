# minecraft

A from-scratch Minecraft Java Edition client (no Minecraft protocol/NBT/world libraries) that
hosts a chain-of-command hierarchy of AI agents — a General commanding Squad Leaders commanding
Soldier player-bots. See [AGENTS.md](AGENTS.md) for the full project conventions, architecture
rationale, and Maven module layout.

New here? Start with [DEVELOPMENT.md](DEVELOPMENT.md) — local dev setup: download/run a
Minecraft server, connect a native client, current build status.

## Docs

- [docs/protocol-pipeline.md](docs/protocol-pipeline.md) — how `protocol`'s Netty pipeline works:
  framing, compression, and marshalling as swappable-delegate handlers, state ownership split from
  packet-sending, and how a join actually unfolds
- [docs/protocol-spec.md](docs/protocol-spec.md) — the Minecraft wire protocol (packet format,
  states, compression, encryption) as implemented by the `protocol` module
- [docs/world-data-spec.md](docs/world-data-spec.md) — the chunk/block/light data formats the
  client decodes from the server

## Building

```
mvn verify
```

Produces a runnable Spring Boot jar at `app/target/app.jar`.
