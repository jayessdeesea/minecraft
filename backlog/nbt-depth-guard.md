---
status: ready
priority: P2
---

# NBT nesting-depth guard

## Why
`Nbt` (protocol) recurses through lists and compounds with no depth limit, so a hostile or broken
server can blow the stack. Every other resource-exhaustion case terminates the connection loudly
(AGENTS.md § No fallbacks; protocol-spec.md § 8). This is the recursive case of the same guard.

## Done when
- Decoding beyond a fixed maximum depth throws and closes the connection. A unit test builds a
  too-deep payload. Use vanilla's own limit, confirmed from the server jar
  (`NbtAccounter`/`NbtIo`).
- protocol-pipeline.md and protocol-spec.md § 8 list the guard alongside the others.
