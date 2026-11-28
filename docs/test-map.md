# Test map

What the suite tests, where each test sits in the code's own hierarchy, and what kind of test it is. Read it before
adding a test, to find the level its rule belongs at and the tests already there.

The tables are generated: `python -I tools/ops/test-map.py` rewrites each `generated` region from the source tree, and
`--check` says whether they are current. The prose is not generated. The generator files a class into an area by its
name or package (`AREAS` and `PACKAGES` in the script), and a test class under the area of its subject - the main class
of the same name less its suffix - else of its name's prefix (`THEMES`), else of the highest-layer main class it names
(a `?` in the appendix). A class names another when it imports it, names it fully qualified, or names it by its simple
name where that is in scope - its own package or one imported whole - and has no type of its own by that name; a name
reached through a dot (`Digger.Progress`) is the outer type's. A main class no rule places is printed as unplaced, and a
name in `AREAS` that names no class as stale.

<!-- generated:totals -->
319 test files: 293 test classes and 26 support classes; 52899 lines of tests against 64644 of main code, 444 main classes in 13 layers once each cycle is taken as one.
<!-- /generated:totals -->

## The hierarchy

The main code's class-level dependency graph, grouped into areas and the areas into tiers. A tier depends on the
tiers above it, not on those below, except within a cycle and along the edges "Tangles" lists.

- **Foundations:** values (`Position`, `Box`, `Cell`); the orders contract between echelons (`Order`, `Report`,
  `Progress`, `SoldierStatus`, `Kit`, `Script`, `Setbacks`); the wire protocol; and the world model (`WorldView`,
  `ChunkCache`, `Hardness`, `BreakTime`, the registries).
- **Getting about:** navigation (`Pathfinder`, `Footing`, `Clearing`, `Kept`, `Stairway`, `WayOut`) and movement
  (`Gait`, `Mover`, `Body`).
- **Fighting:** `Contact`, `Engagement`, `Ballistics`, `Strike`, `ReactToContact`. The hostile and weapon registries
  are filed here too, so more tests reach it than drive combat.
- **Trades:** digging and building (`Digger`, `Miner`, `Staircase`, `Mason`), crafting, stores (`Quartermaster`,
  `Depositor`, `Handover`), farming (`Farmer`, `ChunkFarm`, `FarmPlan`), wood (`Forester`, `TreeFarm`).
- **The soldier:** `DeterministicSoldier`, `SoldierActivity`.
- **Command:** the squad's drills (`DrillSquadLeader`, `Cohesion`, `CrossLevel`), the platoon's (`DrillPlatoonLeader`,
  `BasePlan`, `Plan`, `Keep`, `Flatten`), and `command-ai`.
- **Garrison shell:** `app`. `SoldierRunner`, `ChainOfCommand`, the books, ledgers and watches.
- **Landing queue:** `landing`, apart from the force entirely.

<!-- generated:tiers -->
| Tier | Area | Main classes | Main lines | Own test classes | Test lines | Test classes reaching it |
|---|---|---|---|---|---|---|
| Foundations | values | 18 | 825 | 5 | 245 | 204 |
| Foundations | contract | 13 | 1220 | 5 | 347 | 169 |
| Foundations | wire | 96 | 4812 | 31 | 2215 | 114 |
| Foundations | world-model | 11 | 2381 | 7 | 761 | 146 |
| Getting about | navigation | 14 | 2506 | 11 | 3413 | 118 |
| Getting about | movement | 8 | 1454 | 3 | 368 | 64 |
| Fighting | combat | 11 | 1277 | 14 | 1880 | 176 |
| Trades | dig-build | 14 | 4955 | 13 | 4783 | 62 |
| Trades | craft | 8 | 1974 | 6 | 1045 | 56 |
| Trades | stores-supply | 14 | 3575 | 10 | 1317 | 51 |
| Trades | food-farm | 17 | 3593 | 21 | 5039 | 114 |
| Trades | wood | 4 | 776 | 5 | 661 | 117 |
| The soldier | soldier | 4 | 1097 | 13 | 3080 | 58 |
| Command | squad | 18 | 2446 | 20 | 3846 | 127 |
| Command | platoon | 28 | 3241 | 21 | 4262 | 126 |
| Command | model | 13 | 1736 | 5 | 976 | 43 |
| Garrison shell | garrison-shell | 94 | 21088 | 68 | 7587 | 91 |
| Landing queue | landing | 59 | 5688 | 30 | 5786 | 29 |
| Test harness | simulator | 0 | 0 | 4 | 196 | 0 |
| Test harness | suite | 0 | 0 | 1 | 321 | 0 |
<!-- /generated:tiers -->

**What the trades share is navigation, not movement.** A trade asks `Pathfinder`, `Footing` and `Clearing` where it
can stand and how to get there. It never drives `Mover` or `Gait`: it hands the soldier a goal, and
`DeterministicSoldier` moves. So movement sits beside the soldier, not under the trades: `DeterministicSoldier` is the
only class that names `Mover`, and it and `Mover` the only ones that name `Gait`. Movement is tested mostly through
the whole soldier. The table below gives, for the classes this leans on, how many test classes reach each one and how
many name it directly.

<!-- generated:key -->
| Class | Area | Layer | Test classes reaching it | Naming it directly |
|---|---|---|---|---|
| `Pathfinder` | navigation | 3 | 96 | 14 |
| `Footing` | navigation | 4 | 62 | 3 |
| `Clearing` | navigation | 3 | 96 | 4 |
| `Mover` | movement | 7 | 55 | 0 |
| `Gait` | movement | 6 | 55 | 1 |
| `Body` | movement | 5 | 55 | 0 |
| `DeterministicSoldier` | soldier | 8 | 55 | 15 |
| `Contact` | combat | 5 | 56 | 1 |
| `Hardness` | world-model | 1 | 116 | 8 |
| `Digger` | dig-build | 5 | 60 | 5 |
| `Miner` | dig-build | 5 | 60 | 2 |
| `Farmer` | food-farm | 7 | 45 | 3 |
| `Forester` | wood | 8 | 41 | 1 |
| `DrillSquadLeader` | squad | 7 | 40 | 6 |
| `DrillPlatoonLeader` | platoon | 7 | 38 | 2 |
| `SoldierRunner` | garrison-shell | 11 | 37 | 12 |
<!-- /generated:key -->

## Tangles

The cycles in the main code. Each is a set of classes that can only be understood, and tested, together.

<!-- generated:cycles -->
- 33 classes (app): `CampGround`, `CampMineSites`, `ChainOfCommand`, `ChestStand`, `DayGuardrails`, `DayWorkRules`, `Engineer`, `FarmMissions`, `FlattenMeasures`, `FlattenWork`, `FlattenWorks`, `KeepWood`, `KeptGround`, `MineSiteBook`, `MineSites`, `NightRules`, `OperatorChat`, `OperatorTraces`, `PhaseBook`, `PlacementAudit`, `RequestTracking`, `ScoutMissions`, `SiteGeometry`, `SoldierErrands`, `SoldierRequests`, `SoldierRunner`, `SquadCommand`, `SquadState`, `StatusBoard`, `SupplyTrips`, `TemplateGround`, `TreeFelling`, `WoodsBook`
- 8 classes (command): `Flatten`, `Keep`, `PlatoonSergeant`, `ProductionLevels`, `Renewable`, `SquadStatus`, `Stockpile`, `Subsistence`
- 5 classes (command): `Cohesion`, `CrossLevel`, `ReactToContact`, `Report`, `SoldierStatus`
- 5 classes (landing): `Bounce`, `Faulted`, `MergeOutcome`, `PushOutcome`, `VerifyOutcome`
- 4 classes (bot): `Breacher`, `Digger`, `Miner`, `Staircase`
- 4 classes (command): `Objective`, `ScoutMission`, `WoodDuties`, `WorkArea`
- 3 classes (bot): `Clearing`, `Kept`, `Pathfinder`
- 2 classes (bot): `ChunkFarm`, `FarmPlan`
- 2 classes (command-ai): `SpringAiPlatoonLeader`, `SpringAiSquadLeader`
- 2 classes (landing): `MavenLog`, `MavenProgress`
- 2 classes (protocol): `NettyPlaySession`, `PlayClientHandler`
<!-- /generated:cycles -->

- **The soldier is above what it moves and fights with:** its constants live in `BodyMeasures`, `MovementRates` and
  `ContactThresholds`, which name nothing of the soldier's, so no cycle runs through `DeterministicSoldier`.
  Movement still reads combat, along the edges below: `Mover` asks `Ballistics`, and `Body` reads
  `ContactThresholds` and `WeaponStats`.
- **The `app` cycle** around `SoldierRunner` holds `ChainOfCommand`, the camp, mine and farm books, the day and night
  rules and `OperatorChat`.

The edges against the tiers' order, outside any cycle. Many are a trade or a movement class reading a command concept
(`StockLevels` reading `Keep`, `Body` reading `Keep`): the module boundaries allow it (`bot` depends on `command`), and
it is a level reading one above its own.

<!-- generated:upward -->
- `DutyYield` (contract) reads `Subsistence` (food-farm)
- `EntityTracker` (world-model) reads `HostileMobRegistry` (combat)
- `SquadOrder` (contract) reads `Formation` (squad), `Plan` (platoon)
- `TrackedEntity` (world-model) reads `HostileMobRegistry` (combat)
- `WorldModel` (world-model) reads `BlockPos` (navigation), `ChunkFarm` (food-farm)
- `Body` (movement) reads `ContactThresholds` (combat), `Keep` (platoon), `WeaponStats` (combat)
- `Mover` (movement) reads `Ballistics` (combat)
- `Clearer` (dig-build) reads `LevelPace` (platoon)
- `Depositor` (stores-supply) reads `Stockpile` (platoon)
- `FarmMission` (food-farm) reads `Flatten` (platoon)
- `Handover` (stores-supply) reads `CrossLevel` (squad)
- `Ladder` (craft) reads `CrossLevel` (squad), `Site` (platoon)
- `MetalPool` (stores-supply) reads `Stockpile` (platoon)
- `ResourceSurveyor` (stores-supply) reads `ResourceSite` (platoon)
- `StockLevels` (stores-supply) reads `Keep` (platoon), `ProductionLevels` (platoon), `Renewable` (platoon)
- `WorkLoad` (craft) reads `CrossLevel` (squad)
<!-- /generated:upward -->

## Kinds of test

- **Unit/table:** no fake server and no simulator - values, and ground built by hand as a `WorldView` where the
  decision reads one. A decision's table of evals is one (docs/conventions.md, "A contact met live is a row first").
- **Soldier sim:** one soldier or a few against `bot`'s fakes - `SyntheticWorld`, `Terrain` and `Scenario` for ground,
  `Battle` for hostiles, `FakeCraftingServer` and `FakeChestServer` for windows, the last two standing in at the
  `PlaySession` seam as `SimServer` does. Each answers for one job, and the test steps its ticks; there is no
  `SimClock`.
- **Sim part:** a part of the squad simulation (`SimServer`, `SimClock`, `SimWorlds`) without the runner - the
  simulator's own pieces, or a decision paced on `SimClock`.
- **Squad sim:** soldiers driven by `SoldierRunner` against `SimServer` on `SimClock` (docs/architecture.md, "Squad
  simulation").
- **Stuck replay:** `StuckEvalsTest`, recorded ground replayed (docs/architecture.md).
- **By-name IT:** run by name against a server or as a measurement; not in the verify.

<!-- generated:kinds -->
| Tier | unit/table | soldier sim | sim part | squad sim | stuck replay | by-name IT | architecture | Test lines |
|---|---|---|---|---|---|---|---|---|
| Foundations | 42 | - | - | - | - | 6 | - | 3568 |
| Getting about | 12 | 1 | - | 1 | - | - | - | 3781 |
| Fighting | 7 | 7 | - | - | - | - | - | 1880 |
| Trades | 41 | 4 | 1 | 8 | - | 1 | - | 12845 |
| The soldier | 11 | 1 | - | - | - | 1 | - | 3080 |
| Command | 35 | - | 2 | 7 | - | 2 | - | 9084 |
| Garrison shell | 63 | - | 1 | - | 1 | 3 | - | 7587 |
| Landing queue | 30 | - | - | - | - | - | - | 5786 |
| Test harness | 1 | - | 3 | - | - | - | 1 | 517 |
<!-- /generated:kinds -->

A sim is filed under the area of its subject, or of its name's prefix (`THEMES` in the script) where no class shares
its name: `MineSimulationTest` counts under the trades, not the shell whose runner it drives.

`bot`'s fakes and `SimServer` are two models of the server, one per tier. Neither is checked against the other, or
against the real server, so where both answer for the same thing - a chest's window, a slot's contents - each writes
the server's rule its own way.

## Appendix: every test class, by tier and area

<!-- generated:appendix -->
### Foundations

#### values (5)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [BoxTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/BoxTest.java) | unit/table | 35 | 3 |  | Box |
| [DangerAreasTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/DangerAreasTest.java) | unit/table | 23 | 1 |  | DangerAreas |
| [MissionEstimateTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/MissionEstimateTest.java) | unit/table | 58 | 4 |  | MissionEstimate |
| [ScoutSectorsTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ScoutSectorsTest.java) | unit/table | 68 | 5 |  | ScoutSectors |
| [TimeOfDayTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/TimeOfDayTest.java) | unit/table | 61 | 6 |  | TimeOfDay |

#### contract (5)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [DutyYieldTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/DutyYieldTest.java) | unit/table | 34 | 2 |  | DutyYield |
| [ProgressTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ProgressTest.java) | unit/table | 50 | 4 |  | Progress |
| [ScriptTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/script/ScriptTest.java) | unit/table | 138 | 7 |  | Script |
| [SetbacksTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/SetbacksTest.java) | unit/table | 80 | 5 |  | Setbacks |
| [SupplyTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/SupplyTest.java) | unit/table | 45 | 3 |  | Supply |

#### wire (31)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [AddEntityPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/AddEntityPacketTest.java) | unit/table | 78 | 4 |  | AddEntityPacket |
| [BlockUpdatePacketsTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/BlockUpdatePacketsTest.java) | unit/table | 37 | 2 |  | BlockUpdatePackets |
| [ChatCommandPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/ChatCommandPacketTest.java) | unit/table | 28 | 2 |  | ChatCommandPacket |
| [ChunkDataPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/ChunkDataPacketTest.java) | unit/table | 98 | 1 |  | ChunkDataPacket |
| [ChunkStreamingPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/ChunkStreamingPacketTest.java) | unit/table | 38 | 3 |  | - |
| [CompressionDecoderTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/netty/CompressionDecoderTest.java) | unit/table | 97 | 3 |  | CompressionDecoder |
| [ConnectionWatchdogHandlerTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/netty/ConnectionWatchdogHandlerTest.java) | unit/table | 37 | 3 |  | ConnectionWatchdogHandler |
| [DisguisedChatPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/DisguisedChatPacketTest.java) | unit/table | 82 | 3 |  | DisguisedChatPacket |
| [EntityMovementPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/EntityMovementPacketTest.java) | unit/table | 128 | 9 |  | - |
| [FrameDecoderTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/netty/FrameDecoderTest.java) | unit/table | 65 | 3 |  | FrameDecoder |
| [FrameStatsHandlerTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/netty/FrameStatsHandlerTest.java) | unit/table | 47 | 3 |  | FrameStatsHandler |
| [InventoryPacketsTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/InventoryPacketsTest.java) | unit/table | 241 | 8 |  | InventoryPackets |
| [NbtTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/NbtTest.java) | unit/table | 92 | 4 |  | Nbt |
| [PacketLoggingHandlerTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/netty/PacketLoggingHandlerTest.java) | unit/table | 42 | 3 |  | PacketLoggingHandler |
| [PlayClientHandlerTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/netty/PlayClientHandlerTest.java) | unit/table | 88 | 4 |  | PlayClientHandler |
| [PlayDisconnectPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/PlayDisconnectPacketTest.java) | unit/table | 20 | 1 |  | PlayDisconnectPacket |
| [PlayLoginPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/PlayLoginPacketTest.java) | unit/table | 39 | 1 |  | PlayLoginPacket |
| [PlayerChatPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/PlayerChatPacketTest.java) | unit/table | 43 | 2 |  | PlayerChatPacket |
| [PlayerPositionPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/PlayerPositionPacketTest.java) | unit/table | 39 | 1 |  | PlayerPositionPacket |
| [ProtocolCodecTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/ProtocolCodecTest.java) | unit/table | 109 | 9 |  | ProtocolCodec |
| [RegistryDataPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/RegistryDataPacketTest.java) | unit/table | 64 | 1 |  | RegistryDataPacket |
| [RemoveEntitiesPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/RemoveEntitiesPacketTest.java) | unit/table | 37 | 2 |  | RemoveEntitiesPacket |
| [SessionStateManagerTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/netty/SessionStateManagerTest.java) | unit/table | 42 | 3 |  | SessionStateManager |
| [SetHealthPacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/SetHealthPacketTest.java) | unit/table | 32 | 2 |  | SetHealthPacket |
| [SetTimePacketTest](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/packet/SetTimePacketTest.java) | unit/table | 36 | 1 |  | SetTimePacket |
| [ClockLiveIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ClockLiveIT.java) | by-name IT | 47 | 1 |  | ? |
| [JoinObservationLiveIT](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/JoinObservationLiveIT.java) | by-name IT | 40 | 1 |  | - |
| [PersistentJoinLiveIT](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/PersistentJoinLiveIT.java) | by-name IT | 223 | 7 |  | - |
| [StaircaseProbeIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/StaircaseProbeIT.java) | by-name IT | 54 | 1 |  | ? |
| [StatusPingLiveIT](../protocol/src/test/java/io/github/jayessdeesea/minecraft/protocol/StatusPingLiveIT.java) | by-name IT | 38 | 1 |  | - |
| [TerrainProbeIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/TerrainProbeIT.java) | by-name IT | 154 | 1 |  | ? |

#### world-model (7)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [BlockClassRegistryTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/BlockClassRegistryTest.java) | unit/table | 84 | 8 |  | BlockClassRegistry |
| [BreakTimeTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/BreakTimeTest.java) | unit/table | 38 | 3 |  | BreakTime |
| [ChunkCacheTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/ChunkCacheTest.java) | unit/table | 307 | 18 |  | ChunkCache |
| [EntityTrackerTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/EntityTrackerTest.java) | unit/table | 174 | 14 |  | EntityTracker |
| [HardnessTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/HardnessTest.java) | unit/table | 44 | 4 |  | Hardness |
| [InventoryTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/InventoryTest.java) | unit/table | 95 | 5 |  | Inventory |
| [ItemClassRegistryTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/ItemClassRegistryTest.java) | unit/table | 19 | 1 |  | ItemClassRegistry |

### Getting about

#### navigation (11)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [AccessRouteTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/engineer/AccessRouteTest.java) | unit/table | 183 | 4 |  | AccessRoute |
| [ClearingTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/engineer/ClearingTest.java) | unit/table | 788 | 29 |  | Clearing |
| [FootingTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/FootingTest.java) | unit/table | 46 | 2 |  | Footing |
| [FootprintTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/nav/FootprintTest.java) | unit/table | 29 | 3 |  | Footprint |
| [KeptTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/engineer/KeptTest.java) | unit/table | 345 | 16 |  | Kept |
| [PathfinderTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/nav/PathfinderTest.java) | unit/table | 826 | 31 |  | Pathfinder |
| [RouteEffortTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/nav/RouteEffortTest.java) | unit/table | 66 | 3 |  | RouteEffort |
| [StairwayTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/engineer/StairwayTest.java) | unit/table | 348 | 10 |  | Stairway |
| [TerrainSurveyorTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/nav/TerrainSurveyorTest.java) | unit/table | 206 | 9 |  | TerrainSurveyor |
| [WayOutTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/WayOutTest.java) | unit/table | 216 | 6 |  | WayOut |
| [RoutingSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/RoutingSimulationTest.java) | squad sim | 360 | 9 | slow | - |

#### movement (3)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [GateHabitTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/GateHabitTest.java) | unit/table | 129 | 4 |  | GateHabit |
| [HandsTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/HandsTest.java) | unit/table | 95 | 5 |  | Hands |
| [MovementEfficiencyTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/MovementEfficiencyTest.java) | soldier sim | 144 | 1 |  | - |

### Fighting

#### combat (14)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [BallisticsTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/BallisticsTest.java) | unit/table | 30 | 2 |  | Ballistics |
| [ContactEvalTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/ContactEvalTest.java) | unit/table | 418 | 4 |  | Contact |
| [EngagementTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/EngagementTest.java) | unit/table | 102 | 8 |  | Engagement |
| [HostileMobRegistryTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/HostileMobRegistryTest.java) | unit/table | 40 | 5 |  | HostileMobRegistry |
| [ReactToContactTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ReactToContactTest.java) | unit/table | 46 | 3 |  | ReactToContact |
| [StrikeTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/StrikeTest.java) | unit/table | 53 | 5 |  | Strike |
| [WeaponStatsTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/world/WeaponStatsTest.java) | unit/table | 29 | 2 |  | WeaponStats |
| [CombatConditionMatrixTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/CombatConditionMatrixTest.java) | soldier sim | 149 | 1 | slow | - |
| [CombatMatrixTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/CombatMatrixTest.java) | soldier sim | 116 | 1 | slow | - |
| [CombatPairsConditionMatrixTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/CombatPairsConditionMatrixTest.java) | soldier sim | 70 | 1 | slow | - |
| [CombatPairsMatrixTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/CombatPairsMatrixTest.java) | soldier sim | 165 | 1 |  | - |
| [CombatScenarioTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/CombatScenarioTest.java) | soldier sim | 345 | 4 | slow | - |
| [CombatSquadMatrixTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/CombatSquadMatrixTest.java) | soldier sim | 203 | 1 |  | - |
| [WaterCombatScenarioTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/WaterCombatScenarioTest.java) | soldier sim | 114 | 2 |  | - |

### Trades

#### dig-build (13)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [BreacherTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/BreacherTest.java) | unit/table | 158 | 5 |  | Breacher |
| [BuilderTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/BuilderTest.java) | unit/table | 282 | 10 |  | Builder |
| [ClearerTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/ClearerTest.java) | unit/table | 304 | 8 |  | Clearer |
| [DiggerTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/DiggerTest.java) | unit/table | 443 | 18 |  | Digger |
| [LightingTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/LightingTest.java) | unit/table | 179 | 6 |  | Lighting |
| [MasonTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/MasonTest.java) | unit/table | 501 | 11 |  | Mason |
| [MineHeadTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/MineHeadTest.java) | unit/table | 54 | 2 |  | MineHead |
| [MinerTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/MinerTest.java) | unit/table | 957 | 34 |  | Miner |
| [StaircaseTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/StaircaseTest.java) | unit/table | 362 | 15 |  | Staircase |
| [BuilderSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/BuilderSimulationTest.java) | squad sim | 341 | 9 | slow | Builder |
| [CampLightSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CampLightSimulationTest.java) | squad sim | 287 | 7 | slow | - |
| [MineSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/MineSimulationTest.java) | squad sim | 554 | 17 | slow | - |
| [WallSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WallSimulationTest.java) | squad sim | 361 | 7 | slow | - |

#### craft (6)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [CraftPlanTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/craft/CraftPlanTest.java) | unit/table | 116 | 10 |  | CraftPlan |
| [LadderTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/craft/LadderTest.java) | unit/table | 210 | 16 |  | Ladder |
| [LoadPlanTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/craft/LoadPlanTest.java) | unit/table | 135 | 6 |  | LoadPlan |
| [WorkLoadTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/craft/WorkLoadTest.java) | unit/table | 49 | 3 |  | WorkLoad |
| [WorkshopTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/engineer/WorkshopTest.java) | unit/table | 249 | 11 |  | Workshop |
| [CrafterTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/craft/CrafterTest.java) | soldier sim | 286 | 15 |  | Crafter |

#### stores-supply (10)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [ArmourerTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/ArmourerTest.java) | unit/table | 121 | 3 |  | Armourer |
| [HandoverTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/HandoverTest.java) | unit/table | 169 | 6 |  | Handover |
| [MetalPoolTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/MetalPoolTest.java) | unit/table | 74 | 4 |  | MetalPool |
| [PackTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/PackTest.java) | unit/table | 262 | 10 |  | Pack |
| [QuartermasterTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/QuartermasterTest.java) | unit/table | 72 | 5 |  | Quartermaster |
| [ResourceSurveyorTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/ResourceSurveyorTest.java) | unit/table | 142 | 4 |  | ResourceSurveyor |
| [StockLevelsTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/StockLevelsTest.java) | unit/table | 93 | 6 |  | StockLevels |
| [DepositorTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/DepositorTest.java) | soldier sim | 222 | 9 |  | Depositor |
| [FitterTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/craft/FitterTest.java) | soldier sim | 89 | 4 |  | Fitter |
| [SupplyRunTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/SupplyRunTest.java) | soldier sim | 73 | 2 |  | SupplyRun |

#### food-farm (21)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [BakerDutyTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/BakerDutyTest.java) | unit/table | 47 | 3 |  | BakerDuty |
| [ChunkFarmTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/ChunkFarmTest.java) | unit/table | 226 | 8 |  | ChunkFarm |
| [FarmDutiesTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/FarmDutiesTest.java) | unit/table | 109 | 7 |  | FarmDuties |
| [FarmMissionTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/FarmMissionTest.java) | unit/table | 156 | 10 |  | FarmMission |
| [FarmPlanTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/FarmPlanTest.java) | unit/table | 141 | 6 |  | FarmPlan |
| [FarmSiteTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/FarmSiteTest.java) | unit/table | 118 | 3 |  | FarmSite |
| [FarmerFixturesTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/FarmerFixturesTest.java) | unit/table | 94 | 3 |  | ? |
| [FarmerTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/FarmerTest.java) | unit/table | 907 | 23 |  | Farmer |
| [ForageTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/ForageTest.java) | unit/table | 58 | 1 |  | Forage |
| [HunterTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/HunterTest.java) | unit/table | 164 | 7 |  | Hunter |
| [PastureChunkTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/PastureChunkTest.java) | unit/table | 104 | 2 |  | PastureChunk |
| [PlaceholderChunkTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/PlaceholderChunkTest.java) | unit/table | 35 | 1 |  | PlaceholderChunk |
| [RationsTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/RationsTest.java) | unit/table | 214 | 8 |  | Rations |
| [StoresChunkTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/StoresChunkTest.java) | unit/table | 147 | 3 |  | StoresChunk |
| [SubsistenceTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/SubsistenceTest.java) | unit/table | 82 | 7 |  | Subsistence |
| [WaterCarrierTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/WaterCarrierTest.java) | unit/table | 177 | 4 |  | WaterCarrier |
| [AnimalScoutEvalTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/AnimalScoutEvalTest.java) | sim part | 238 | 7 |  | ? |
| [FarmDutiesSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FarmDutiesSimulationTest.java) | squad sim | 279 | 7 | slow | FarmDuties |
| [FarmMissionSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FarmMissionSimulationTest.java) | squad sim | 852 | 14 | slow | FarmMission |
| [FoodSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FoodSimulationTest.java) | squad sim | 757 | 24 | slow | - |
| [PavingRateIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/PavingRateIT.java) | by-name IT | 134 | 2 | slow | ? |

#### wood (5)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [ForesterTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/supply/ForesterTest.java) | unit/table | 208 | 6 |  | Forester |
| [TreeChunkTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/TreeChunkTest.java) | unit/table | 150 | 5 |  | TreeChunk |
| [TreeFarmTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/farm/TreeFarmTest.java) | unit/table | 65 | 2 |  | TreeFarm |
| [WoodDutiesTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/WoodDutiesTest.java) | unit/table | 94 | 6 |  | WoodDuties |
| [WoodDutiesSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WoodDutiesSimulationTest.java) | squad sim | 144 | 2 | slow | WoodDuties |

### The soldier

#### soldier (13)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [ActivityTrackTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/ActivityTrackTest.java) | unit/table | 81 | 6 |  | ActivityTrack |
| [DeterministicSoldierCombatTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/DeterministicSoldierCombatTest.java) | unit/table | 979 | 34 |  | ? |
| [DeterministicSoldierTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/DeterministicSoldierTest.java) | unit/table | 841 | 39 |  | DeterministicSoldier |
| [EndermanGazeTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/EndermanGazeTest.java) | unit/table | 36 | 2 |  | EndermanGaze |
| [EndermanSelfDefenseTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/EndermanSelfDefenseTest.java) | unit/table | 72 | 2 |  | ? |
| [PillarTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/PillarTest.java) | unit/table | 110 | 3 |  | ? |
| [RecordedContactTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/RecordedContactTest.java) | unit/table | 174 | 4 |  | ? |
| [RecordedGroundEfficiencyTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/RecordedGroundEfficiencyTest.java) | unit/table | 190 | 1 |  | ? |
| [SoldiersWorkshopTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SoldiersWorkshopTest.java) | unit/table | 23 | 1 |  | - |
| [WedgedTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/WedgedTest.java) | unit/table | 60 | 3 |  | ? |
| [WideSearchBackoffTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/WideSearchBackoffTest.java) | unit/table | 58 | 1 |  | ? |
| [SoldierScenarioTest](../bot/src/test/java/io/github/jayessdeesea/minecraft/bot/scenario/SoldierScenarioTest.java) | soldier sim | 197 | 1 |  | - |
| [WorldProbeLiveIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WorldProbeLiveIT.java) | by-name IT | 259 | 1 |  | ? |

### Command

#### squad (20)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [CohesionTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/CohesionTest.java) | unit/table | 45 | 3 |  | Cohesion |
| [CrossLevelTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/CrossLevelTest.java) | unit/table | 227 | 15 |  | CrossLevel |
| [DrillSquadLeaderTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/DrillSquadLeaderTest.java) | unit/table | 143 | 6 |  | DrillSquadLeader |
| [EscortTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/EscortTest.java) | unit/table | 57 | 2 |  | Escort |
| [FormationsTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/FormationsTest.java) | unit/table | 117 | 8 |  | Formations |
| [LinkUpTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/LinkUpTest.java) | unit/table | 181 | 11 |  | LinkUp |
| [LogstatTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/LogstatTest.java) | unit/table | 27 | 1 |  | Logstat |
| [ReadinessTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ReadinessTest.java) | unit/table | 54 | 2 |  | Readiness |
| [RecoveryTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/RecoveryTest.java) | unit/table | 385 | 20 |  | Recovery |
| [ResupplyTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ResupplyTest.java) | unit/table | 50 | 3 |  | Resupply |
| [ReviewOrdersTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ReviewOrdersTest.java) | unit/table | 139 | 4 |  | ReviewOrders |
| [ShareWatchTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ShareWatchTest.java) | unit/table | 95 | 6 |  | ShareWatch |
| [TaskRegisterTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/TaskRegisterTest.java) | unit/table | 99 | 7 |  | TaskRegister |
| [TaskingTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/TaskingTest.java) | unit/table | 103 | 8 |  | Tasking |
| [WorkOrdersTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/WorkOrdersTest.java) | unit/table | 77 | 5 |  | WorkOrders |
| [DecisionPaceTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/DecisionPaceTest.java) | sim part | 611 | 15 |  | - |
| [DrillSquadLeaderTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/DrillSquadLeaderTest.java) | sim part | 44 | 1 |  | DrillSquadLeader |
| [RecoverySimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/RecoverySimulationTest.java) | squad sim | 409 | 13 | slow | Recovery |
| [SquadSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SquadSimulationTest.java) | squad sim | 844 | 22 | slow | - |
| [SquadLeaderScenarioIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SquadLeaderScenarioIT.java) | by-name IT | 139 | 1 |  | SquadLeader |

#### platoon (21)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [ArmourCheckTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ArmourCheckTest.java) | unit/table | 152 | 6 |  | ArmourCheck |
| [BasePlanTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/BasePlanTest.java) | unit/table | 98 | 4 |  | BasePlan |
| [DrillPlatoonLeaderTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/DrillPlatoonLeaderTest.java) | unit/table | 45 | 2 |  | DrillPlatoonLeader |
| [FlattenTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/FlattenTest.java) | unit/table | 119 | 8 |  | Flatten |
| [LevelPaceTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/LevelPaceTest.java) | unit/table | 65 | 4 |  | LevelPace |
| [LevelPlanTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/LevelPlanTest.java) | unit/table | 111 | 6 |  | LevelPlan |
| [MissAnswerTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/MissAnswerTest.java) | unit/table | 63 | 4 |  | MissAnswer |
| [PatrolBaseTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/PatrolBaseTest.java) | unit/table | 226 | 16 |  | PatrolBase |
| [PhaseTrackerTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/PhaseTrackerTest.java) | unit/table | 80 | 5 |  | PhaseTracker |
| [PlanTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/PlanTest.java) | unit/table | 92 | 8 |  | Plan |
| [PlatoonSergeantTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/PlatoonSergeantTest.java) | unit/table | 274 | 16 |  | PlatoonSergeant |
| [ProductionLevelsTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/ProductionLevelsTest.java) | unit/table | 52 | 5 |  | ProductionLevels |
| [RegistersSaidTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/RegistersSaidTest.java) | unit/table | 67 | 3 |  | RegistersSaid |
| [SiteTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/SiteTest.java) | unit/table | 53 | 4 |  | Site |
| [WorkAreaTest](../command/src/test/java/io/github/jayessdeesea/minecraft/command/WorkAreaTest.java) | unit/table | 54 | 4 |  | WorkArea |
| [ArmourCheckSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ArmourCheckSimulationTest.java) | squad sim | 136 | 3 | slow | ArmourCheck |
| [FlattenTopsSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FlattenTopsSimulationTest.java) | squad sim | 265 | 7 | slow | - |
| [KeepSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/KeepSimulationTest.java) | squad sim | 818 | 22 | slow | Keep |
| [ScoutMissionSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ScoutMissionSimulationTest.java) | squad sim | 128 | 2 | slow | ScoutMission |
| [TaskOrganisationSimulationTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/TaskOrganisationSimulationTest.java) | squad sim | 902 | 30 | slow | - |
| [PlatoonLeaderScenarioIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/PlatoonLeaderScenarioIT.java) | by-name IT | 462 | 1 |  | PlatoonLeader |

#### model (5)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [CommandAiConfigurationTest](../command-ai/src/test/java/io/github/jayessdeesea/minecraft/command/ai/CommandAiConfigurationTest.java) | unit/table | 34 | 2 |  | CommandAiConfiguration |
| [PlatoonLeaderChatTest](../command-ai/src/test/java/io/github/jayessdeesea/minecraft/command/ai/PlatoonLeaderChatTest.java) | unit/table | 377 | 12 |  | PlatoonLeaderChat |
| [PromptTemplateTest](../command-ai/src/test/java/io/github/jayessdeesea/minecraft/command/ai/PromptTemplateTest.java) | unit/table | 43 | 4 |  | PromptTemplate |
| [SpringAiPlatoonLeaderTest](../command-ai/src/test/java/io/github/jayessdeesea/minecraft/command/ai/SpringAiPlatoonLeaderTest.java) | unit/table | 308 | 23 |  | SpringAiPlatoonLeader |
| [SpringAiSquadLeaderTest](../command-ai/src/test/java/io/github/jayessdeesea/minecraft/command/ai/SpringAiSquadLeaderTest.java) | unit/table | 214 | 12 |  | SpringAiSquadLeader |

### Garrison shell

#### garrison-shell (68)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [AfterActionReviewTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/AfterActionReviewTest.java) | unit/table | 76 | 2 |  | AfterActionReview |
| [BedStringTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/BedStringTest.java) | unit/table | 41 | 3 |  | ? |
| [BodyInTheWayTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/BodyInTheWayTest.java) | unit/table | 28 | 2 |  | ? |
| [BuiltReviewsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/BuiltReviewsTest.java) | unit/table | 63 | 2 |  | BuiltReviews |
| [BurnStoreTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/BurnStoreTest.java) | unit/table | 33 | 1 |  | BurnStore |
| [CampFarmTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CampFarmTest.java) | unit/table | 66 | 1 |  | CampFarm |
| [CampGroundTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CampGroundTest.java) | unit/table | 67 | 4 |  | CampGround |
| [CampMineSitesTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CampMineSitesTest.java) | unit/table | 109 | 7 |  | CampMineSites |
| [CampTreesTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CampTreesTest.java) | unit/table | 61 | 1 |  | CampTrees |
| [ChainOfCommandTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ChainOfCommandTest.java) | unit/table | 714 | 25 |  | ChainOfCommand |
| [ChestStandTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ChestStandTest.java) | unit/table | 126 | 7 |  | ChestStand |
| [ChunkLedgerTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ChunkLedgerTest.java) | unit/table | 66 | 2 |  | ChunkLedger |
| [CommandScorecardTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CommandScorecardTest.java) | unit/table | 55 | 3 |  | CommandScorecard |
| [CrowdWatchTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CrowdWatchTest.java) | unit/table | 45 | 2 |  | CrowdWatch |
| [CutFirstTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CutFirstTest.java) | unit/table | 24 | 1 |  | CutFirst |
| [CutOffTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/CutOffTest.java) | unit/table | 53 | 3 |  | CutOff |
| [DangerAreaBookTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/DangerAreaBookTest.java) | unit/table | 30 | 1 |  | DangerAreaBook |
| [DutyBookTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/DutyBookTest.java) | unit/table | 248 | 8 |  | DutyBook |
| [ErrandLedgerTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ErrandLedgerTest.java) | unit/table | 132 | 6 |  | ErrandLedger |
| [FarmMissionsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FarmMissionsTest.java) | unit/table | 873 | 22 |  | FarmMissions |
| [FaultsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FaultsTest.java) | unit/table | 57 | 3 |  | Faults |
| [FlattenWorkTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FlattenWorkTest.java) | unit/table | 153 | 9 |  | FlattenWork |
| [FlattenWorksTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FlattenWorksTest.java) | unit/table | 47 | 2 |  | FlattenWorks |
| [FlightRecorderTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FlightRecorderTest.java) | unit/table | 51 | 3 |  | FlightRecorder |
| [FoodLedgerTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/FoodLedgerTest.java) | unit/table | 100 | 5 |  | FoodLedger |
| [IdleWatchTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/IdleWatchTest.java) | unit/table | 56 | 4 |  | IdleWatch |
| [KeptGroundTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/KeptGroundTest.java) | unit/table | 248 | 13 |  | KeptGround |
| [LadderOrdersTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/LadderOrdersTest.java) | unit/table | 49 | 3 |  | LadderOrders |
| [LedgerTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/LedgerTest.java) | unit/table | 48 | 3 |  | Ledger |
| [LoopReportsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/LoopReportsTest.java) | unit/table | 50 | 2 |  | LoopReports |
| [MineDangerTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/MineDangerTest.java) | unit/table | 50 | 2 |  | MineDanger |
| [ModelCallsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ModelCallsTest.java) | unit/table | 30 | 1 |  | ModelCalls |
| [NearestInBoxTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/NearestInBoxTest.java) | unit/table | 29 | 2 |  | ? |
| [ObjectiveBookTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ObjectiveBookTest.java) | unit/table | 149 | 4 |  | ObjectiveBook |
| [OperatorChatTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/OperatorChatTest.java) | unit/table | 335 | 8 |  | OperatorChat |
| [OperatorOrdersTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/OperatorOrdersTest.java) | unit/table | 76 | 7 |  | OperatorOrders |
| [OrderTraceTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/OrderTraceTest.java) | unit/table | 70 | 3 |  | OrderTrace |
| [OverworldClockTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/OverworldClockTest.java) | unit/table | 49 | 3 |  | OverworldClock |
| [PaceTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/PaceTest.java) | unit/table | 203 | 11 |  | Pace |
| [PhaseGuardrailsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/PhaseGuardrailsTest.java) | unit/table | 64 | 3 |  | PhaseGuardrails |
| [PlacementAuditTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/PlacementAuditTest.java) | unit/table | 88 | 3 |  | PlacementAudit |
| [PlanBookTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/PlanBookTest.java) | unit/table | 55 | 3 |  | PlanBook |
| [PoliceCallTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/PoliceCallTest.java) | unit/table | 154 | 6 |  | PoliceCall |
| [RegistersTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/RegistersTest.java) | unit/table | 83 | 4 |  | Registers |
| [RequestLedgerTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/RequestLedgerTest.java) | unit/table | 51 | 3 |  | RequestLedger |
| [RosterTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/RosterTest.java) | unit/table | 26 | 2 |  | Roster |
| [ScoreboardTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ScoreboardTest.java) | unit/table | 66 | 4 |  | Scoreboard |
| [SiteGeometryTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SiteGeometryTest.java) | unit/table | 29 | 2 |  | SiteGeometry |
| [SoldierErrandsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SoldierErrandsTest.java) | unit/table | 111 | 6 |  | SoldierErrands |
| [SquadCommandTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SquadCommandTest.java) | unit/table | 56 | 2 |  | SquadCommand |
| [SquadStateTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SquadStateTest.java) | unit/table | 60 | 2 |  | SquadState |
| [StockpileDrawTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/StockpileDrawTest.java) | unit/table | 67 | 5 |  | ? |
| [StockpileWatchTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/StockpileWatchTest.java) | unit/table | 58 | 3 |  | StockpileWatch |
| [StoodStillTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/StoodStillTest.java) | unit/table | 45 | 2 |  | StoodStill |
| [StoresBookTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/StoresBookTest.java) | unit/table | 54 | 3 |  | StoresBook |
| [SupplyTripsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SupplyTripsTest.java) | unit/table | 114 | 10 |  | SupplyTrips |
| [SurfaceCacheTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SurfaceCacheTest.java) | unit/table | 87 | 2 |  | SurfaceCache |
| [TemplateGroundTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/TemplateGroundTest.java) | unit/table | 372 | 12 |  | TemplateGround |
| [TreeFellingTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/TreeFellingTest.java) | unit/table | 119 | 7 |  | TreeFelling |
| [WallStoreTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WallStoreTest.java) | unit/table | 59 | 4 |  | WallStore |
| [WantsFoodTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WantsFoodTest.java) | unit/table | 29 | 2 |  | ? |
| [WoodSightingsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WoodSightingsTest.java) | unit/table | 42 | 2 |  | WoodSightings |
| [WoodsBookTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WoodsBookTest.java) | unit/table | 59 | 1 |  | WoodsBook |
| [WorldRecordsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/WorldRecordsTest.java) | sim part | 84 | 3 |  | WorldRecords |
| [StuckEvalsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/StuckEvalsTest.java) | stuck replay | 85 | 1 | slow | ? |
| [ChainScenarioLiveIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ChainScenarioLiveIT.java) | by-name IT | 444 | 1 |  | ? |
| [OperatorChatScenarioIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/OperatorChatScenarioIT.java) | by-name IT | 170 | 1 |  | OperatorChat |
| [StuckTriageIT](../app/src/test/java/io/github/jayessdeesea/minecraft/app/StuckTriageIT.java) | by-name IT | 96 | 0 |  | ? |

### Landing queue

#### landing (30)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [Amendment2Test](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/Amendment2Test.java) | unit/table | 489 | 22 | slow | - |
| [Amendment3ReviewTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/Amendment3ReviewTest.java) | unit/table | 112 | 6 |  | - |
| [AuditTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/AuditTest.java) | unit/table | 343 | 14 |  | Audit |
| [CrashTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/CrashTest.java) | unit/table | 62 | 1 | slow | - |
| [EntriesTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/EntriesTest.java) | unit/table | 132 | 3 |  | Entries |
| [ExitCodeTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/ExitCodeTest.java) | unit/table | 87 | 4 |  | ExitCode |
| [FaultClassTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/FaultClassTest.java) | unit/table | 149 | 5 | slow | - |
| [InstallerTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/InstallerTest.java) | unit/table | 241 | 11 |  | Installer |
| [IntegratorTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/IntegratorTest.java) | unit/table | 363 | 6 |  | Integrator |
| [InterleavingTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/InterleavingTest.java) | unit/table | 117 | 1 | slow | - |
| [JobTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/JobTest.java) | unit/table | 104 | 4 |  | Job |
| [JournalCodecTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/JournalCodecTest.java) | unit/table | 141 | 3 |  | JournalCodec |
| [MainReadsTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/MainReadsTest.java) | unit/table | 53 | 4 |  | MainReads |
| [MainRefReadsTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/MainRefReadsTest.java) | unit/table | 70 | 2 |  | - |
| [MavenProgressTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/MavenProgressTest.java) | unit/table | 120 | 9 |  | MavenProgress |
| [MergeOutcomeTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/MergeOutcomeTest.java) | unit/table | 63 | 1 |  | MergeOutcome |
| [ProgramsTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/ProgramsTest.java) | unit/table | 48 | 4 |  | Programs |
| [PushOutcomeTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/PushOutcomeTest.java) | unit/table | 86 | 2 |  | PushOutcome |
| [QueueTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/QueueTest.java) | unit/table | 595 | 25 | slow | - |
| [ReviewRowsTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/ReviewRowsTest.java) | unit/table | 302 | 14 | slow | - |
| [ScriptsTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/ScriptsTest.java) | unit/table | 394 | 18 |  | - |
| [ShellPartsTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/shell/ShellPartsTest.java) | unit/table | 521 | 26 |  | - |
| [StaleTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/StaleTest.java) | unit/table | 107 | 4 |  | Stale |
| [ValuesTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/ValuesTest.java) | unit/table | 122 | 7 |  | - |
| [VerifyEstimateTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/VerifyEstimateTest.java) | unit/table | 186 | 13 |  | VerifyEstimate |
| [VerifyHistoryTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/VerifyHistoryTest.java) | unit/table | 98 | 7 |  | VerifyHistory |
| [VerifyOutcomeTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/VerifyOutcomeTest.java) | unit/table | 114 | 3 |  | VerifyOutcome |
| [VerifyPassTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/VerifyPassTest.java) | unit/table | 178 | 9 |  | VerifyPass |
| [VerifyWatchTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/VerifyWatchTest.java) | unit/table | 151 | 7 |  | VerifyWatch |
| [WaitingTest](../landing/src/test/java/io/github/jayessdeesea/minecraft/landing/WaitingTest.java) | unit/table | 238 | 16 |  | Waiting |

### Test harness

#### simulator (4)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [SimChunksTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SimChunksTest.java) | unit/table | 34 | 2 |  | - |
| [SimAnimalsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SimAnimalsTest.java) | sim part | 96 | 4 |  | - |
| [SimBucketsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SimBucketsTest.java) | sim part | 46 | 1 |  | - |
| [SimItemsTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/SimItemsTest.java) | sim part | 20 | 1 |  | - |

#### suite (1)

| Test | Kind | Lines | Tests | Slow | Subject |
|---|---|---|---|---|---|
| [ArchitectureTest](../app/src/test/java/io/github/jayessdeesea/minecraft/app/ArchitectureTest.java) | architecture | 321 | 13 |  | - |

### Test support

`Battle`, `Body`, `FakeChatModels`, `FakeChestServer`, `FakeCraftingServer`, `Fixtures`, `JobHolder`, `KeptStairs`, `LockHolder`, `ProcessTree`, `Prompts`, `PureCoreFixtures`, `Scenario`, `ScratchRepo`, `SimAnimals`, `SimBuckets`, `SimChunks`, `SimClock`, `SimItems`, `SimServer`, `SimSquad`, `SimWorlds`, `SquadLeaderScenarios`, `SyntheticWorld`, `Terrain`, `TestClock`
<!-- /generated:appendix -->
