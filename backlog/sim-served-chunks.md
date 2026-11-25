---
status: ready
priority: P3
depends-on: []
---

# The simulated server unloads the chunks a soldier leaves behind

## Why
`app.SimServer` sends a soldier the 7 by 7 chunks round where it joins and those that come into view as it walks
(`SimChunks`), and unloads none. A live server drops the ones behind: the simulation meets neither a chunk unloaded
under a soldier's route nor ground a squadmate knows and it no longer does.

## Done when
- `SimServer` unloads the chunks that leave a soldier's view, through the listener a connection uses
  (`onChunkUnload`), and sends them again when it comes back.
- A simulation walks a soldier out past its view and back over ground changed while it was away.

## Notes
`SimServer` is at the 1000-line cap: this goes in `SimChunks`, beside it (backlog/split-oversized-sources.md).
