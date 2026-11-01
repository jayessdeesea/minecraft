---
status: ready
priority: P2
---

# Bump the version pin to 26.3

## Why
Mojang's version manifest lists 26.3 as the current release, out since 2026-09-15. We're pinned
to 26.2 (protocol 776). A native client on the current version can't join our dev server to
watch.

## Done when
- Everything that carries the pin is updated together:
  - the protocol number;
  - the Dockerfile's server `ARG`s and SHA-1;
  - every Play packet ID in protocol-spec.md § 3.5, re-read from `GameProtocols` with `javap`;
  - the generated block and mob lists, regenerated and renamed (DEVELOPMENT.md § 7).
- Unit tests pass, and the live checks (DEVELOPMENT.md § 5) and the full squad run pass against
  the new server.

## Notes
- Packet IDs shift whenever Mojang inserts a packet, so re-derive every ID, not just new ones.
- Block state IDs shift too, which breaks hard-coded state IDs in test fixtures. Fixture state IDs
  are listed in the tests that use them.
