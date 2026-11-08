# Squad leader: its drills

Pure, deterministic rules in `command`, run every squad cycle (1 second) in this order, each over
the orders the one before it gave. They're the squad's battle drills: what a trained squad does
without being told.

| Order | Drill | What it does |
|---|---|---|
| 1 | `Formations.assign` | The `SquadOrder` to each soldier's slot: formation, interval, risk. |
| 2 | `LinkUp` | A respawned soldier closes on its squad (`LinkingUp`); it's back once within 6 blocks of a squadmate. |
| 3 | `Cohesion` | Pace control: the point man halts while a soldier who can follow is out of supporting distance ("HALT: point man waits"). A stuck one (CANTCO) doesn't hold the squad. |
| 4 | `Resupply` | A soldier short of a weapon or armour draws from the supply chest, when there is one. |
| 5 | `ReactToContact` | The soldier in contact fights (its own reflex), and the rest of the squad moves to support it. |
| - | `Escort` | Instead of 1 to 3: a ring round a named player, with the nearest free soldier sent at each threat. |
| - | `CrossLevel` | Logs, food or a spare weapon from a squadmate with some to spare to one that needs them. Not in contact, not to one linking up. |
| - | `PatrolBase` | The hut's outline, one cell per soldier, sited on level ground; breaches and dark floor checked from the world. |

After the drills, the runner sends some soldiers somewhere else, by the squad's task. The hut
builder goes to its cell inside the outline, the mason to its wall cell, the cutter to its step,
the gatherer to its tree (docs/personas/soldier/routines.md).
