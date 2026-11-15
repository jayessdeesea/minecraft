# Platoon sergeant: what it decides

The platoon's sustainment, as code: `command.PlatoonSergeant`, pure decisions over what the
soldiers carry and what the platoon needs, called from `app.SoldierRunner` (ATP 3-21.8: the
platoon sergeant is the platoon's primary sustainment operator). It never calls a model, and the
platoon leader sees what it decides only as state: specialists produce, missions consume
([tenets](../tenets.md)).

## Decides

| Decision | When | From | Gives |
|---|---|---|---|
| A squad's Class I | each SITREP | each soldier's food and rations, who has no means of food, who a squadmate is sharing with, and whether wheat grows in the camp's farm | GREEN, AMBER or RED (`command.Subsistence`), logged when it changes |
| The keep's Class IV request | each SITREP, for a squad on the keep | the logs its builders carry and the stockpile holds, and whether the depot's chest stands | the request up the chain when they're short of 16 logs ([military-doctrine.md § 4](../../military-doctrine.md#4-reports-upward)) |
| A squad's cross-leveling | each command cycle | what each soldier carries, the logs each wants for a tool, who the recovery drill has and who is making its kit | hand-overs: logs, rations to one running low, a spare weapon to one bare-handed, a pickaxe or a shovel to one short of the tool load ([military-doctrine.md § 13](../../military-doctrine.md#13-cross-leveling); paired by `command.CrossLevel`) |
| Rations across squads | each command cycle | the soldiers out of contact and the hand-overs their squads already make | rations to one running low that no squadmate is sharing with, from the nearest with some to spare |
| The keep's wood party | each time the keep's foreman plans, about every 5 seconds | each builder's logs and saplings, the stockpile's logs, whether the keep's squads have seen trees, whether the keep's grove has a bare cell and whether its planting waits | the builders off the dig to cut trees and the one planting the grove ([military-doctrine.md § 4](../../military-doctrine.md#4-reports-upward), "The keep's wood party") |

The party is two builders at most and a third of the crew, never the foreman. It goes out to cut
when the crew and the stockpile hold fewer than 8 logs, and comes back at 16; a cutter back with
16 logs is on the dig again. A place left over plants the grove. With no trees seen and no sapling
carried there is no party.

## Still elsewhere

Sustainment the platoon sergeant is to own, and where it lives today (backlog/fleet-command.md):

| Duty | Where |
|---|---|
| Kit: the equipment ladder | `bot.craft.Ladder`, per soldier |
| Ore pooled through the stockpile | `bot.supply.MetalPool` |
