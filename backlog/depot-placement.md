---
status: ready
priority: P1
---

# Where the supply points go: the platoon leader places them from the walks the force makes

## Why
The operator (2026-11-24): the platoon leader should profile the soldiers' paths and work out where supply dumps do
the most good - purely algorithmic. Live the same day, in daylight, eleven of sixteen soldiers queued at one chest's
one stand by the stockpile at (38, 67, 82), thirty blocks from the farms they were sent to work; a paver had walked
616 blocks an item the day before. The stores today are wherever a camp once stood; a supply point is sited by the
base plan's fixed pattern (`command.BasePlan.template`), which knows nothing of where the work is.

## The problem, stated
A depot serves the work round it. What it costs the force is the walking and the waiting: for every trip a soldier
makes to put a load in or draw one, the blocks walked there and back and the seconds stood at the chest. Choosing where
depots stand to make that least is facility location - given where the loads come from and go to, and the sites a
depot may stand on, open the sites that cut the most walking for what they cost to build.

## Done when
- **The walks are kept.** Each stockpile trip - whose, from where its work was, to which chest, the blocks walked by
  route (not as the crow flies), the seconds waited within reach of the chest for a stand or a turn, what and how
  much was moved - is a row in the registers, as the errand ledger already measures it (`app.ErrandLedger`), summed by
  the chunk the work was in and by day.
- **The demand is said.** For each chunk worked: loads a day, in and out, and what they cost now - blocks and seconds a
  load to the chest that served it. In the chat's answer and the platoon leader's picture, computed, never worked out
  by a model.
- **A pure rule chooses** (`command`, with its unit tests): from the demand, the depots standing and the candidate
  sites - chunks of the base plan not built on, off the camp, flat enough - the site whose opening saves the most
  walking a day; opened when that saving, over a set number of days, is more than its building costs in soldier-hours
  (its flatten, slab, paving and template, from what the last ones took). One at a time, never more missions open
  than squads (`BasePlan`'s own rule). The base plan's pattern becomes the candidates, not the order.
- **A depot is sized by its queue**: stands and chests enough that the seconds waited a load stay under a bound; a
  second row, or a second depot, when they don't.
- **A depot nobody walks to is closed**: emptied into the nearest, its chests taken up - and an old camp's stockpile
  on a hilltop is the first of these (backlog/authority-and-missions.md, the stockpiles in the sky).
- **The trips themselves are fewer**: the same record says which loads needn't have been carried - a pack filled with
  what its job doesn't use (backlog/authority-and-missions.md, a soldier's pack for its job). Placement is measured
  after that, not instead of it.
- Evals: the rule on made-up demand - two farms and a mine apart, one depot between them, chosen where the sum is
  least; a site that doesn't pay left unbuilt; a queue that opens a second row. And a squad simulation, PavingRateIT's
  pattern: the same work with the depot far and near, the blocks an item measured.
- Live: blocks and seconds a load, by routine, fall and stay down; no squad stands at a chest in daylight.

## Which chest a load goes to
The operator (2026-11-24): a different strategy for picking chests for putting stuff away. Today a soldier's stockpile
is one chest by standing - the operator's supply point, else its flatten crew's depot, else its own workshop's chest,
else the camp's (`SoldierRunner.stockpileFor`) - whatever is carried, however far it is, however full, and whoever else
is on the way there. So sixteen soldiers share one or two chests, and a trip that finds no room is made again.

Wanted, a choice made for each load, by its squad leader, as a pure function with its tests:
- **Every chest the stores know is a candidate** - but one being emptied, a mine's ore stash for anything but ore, a
  farm's chest for anything but its crop and seed.
- **Room first**: a chest with no room for what's carried - by kind, under the stores' caps - isn't one. What no chest
  wants (cobblestone and dirt over the cap, a fifth pickaxe) is dropped at a dump, not carried round.
- **Least cost among the rest**: the blocks from where it stands, on to the chest, and back to its work, plus the
  wait - those already on their way there, a turn each. Not the nearest alone: the one the round trip is shortest by.
- **By kind inside a supply point, as its template already lays out** (`bot.farm.StoresChunk.Row`: food; building -
  cobblestone, dirt, logs, planks; and the rest, to fuel and arrows; `Row.of(kind)` - nothing is without a row; operator,
  2026-11-24: we picked what went where). The choice is of a row's chest, so a load of two kinds is two stops in one
  depot, and a draw knows where to look. Only the first two rows are built today (`FIRST_ROWS`).
- **Not always the least** (operator, 2026-11-24: some jitter on which chest - it's not always the closest one):
  among the chests whose cost is within a margin of the least, one is taken by the soldier's own salt, as its waits
  are (`bot.supply.Setbacks`) - the same soldier the same way, so a test can say which, and sixteen not as one.
- **A stand is taken, not raced for**: the choice reserves the chest's turn, so the next soldier's cost counts it and
  goes elsewhere.
- **A trip that fails goes to the next in the ranking**, never the same chest again.
- Evals: sixteen with full packs and three depots - no chest with more than its stands' worth walking to it, the sum
  of the walks less than to the nearest alone; a full chest never chosen; a farmer's wheat to its farm's chest.

## Notes
- By route, because the ground isn't flat: the walk a soldier made is the distance, and it's already counted.
- Greedy is enough: sites are few (the base is eight chunks a side) and one is opened at a time.
- What it replaces is `BasePlan.next`'s fixed order for STORES; wheat, trees and mines keep theirs until their own
  demand is measured the same way (backlog/base-of-influence.md, shortfall-driven missions).
