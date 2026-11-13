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

The ground work comes as commands over a `command.Box` (min and max x, y and z) - concrete, so the
soldier is told cells, never "level the camp" ([military-doctrine.md § 15](../../military-doctrine.md#15-securing-the-camp)):
- **`Dig(box, except)`**: dig every block in the box that breaks by hand within 20 seconds -
  logs, leaves, soil, stone, sand, glass, planks - but the cells in `except`, and never a chest,
  a workstation or farmland: a tree from its foot up, everything else from its top down, never
  ground with water beside it. Each is dug in its own break time, from the block's hardness.
- **`Fill(box, except)`**: put a block in each open cell of each column in the box, from its top
  down to the first solid block or water.
- **`Gather(resource, box, amount)`**: go to the box and dig the resource there until it carries
  `amount`: a supply run off base.

The squad's drill (`command.Tasking`) hands these out; the runner gives each to the routine that
does it - `CLEARER`, `FILLER`, `GATHERER` ([routines.md](routines.md)) - alongside the soldier's
movement order, which the routine's stand then sets.

Its squad leader sends an order per soldier each squad cycle: the formation slot by default, or
what a drill puts in its place (docs/echelons/squad-leader/drills.md). The routine that has its
hands can also send it somewhere - to a tree, a wall cell, a step of the staircase - with an order
of the same kind ([routines.md](routines.md)).

## Its plan: a role, and what it does first
Its squad leader keeps a standing plan for it (`command.Plan`, kept on disk): one role at a site,
and the directives it does first ([military-doctrine.md § 18](../../military-doctrine.md#18-task-organisation)).
- **Role** (`command.Role`): `RIFLEMAN`, with the squad - its formation slot and the squad's task;
  or `MINER`, `FARMER`, `MASON`, `FORESTER`, `WOODCUTTER`, `QUARRYMAN`, `SENTRY` - that task at
  its site, with the rest of the squad in that role.
- **Directives** (`command.Directive`), ahead of the role: `GoTo(at)`, held until replaced by a rifleman, done on arrival by one with a role;
  `Fetch(resource, amount, near)`, done once it carries the amount; `Level(box, y)`, done once the
  world shows the ground level.

## The work: its unit's task
Apart from `Place` and the ground work's commands, the work it does with its hands isn't in its
order. It comes from its unit's objective - the general's `Objective.Task` for a rifleman, its
role's task at its site for a detachment - read by the runner (`app.SoldierRunner`), which hands
each routine the site and the cells it works on ([routines.md](routines.md)).

## What it reports: `command.Report`
| Report | Meaning |
|---|---|
| `Idle` | No order to carry out. |
| `Moving` | On its way, or cutting or building a step on the way. |
| `InPosition` | Arrived; holding. |
| `InContact(hostiles)` | Fighting. |
| `Cantco(reason)` | Can't comply, and why: no route (with the price of a riskier route if one exists), or the routine's failure ("can't cut the staircase further: water or lava beside step 2"). After 3 failed plans. |
| `LinkingUp` | Respawned, and closing on its squad. Set by the squad's drill, not by the soldier. |
| `Recovering` | Stranded, and the squad's recovery drill getting it out. Shown to the squad leader in place of its CANTCO, at the rear; set by the drill, not by the soldier. |
| `Building(placed, cells)` | Filling the cells of its `Place`: so many in. A `Place` it can't finish is a `Cantco`. |
| `Working(command, left)` | On a ground-work command: so many of its cells left. |
| `Rearming`, `Gathering(resource)`, `Fortifying`, `Farming`, `Hunting`, `Planting`, `Securing`, `Mining(step, steps)`, `CrossLeveling(to, item, count)` | The routine that has its hands. |

## Its condition: `command.Condition`
Health, food, deaths, and its `Kit`: the best weapon, arrows, armour points and fall protection,
logs, cobblestone, dirt, rations, shield, pickaxe, bow, spare weapons.
