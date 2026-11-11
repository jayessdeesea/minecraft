# Soldier: its routines, and the task that starts each

A routine is an expect script (`bot.script.Script`): it acts, then waits for the server's
answer, never for time (docs/architecture.md, behaviour modelling). One routine has the soldier's hands at a
time, by the priority below (`bot.Hands`). A routine under way keeps them until it's done, fails or
its guard stops it. Nothing preempts. A mason whose hands are taken a moment - a hostile near, a
meal - holds its job's place for up to 10 seconds and goes on from it, rather than starting the
cell again (`bot.supply.Mason`). Fighting isn't a routine: contact takes over the soldier's
movement whatever routine holds its hands.

The runner (`app.SoldierRunner`) starts each routine from the squad's `Objective.Task` and the
squad's state, recomputed every squad cycle.

| Priority | Routine | What it does | Started by |
|---|---|---|---|
| 1 | `SUPPLY_RUN` (`SupplyRun`) | Takes weapon and armour from the supply chest and puts them on. | Objective at the supply point. |
| 2 | `DOORWAY` (`Breacher`) | Breaks two wall blocks out of a hut. | The squad leaving its patrol base. |
| 3 | `TORCHMAKER` (`Torchmaker`) | Makes torches: logs to charcoal, sticks, torches. | A base to light and no torch in the squad. |
| 4 | `RATIONS` (`Rations`) | Eats. | Hungry, with food carried. |
| 5 | `SEALER` (`Builder`) | Puts blocks in the water round it, sources first, to stop the flow and climb out. | Stuck in water. |
| 6 | `HUNTER` (`Hunter`) | Kills an animal for meat. | Hungry by day with nothing to eat ([§ 4](../../military-doctrine.md#4-reports-upward)). |
| 7 | `ARMOURER` (`Armourer`, `Crafter`) | Makes the next rung of the equipment ladder. | The squad leader's `rearm`, out of contact ([§ 11](../../military-doctrine.md#11-pre-combat-checks)). |
| 8 | `BUILDER` (`Builder`) | Walls and roofs a patrol base round the squad. | `PATROL_BASE`, all the squad inside the outline ([§ 14](../../military-doctrine.md#14-patrol-base)). |
| 9 | `PLACING` (`Builder`) | Fills a structure the engineer laid out, cell by cell, standing where each needs. | `Order.Place` ([orders](orders.md)). |
| 10 | `DEPOSITOR` (`Depositor`) | Takes a full load to the stockpile chest. | A full load of logs or cobblestone. |
| 11 | `HANDOVER` (`Handover`) | Throws logs, food or a spare weapon to a squadmate. | Cross-levelling ([§ 13](../../military-doctrine.md#13-cross-leveling)). |
| 12 | `MINER` (`Miner`) | Cuts the spiral staircase a step at a time, mining ore beside it. | `MINE`: the squad's first soldier with a pickaxe ([§ 16](../../military-doctrine.md#16-mining-down)). |
| 13 | `CLEARER` (`Clearer`, `Digger`) | Digs every block of its box: trees from the foot up, ground from the top down. | `Order.Dig` ([orders](orders.md), [§ 15](../../military-doctrine.md#15-securing-the-camp)). |
| 14 | `FILLER` (`Filler`, `Builder`) | Fills the open cells of its box, lowest first. | `Order.Fill`. Out of blocks, an `Order.Gather` of SOIL instead. |
| 15 | `MASON` (`Mason`) | Builds the wall round a camp. | `FORTIFY` ([§ 15](../../military-doctrine.md#15-securing-the-camp)). Out of blocks, it digs dirt first (`GATHERER`). |
| 16 | `LAMPLIGHTER` (`Lamplighter`) | Puts torches on the dark ground. | `SECURE`. |
| 17 | `FARMER` (`Farmer`) | Tills, plants and harvests the farm. | `FARM`. |
| 18 | `FORESTER` (`Forester`) | Plants saplings. | `TREE_FARM`. |
| 19 | `GATHERER` (`Gatherer`, `Digger`) | Chops logs, quarries stone, digs dirt. | `WOOD`/`STONE` (Gather), an `Order.Gather`, or a routine short of material ([§ 12](../../military-doctrine.md#12-gathering-materials)). |
| 20 | `SCAVENGER` (`Scavenger`) | Picks up dropped items nearby. | Nothing threatening. |

Each routine has unit tests against a fake server that answers as the real one does
(`FakeCraftingServer`, `FakeChestServer`, and the per-routine tests in `bot.supply`).
