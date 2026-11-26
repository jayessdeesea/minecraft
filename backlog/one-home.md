---
status: ready
priority: P1
---

# One home for each fact, and the assumptions that no longer hold

## Why
The operator (2026-11-24): a change that has to be made in several places to stay consistent - constants, for one - is
the fault, and the fact gets one home; and the architecture refactoring revisits old assumptions and checks they still
hold (docs/conventions.md, the refactor section). A read of the main sources (39.5k lines) found the lists below. Five
were checked against the code (A1, A3, A12, A25, the dead `walkable`); the rest are confirmed when each is taken up.

Copies that **differ** are findings: which is right is decided and said, as its own commit, a row or test first. Copies
that agree are folded into one home by whichever refactor next opens the file.

Paths: `app/`, `bot/`, `command/` are each module's `src/main/java/io/github/jayessdeesea/minecraft/<module>/`.

## Fix first
1. **A1 - a resource's item, load and dig target**: already wrong in two arms, and `default` hides the next.
2. **A2 - a CANTCO's reason compared as text**: nine behaviours hang on prefixes of log sentences.
3. **B1 with A11 - a ground per mission**: `FlattenWork` by name; the blocker backlog/named-work.md names.
4. **A6, A7, A8 - one `Chunk` value and one `Template` interface**: abstraction (2); a new template costs nine switches.
5. **A3, A4, A5 - one `Body`**: standable, reach, eyes, box.

## A. One fact in several places

### Copies that differ
- **A1 Resource -> item, load, dig target, carried count.** `bot/supply/Gatherer.java:201,207,240` (`default ->
  COBBLESTONE`, so FARMLAND is cobblestone), `app/SoldierRunner.java:7646` (SOIL, FARMLAND -> DIRT),
  `command/Directive.java:87` (`default -> k.dirt()`, so FLINT reads the dirt count),
  `command-ai/SpringAiPlatoonLeader.java:377`. Home: fields on `command.Resource`, no `default`.
- **A2 Reasons compared as text.** "no route from" made by `bot/Mover.java:612`, `app/SoldierRunner.java:4639`, and "no
  route to" by `bot/supply/Mason.java:405`; read as "no route from" by `command/Recovery.java:87`,
  `bot/DeterministicSoldier.java:297` and as "no route" by `app/CutOff.java:30`, `app/PoliceCall.java:94`,
  `SoldierRunner.java:7008,7395,7486`. The same family: "(MEDIUM risk)" (`Mover:651` -> `DrillSquadLeader:91`), "water
  or lava beside step" (`Miner:957` -> `SoldierRunner:5234`), "no floor" (`Miner:966` -> `Miner:448,497,515`,
  `Staircase:611`), "nothing in reach" (`Clearer:295` -> `Farmer:735`), "walk to " (twelve step names -> `Mason:504`),
  "no LOG" / "{LOG=" (`Armourer:419`, a `Map.toString` -> `SoldierErrands:85,177,182`), "[Verify]" + "again"
  (`TaskRegister:132,140` -> `SoldierRunner:3886`), "[Alarm]" / "-> PL]" (`ShareWatch`, `MissAnswer` -> `DutyBook:103,120`,
  `SoldierRunner:3656`), "ALARM" (`OrderTrace:98,118` -> `ChainOfCommand:543`), "stationary" (`IdleWatch:33` ->
  `SoldierRunner:7589`), "loop"/"errands" (`ErrandLedger` -> `SoldierRunner:7515`), "superseded by" (`FarmMissions:81` ->
  `RegistersSaid:40`), "dig the keep" / "fill the keep" (`SiteGeometry:125`, ten sites in `SoldierRunner`). Home: a
  reason's kind as a type on `Report.Cantco` and `Miner.Progress.Stopped`; the words stay for the log.
- **A3 Standable, twice.** `bot/nav/Pathfinder.java:130` (never on a fence top; hazard at the head too) and
  `bot/supply/Digger.java:705` (solid below, hazard at the feet only, no fence rule). Home: `Pathfinder.standable`.
- **A4 A body's reach to a block: 3.5 to 5.0 by routine.** `Crafter:89` 4.5 from the eyes, `Miner:828` 4.5,
  `Clearing:33` 5.0, `Builder:44` 4.0 less a margin, `GateHabit:32` 4.0, `Farmer:464` 4.0, `ChestWindow:25` 3.5 from the
  feet. Home: `bot.Body` - reach, eyes, width, height, one `withinReach(eyes, cell, margin)`; a routine keeps its margin.
- **A5 "A body in a cell".** `Builder:392` (the feet's cell and the one over) against `SiteGeometry:230` (1.8 high): they
  differ mid-jump. Eye 1.62, half-width 0.3 and height 1.8 are each a constant in four to six classes and a literal in
  more (`DeterministicSoldier:184` and `:394` name the height twice). Home: `Body`.
- **A11 The flatten a mine site stays clear of.** `app/ChainOfCommand.java:343,365` read the field, `:354` the
  accessor - a mission's chunk while its flatten is under way. Goes with B1.
- **A12 The world's floor and top.** Top is 318 (`command/Flatten.java:50`), 319 (`command/Keep.java:44` SKY_TOP, read
  by `bot/Body.java:106` and four app classes), 320 (two prompts in `command-ai`), 384 (`SoldierRunner:1506`). Floor -64
  in six places. Home: one world-facts class in `command` - floor, top, sea level, a chunk's side, a day's ticks, a
  tick's millis.
- **A14 Walking pace.** `bot/Gait.java:19` 0.2 a tick (4.0 a second, what a soldier does), `bot/Engagement.java:26` 4.3,
  `Digger:354` 4.317, `Trip:10` 4.9 ticks a step; walk allowances of 20 ticks, 8 ticks and 6 seconds a block. Home:
  `Gait`; the rest derive.
- **A15 "On its cell".** `hypot < 0.6 && |dy| < 0.6` in six routines; `Farmer:868` and `Mason:495` with other
  tolerances. Home: one static on `Body` or `Footprint`, the tolerance an argument.
- **A25 One name, two values.** `VERTICAL_TOLERANCE` is 3, 3, 3 and 1 across four `command` classes. A block's cell is
  both `command.Cell` and `bot.nav.BlockPos` (19 conversions; `(int) Math.floor` 107 times beside
  `BlockPos.containing`).

### Copies that agree
- **A6 A mission's kind -> template, task, done-measure, words**: nine dispatch sites (`FarmMission:79,93-143,151`,
  `FarmMissions:173,235,577`, `TemplateDrift:76,118,146`, `ChatCommands:28-59`, `BasePlan:38`), two with a hidden
  `default`; `TemplateDrift:109` `PastureChunk.Animal.valueOf(m.crop().name())` needs two enums to share names; a
  template built from (chunkX, chunkZ, level) at 30 sites. Home: one `Template` interface in `bot.farm` and one
  kind -> template factory (abstraction 2).
- **A7 A template's level and its offsets**: `level - 1`, `+ 1`, `+ 2` at about 45 sites. Home: named heights on the
  template (its slab, its ground, where things stand, a post's top).
- **A8 A chunk's 16**: three constants and about 70 literals; `chunk * 16` defined seven times, `floorDiv(x, 16)` in
  eight classes, `+ 15` in nine, a chunk's `Flatten` built four times; which cells are a template's is 0..15, 1..15 or
  0..16 by class, reconciled by hand in `KeptGround.columns`. Home: a `Chunk(x, z)` value in `command`.
- **A9, A10 Sea level 63** in four constants and four literals; **the default ground "-16 -16 16 16 63"** in
  `Keep`, a `@Value` default and `application.yml`. Home: the world-facts class; `application.yml` alone.
- **A13 A day's length**: `DAY_MILLIS` defined in `SoldierRunner:517` and `FarmMissions:491`, `20 * 60_000` written out
  in four more; a tick's 50 ms and `/ 20` in six. Home: `TimeOfDay`.
- **A16 The mineshaft's well** in `Staircase:54-58` and again as `MineHead:28` ring numbers. Home: `MineHead` derives.
- **A17 Template layouts repeated**: chest column, torch grids, table and furnace lines, the fence-gates-torches
  preamble of `fixtures()`, "footing still to pave" (identical in `ChunkFarm:176` and `PastureChunk:112`), "is ground"
  in five places. Home: A6's interface.
- **A18 An item's class -> where it's kept**: `StoresChunk.Row`, `MineHead.Bank`, `SoldierErrands:91,237`,
  `command/Stockpile:30`. Home: one supply-class table; rows and banks name a class. (The chest-contents check reads it.)
- **A19 Routine deadlines**: `ANSWER_TICKS = 40` in 13 files, a walk's 400 or 600 in ten. Home: `bot.script.Script`.
- **A20 to A24**: a tree's limits and a work area's box (`Gatherer:82`, `TreeFelling:51`, `SoldierRunner:3295`, `WoodDuties`,
  `WorkArea`); camp radii re-derived (`WALL_RADIUS - 1` at seven sites); protocol-777 facts as literals (entity ids in
  `EntityTracker:140-183` beside a registry by name; the block index in a section in four places; hotbar slots
  redefined in `StuckCase:26`); thresholds under two names (food 6, drop 3, 132 uses, a stack's 64); the night
  predicate written five times as `time().map(TimeOfDay::dark).orElse(false)`.
- About 40 more of lower rank: search radii, summary periods, retry waits, reach budgets.

## B. Assumptions

### No longer true
- **B1 There is one ground to flatten.** `FarmMissions:140` "one flatten at a time", `:323` `findFirst`;
  `ChainOfCommand:507` `flatten()`; `SoldierRunner.flattenWorks` one entry of `FlattenWork`, holding one plan, workshop, road
  and foreman. `BasePlan` gives up to 63 chunks; the code works one. backlog/named-work.md.
- **B2 "The camp's X".** `campStairs`, `campFarm`, `campTrees`, `campGround`, `campWorkshop`, `campMines`, `stockpile`,
  `keepMine`; `FarmMissions:55,567,950` keep the last tree farm made as the camp's (wheat is a list, trees are not).
- **B3 The keep is at the origin, y=63.** `command/Keep` CENTRE, DEPOT, BOTTOM, FLOOR and `dig()` are read only by
  tests; `MineSites:29` and `BasePlan:25,34` are fixed to the origin whatever `minecraft.camp` is.
- **B4 A level assumed fixed.** The camp's ground flattened to 63 whatever its height (`ChainOfCommand:590`); "below
  ground" is `y < 63 - 8` (`CampGround:113`, `SoldierRunner:7492`); digs audited only at or over 63
  (`PlacementAudit:83,145`).
- **B5 Dusk ends the day's work.** `NightRules:47-110`, `ChainOfCommand:295,749`, eight sites in `SoldierRunner`,
  `DrillSquadLeader:85-93` - against "a squad works till it is told differently" (backlog/chunk-tasks.md).
- **B6 A model decides.** `docs/architecture.md:11-16,39-40` (a class that doesn't exist), `docs/echelons/README.md:11`,
  comments in `ChainOfCommand:62-98`; and throttles built for a model's latency still gate rules that answer in
  microseconds: a 20 s period, an 8 s gap on a new CANTCO or contact, a queue of three (`SquadCommand:39,267,272`,
  `ModelCalls:28`). Whether the waits are wanted is the operator's.
- **B7 Javadoc saying what the code no longer does.** `FarmMission:16-23,92`, `FarmMissions:40,45`,
  `SoldierRunner:5238`, `MineSites:26`.
- **B8 Code for cases that can't arise.** `Keep.FILL = false` and the fill branch and state behind it
  (`SoldierRunner:2312`, `FlattenWork.fillLeftBySquad`, `SquadState:87`); `SoldierRunner:6369` `walkable`, uncalled; three constants
  never read (`CampFarm:46`, `SoldierRunner:3692,3694`).
- **B9 No fallbacks.** Three `@Value` defaults (`ChainOfCommand:250,463,515`); unknown time read as day, five times;
  an empty squad held at (0, 0, 0) (`DrillPlatoonLeader:58`); `default ->` over an enum in three classes.
- **B11 A comment is the rule and its reason.** 386 `fieldNNN` references in 87 files and about 640 "Live, ..."
  narratives predate the rule; some guards give only the incident.

### Unclear, and what settles it
- **B10 The line caps are being met by packing**: several fields to a line (`SoldierRunner:877,4036`), long lines in
  `FarmMissions:183,491`. The ratchets count lines; count declarations and statements instead.
- **B12 `command` is "the chain of command's interfaces"**: it is 77 files of rules and geometry, and world facts leak
  through it (`Body` takes the sky's top from `Keep`). The enforced boundaries hold.
- **B13 A `Position` used as a cell has whole coordinates**: 25 `(int) c.x()` truncations in `SoldierRunner`, which
  differ from floor west and north of 0. `PatrolBase` should hand out `Cell`.
- **B2, B4, B8**: which of `minecraft.farm-squad` and the drill's `i % 2` deals the farms; whether the night's mine at
  y=16 holds beside a mission's shaft to the bottom; whether `CampFarm` still sites anything.

### Hold
One pinned version and dimension; no mutable static state; no Netty outside `protocol`, no Spring in `command`; a
routine waits on an event (two timed waits: `Pack:31`, `Handover:43`).

## Done when
Each item above is gone from this file: folded, decided, or deleted with the test that shows it.
