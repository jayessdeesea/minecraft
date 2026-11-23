---
status: ready
priority: P1
depends-on: []
doctrine: FM 6-0 (task organisation, priorities of work); ATP 3-21.8 (assembly areas)
---

# Task organisation: what's left

## Why
Roles, detachments and three directives (`GoTo`, `Fetch`, `Level`) are built, followed as work orders and kept
across a restart (docs/military-doctrine.md § 18). The squad leader still can't say several things it needs
to, and isn't told at once when a soldier can't do what it was given.

Who gives a soldier its work, what an idle soldier does, and what it carries are not this item's: a soldier
makes up no task of its own (operator, 2026-11-22) - backlog/authority-and-missions.md (step 4, sectors),
backlog/soldier-order.md and backlog/load-plans.md.

## What's left
Each a squad-simulation scenario, then seen live:
- **A CANTCO ends a directive at once.** `command.WorkOrders` only times a directive out (no progress in
  2 minutes, or past its allowance); a soldier's own CANTCO on it should end it there, with why. A refusal
  that grows longer each time the same order times out again. The role's work followed the same way, with
  `TaskRegister`'s STALLED shown to the squad leader.
- **Liveness: time back to the requester.** Requests up the chain are timed and alarmed (`app.RequestLedger`).
  Left: the same age alarm for model calls waiting and for a work order given and not begun, and each dawn's
  review saying the slowest answer of each kind.
- **CLEAR(box).** Everything in a box dug out - an encampment's ground, so nothing hides in shade by day -
  reaching high blocks from pillars it takes down after. Bamboo scaffolding later.
- **Assembly.** Soldiers sent to one point report closed on it (`InPosition` says it for one); the squad
  leader sees the squad closed before the next phase.
- **More directives:** `Deposit(items, at)` and `Withdraw(items, at)` at a chest; `Craft(item, n)`; `Fetch` of
  what a kit doesn't count yet (raw iron, wheat).
- **Outpost standards.** A mining outpost's checklist - a chest, a table and furnace, beds, a farm, wood -
  checked from the world and turned into directives by a drill, the model only choosing the template
  (backlog/echelon-contracts.md's engineer).
- **Holding under fire.** An order that says so (`risk` HIGH, or its own flag) keeps a soldier on its work in
  contact.
- **The platoon leader asking for a detachment** ("one soldier on the mine"). Waits until a standard needs
  one, and then wants its own `PlatoonLeaderScenarioIT` situation: a system-prompt line telling it that it can
  took that eval from 71/72 to 67/72 (sheltering by day, pulling a squad from its base).

### A soldier in a worker's way is asked to move, through the chain (operator, 2026-11-23)
"If an idle soldier is blocking a working soldier, like the worker can't place a block: maybe the soldier asks the squad
leader for help and the squad leader tells them to move. If it's for a different squad, maybe it has to go through the
platoon leader. This would be a temporary order, as the situation might be stale, and we wouldn't want to take them off
important work, like cutting a tunnel."
- The worker reports it: a placement or a dig refused because a soldier stands in the cell (or on its stand) is a
  request up - who, where, for what - not a retry.
- Its squad leader answers if the one in the way is its own; else the request goes up to the platoon leader and down to
  the other squad's leader. The answer is an order to step clear: a move of a few blocks, with a time to live of
  seconds, dropped unread if by then the cell is clear or the worker has gone on.
- It never takes a soldier off work it's at - a tunnel's cut, a fight: the one in the way steps aside only if idle or
  walking, and otherwise the worker is told to work round (another cell of its strip, and back).
- Counted: requests made, answered, and how long a worker waited - a soldier often in the way is a tasking fault.
- The eval: two soldiers of one squad, one idle in the other's next cell; then of two squads; the block goes in, and
  the one asked goes back to what it was doing.

## Done when
- Each bullet above passes as a squad-simulation scenario and is seen live.

## Notes
- **Decided (operator): one role per soldier.** Lighting a dark place it works in is every soldier's duty,
  not a role.
- **Decided (operator): one FARMER role, the farm's cycle the drill's** (`command.FarmDuties`), to keep the
  model's round trips few - revisited if that proves too coarse.
- **Decided (operator): realistic mining** - tunnels from what the soldier can see, not ore read through
  stone from the chunk data. Deeper levels are backlog/mining.md's.
- **Decided (operator): self-preservation wins unless the squad leader overrules it** - a farmer attacked
  draws its sword.
- Beds and who sleeps: backlog/rest-plan.md.
