---
status: proposed
priority: P3
---

# Soldiers choose their own Client Information

## Why
`ClientInformationPacket.defaults()` is hardcoded in two control handlers: `JoinClientHandler`
(the observed join) and `PlayClientHandler` (the one `MinecraftClient.join` runs). That's application
content (view distance, skin parts, main hand) living in control-plane code
(docs/architecture.md § Protocol pipeline architecture).

## Done when
- The values come from the caller of `MinecraftClient.join` (and of `observeJoin`), and both
  control handlers only send them.
