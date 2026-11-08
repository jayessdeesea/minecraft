# Soldier: what it's told, and what it says back

## The order: `command.Order`
A soldier takes exactly one order at a time, and keeps it until the next replaces it:
- **`Idle`**: stand where it is.
- **`MoveTo(x, y, z, horizontalTolerance, verticalTolerance, risk, urgent)`**: get to any
  standing spot within the tolerances of the point and stay there.
  - `horizontalTolerance` and `verticalTolerance` are its freedom: under half a block means a
    spot, not just a cell, and it steps onto the spot.
  - `risk` (`LOW`, `MEDIUM`, `HIGH`) is how much health the order may cost in falls
    ([military-doctrine.md § 7](../../military-doctrine.md#7-risk-acceptance)).
  - `urgent` lets it sprint whatever it carries.
- **`Place(cells)`**: put a block in each cell, in order, from the blocks it carries. It picks where
  to stand for each, and skips a cell it can't reach or that won't take a block. The cells come from
  the engineer's layout, so the soldier knows nothing of what it's building: the first is the
  recovery drill's stair down from a ledge (`bot.engineer.Stairway`,
  [military-doctrine.md § 7](../../military-doctrine.md#7-risk-acceptance)).

Its squad leader sends an order per soldier each squad cycle: the formation slot by default, or
what a drill puts in its place (docs/echelons/squad-leader/drills.md). The routine that has its
hands can also send it somewhere - to a tree, a wall cell, a step of the staircase - with an order
of the same kind ([routines.md](routines.md)).

## The work: the squad's task
Apart from `Place`, the work it does with its hands isn't in its order. It comes from its squad's `Objective.Task`,
read by the runner (`app.SoldierRunner`), which hands each routine the site and the cells it
works on ([routines.md](routines.md)).

## What it reports: `command.Report`
| Report | Meaning |
|---|---|
| `Idle` | No order to carry out. |
| `Moving` | On its way, or cutting or building a step on the way. |
| `InPosition` | Arrived; holding. |
| `InContact(hostiles)` | Fighting. |
| `Cantco(reason)` | Can't comply, and why: no route (with the price of a riskier route if one exists), or the routine's failure ("can't cut the staircase further: water or lava beside step 2"). After 3 failed plans. |
| `LinkingUp` | Respawned, and closing on its squad. Set by the squad's drill, not by the soldier. |
| `Building(placed, cells)` | Filling the cells of its `Place`: so many in. A `Place` it can't finish is a `Cantco`. |
| `Rearming`, `Gathering(resource)`, `Fortifying`, `Farming`, `Hunting`, `Planting`, `Securing`, `Mining(step, steps)`, `CrossLeveling(to, item, count)` | The routine that has its hands. |

## Its condition: `command.Condition`
Health, food, deaths, and its `Kit`: the best weapon, arrows, armour points and fall protection,
logs, cobblestone, dirt, rations, shield, pickaxe, bow, spare weapons.
