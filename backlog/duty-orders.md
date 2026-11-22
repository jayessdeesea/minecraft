---
status: proposed
priority: P1
doctrine: ATP 3-21.8 (the squad leader's tasks to its soldiers, and checking they're done); FM 6-0 (the order's execution and sustainment paragraphs)
---

# Complete duty orders, and the squad leader checking their yield

## Why
The operator (2026-11-21): the soldier is how - the walk, the steps. Where the work's makings are, where it's done,
how much and where its yield goes is the squad leader's, and the squad leader tracks each soldier's subtask and its
outcome. Live (field640-657), the farm squad's baker sat a run on 15 wheat beside chests of 544, the stores at no
bread: its duty order said only "baker, the dump at (38, 68, 94)", so which chest, how much to draw and when to bake
were rules in the soldier's own loop - two of them at odds - and the squad's "behind its share" alarm, raised every
few minutes for fifteen runs, had no standing rule answering it.

## The echelons (docs/echelons/tenets.md)
- **Platoon leader**: the squad's objective and why (Delta keeps the camp's farm: bread for the force), the force's
  outcome (bread against its least), and a squad's gap report it can't close itself.
- **Squad leader** (standing rules, `command`): each soldier's subtask whole - its source, where it's worked, how
  much, where the yield goes - computed from the stores book and the camp's fixtures; each duty's expected yield;
  the check that it comes, and the answer when it doesn't.
- **Soldier**: executes it - walks, opens, takes, crafts, puts away - and reports CANTCO with why a step failed.
  No thresholds of its own on what to fetch, from where or when.

## The plan
1. **The baker's order whole** - built: its source chest, table, batch and dump named, re-pointed when the source
   runs dry (docs/military-doctrine.md). Left of it: the bake itself at the order's table (the soldier's crafting
   still picks its workshop), and the draw and bake thresholds read from the order's batch, not the runner's.
2. **Each duty's yield**: what a share should make in ten minutes - a baker its batches while wheat's stored, a
   harvester the ripe cells of its plot, a woodcutter its logs. Measured from the errand ledger's counts
   (`ErrandLedger`), not from a product merely changing (`ShareWatch` today).
3. **The squad leader's answer to a miss**, a standing rule per cause, each said on the net: the source empty -
   the next source; the table or chest unreachable - the next, or a CANTCO up; the soldier taken by other work for
   the spell - reported as the cause, not re-ordered; no cause found - a gap report to the platoon leader naming the
   duty, the yield short and what was checked.
4. Then the harvester's and the woodcutter's orders the same way: the harvester's plot and its seed chest, the
   woodcutter's sector, its planter and the tree chunk's chest.

## The eval (operator, 2026-11-21: an eval to make sure this will work)
A squad simulation of a farm squad's bread chain end to end, and the misses injected, each held to what correct is:
- **The chain**: harvesters, a baker, a table, a dump, wheat in a chest the stores book knows from before a
  restart: bread in the dump at the baker's expected yield within the spell (`FarmDutiesSimulationTest`'s
  `aBakerShortOfABatchDrawsTheRestAndBakes` is its first cell).
- **The source runs dry** (built, `aBakersSourceRunDryIsRepointedToTheNextChest`): the chest emptied mid-run, wheat in
  another - the order re-pointed, both drawn from.
- **No wheat anywhere**: the baker idle, the squad leader's report up names it ("no wheat stored") - not "behind".
- **The table gone**: dug - the baker's CANTCO, the squad leader's answer (another table, or up the chain).
- **The baker pulled off**: a fight or a ration run - the yield short, the cause said, no re-order.
Each cell's yield and its time to detection logged, so a regression shows as a number, not a stall found live.

## Done when
- The eval's cells pass, the misses each detected within one spell (`ShareWatch.DUE_MILLIS`) and answered by
  their rule.
- Live: bread made each day the stores hold wheat; no "behind its share" alarm raised twice running for one cause.
- docs/echelons/squad-leader/ says what a duty order carries and how a miss is answered; the runner holds no
  baker thresholds of its own.
