---
status: ready
priority: P1
---

# One answer to "may this block be dug, cut, built in, stood in?"

## Why
The operator (2026-11-24): refactoring for understanding is prioritised among the fixes - with the abstractions and
interfaces right, the fixes should be fewer. What is protected from what is decided in ten places that disagree, and a
farm's gate was safe from the haul road only because no stand was found for it: two changes that found one each failed
`FarmMissionSimulationTest.aTreeFarmBuiltBesideAFarmLeavesTheFarmAsItWas`.

## Where it is decided today
| Site | Protects | From | Keyed by |
|---|---|---|---|
| `SoldierRunner.campKeep` | walls, huts, workshops' stand, table, furnace and chests, full chests, the cell under each | dig, fill | cell |
| `SoldierRunner.groundExcept` | `campKeep`, the camp's mine, the squads' stairs | dig, fill | cell |
| the keep's `digExcept` | the slab's rock or soil, the pillar under a chest not yet emptied, ore set aside | dig | cell |
| `WorldModel.keepOpen` / `noBuild` | the staircases' cuts | build in | cell |
| `FarmMissions.kept`, `slopes`, `floorCells` | other templates' chunks and fence lines; a tree farm's soil | dig, pave, slope | column |
| `AccessRoute.stepCost` | hazard, workstation, chest; the caller's keep-out | road cut, pass | kind, predicate |
| `Clearing.diggable`, `safeFrom` | live farmland, what holds water back | dig | kind |
| `Clearer`'s stand | a cell with a block in it | stand | cell |
| `Pathfinder.spoils`, `Builder` | farmland, a crop, `noBuild`, a gate's cell | build in | kind, cell |
| `SoldierRunner` (stood in a block; shut in) | a gate | dig out | kind |

They disagree on: a **gate** (diggable by kind; never built in; listed by the haul road as rock to cut, with the cell
over it, which is no block); a gate's cell **as a stand** (`Clearing.standFor` takes it; only `Clearer` refuses it);
the **haul road** (sees none of `groundExcept` or `FarmMissions.kept`); **templates** (kept by column from flatten
missions only - the keep's dig, a tunnel and an `Order.Dig` never ask); `noBuild` (bars building, not digging or
standing); a **chest or workstation** (diggable by kind, uncuttable by the road, safe from a dig only if listed).

## The abstraction
`bot.engineer.Kept`, pure, beside `Clearing`:
- `enum Use { DIG, CUT, BUILD, STAND }`
- `record Kept(Set<Cell> cells, BiPredicate<Integer, Integer> columns, Set<Use> gateBars)` with
  `boolean bars(Use, WorldView, BlockPos)`, `Optional<String> why(...)` for the logs, `NONE`, `and(Kept)`, `but(Cell)`.
- The rules by kind - gate, farmland, crop, chest, workstation, the cell over a fence - are one table of kind by use
  inside it. A flatten of a farm given up digs its gates, so the gate's rule is each `Kept`'s, not the world's.
- What a mission or a squad keeps is built for each call in `app.KeptGround` - `camp(...)`, `of(FarmMission, FarmPlan)`,
  `keep(Flatten, condemned)` - static, from arguments, holding nothing. `Order.Dig`'s `Set<Cell> except` stays as it is.

## Its commits, each changing no behaviour
1. **Today's answers pinned, tests only**: a road past a shut gate lists the gate and the cell over it in `toCut`, and
   refuses a chest (`AccessRouteTest`); `diggable(gate)` is true, `nextInTunnel` takes a gate in its cover, `standFor`
   returns a shut gate's cell (`ClearingTest`); `spoils` on a gate, farmland, a crop, `noBuild` (`PathfinderTest`);
   `kept` on a shared fence line and not on its own chunk (`FarmMissionsTest`). The test world may want a gate's state.
2. **`Kept` with its table saying exactly what is said today** - a gate diggable included: `Pathfinder.spoils`,
   `Builder`, `Clearing.diggable` and `safeFrom`, `AccessRoute.stepCost`, `Clearer`'s stand ask it.
3. **`KeptGround`**: `campKeep`, `groundExcept` and the `digExcept` out of `SoldierRunner`, its cap lowered;
   `FarmMissions.kept` with them.
4. **`Kept` passed where the sets were**: `AccessRoute.plan(world, start, goal, Kept)`, `Clearing.toClear`,
   `nextInTunnel`, `standFor`; `keepOpen` a `Kept` handed to the pathfinder if it's cheap.
5. Docs: `GateHabit`'s comment (a shut gate is solid to `ChunkCache`), `docs/architecture.md`.

## What it then makes one line each, in this order
1. The haul road neither cuts nor passes a gate, and keeps off what its mission keeps.
2. A tunnel's cut takes no gate unless its cover is a flatten's own - and Pete's stair is cut from where it stands
   (backlog/mobility.md; the patch at `C:/mc-state/garrison/pete-road-gate.patch`).
3. Nobody stands or stops in a gate's cell or the cell before it (`standFor`, a `MoveTo`'s goal).
4. A formation's slot never lands in a column another mission keeps.

## Risks
- `SoldierRunner` is at its cap: commit 3 is a net removal, and `campKeep` reads eight fields.
- `spoils` and `standFor` are hot: `Kept.bars` adds no block read - checked at the next profile.
