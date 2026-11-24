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
| `KeptGround.camp` | walls, huts, workshops' stand, table, furnace and chests, full chests, the cell under each | dig, fill | cell |
| `KeptGround.ground` (`SoldierRunner.groundExcept` gathers its arguments) | `camp`, the camp's mine, the squads' stairs | dig, fill | cell |
| `KeptGround.keep`, the keep's `digExcept` | the slab's rock or soil, the pillar under a chest not yet emptied; ore set aside, added by `SoldierRunner.keepwork` | dig | cell |
| `WorldModel.keepOpen` / `noBuild` | the staircases' cuts | build in | cell |
| `KeptGround.columns` (`FarmMissions.kept` hands it the templates); `FarmMissions.slopes`, `floorCells` | other templates' chunks and fence lines; a tree farm's soil | dig, pave, slope | column |
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
`bot.engineer.Kept`, pure, beside `Clearing` (built; what follows is what's left to give it):
- `enum Use { DIG, CUT, BUILD, STAND }`
- `record Kept(Set<Cell> cells, BiPredicate<Integer, Integer> columns, Set<Use> gateBars)` with
  `boolean bars(Use, WorldView, BlockPos)`, `Optional<String> why(...)` for the logs, `NONE`, `and(Kept)`, `but(Cell)`.
- The rules by kind - gate, farmland, crop, chest, workstation, the cell over a fence - are one table of kind by use
  inside it. A flatten of a farm given up digs its gates, so the gate's rule is each `Kept`'s, not the world's.
- What a mission or a squad keeps is built for each call in `app.KeptGround` - `camp(world, Camp)`,
  `ground(campKeep, campMine, stairs)`, `keep(world, Flatten, kept, standing)`, `columns(templates, FarmMission)` -
  static, from arguments, holding nothing (built, returning the sets and the predicate; each returns a `Kept` once its
  callers take one). `Order.Dig`'s `Set<Cell> except` stays as it is.

## Its commits, each changing no behaviour
Built: today's answers pinned by tests; `bot.engineer.Kept` with its table of kind by use, asked by `Pathfinder.spoils`,
`AccessRoute.stepCost`, `Clearing.diggable` and `Clearer`'s stand. Left as they were, each for its reason: `Builder`
(it refuses no cell by kind - its checks ask whether a block landed) and `Clearing.safeFrom` (never straight down onto
farmland is about where the digger stands; under STAND it would change `Clearer`, which stands on farmland).
`app.KeptGround` builds the camp's keep, the ground work's, the keep's dig's and a mission's columns from arguments,
pinned by `KeptGroundTest`; the ore a keep sets aside stays `SoldierRunner.keepwork`'s, worked out with its pickaxes.

1. **`Kept` passed where the sets were**: `AccessRoute.plan(world, start, goal, Kept)`, `Clearing.toClear`,
   `nextInTunnel`, `standFor`; `keepOpen` a `Kept` handed to the pathfinder if it's cheap.
2. Docs: `GateHabit`'s comment (a shut gate is solid to `ChunkCache`), `docs/architecture.md`.

## What it then makes one line each, in this order
1. The haul road neither cuts nor passes a gate, and keeps off what its mission keeps.
2. A tunnel's cut takes no gate unless its cover is a flatten's own - and Pete's stair is cut from where it stands
   (backlog/mobility.md; the patch at `C:/mc-state/garrison/pete-road-gate.patch`).
3. Nobody stands or stops in a gate's cell or the cell before it (`standFor`, a `MoveTo`'s goal).
4. A formation's slot never lands in a column another mission keeps.

## Risks
- `SoldierRunner` is at its cap: passing `Kept` where the sets were can add no line to it.
- `spoils` and `standFor` are hot: `Kept.bars` adds no block read - checked at the next profile.
