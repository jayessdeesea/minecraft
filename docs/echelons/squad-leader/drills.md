# Squad leader: its drills

Pure, deterministic rules in `command`, run every squad cycle (1 second) in this order, each over
the orders the one before it gave. They're the squad's battle drills: what a trained squad does
without being told.

| Order | Drill | What it does |
|---|---|---|
| 1 | `Formations.assign` | The `SquadOrder` to each soldier's slot ([§ 2](../../military-doctrine.md#2-formations-and-intervals)). |
| 2 | `LinkUp` | A respawned soldier closes on its squad, `LinkingUp` until it's back ([§ 6](../../military-doctrine.md#6-replacements-rally-point-and-link-up)). |
| 3 | `Cohesion` | Pace control: the point man halts for a straggler that can follow ([§ 9](../../military-doctrine.md#9-pace-control-and-supporting-distance)). |
| 4 | `Resupply` | A soldier short of a weapon or armour draws from the supply chest ([§ 11](../../military-doctrine.md#11-pre-combat-checks)). |
| 5 | `ReactToContact` | The soldier in contact fights; the rest move to support it ([§ 8](../../military-doctrine.md#8-actions-on-contact-and-the-react-to-contact-drill)). |
| - | `Escort` | Instead of 1 to 3: a ring round a named player ([§ 10](../../military-doctrine.md#10-close-protection)). |
| - | `CrossLevel` | Kit from a squadmate with some to spare to one that needs it ([§ 13](../../military-doctrine.md#13-cross-leveling)). |
| - | `PatrolBase` | The hut's outline, one cell per soldier, checked from the world ([§ 14](../../military-doctrine.md#14-patrol-base)). |

After the drills, the runner sends some soldiers somewhere else, by the squad's task. The hut
builder goes to its cell inside the outline, the mason to its wall cell, the cutter to its step,
the gatherer to its tree (docs/echelons/soldier/routines.md).
