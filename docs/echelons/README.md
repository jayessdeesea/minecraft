# Echelons: what each can do, and what it's told

Each echelon as a role: what it does on its own, what it's given, what it decides or does, and what
it reports. These describe the code as it is ([conventions.md](../conventions.md#docs)). Each is the
contract; the mechanics behind a line, and the doctrine it comes from, are in the
[military-doctrine.md](../military-doctrine.md) section it links to.

| Echelon | Thinks with | Decides every | Gives | Files |
|---|---|---|---|---|
| General | a model | 2 minutes | an `Objective` per squad: place, words, `Task` | [decisions](general/decisions.md), [operator rules](general/operator-rules.md) |
| Squad leader | a model, then drills | 20 seconds (drills: 1 second) | a `SquadOrder`, then an `Order` per soldier | [decisions](squad-leader/decisions.md), [drills](squad-leader/drills.md) |
| Soldier | plain Java | every tick (50 ms) | moves, fights, and works with its hands | [capabilities](soldier/capabilities.md), [orders and reports](soldier/orders.md), [routines](soldier/routines.md) |

```
General ──Objective(place, mission, intent, Task)──▶ Squad leader ──SquadOrder──▶ drills ──Order──▶ Soldier
   ▲                                                    ▲                                         │
   └──────────── SquadStatus, Foothold ─────────────────┴──────────── SoldierStatus ◀─────────────┘

                 Task ─────────────── read by app.SoldierRunner ──── starts the routines ─────▶ Soldier's hands
```

The second line is the one that isn't a message between echelons: the general's `Task` goes past
the squad leader, and the runner reads it to start the soldiers' routines.
