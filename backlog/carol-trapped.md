---
status: proposed
priority: P2
---

# Carol is repeatedly trapped near (84, 65, 30)

## Why
In every recent live run, Carol ends up stuck near (84, 65, 30) and reports CANTCO. She may be
genuinely enclosed, or it may be a terrain-class or pathfinding error that live runs keep
re-triggering.

## Done when
- The cause is known. If it's a bug, it's fixed with a regression test (a synthetic world that
  reproduces the spot). If the spot really is enclosed, the respawn and link-up behaviour
  (respawn-linkup.md) is what eventually gets her out.

## Notes
- Inspect the blocks there through the server console, or dump the `ChunkCache` classes around
  that cell.
