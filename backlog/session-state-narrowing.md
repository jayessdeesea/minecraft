---
status: proposed
priority: P3
---

# Narrow `SessionStateManager.transitionTo`

## Why
`transitionTo` is wider than it needs to be. The footnote to control-plane tenet 2 in
protocol-pipeline.md proposes narrowing it to package-private, plus a
`beginHandshake(ctx, nextState)` entry point. It's small and not urgent.

## Done when
- Only `SessionStateManager` itself and the handshake entry point can change state.
