# Echelons: what each can do, and what it's told

Each echelon as a role: what it does on its own, what it's given, what it decides or does, and what
it reports. These describe the code as it is ([conventions.md](../conventions.md#docs)). Each is the
contract; the mechanics behind a line, and the doctrine it comes from, are in the
[military-doctrine.md](../military-doctrine.md) section it links to. The principles the contracts
come from, and that each bad outcome is checked against, are the [tenets](tenets.md).

| Echelon | Thinks with | Decides every | Gives | Files |
|---|---|---|---|---|
| Platoon leader | standing rules (a model selectable) | when woken: dawn, dusk, an operator order, a request - at that SITREP (a model: a request not within a minute of its last decision) | an `Objective` per squad: place, words, `Task` | [decisions](platoon-leader/decisions.md), [operator rules](platoon-leader/operator-rules.md) |
| Platoon sergeant | plain Java | each SITREP and each keep plan (about 5 seconds) | Class I and IV ratings and requests; cross-leveling; producers detached: the keep's wood party | [decisions](platoon-sergeant/decisions.md) |
| Squad leader | standing rules (a model selectable) | each SITREP, 1 second (a model: 20 seconds, its drills still each second); the waits are [the decider's](../architecture.md#latency-architecture--why-the-hierarchy-is-stratified-by-timescale-not-just-rank) | a `SquadOrder`, then an `Order` per soldier | [decisions](squad-leader/decisions.md), [drills](squad-leader/drills.md) |
| Soldier | plain Java | every tick (50 ms) | moves, fights, and works with its hands | [capabilities](soldier/capabilities.md), [orders and reports](soldier/orders.md), [routines](soldier/routines.md) |

```
Platoon leader ──Objective(place, mission, intent, Task)──▶ Squad leader ──SquadOrder──▶ drills ──Order──▶ Soldier
   ▲                                                    ▲                                         │
   └──────────── SquadStatus, Foothold ─────────────────┴──────────── SoldierStatus ◀─────────────┘

          Task, and each soldier's Plan ── read by app.SoldierRunner ── starts the routines ──▶ Soldier's hands
```

The second line is the one that isn't a message between echelons: the runner reads the platoon leader's
`Task` for the riflemen, and each soldier's `Plan` - its role and directives, set by the squad
leader - for the rest, to start the soldiers' routines.
