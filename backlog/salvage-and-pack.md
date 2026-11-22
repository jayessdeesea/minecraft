---
status: proposed
priority: P2
doctrine: FM 4-0 (supply discipline; Class IV and VII recovery); ATP 4-31 (recovery); FM 3-21.8 (load management)
---

# Salvage and the pack: what's worth picking up, keeping, and throwing away

## Why
A soldier keeps what it digs and empties a chest before digging it (docs/military-doctrine.md § 17), but
nothing else placed on ground being reworked: torches, planks, tables, fences and huts are dug and left, and
wood is the bottleneck. What a full pack throws away is a fixed list shed at a fill line (`bot.supply.Pack`,
`Pack.FULL_AT`), not what's worth least now. The live cases are the throw-aways in
backlog/authority-and-missions.md, "Left from the flatten work".

## Decided (operator)
- 2026-11-12: pick up what's near and might be useful, by default; taking down what's on ground being
  reworked and keeping it is part of the work. Priority is dynamic, set by each resource's scarcity: no
  standing about for a sapling when the stockpile and the grove are full. The same value decides the pack:
  rotten flesh before a torch - unless it's wanted, such as for trading with a villager - and a torch before
  diamonds.
- 2026-11-15: a full pack is the norm. Shedding is on demand, not at a fill line: a routine about to break a
  block whose drop has no room - no free slot, no part-stack of it - first sheds what's worth least;
  otherwise the pack stays full.

## What's left
- **One value per item class, computed, not listed:** how short the force is of it - what's wanted (the basic
  load, the standards' needs) against what's carried and stockpiled. Pure, in `command`, over the squad's kit
  and the stockpile as last seen, so the dawn review can show it.
- **Shed on demand**, by that value, replacing `Pack.FULL_AT` and the fixed keep lists.
- **Pickup:** a drop within reach is taken if it's worth more than the walk; waiting for one that may not come
  (a sapling from leaves) only if it's scarce. Needs a dropped item's type, from its entity metadata, which
  the protocol doesn't decode yet.
- **Salvage beyond chests:** torches, planks, tables, fences, a hut on ground being reworked, taken down and
  kept; and what a chest holds of no depot kind (tools, armour), which spills when it's dug.

## Done when
- A simulation: soldiers clearing ground under a torch and a crafting table come away with both.
- A simulation: a soldier with a full pack facing a scarce drop throws away the least valuable stack, rotten
  flesh before torches, and takes it; with the item plentiful, it doesn't wait.
- Live: the camp's flattening loses no torches or planks it passes under.
