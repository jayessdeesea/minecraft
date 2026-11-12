---
status: proposed
priority: P2
doctrine: FM 4-0 (supply discipline; Class IV and VII recovery); ATP 4-31 (recovery); FM 3-21.8 (load management)
---

# Salvage and the pack: what's worth picking up, keeping, and throwing away

## Why
A soldier keeps what it digs, but nothing else: a torch popped off a block it cut, a sapling
falling out of reach, planks, fences and huts on ground being rebuilt are left behind
(docs/military-doctrine.md § 15). Flattening the base (backlog/cells.md) will pass under many of
them, and wood is already the bottleneck. And nothing decides what a full pack throws away.

## Direction (operator, 2026-11-12)
- **Pick up what's near and might be useful, by default.** Taking down what's on ground being
  reworked - torches, planks, crafting tables, fences, a hut - and keeping it is part of the
  work, as recovery is.
- **But priority is dynamic: set by the need of the hour, the scarcity of each resource.** A drop
  the base already has plenty of isn't worth waiting for: no standing about for a sapling or a
  stick when the stockpile and the grove are full. A scarce one is.
- **The same value decides the pack.** A full pack tosses what's worth least now: rotten flesh
  before a torch - unless it's wanted for something, such as trading with a villager - and a
  torch before diamonds.

## Design sketch
- **One value per item class, computed, not listed.** Its worth is how short the force is of it:
  what's wanted (the basic load, the standards' needs - torches for dark cells, logs for fences,
  stone for paving) against what's carried and stockpiled. Pure, in `command`, over the squad's
  kit and the stockpile as last seen, so the dawn AAR can show it.
- **Pickup:** a drop within reach is taken if it's worth more than the walk; waiting for one that
  may not come (a sapling from leaves) only if it's scarce.
- **Salvage:** the clearing and ground work's targets include placed blocks on ground being
  reworked; chests are emptied into the stockpile before they're taken down.
- **The pack:** when full, the least valuable stack is dropped (or deposited, at the stockpile)
  to make room for a more valuable one.

## Built so far
- Seeds beyond two stacks are thrown away (`bot.supply.Pack`) - live, packs full of seeds left a
  harvest's wheat on the ground. A fixed rule for one item; the value by scarcity is still to come.

## Done when
- A simulation: soldiers clearing ground under a torch and a crafting table come away with both.
- A simulation: a soldier with a full pack facing a scarce drop throws away the least valuable
  stack, rotten flesh before torches, and takes it; with the item plentiful, it doesn't wait.
- Live: the camp's flattening loses no torches or planks it passes under.
