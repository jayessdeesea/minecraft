---
status: ready
priority: P1
depends-on: [lace-report]
doctrine: rally points and link-up procedures (ATP 3-21.8; older FM 3-21.9 ch. 7)
---

# A respawned soldier rejoins by rally point and link-up

## Why
After a death, a soldier respawns at world spawn and heads straight for its formation slot, even
if the squad is in contact and the route runs through the fight. Doctrine treats it as a
replacement soldier. It doesn't walk alone into an engagement. It moves to a rally point and links
up when the unit can receive it.

## Done when
- `SquadOrder` can carry a rally point. The squad leader chooses it, defaulting to the last
  position where the squad wasn't in contact.
- A soldier that has respawned runs a deterministic rejoin sequence: move to the rally point, hold
  while the squad reports `InContact`, then link up, which means approaching the nearest squad
  member and taking its formation slot.
- It reports this as its own `Report` (for example `LinkingUp`), so the squad leader can see the
  squad is reconstituting.
- A test with a squad held "in contact" shows the replacement holding at the rally point, then
  rejoining once contact clears.

## Notes
- Doctrine's link-up runs: recognition at a distance, recognition up close, move to the link-up
  point, set local security. In Minecraft, "recognition" is just "I can see a squad member in
  `EntityTracker`".
- Fold in the stale-report gap: right after a respawn the tick loop can report the pre-respawn
  position once, before the server's correction lands. The server logs "moved too quickly". It's
  harmless today, but the rejoin sequence should start from the corrected position.
