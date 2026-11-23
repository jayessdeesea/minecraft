---
status: ready
priority: P2
---

# A wood shared again without thrashing, and a woodcutter that looks its new strip over first

## Why
A strip cut out, the wood left is shared again among the squad's woodcutters (`command.WoodDuties.worked`,
`app.DutyBook.cutOut`). Two faults make that not work, and one hides the other:
- **A woodcutter's old search speaks for its new strip.** `bot.supply.Gatherer` stays `exhausted` until its retry runs
  out, whatever ground it's then given; the runner stamps "found nothing" every tick it's exhausted, so the strip just
  given is marked cut out the tick after. The strips go round the whole wood in under a second - 28 "is cut out" lines
  in `WoodDutiesSimulationTest` - and end as they began, with soldiers holding strips for a tick each on the way, a
  tree begun in one and left "out of reach" in the next.
- **Sharing again moves every strip.** With the first fault mended (the gatherer forgetting `exhausted` and its retry
  when `within` changes), the remaining ground is split evenly by x at each cut-out: every woodcutter's bounds move,
  a tree half felled falls outside its feller's new strip and is left, narrow strips with no tree in them are cut out
  in turn, and the wood is said spent with logs standing.

`WoodDutiesSimulationTest.woodcuttersFellOnlyTheirOwnStripsAndThePlanterNone` passes on the first fault: it judges each
log against the strip first given, which holds only because the strips come back round. A change of timing alone - the
depositor's waits jittered - failed it. So nothing jitters `Depositor.trips` yet (`Setbacks` takes a salt).

## Done when
- A woodcutter given other ground looks it over before anything is said of it: a strip is cut out once, when its trees
  are felled.
- A strip cut out moves only its own woodcutter: the others' strips, and the trees they've begun, are left as they are -
  it's given a share of the strip with the most standing, split along a line between trees.
- The wood is said spent only when no log stands in it.
- `WoodDutiesSimulationTest` judges each tree against the strip its feller held when it began it, asserts each strip cut
  out once, and that the wood is felled; `aWoodcutterWithNothingToFell...` shows it given ground that has some.
- `Depositor.trips` salted by its soldier's name, and that simulation still passing.
