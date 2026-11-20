---
status: ready
priority: P3
depends-on: []
doctrine: docs/conventions.md (profile every sixteenth commit)
---

# Route planning is a third of the garrison's time

## Why
The profile at commit 768 (three minutes of the live garrison, 11,662 execution samples): 35% of them
in `ChunkCache.rawStateIdAt`, nearly all of it the route planner asking cells whether they're solid
and a hazard (`Pathfinder.open < clear < standable`). By the caller that planned:
- `DeterministicSoldier.navigate` - 2,304 samples: soldiers planning their routes, some again and again
  (backlog/long-routes.md: a leg planned 109 times in a night).
- `SoldierRunner.cutOff` - 753: a reachability flood each squad cycle for every soldier with a recent
  "no route" or an idle alarm, whether or not it has moved.
- `DeterministicSoldier.priceOfARiskierRoute` - 322.

The lookup itself is already memoised by chunk. Asking each cell once instead of up to three times (a
fused `isOpen`) was tried and measured (10,933 samples, the lookup still 39%): the cost is how often
routes are planned, not what a cell costs.

## Done when
- A soldier that hasn't moved, with the world round it unchanged, doesn't plan the same route again:
  a route replanned only when its goal, its cell or the blocks on it change (`cutOff`'s flood is kept, 865).
- The next profile shows the route planner's share halved, both sample counts in the commit (met at 880: 53% to 27%).

## Measured again at 784
4,384 samples in three minutes, against 768's 11,662 - the garrison's whole load down 62% - and the block
lookups' share from 35% to 20%, spread across their callers (`Clearer.tick` 235, `navigate` 200, the squad's
orders 124, `cutOff` 53). Nothing dominates now: the drop came with the replanning loops ended (the stand
given up when walled off, the stair walked in legs), not from a cache. `EntityTracker.nearbyHostiles` is
next at 8%, a scan of every tracked hostile per call - worth a look if it grows. Left of the item: `cutOff`'s
flood kept per soldier while its cell holds.

## Measured at 832 and 848
832: route search about 36% of the samples, most of it `DeterministicSoldier.navigate`'s wide search (60,000 cells,
made when a cheap plan gains no ground), `EntityTracker.nearbyHostiles` 7.5%. 848 (field494, three minutes, 7,045
samples): the route planner 39% (2,759) - by its first caller, the wide search in `navigate` 269, `Gatherer.next` 114,
`navigate`'s cheap search 93 - and `nearbyHostiles` 9% (647, its distance filter over every tracked entity). Built at
848: the wide search's answer kept (`bot.nav.PlanMemo`) while it's asked again from the same cell to the same goal over
the same ground - `WorldView.stamp`, the block writes into the chunks within four of the start - round the same cells
with the same blocks. Left: `nearbyHostiles` by chunk, not a scan of every entity (3% at 864).

## Measured at 864
field500, three minutes, 5,472 samples: the route planner 53% (2,888) - by its first caller `navigate` 1,217 (its
legs and wide search), `cutOff`'s flood 563, a FARM objective with no water sited afresh each squad cycle 451,
`Clearer.tick` 363; `EntityTracker.distanceSquared` 3%. Built at 865: `cutOff`'s flood kept ten seconds while the
soldier stands on its cell (`SiteGeometry.Flood`), and a FARM point none was found for left a minute. Left: the next
profile (880) against these counts; `navigate`'s share, by which leg and goal.

## Measured at 880
field510, three minutes, 2,971 samples against 864's 5,472 - the garrison's load down 46% - the route planner 27%
(804, from 2,888): `navigate` 499, `cutOff`'s kept flood 97, the camp's lighting plan 145. The tick's own frame is
20%, spread over its lines - the soldier's tick, the food ledger's sample, the snag check - none above 4%. Left:
`navigate`'s share, by which leg and goal; `nearbyHostiles` by chunk (`EntityTracker.distanceSquared` 4.6%).
