# Military doctrine this project emulates

The chain of command here (platoon leader → squad leader → soldier) deliberately borrows real military
doctrine for *how* echelons talk to each other, not just what they're called. The point is to see
where a doctrine refined over a century for humans under stress - limited bandwidth, partial
information, subordinates who must act without asking - overlaps with what works for LLM agents
at different timescales. When code implements one of these ideas, it cites the section here, and
this doc cites the manual. What each echelon is told, reports and can do - its contract - is in
[echelons/](echelons/README.md); this doc holds the doctrine and how it's carried out.

**Source confidence.** Formation text below is quoted from the 2024 ATP 3-21.8 as reproduced by
InfantryDrills.com, with paragraph numbers. Proword and report definitions come from secondary
sources consistent with each other (USMC radio training material, ACP 125 summaries, archived
FM 34-35); the primary manuals (ACP 125(G), ATP 6-02.53, ATP 1-02.1, FM 6-99) weren't readable
from here. Treat those entries as "well-attested, not verified against the primary text."

## 1. Mission command: intent over instructions

**ADP 6-0, *Mission Command*.** Commanders direct through *mission orders* that state the task and
the **commander's intent** - the purpose and desired end state - and leave the *how* to
subordinates, who exercise **disciplined initiative**: acting without further orders when the
situation changes, as long as they stay inside the intent.

*How we use it:* every order down the chain carries a short `intent` (why), not just a target
(what). That's what lets a soldier who fell into a hole, or a squad that hits a cliff, do something
sensible without waiting for an LLM round trip - and it's the same reason a good prompt states the
goal, not only the steps.

## 2. Formations and intervals

**ATP 3-21.8, *Infantry Rifle Platoon and Squad* (2024), Chapter 3, Section 2.**

General principles:
- ¶3-44: "Soldiers must know their location in the formation relative to the other fire team
  members and team leader."
- ¶3-45: "As a rule, the unit should be dispersed up to the limit of control." "The team leader
  adjusts the team's formation as necessary while the team is moving."
- ¶3-47: "fire team leaders are in the lead position with their Soldiers echeloned to the right and
  left behind them." "The positions for all but the leader may vary."

The formations we implement:

| Formation | Doctrine | When |
|---|---|---|
| **Wedge** | ¶3-46: the basic fire team formation; "The interval between Soldiers in the wedge formation is normally 10 meters." "The wedge expands and contracts depending on the terrain." "The sides of the wedge can contract to the point where the wedge resembles a single file." | Default for moving. |
| **File** (column/file) | ¶3-48: used "when employing the wedge is impractical" - "severely restrictive terrain, when inside a building, in dense vegetation." Spacing "changes due to constraints of the situation." | Forest, tunnels, buildings, narrow paths. |
| **Line** | ¶3-54 (squad line): "provides maximum firepower to the front"; "used to assault or as a pre-assault formation." | Approaching a known threat. |

*How we use it:* the squad leader picks the **formation** and an **interval** (tight, normal,
extended). Each soldier's slot is a fixed offset from the **point man** - the lead soldier, who
heads for the objective, the first not detached to the farm or the wood party - in the direction of travel (¶3-47: leader in front, others echeloned
behind). The slot is a *zone*, not a point: the soldier routes into it however the ground allows
(¶3-46 "expands and contracts depending on the terrain", ¶3-47 "positions for all but the leader
may vary"). Blocks are metres, so doctrine's 10 m wedge interval is our *extended* interval; normal
and tight are closer because Minecraft visibility and mob density make 10 blocks loose for a
fire team of two or three.

The rest of chapter 3 (squad column, movement techniques) is in backlog/wider-doctrine.md.

## 3. Acknowledging orders: prowords

Radiotelephone procedure (ACP 125; Army: ATP 6-02.53) uses *prowords* with exact meanings so a
garbled channel can't create ambiguity:

| Proword | Meaning |
|---|---|
| **ROGER** | "I have received your last transmission satisfactorily." *Not* agreement, *not* compliance. |
| **WILCO** | "I have received your message, understand it, and will comply." Includes ROGER; never sent together. |
| **CANTCO** | "Unable to comply" (multi-service brevity, ATP 1-02.1). Aviation's equivalent is **UNABLE**, "normally followed by a reason." |
| **SAY AGAIN** | "Repeat all of your last transmission" - it wasn't understood. |

*How we use it:* a subordinate answers an order with **WILCO** or **CANTCO + reason**. CANTCO is
the push-back channel: the squad leader orders a spot that can't be reached and the squad *says
so*, rather than silently stalling. A soldier that can't reach its slot (fell into a pit, cut off
by water) reports CANTCO with the reason; the squad leader decides what to do about it - the same
loop a human squad runs. At the top of the chain, the platoon leader answers a new mission from the human
operator with WILCO on the radio net, and an order it doesn't recognise with SAY AGAIN.

An order is acknowledged at every echelon it passes, and the last acknowledgement is the soldiers
doing it: each operator order is traced (`app.OrderTrace`) from the platoon leader's decision through
each squad leader's FRAGO to a SITREP showing the squad's soldiers at the task - Fortifying for
FORTIFY, Securing for SECURE, and so on - each hop timed, and each one late raising an alarm.

A soldier is always meant to be at something, by night too (the mine), so one that hasn't moved 2
blocks any way in a minute, other than while its squad hides or sleeps - in its patrol base, or
down its mine for the night - or holds where it was sent and got to, a rifleman on its squad
leader's point or any soldier reporting itself in position, as a builder waiting where it stands
for the next rectangle does (within the order's tolerances: live, one sent 60 blocks off and stood
waist-deep in gravel was read as holding there for a day) - raises
an alert in the log, once a spell (`[Alert]`, `app.IdleWatch`): its report, its order, which
routine has its hands, and its squad's task. It's logged again when it moves on. A soldier held at
a table or furnace shows its routine; one with free hands and a task its routines can't start on is
the chain of command's gap; one jumping in place is stuck. Beside it, the space bubble (operator,
2026-11-15): one within a body's width (0.6 blocks) of another for 10 seconds, moving or not,
outside a rest (`app.CrowdWatch`), is alerted too, with
what it's at, and logged again when it has room: two soldiers in one cell that long are working one
job, or walking into each other.

## 4. Reports upward

| Report | Content | Source |
|---|---|---|
| **SITREP** (situation report) | Where we are, what we're doing, our condition, what we can't do. Routine and on request. | FM 6-99, *U.S. Army Report and Message Formats* |
| **SPOTREP** using **SALUTE** | Enemy contact: **S**ize, **A**ctivity, **L**ocation, **U**nit, **T**ime, **E**quipment. | FM 6-99; FM 34-35 App. C |
| **LACE** | A unit's own condition after contact or on request: **L**iquids (water, fuel), **A**mmunition, **C**asualties, **E**quipment. | Small-unit convention during consolidation and reorganization; not confirmed in a primary manual |
| **LOGSTAT** (logistics status) | What a unit holds and needs, by class of supply: Class I subsistence, Class II clothing and individual equipment, Class V ammunition, Class VIII medical. | FM 4-0, *Sustainment Operations* |

*How we use it:* each soldier's status is a SITREP (position, task, condition, and a CANTCO if
any); contact with hostiles adds a SALUTE-shaped spot report. The condition is LACE-shaped
(`command.Condition`): liquids is food, casualties is health and deaths, ammunition is the weapon
in hand and arrows, equipment is armour worn (`command.Kit`). A soldier eats from its rations when
it's out of contact and nothing else has its hands (`bot.supply.Rations`): at food 15 or below,
where a loaf of five fits under the bar with nothing lost, or - hurt - below 18, the level health
regenerates at. The food goes into a hotbar slot without a weapon or tool, the server times the
meal, and the weapon comes back to hand. Rations are the food items it carries, less
those that poison or are too precious to eat (`tools/item-class-generator`).

*Requests up the chain, timed* (`app.RequestLedger`). A soldier asks its squad leader for help by
reporting CANTCO with its reason; a squad asks the platoon leader by its task's progress (DONE or
STALLED) and by its Class I RED. There's no queue to wait in: each SITREP carries them, every
second to the squad leader's drills and every squad-leader cycle, and to the platoon leader,
which the request wakes, at most a minute after its last decision. Each request is
logged when raised (`[Request] Bob of Alpha -> squad leader: CANTCO: ...`). It's answered when the
echelon it went to changes the order it was about: the soldier's to another kind, or to a place
more than 4 blocks off (a formation's places shift a block or two every cycle, which answers
nothing); the squad's objective, for a task; and only a `FARM` objective for a request for food -
another order is logged as not answering it, and the request stays open. In the platoon leader's prompt
a RED request heads the squad's section.
The answer is logged with how long it took. One settles by itself when what raised it clears
first. One still open past its echelon's deadline - 60 seconds for a squad leader, 5 minutes for
the platoon leader - raises `[Request] ALARM`, once. The dawn review gives each echelon's answers, their
median and longest time, the alarms, and what's still open.

*Supervise and refine: the squad's task register* (`command.TaskRegister`; ATP 3-21.8, the last
step of troop leading procedures). Each squad keeps who is on what. Every cycle each soldier's
tasking - gather SOIL for the ground work, fill the camp's ground, dig the camp's ground, wall the
camp, wall the camp in stone, light the ground, cut the mine, keep the farm, plant the tree farm,
gather a resource, patrol (in that order of precedence; lighting only for a soldier with torches
carried or being made) - is recorded with its report and what's left of the task as the world
measures it (cells to dig or fill, wall blocks, torches). The report is
trusted and verified: a soldier reporting work while what's left hasn't fallen in 2 minutes is
logged as `[Verify] ... hasn't fallen` and its tasking marked STALLED, until progress shows again.
A CANTCO is held open on its tasking. A tasking ends done when what's left reaches 0 - or when its
soldier moves off a task that now measures 0, as when the last log falls - or is counted as taken
off unfinished. Ended taskings are counted in the dawn review and forgotten; what's still open is
listed. A STALLED finding is acted on: the squad takes the soldier off that task (digging,
filling, walling or lighting) for its next work, and puts it back after 5 minutes - twice as long
each time it stalls there again, up to 40 - so a soldier at a tree too tall to fell walls instead
of standing at it.

*Class I, by echelon* (`command.Subsistence`; FM 4-0, ATP 4-41 field feeding). Each soldier
carries a **basic load** of 64 rations - a stack. Below 32 it restocks when that's convenient
and asks its squadmates for some, and below 10 it is short, and food comes before its work:
1. **The soldier feeds itself.** Hungry, it eats. Below its basic load it bakes all the wheat it
   carries into bread at its workshop (§11). Below 32, within 16 blocks of a stockpile last seen
   holding rations, it draws some out until it carries 32 (`SoldierRunner.drawsFood`); short, it
   draws from it wherever it is, and hunts any game in sight, fed or not. Bread beyond its basic
   load - a whole stack, the basic load still in its pack - goes into the stockpile (§12).
2. **The squad shares, and keeps the camp's farm.** A soldier below 32, hungry yet or not, is given
   rations by the nearest squadmate who keeps 15 (cross-leveling, §13) - one at a time, the one
   carrying fewest first, and no more often than every four seconds from one giver to one receiver,
   so a giver's spare goes round everyone short rather than to the first (the operator's). A
   squadmate below 32 itself still gives, to one it's left with more than; below 15 it has none to
   spare. One no squadmate can feed is given them across squads, by the nearest soldier of another
   squad within 32 blocks with some to spare and no hand-over of its own (`CrossLevel.across`). One starving at death's door - food
   0, health 6 or less - gets a meal from the nearest squadmate carrying any, its last or not,
   before anything else is handed across: buddy aid. Live, a soldier stood at a third of a heart
   beside a squadmate with one ration, kept as its own. And while the squad is RED, or a soldier
   of it is hungry with nothing to eat (`Subsistence.mustFeed`), every other soldier of it keeps
   the camp's farm by day, whatever its task - harvesting what's ripe, sowing and tilling (§12) -
   as feeding is a unit's first priority of work, not only the platoon leader's to order; while it's
   merely AMBER, short of rations but fed, one soldier keeps the farm and the rest work - live,
   two squads AMBER on rations alone had half of each at the farm and the keep stood. The force's
   rations, counted in days (§ 17), send farmers too: one a squad while they're under their reorder
   point, half of it under their safety level, however its own squad is fed (`PlatoonSergeant.farmers`). Neither while
   the squad carries ten rations a head between them (`Subsistence.canShare`): then the short one is
   fed by a hand-over of seconds, and the farm is a walk of minutes - live, a builder with an empty
   pack walked 80 blocks to the farm, baked four loaves for itself and walked 80 back while three
   squadmates carried plenty. A farmer sent stays four minutes whatever the rating does meanwhile,
   since the walk is the cost: that one was taken off the farm 24 seconds after arriving. With a
   farm squad named (operator, 2026-11-15: a dedicated squad for the farming; `minecraft.farm-squad`),
   all of the above gives way to it: that squad keeps the camp's farm by day as its standing job, every
   soldier of it, whatever it's ordered, and no other squad farms but under a FARM order - a hungry
   soldier elsewhere is fed from the stockpile or a squadmate's pack. Live, half of each hungry squad
   on the keep went to the farm by the rule above, and the keep's crew was a third off its ground. The camp's farm is sited once, on ground by water near the camp that
   the squad can walk to - a flood from the camp's ground, as a FARM order's plot is from the squad -
   and kept while its water is there - on disk too, under `minecraft.state-dir` as the wall is
   (`app.WallStore`), since sited again after a restart it moved to another pond. Once a run it's
   grown toward 192 cells with the plots round the waters within 16 blocks of its own
   (`FarmPlan.grown`): one water's plot is 80 cells at best and about half that by a pond. Sized
   to build the basic load, not only to feed: live, 97 cells made about 20 loaves an hour, as many
   as eight soldiers ate, and none carried more than a few rations.
3. **The squad asks.** A soldier still short with none of those means - no wheat, no game in sight,
   no rations in the stockpile, nobody sharing, and no wheat growing in the camp's farm, whose bread is on the way - makes the
   squad's Class I **RED**. Its LOGSTAT then carries the request: "Class
   I RED - request: ... The squad can't feed itself."
4. **The platoon leader answers.** Feeding a force is planned above the squad, as field feeding is above
   the company. Its answer to RED is a `FARM` task at ground by water, which harvests what's ripe
   and bakes it, or an objective where game is.

Class IV, construction materials, goes up the same way (`PlatoonSergeant.classFour`, operator 2026-11-13:
with no resources, the workers ask for supplies). The keep's builders need logs - two for the
chest at the depot, one each for a pickaxe or four torches, and stone dug by hand drops nothing to
lay the slab from - and get their own where a tree is in reach; with fewer than 16 between them and
the stockpile, the squad's LOGSTAT carries "Class IV RED - request: the keep's builders carry N
logs between them and the stockpile holds M: no chest at the keep's depot, no pickaxe or torch to
make. Request logs - send a squad for WOOD where trees are." The platoon leader answers it with a `WOOD`
task at trees a squad has reported, after food and before the building the logs are for. Live, the
builders worked a day with no tree within 48 blocks, no chest and no pickaxe, and nobody was asked.
The depot's workshop - the keep's table, furnace and chest, one for every squad on it - is sited on
the depot's surface, from high over any hill there down (`TerrainSurveyor.surface`), not at the first
cell with ground under it near the slab's height: under a hill that's a cave's or a tunnel's floor,
and live it was a one-wide tunnel into the hill west of the keep, with nowhere beside a stand for a
table, said every second for hours while the builders walked 50 blocks to a stray table for bread.

A site longer than 48 blocks has more than one depot (operator, 2026-11-15): one every 48 along
its west edge, evenly spread (`Flatten.depots`) - three for x=-16..16 z=-64..64, at z=-43, 0 and 43
- the middle one the main depot, the others outposts, each a hasty workshop with its own row of
chests. A builder's load goes into the depot nearest it, and its chest is fitted there; draws -
food, logs, tools - are from the main depot, whose chests hold the stores.
No load is taken to a chest known full (live, with the depots' rows full of cobblestone, the crew's
ticks were 38% failed trips) - known across restarts, kept on disk (`chests/full`), and no longer
full once a load goes into it (live, after every restart the crew found each full chest again by a
failed trip, a third of its ticks).

**The stockpile's levels** (`bot.supply.StockLevels`; operator, 2026-11-17: nothing is garbage, but
the packs and chests need room for what's worth more). Each material has a reserve, held as rations
and ammunition are, and a most, past which none is hoarded. Under its reserve, a soldier carrying
16 or more past its pack's keep - or enough to fill it - makes a trip of its own. Between the two,
it rides along on a trip made anyway. At its most, it's what a full pack throws away. Work draws on
it down through the reserve, which is refilled once there's a surplus again. The surface chests'
counts are read each time a load goes in, or a chest is opened and found full.

| Material | Reserve | Most | Past the most |
|---|---|---|---|
| Diamonds, raw iron, iron ingots | - | none | never shed; the depot's row grows for them |
| Logs | 64 | 256 | never shed; the keep's wood party works to these, and under the reserve a released squad is sent for wood; to the most, one with nothing else to do, before bread |
| Bread | 64 | 1,024 | under the reserve a released squad farms; to the most, one with nothing else to do; past it, it stays in the packs - each soldier carries up to 128, half of it to share |
| Wheat | 64 | 256 | past a farmer's 64 goes in |
| Raw copper, copper ingots | 64 | 256 | raw copper left out of the metal pool, thrown last by a full pack |
| Saplings, seeds, string, flint | 64 | 256 | thrown by a full pack |
| Fences | 0 | 64 | thrown by a full pack |
| Crafting tables | 1 | 4 | thrown by a full pack |
| Dirt, cobblestone | - | 256 | left at the dump: so common they're all but had on demand |

Cobblestone or dirt past its most - or either once every chest a builder could go to is full - is left at
its depot's dump, not carried in: ground five blocks west of the stand, off the site and clear of
the chests. A builder throws down every stack of it there but one of each kind, kept for steps,
and the scavengers leave what lies within four blocks of a dump to despawn - a soldier picks up
what it walks over.
Every builder on the flatten's crew stocks and draws at the depot while a chest of it stands,
whatever its own squad's task - live, the builders of squads sent to it from the mine carried 53 of
92 loads to the camp's chest 70 blocks off. The depot is a row of chests, not one (operator, 2026-11-14): as many cells as fit round the
workshop's stand, each within reach of it and a step up or down a slope from it at most, open
above so its lid lifts, none beside the stand or beside another chest - two
side by side would join into one double chest (`Workshop.chestCells`). They're filled in turn: a
keep builder's load that won't go into the chest it reached and opened marks that chest full
(`Depositor.full`), and the depot's chest becomes the next cell, fitted by the first builder there
with the logs for it or a full pack. Every workshop's chest - the camp's stockpile, a squad's
own - grows the same way, along its row: live, the camp's filled, and five soldiers of two squads
carried loads to it and back; then the mine head's, and its miner did the same. A keep builder's pack is a full load with four slots still
free (`SoldierRunner.KEEP_FULL_AT`), not two: a tool made at a table passes through planks, sticks
and the table itself, a slot each, so a pack kept open is one that can still make things; with a
full load it unloads before it makes the equipment ladder's next rung. While it's on its way to
the workshop - to make kit, or to fit the chest - it takes no new block of the dig: neither walk
holds its hands, so each block taken dropped the errand. Live, the one chest filled with the first
builder's load (27 stacks against 13 of cobblestone in a pack) and every builder after it walked
there, failed and walked back.

Each level of the keep's dig is timed against the fastest it could have gone (operator, 2026-11-14:
predict the theoretical maximum from the route, the tool's break time and the travel; below 90%,
dig in and find out why; `command.LevelPace`). Every dig a builder finishes records the ticks its
parts took - the walk to its stand, the tool taken in hand, the break, the server's word the block
is gone, the pickup of its drop - against its best: the block's break time as vanilla gives it with
the best tool the builder carries, and the straight walk to its stand at walking speed. A level's
best is all its digs' best shared evenly over its crew - the theoretical maximum; the slowest
builder's own best, the level as it was split, is shown beside it, so an uneven split shows as a
loss. Every tick of every builder on the keep is put down to what held it - the routine holding its
hands (the clearer, the depositor, the armourer at a table, rations), or with none, why it has no
rectangle: the wood party, the haul road, off the crew, done and waiting for the level, or
waiting - on the crew with nothing given - and with work left and its clearer idle, why
(`Clearer.idleReason`: nothing in reach, no blocks to climb on, a cut failed, no tool) - so the
gap to the best is accounted for. When the level falls, the log says what it took against
its best and its split's - the nights left out, its clock and the tally stopped while it's dark - where the crew's ticks went ("waiting, no rectangle 40%, clearer 35%,
..."), each builder's ticks taken against best; then the dig so far, level on level, the same way:
that line is the one that says whether the method is within 90% of its best. Below 90% either line
is a warning, "BELOW 90%".

The keep's smelter (the ore's chain of hands, backlog/common-stockpile.md): while the surface
chests hold 8 raw ore or more, one builder with no rectangle - the nearest the main depot - is off
the dig to smelt it ("[Keep] Carol is the smelter: 22 raw ore in the chests"). It draws a stack of
one metal's raw ore from the nearest chest holding it, smelts all it carries at the main depot's
furnace, and its ingots go in as anyone's do (`MetalPool`), for whoever's next piece they complete;
back on the dig once there's none left in the chests or its pack. Between batches it empties the
force's furnaces - the depots', the camp's, the squads' workshops', any a soldier has used, and any
standing within 32 blocks of the main depot whoever put it there, each every 10 minutes at most - of what's in their
output and input (`Depositor.tickEmpty`): a smelt left by a soldier gone off goes in the chests,
not stands there (live, 4 copper ingots stood in the camp's furnace and 4 in an outpost's). Armour then needs a table and a
chest's ingots, not a furnace and fuel where each soldier stands: live (field302), the farm squad
walked between a table with no furnace by it, a furnace 22 blocks off and a table 50 off, for copper
leggings, with 74 raw copper in chests.

The keep's wood party - the platoon sergeant's, `command.PlatoonSergeant` (operator, 2026-11-14: trees are everywhere - break from the dig, harvest
some, build crates at the depot and replant; a spruce every four blocks; the lumberjacks use axes)
is the foreman's errand in place of a rectangle, two builders at most and a third of the crew,
never the foreman. One
plants the keep's grove while it has a cell with nothing growing, with a sapling carried or trees
seen. Between them the builders and the stockpile hold logs against the logs' levels: under Class
IV's reorder point (below) - never under the reserve, 64 - the whole party cuts; from there to the
stockage objective - never under the most, 256 - one builder stays on it, a producer kept at work
so the reserve is refilled before it's an emergency (operator, 2026-11-17: an emergency is a signal
to change; live, the party went out at 3 logs and was one builder); at the most none does. The
party cuts where a keep squad last saw trees, 16 logs a builder - each tree it starts felled whole,
the load or not, its top logs from its own steps, and a sapling planted where it stood (`bot.supply.
Gatherer`; operator, 2026-11-17: get all the wood, so the trees fall, so you get saplings and
replant - live, cutters took what was in reach and walked on, and nothing was replanted) - back with
them it's on the dig again and another goes - after
making itself a stone axe from the cobblestone it carries, a log in 15 ticks rather than the hand's
60. The rest dig. The foreman says it on the net: "KEEP WOOD 16", "KEEP PLANT x z". The grove is a
tree farm (§ 12) sited once with the depot: of the ground in rings round the depot's stand that a
soldier can walk to from it, and no more than 4 blocks over its height, any depth under it - with
none, the wood party clears the nearest such ground whose trees keep it from being one, felling
them whole, and the grove is sought again every two minutes until it's sited (operator,
2026-11-17: we can clear an area for a grove as well) -
live (field350-364), with level ground only 6 under the depot's stand the keep had no grove at all,
and nothing was replanted. Live, a grove on a hilltop past the
site's bench had its planter put a pillar up the bench's outer row from the level to reach it, each
step the site's highest ground - clear of
the slab and its borrow pits, the depot, the mine's head, the farm and the camp, with room for 16
saplings or more, the flattest, then the nearest sea level, then the nearest. Live, eight builders
with a chest's worth of logs between them stood the depot at two chests, both full.

GREEN is everyone carrying food, and AMBER is some short but each with a means. The rating is
logged when it changes.

A soldier short of rations hunts at food 14 or below, or wounded and below food 18, where it would
heal, and at any food with game in sight: by day, out of contact and not walling in a patrol base, it
chases the nearest cow, pig, sheep, chicken or rabbit within 48 blocks of its squad's objective -
not of itself, or one hunt after another carries it off; 96 once it's starving, at the food 6 it
can no longer sprint at, since the ground round a camp is hunted out first - and within 4 blocks
up or down of it, as an animal in a cave below is near only as the crow flies. One animal is one
hunter's: the force's hunters share their claims, and the next goes to the next animal (live, eight
chased one twelve blocks down a cave and built steps toward it), and its walk to the animal needn't
match the animal's height. It strikes it each
time its weapon is charged and stands where it fell for the meat (`bot.supply.Hunter`); one it
gives up on after a minute's chase it leaves for two. Food comes first:
a soldier below food 18 with a ration in its pack, or wanting food with game in sight, puts down
its day's work - gathering, lighting, walling, farming, planting - at its next step, and eats or
hunts. Raw meat is a ration
like any other. It reports `Hunting`. Squad leaders aggregate their
soldiers' SITREPs into one for the platoon leader, with a LOGSTAT summed from the soldiers' LACE
(`command.Logstat`): hungry soldiers and the rations they carry (Class I), empty armour slots (Class II), bare-handed
soldiers and arrows (Class V), and casualties. These are the structured inputs the LLM-backed
echelons read - the same economy of attention doctrine optimizes for.

*Resupply:* the operator can set up a supply point - a chest at `minecraft.supply.point` - and
the platoon leader sees it next to the LOGSTAT. When the platoon leader gives a squad an objective there, the
`command.Resupply` drill sends each soldier short of a weapon or armour to the chest. A soldier
short of kit who comes within reach of the chest draws from it on its own initiative, whatever its
order (`bot.supply.SupplyRun`). It takes only what fills a gap - the best melee weapon if it has
none, and one piece for each empty armour slot (`bot.supply.Quartermaster`) - then wears the
armour and holds the weapon. Nothing restocks the chest but the operator.

## 5. Orders downward

| Order | Use | Source |
|---|---|---|
| **OPORD** | Full order, five paragraphs: **S**ituation, **M**ission, **E**xecution (incl. commander's intent), **S**ustainment, **C**ommand and signal ("SMEAC"). | FM 6-0 |
| **FRAGO** (fragmentary order) | Changes to an existing order - only what's different. | FM 6-0 |

*How we use it:* the platoon leader's objective to a squad is OPORD-shaped but small - situation, mission
(who / what / where / why), intent. The squad leader's frequent re-tasking is FRAGO-shaped: a new
objective, formation, or interval against the standing mission. The platoon leader orders every squad in
one decision, so it divides the mission between them; its prompt says one squad can hold or
observe while another moves, and that a squad beside one in contact or weak brings it help.

An order stands until changed, and changed for a reason. The platoon leader sees each squad's standing
order and how it is going (for a patrol base: not laid out yet, how many blocks are still to place,
or sealed with the squad inside), and for
each squad either keeps it, exactly as given, or gives a new objective with the reason why, which
is logged (`[PL -> Bravo] changing the objective: ...`). A change with no reason is rejected
and the squads keep their orders. A kept order isn't re-sent: the log says `holds`.

The platoon leader's heights are guesses, so an objective is put on the ground at its x and z: the
typical height of the ground round it - not the top of a heap of old huts at a camp's centre -
looked for within 24 blocks of the squad's middle height - not one soldier's, which
might be stranded up a tower
(`ChainOfCommand.grounded`; the log says `put on the ground`). A MINE objective keeps its own, since
its y is a depth.

## 6. Replacements: rally point and link-up

| Practice | Content | Source |
|---|---|---|
| **Rally point** | A place the unit can reassemble at if it's dispersed - chosen where the unit last was secure. | ATP 3-21.8 (rally points) |
| **Link-up** | Two elements joining up safely: recognition at a distance, then up close, then moving together, then local security. | FM 3-21.9 ch. 7 (older manual) |

*How we use it:* a respawned soldier is a replacement. `command.LinkUp` reports it as
`LinkingUp` and moves it to the rear of the formation, so if the point man died the next soldier
takes point. While anyone in the squad is in contact the replacement holds at the rally point,
which is where the point man last stood with nobody in contact. Otherwise it closes on the nearest
- never one the recovery drill (§7) has, whatever that one reports: live, a soldier rejoining closed
on a builder in a dead end at a mine's bottom and stood on the stair above him at "no route" -
squad member, and within 6 blocks it has linked up and gets a formation slot again. Recognition is
just knowing where the squad is.

Each death is logged as a casualty report (`[Casualty]`, `app.FlightRecorder`): the server's death
message, where the soldier died and which routine had its hands, how far it fell if it stood 3 or
more blocks higher in the 15 seconds before, and its last 5 seconds four times a second - position,
report, health, food, routine and the nearest hostile within 24 blocks. A tally of deaths by cause
since the app started follows each one.

## 7. Risk acceptance

| Practice | Content | Source |
|---|---|---|
| **Risk levels** | Low, medium, high, extremely high: how likely and how severe a loss the mission exposes people to. | ATP 5-19, *Risk Management* |
| **Risk acceptance** | The commander decides how much risk the mission justifies, at the level that owns the mission; subordinates operate within it. | ATP 5-19 |

*How we use it:* the squad leader's order carries a `Risk` (LOW, MEDIUM or HIGH), the health the
mission justifies spending to keep moving. The priorities are the mission first, then the
soldiers' survival, then avoiding damage. Today health is spent only on falls of more than 3
blocks, at 1 health per block beyond 3, cut by the worn armour's Feather Falling (3 points a level)
and Protection (1 a level) at 4% a point, capped at 20 points - vanilla's enchantment protection.
`command.Kit.fallProtection` carries it. Each soldier turns the order's
risk and its own condition into a fall allowance:
- **The floor:** it never falls below the risk's minimum health (LOW keeps 15 and spends at most 3 on
  a route, MEDIUM keeps 10, HIGH keeps 4). LOW's minor fall is the squad's standing risk decision,
  as a unit's SOP carries one so its leader isn't asked each time: a soldier a block too high on a
  roof or a ledge gets down without a call, and anything more is still CANTCO, the leader's to grant.
- **The price:** each point of damage costs extra route length - 40 blocks at LOW, 20 at MEDIUM, 5
  at HIGH - halved for a well-fed soldier because it regenerates.

So a safe route always wins when one exists, and a fall is planned only when there's none, on
foot or by the steps it can build (docs/echelons/soldier/capabilities.md). A soldier with no such
route - its own planner having tried building its way with whatever it carries - is stranded: cut off in
a pocket, below where it's sent with no way up, or under the ground - more than 8 blocks of rock or
soil over it, a tree's leaves and logs not counted, and none of the ground it can walk to out from
under it - and sent to the surface, however big its cave (operator, 2026-11-17: a soldier digs itself
out of anything; live, Ivy stood half an hour in a stone slot 26 under a hill, her cave too big to
be a pocket). It's reported "no route" 20 squad cycles in a row (about
20 seconds), since one failed hop across the camp isn't a soldier stuck; "no route" to where it's
going outranks whatever its routine reports. The squad's recovery drill (`command.Recovery`) picks
the nearest squadmate carrying 24 or more blocks - one at a time, never in contact - and sets it
to build the engineer's stair down from where the stranded one stands (`bot.engineer.Stairway`,
an `Order.Place`). The stair runs one block down and one along from the ledge's edge to the
ground, the shortest of the four headings with clear air over it and no water under it. Each step
stands on a block of its own, so it's two blocks a step, built from the ground up. While the drill
has it, the squad leader is shown the stranded soldier as `Recovering`, at the rear of the
squad, not its CANTCO: shown the CANTCO, the squad leader took it for the mission's and reported
the mission one (in `SquadLeaderScenarioIT`'s `stranded` variant, 6 of 14 right; 14 of 14 shown
`Recovering`), and live the platoon leader spent the squad's days moving it to "resolve movement
issues". Nor is the squad's ground surveyed from where it stands: from one in a canopy, or in a
hole under the mine's landing, the squad was shown none of its ground reachable. A stranded
soldier carrying the blocks for its own stair - stone, dirt or logs - builds it itself from the
top down, nobody fetched: first a block under the ledge's edge, against its underside, since the
top step touches the ledge only at a corner, then each step and the block under it in turn. Live,
a soldier stood a quarter of an hour on a ledge with 8 logs, no squadmate carrying 24. One stranded
below - boxed in a pocket, with no stair down to lay, or more than 8 under the ground over it, where
a stair down leads only further in (live, one in a cave at y=-51 had a one-block stair down laid out
to its floor, carried no block, and stood) - gets the engineer's stair up instead, laid again from where it stands once it's more than 6 off every cell of it, and dug through any ore on its way whatever the pickaxe, the drop lost (live, an ore cell a stone pickaxe couldn't take was set aside, and the stair stood uncut) (live, one walked off 8 before it began, and stood under it with no cell in reach)
(`Stairway.up`) - its hands the stair's alone till it's out, no load put in nor stack picked up, nor given ground on the keep at all - its rectangle, the stockpile's cobblestone or a pit's dirt - nor anything to make, whose walk to a workshop would take it off the stair (live, an iron pickaxe walked one off its stair up every second),
past it, as on a stair down (live, two in a cave under the mine's stairs held theirs for both, with
no route to either, and stood; and one on a ledge with its stair down laid out held its for a
stockpile, its order the stair's, and stood) -: the cells to dig, one block up and one along a step to open ground, up to 136
steps - a tunnel to the surface from a dead end at a mine's bottom, as deep as a mine goes, just over
bedrock (live, one at y=25 under a head at y=70 was five short of 40, and one in a cave at y=-52 was
still under the ground at 64) - winding left or right of its
heading where a cave across the way or gravel over it stops it going straight (live, every one of
the four straight flights was), and never under sand, gravel, water or lava (the operator's rule:
cut above only toward what's known) - a cell of gravel itself is dug like any other, and gravel
over a cell the stair digs anyway falls into the hole and is dug again; what counts is what's over
the head room (live, the pocket at the mine's bottom was walled and roofed in gravel). With no way
out, the log says for each heading how far it got and what stopped it there. Carrying a pickaxe, the
stranded soldier digs it itself; otherwise the nearest squadmate with one digs it from the top, as
an `Order.Dig`. Live, a soldier waited two runs in a pocket walled partly in cobblestone, and
another, with a pickaxe and 263 cobblestone, stood a day in a dead end 30 blocks under the camp, a
12-step stair short. A squadmate is sent where no stair or cut is laid only to one up on a ledge -
a carrier from below, whose own route builds the steps it finds - never down to one in a pocket:
live, two squadmates in turn walked down to one and were trapped beside it. Stranded means cut
off: a soldier reporting no route that can walk no more than 128 cells from where it stands, by the
planner's own moves (`SoldierRunner.cutOff`, WayOut's measure of a pocket) - or, sent more than 6
blocks above itself, can walk to nothing within its order's height of that point, a cave of any
size under it, by a flood that ran out of ground and not of budget (one on a mine's stairs whose
flood filled the tunnels first was read as cut off): live, two soldiers stood a night shift in one of 866 cells at y=44, sent up to y=64,
and no stair up was laid. One with the ground to
walk - its order's goal out of reach, a squadmate in a pocket, a plot across water - isn't
stranded, and nothing is cross-levelled to or from one the drill has: live, the keep's foreman
spent a day on the stairs with rations for one in a dead end, "stranded" every minute on its way.
The drill's order - its own stair or cut, or a squadmate's - outranks ground work: live, a builder
in a dead end kept its keep strip's dig, 50 blocks off and up, over the stair out. Without a pickaxe
and with nobody carrying one to come, the stranded soldier digs the cut by hand. With nobody to
fetch it and nothing to build with, the stranded soldier
goes down to its nearest squadmate itself at MEDIUM risk, over its own routine's stand: a fall
that leaves it half its health or more. Live, soldiers stood for an hour on hut roofs, the wall
and a canopy over the camp, a 4-block drop from the ground that the squad's LOW risk wouldn't take. A recovery lasts until the
stranded soldier has moved two blocks off where it was stuck. Its own report flickers as it
retries its route, and a recovery that ended with each flicker would pass the stair from one
squadmate to another. A soldier that reports CANTCO prices the riskier
order that would free it, for example "a route with falls costing 8 of my 20 health exists (MEDIUM
risk)". The squad leader then decides whether the mission is worth that. It can set risk for one
soldier (`SquadOrder.soldierRisk`), so freeing a stuck soldier at MEDIUM doesn't expose the rest of
the squad to falls.

## 8. Actions on contact and the react-to-contact drill

| Practice | Content | Source |
|---|---|---|
| **Actions on contact** | The element in contact returns fire and takes cover immediately, without waiting for orders. | ATP 3-21.8 |
| **React to contact** (battle drill) | The rest of the unit moves to support the element in contact. The leader then decides whether to assault, bypass or break contact. | ATP 3-21.8 (battle drills) |

*How we use it:*
- **The soldier's reflex.** A soldier in contact fights the nearest hostile at arm's length
  (`DeterministicSoldier.tick`): it closes to its 3-block reach at a sprint, strikes when its attack has
  recharged, backs off inside 2.5 blocks, which is outside a zombie's reach, and holds in
  between. It gets away from creepers instead: of the headings round straight away, the one that
  gains it the most distance, so a wall at its back turns it sideways, and when no heading gains
  any it strikes the creeper, knocking it back out of its blast's reach. One that holds it inside
  its reach for ten seconds (`DeterministicSoldier.CREEPER_STANDOFF_TICKS`) - in a tunnel it can't
  be gone round, and backing off only keeps the distance - it engages: closes, and strikes (the
  engagement doctrine: both can reach, blocking the path; live, a miner stood a minute 2.9 blocks
  from one in a one-wide tunnel). No step in a fight -
  closing, backing off, getting clear, taking cover - is taken over a drop deeper than 3 blocks,
  the most the server charges nothing for, unless water lies under it: a route plans its drops,
  a fight doesn't, and soldiers fighting on the roofs of the camp's huts backed off their edges.
  Losing health with no hostile within 8 blocks
  means being shot at, so for 5 seconds it looks out to 16 blocks, beyond a skeleton's 15-block
  bow range, and goes for the shooter. Only a hostile it can see counts - nothing solid between
  its eyes and the hostile - so a squad walled into its patrol base (§14) isn't in contact with
  the monsters outside. A soldier with no melee weapon doesn't close on an archer (a skeleton or a
  pillager), which out-fights a bare hand from any range: it moves to the nearest spot within 6
  blocks the archer can't see, or backs off if there's none, and holds there while it's under
  fire. An armed soldier does the same when no route reaches the archer - water, a drop or a wall
  between them - checked once a second while in contact. Across water among trees, that cuts
  deaths from 36 in 40 to 12. A fighting soldier never jumps over water or lava: a jump is only
  ever up a block, since nothing says where one over a gap comes down. It swings each weapon at its full recharge, since a hit's damage scales with the charge
  (`bot.world.WeaponStats`, measured on the live server).
- **The bow.** A soldier carrying a bow and arrows returns fire: what it can see within 16
  blocks is contact, and one beyond 5 blocks - or one it can't close on, or any at all with no
  sword - it stands and shoots at rather than closes. It aims along the arrow's own arc, flying
  it as the server does (3 blocks a tick at full draw, drag, gravity), draws 20 ticks for full
  power, looses, and draws again; the bow goes in hand for it and the sword comes back after
  (`bot.supply.Quiver`). Seen live in `bow-against-skeletons`: skeletons on pillars no sword
  reaches, 17 arrows from five soldiers, both down.
- **The shield.** A soldier with a shield in its off hand raises it while an arrow is in flight
  within 16 blocks, facing it, and lowers it as soon as the arrow is past. An arrow seen counts
  as being shot at, as a hit does, so it goes for the archer. A raised shield stops a blow from
  in front once it has been up 5 ticks. Meanwhile the soldier walks at a fifth of its pace and
  can't swing, as the vanilla client can't, which is why it isn't held up longer: an archer it
  can't close on keeps shooting.
- **Out of reach both ways.** A hostile that fights hand to hand or explodes, out of a sword's reach with no
  walk to striking distance of it - no steps put, no block dug, since it can do neither - - water, a drop, a wall between - has none to the soldier
  either: it isn't contact, and the soldier carries on with its order; once it's within reach, it
  is (live, the farm squad stood a night's start "in contact" with one across from it, each
  stepping at it into what stopped it, none striking). It's looked for swimming too, not only on
  the ground: one in water stood "in contact" across two restarts, its drill held off; and three
  of a squad in a cave stood a night 5 to 8 blocks from creepers that had no way to them. And a
  creeper more than 4.5 blocks off, past its blast, is contact the soldier carries on through: it
  never closes on one, and one that didn't come on held three standing for hours; it goes on with
  its order, and closing inside that, it backs off. Its route goes round a creeper (operator,
  2026-11-16, the engagement doctrine: bypass): every cell within 5 blocks of one within 24 costs
  a route more, so a way round is taken where there's one, and a creeper newly in range has the
  route planned again (`DeterministicSoldier.CREEPER_BERTH`). Without it, a soldier walking past
  one closed to its blast, backed off, and never got by.
- **The squad's drill.** `command.ReactToContact` sends teammates within 24 blocks to support,
  ahead of the squad leader's next decision.
- **Work under contact.** The soldiers in contact drop their ground work - the keep's rectangle, the
  camp's ground, a levelling order - and answer the squad leader until it's over; the rest keep
  theirs, and aren't sent to support: that drill is for free hands. On the keep the ground under
  the fight is nobody's meanwhile (§17). The squad's side jobs - the wall, the lights, the mine's
  cutter, rearming past the basic load, the farm - pause for the whole squad as before. Live, one
  zombie at the keep's edge held eight builders off it for a quarter of an hour, every one of them
  answering the squad leader's "move to the keep" instead of digging.

In the combat simulation, three zombies on one soldier's flank kill a lone soldier 64 times in
100. A squad spread 10 blocks apart without the drill still wins, but loses its flank soldier 66
times in 100. With the drill it loses nobody.

A squad closing on one hostile stands round it, and a sword's sweep hits every body within a block
of what it strikes, squadmates included. So a soldier holding a sword doesn't swing while another
player stands within 1.6 blocks of its target across, its body meeting the target's box grown a
quarter block up or down (`DeterministicSoldier.SWEEP_REACH`, `SWEEP_RISE`): a flier two blocks over
a squadmate's feet still sweeps it. It waits until one of them moves. It knows itself among
those players by its entity, not by where it stands: players don't collide, and live a squadmate a
tenth of a block from the swordsman was taken for it, and killed by the sweep at a third of a heart.

## 9. Pace control and supporting distance

A leader keeps the unit's elements within supporting distance, close enough to reinforce each
other in contact. When the unit strings out, the lead element halts or slows until the rest
close up (ATP 3-21.8, movement and dispersion).

*How we use it:* `command.Cohesion` halts the point man while a soldier who can follow is more than
24 blocks from him, the same distance the react-to-contact drill sends support. Every formation
slot is measured from the point man, so holding him holds the squad. Stuck soldiers (CANTCO),
replacements linking up and soldiers in contact don't hold the squad; each has its own mechanism.
The squad leader's prompt shows each soldier's distance from the point man, and the log reads
`[Alpha] HALT: point man waits for Bob`.

## 10. Close protection

| Practice | Content | Source |
|---|---|---|
| **Protective detail** | A team assigned to a principal moves with them, keeps a perimeter around them, and meets a threat before it reaches them. | ATP 3-39.35, *Protective Services* |

*How we use it:* the operator attaches the first squad (Alpha) to a player with `!escort` (or
`!escort <player>`) in game chat. `ChainOfCommand` takes it out of the platoon leader's command - the
platoon leader goes on commanding the other squads - and gives its squad leader the principal's
position as its objective, re-issued whenever the principal has moved 8 blocks. The principal
moves every tick, faster than any LLM decides, so `command.Escort` - a drill like the others - is
recomputed each squad cycle from where the principal is now:
- **The ring:** the soldiers stand evenly around the principal at half the squad leader's interval,
  at least 2 blocks out. TIGHT keeps them at arm's reach.
- **Interception:** each hostile within 12 blocks of the principal draws the nearest soldier not
  already fighting, stuck or linking up. The soldier's own reflex (§8) takes over once it's close.

A soldier who respawns goes straight to its slot in the ring rather than linking up with the point
man first (§6), and any soldier more than 8 blocks from its goal sprints, so the ring keeps up with
a principal on the move. Sprinting is the game's, at the game's price: the soldier tells the
server it's sprinting (Player Command), so the server charges the food it costs - 0.1 exhaustion a
metre, where walking costs none. An ordinary move is walked, whatever it carries: live, soldiers
sprinting every walk past 8 blocks ate eleven loaves a day each, about the farm's whole harvest.
An urgent one sprints: the ring's moves, an intercept, a teammate going to support one in contact
(§8), closing on a hostile it fights, and any move while under fire. Nobody sprints at food 6 or
below, where vanilla won't let a player, or with a shield raised. The squad leader still chooses the interval and the risk. The soldiers find the principal by its
offline-mode UUID among the entities they see; if none of them can see it, the squad holds the last
known position. Out of sight for 30 seconds - a principal who has left the game is gone from the
world - the escort ends, said on the net, and the platoon leader decides for the squad at once. `!mission`
ends the escort too and hands the squad back to the platoon leader.

## 11. Pre-combat checks

| Practice | Content | Source |
|---|---|---|
| **Pre-combat checks and inspections** | Before a unit is employed, every soldier is checked for the equipment the mission needs, and shortfalls are made good. | ATP 3-21.8, troop leading procedures |

*How we use it:* a soldier makes its own kit (`bot.supply.Armourer`, which also makes the pickaxe
of §12, several things at one opening of the table when it needs several). What it makes next is
a fixed table, the equipment ladder (`bot.craft.Ladder`), not a model's decision:

1. A weapon: a stone sword if it carries the cobblestone, a wooden one if not.
2. A stone sword.
3. A bow, from three sticks and three string, once it carries the string: string is a spider's
   drop, not something it goes and fetches. A soldier with a bow makes arrows - flint, a stick, a
   feather, four at a time - while it carries flint and feathers, up to 32. Short of arrows, it
   fetches the makings by day, round its squad's objective: with no feather, it hunts a chicken
   (§4's hunting, chickens only); with no flint and nothing else to gather, it digs gravel by hand,
   one block in ten giving flint, until it carries two.
4. A stone pickaxe, which mines iron ore.
5. A spare weapon, then a spare pickaxe, of stone - a wooden spare would spend the logs the first
   tools and torches want: a sword breaks in a fight and a pickaxe halfway down the mine, and the
   spare is in the pack. A
   piece of kit asked for is one more than carried when first asked, held to that while it's asked,
   so a spare is a second and not a third (`Armourer`).
6. An iron pickaxe, from the first three ingots (operator, 2026-11-14): it digs what stone can't
   - emerald ore, diamonds - and what it digs pays for the armour after it.
7. A shield, worn in the off hand and raised against arrows (§8).
8. Copper armour - chestplate, leggings, boots, helmet - from copper ore mined with the stone
   pickaxe and smelted: copper lies near the surface far more often than iron.
9. Iron armour: chestplate, leggings, boots, helmet.
10. An iron sword.
11. An iron axe, which chops a log in a sixth of the bare-handed time.
12. Diamond - a sword, then the armour, then a pickaxe - once it carries the diamonds: they're ore
    the miner takes beside its stairs (§16) with an iron pickaxe, not something a soldier fetches.
13. The tool load (`CrossLevel.TOOL_LOAD`): two each of melee weapons, pickaxes and shovels, of
    stone - a main and a spare: they wear out, and the next is in the pack (operator, 2026-11-15,
    the fighting load; four before it). What's past a soldier's load for its work goes to the
    depot (`bot.craft.LoadPlan`, below). Last, after the
    armour: protection first, and a rung whose makings can't be had is passed over.

A keep builder's work load - not the wood party's, whose load is its axe - comes before the ladder past its protection floor (operator,
2026-11-14: kit by role, protection and work apart; `bot.craft.WorkLoad`): once it holds a stone
sword, it makes stone pickaxes and shovels, whichever it's further short of, up to its share of the
keep's dig still to do, halved between the two, over a stone tool's 132 uses, plus one in hand,
the tool load of each at most.

**The fighting load by kind of work** (operator, 2026-11-15; ATP 3-21.18: the fighting load on the
soldier, the rest with the trains; `bot.craft.LoadPlan`, backlog/load-plans.md): the keep's hands
flatten - two swords, two pickaxes, two shovels, two axes, a stack of torches - and a mine's cutters
mine - two swords, three pickaxes, a shovel, no axe, a stack of torches; food's basic load is
`Subsistence`'s. The best of each is kept up to its figure - an axe counts as an axe, not a
weapon - and what's past it, the worst of a kind, torches past a stack, and anything of no part of
a load - an emerald, a mineral of any kind, a diamond (`ItemClass.MINERAL`: redstone, lapis,
emerald, gold, quartz, amethyst) - goes into the depot on the soldier's next trip there with its loads, or on a
trip of its own once it's three slots: room in the pack for what the work digs, and the spare kit
in the depot for whoever's short. Only from what it carries, cobblestone and wood, never quarried or chopped
for. Live, builders climbed through copper armour while the dig's tools wore out with no spare.

No rung past what the camp's level authorises is made (backlog/maturity-model.md, basis of issue;
`Ladder.authorised`): the iron pickaxe, the enabler, at any level (operator, 2026-11-15: it breaks the
keep's emerald - live, the camp fell to SHELTER when eight soldiers joined unarmed, with twelve raw
iron in a pack); at SHELTER nothing else that costs iron; at BUILDUP a shield, armour and an iron axe too; diamond, and an iron sword, only once
it's ESTABLISHED (§ 17). Copper costs no iron, so copper armour comes at any level.

The rungs are ranked by survival gained per resource spent, as `CombatScenarioTest` measures it.
Against a skeleton, a stone sword over a wooden one saves 0.3 health a fight. A shield saves
2.1 more for one iron ingot, about what a chestplate saves for eight, and full iron armour 5.9:
the arrows on the way in do the harm, not the melee. Out of contact and out of a patrol base, a
soldier walks onto whatever lies within 6 blocks - a spider's string, a chicken's feather - and
leaves for a while what it can't reach (`bot.supply.Scavenger`). A rung is held
once something as good is, and one whose materials can't be had is passed over, not waited on.
A tool that wears out is made again, since it no longer holds its rung.

Off the ladder, a soldier without a bed makes one - three white wool over three planks - and,
short of the wool, hunts sheep for it by day like the chicken for a feather. Only white wool
counts: a bed wants three of one colour, and most sheep are white. Placing the bed and lying down
in it are backlog/rest-plan.md.

A soldier on ground work - a dig of the camp's or the keep's ground, or dirt for a wall - makes a
shovel if it has none, stone if it carries the cobblestone: it digs soil and gravel with its
fastest shovel, a stone one in a quarter of the bare-handed time. Every other block it clears is
dug with the tool of its kind too (operator, 2026-11-13; `Hardness.Tool`, from the server's
`mineable/*` tags in the block registry): the best pickaxe for rock, the fastest axe for wood,
chests and tables, the fastest shovel for sand, a bare hand for glass. Fists are the floor
(operator, 2026-11-14: the best tool available, and sometimes that's fists): they break anything
but bedrock, slowly and for no drop - stone in 7.5 seconds for no cobblestone - so a soldier with no
pickaxe digs stone by hand rather than stand, for a way out, a snag or the ground. The one block it
leaves is ore whose drop needs a pickaxe of a tier it hasn't got (`needs_*_tool`: stone for iron
ore, iron for diamond), for a squadmate's pickaxe or until one is made, since dug with less the
drop is lost for good (`Clearer`, "leaves N ore block(s) its tools can't dig for their drop"). On the keep
such ore is set aside with the ground under it when no builder on the site, of any squad, carries
the pickaxe for it - one answer for the whole site, not each squad's nor each rectangle's holder's,
whose changes moved the site's highest ground and the level with them every second - and the site goes
on down past it - left in the dig it would hold the whole site at its level in lock step (§17) - and
with the pickaxe carried it's the strip's top again, cleared like any block. Set aside isn't done:
the keep's ground isn't flattened while any stands (operator, 2026-11-15), and its estimate (§ 17)
bills the iron pickaxe it waits on. Once ore set aside is at or over the level - in reach on its
pillar, or all that's left - or ore is left in a rectangle whose holder can't take it, the site's
nearest hand who carries the pickaxe for it takes it,
whatever its squad (`[Squad] Alice takes the ore set aside at 12 64 0`): live, the emerald was in
one squad's share and the one iron pickaxe in another's. A drop fallen where no
body can stand - into a gap a block high - is lost, not walked to: live, three builders in turn
stood a minute over one. A step or pillar it builds is of
dirt before cobblestone (`Footing.placeStep`): scaffolding, while cobblestone is a floor's and a
wall's. What stands in its cell that isn't solid and no block replaces - a flower, a torch, a
sapling - is broken first, at a touch (live, three builders stood minutes in the dig, each step into
a cell of wildflowers refused). One the server refuses eight times running in the same cell - the
soldier standing in it, say - isn't put again for ten seconds, and the line names the world
model's block there and under it, with its class (`SoldierRunner.refusedStep`, "its step at ...
keeps being refused"); the blocks named in refused steps and stalls are counted, said at the 1st,
10th and 100th of a kind ("[Blocks]"), for the classes to fix: the soldier stands, and the stuck alarm and the recovery drill take it from there. Live,
a builder put one step every tick for four minutes, 700 times.

With no tree in reach for its kit's logs, or its torches' with ground to light and none carried, it draws 8 from the nearest surface chest seen holding
them, as metal is drawn, while the stores were last seen holding that many, or haven't been seen
since a restart (`SoldierRunner.drawsLogs`): live, a miner at the mine's head couldn't make a
pickaxe with 71 logs in the keep's depot, his own stockpile the camp's chest.

It digs what it's short of and can dig itself: logs from the nearest tree within 16 blocks it can
stand beside, with its best axe if it carries one, and cobblestone or iron ore when its pickaxe is good enough and some is in reach -
a tree or ore looked for at any height it can walk to, up a slope or down the mine's stairs, and
ore met in a dig of the ground taken for its drop, not broken as a block in the way - every kind:
iron, copper, coal, and any other with the best pickaxe carried (`Digger.Target.ORE`), its drop
worth more than the dirt and cobblestone round it (operator, 2026-11-15) and never thrown away as
junk from a full pack -
only a block it can walk to a stand beside (`Armourer.spotFor`, a flood fill from where it stands):
live, builders left the keep for iron seen 40 blocks off through solid rock, and copper under a
blocked stair, and each stood 20 seconds at "no route" before giving up, every few minutes, all day.
It crafts what it can in its 2x2 inventory grid. What needs a table or a furnace it makes at one
standing within 16 blocks, walked to - only one of a kind the making needs, with a side it can walk to (live, a table left in a cave over a mine's bottom held a miner a minute): live, a soldier smelting copper walked to a table, and in its reach went on to the workshop, and back, for five minutes - or else at its squad's workshop. What's made is put on: armour worn, the best weapon in hand. And whenever its hands are
free, a soldier stands to: every two seconds it puts on any better armour it carries and takes its
best weapon back in hand, rather than the seeds or dirt its last job left there. A first wooden sword takes
about a minute. While crafting or smelting, the soldier stays where it began, since a window closes
on a player who walks away from it. A furnace burns one kind of fuel at a time: the soldier feeds
it coal (or charcoal) it carries first - eight smelts a lump - then a stack of planks that covers
the smelts, or failing that of logs, which burn as long; and its plan counts coal carried as the
furnace's fuel before it asks for planks, so a soldier with ore and coal and no logs smelts.

The squad climbs together. Nobody works above the lowest rung a squadmate is still working toward,
so the first soldier up the ladder doesn't dig out the ore the rest need for their shields. A
table is shared, since each player's crafting grid is its own, but a furnace isn't: whoever opens
it can take what's in it. So a soldier holds the furnace it smelts in until it has collected what
it left there, and a squadmate waits for it.

**Workshops.** A soldier never chooses where a table goes: the engineer sites each workshop
(`bot.engineer.Workshop`), and the soldier is sent to it.
- **The camp's**, at the camp's ground: for every squad whose objective is within 48 blocks of it.
- **A hasty one** at the objective's ground otherwise. It is sited round a table already standing
  there, if there is one, so the same workshop is found after a restart, and never in the squad's
  patrol base or on its wall.
- **The mine base's**, at the bottom of the camp's stairs (§16): for a soldier more than 3 blocks
  down, once the landing is cut. By night it's a mining squad's only workshop, so nobody climbs
  out into the dark for a tool.

Each names a cell to stand in, and a cell each for the table and the furnace, on adjacent sides so
the stand stays open on the other two. A surface workshop also names a chest's cell, behind its
table or its furnace and in reach of the stand: the site's store (§12) - a chest already there a
block up or down first, since the ground at a depot moves, and no block is ever put against a
chest's face, whose click opens it (`Builder.support`), nor a chest's row cell named over another
chest, which it would shut. Live, a depot sited a block higher took its
own chest for none, and the chest put in its place, against the old one, never went in all night. A fixture not standing yet is fitted by the first soldier
that needs it (`bot.supply.Fitter`): it crafts the item - the table in its grid, the furnace from 8
cobblestone at the table - and places it into its cell. A fixture counts as standing only when its own block is in the cell:
another block there - a wall's cobblestone - isn't the chest. With no workshop sited, the soldier says so
rather than putting a table down where it stands.

A soldier's basic load is its own to keep: out of contact, it makes a weapon and climbs unordered
through a stone sword, a bow, a stone pickaxe, a spare weapon and pickaxe, a shield and copper or iron armour from the ore to
hand (`Ladder.BASIC_LOAD`) - the kit the camp's armed and armoured standards ask of it. The squad
leader decides whether there's time for more: its order's `rearm` lets soldiers climb past the
basic load, to an iron sword and up. Climbing past it also waits until the
squad isn't ordered to a patrol base, since shelter comes first
and digging iron isn't building it. Each soldier also keeps back its share of the cobblestone its
squad's base still needs, so a stone sword never costs a wall. A soldier at it reports `Rearming`,
and the squad doesn't halt for it.

Contact outranks this work, as the react-to-contact drill (§8) outranks priorities of work. A
hostile it can see within 16 blocks, or the squad in contact, pauses the job (one out of sight - in a mine shaft below, behind a hill - doesn't): windows close, and the soldier
fights with what it has. Once clear, it picks up where it stopped. The exception is a soldier with
no weapon standing at an open table: the weapon is a click away, so it finishes it first. A fault,
such as no tree in reach or an answer from the server that never comes, ends the attempt. Any
half-crafted items go back in the inventory, and the soldier tries again a minute later. A failed
sword isn't reported as CANTCO: the check is the soldier's own, not its order, and the squad's
LOGSTAT already shows it bare-handed. A failed pickaxe is, when the order is to quarry stone.

## 12. Gathering materials

| Practice | Content | Source |
|---|---|---|
| **Class IV supply** | Construction and barrier materials - lumber, sandbags, wire - requisitioned or, in the field, obtained locally. | FM 4-0, *Sustainment Operations* |

*How we use it:* the platoon leader can give a squad an objective with `gather: WOOD` or `STONE`
(`command.Objective.gather`). Its soldiers gather nearest the objective on their own - within 16 blocks, and
when that's bare within 32, then 48, before reporting nothing left - dirt, which is everywhere, only
within 16: live, one dug dirt for its squad's hut forty blocks off by night and met a witch there;
a soldier digging for its own
tools' makings looks as far (`bot.supply.Gatherer`, digging with `Digger`, shared with §11): each works a log or a block of
stone no squadmate has claimed, checks it actually broke, walks onto the drop wherever it landed
(done once it's off the ground - a squadmate working the same trees often gets there first), and
stops at its load (16 logs, 32
cobblestone) or when anything nears. Logs are chopped bare-handed - a sword wears, and digs no
faster. Stone needs a pickaxe, or it drops nothing: a soldier without one makes a wooden pickaxe
first, the way it makes a sword (§11), from logs it carries or chops. Break times are the
server's - hardness over tool speed - with a margin, since the server times each dig. A soldier at it reports `Gathering`. One that finds nothing left in reach goes, while that order
stands, to one of the places its squad saw the resource, as for torch-makings (§15); with none seen
beyond the objective's reach it reports CANTCO, which the squad leader passes up so the platoon leader
can choose another place. To choose, the platoon leader
reads what each squad can see within 64 blocks of the soldier at its middle height: for trees and for stone open to
the sky, the nearest site and the two biggest (not cave walls, which no gatherer can
reach), each with its count, distance and direction (`bot.supply.ResourceSurveyor`,
`command.SquadStatus.resources`). A soldier that can't make the pickaxe it needs - no trees in reach
- reports CANTCO too.

The supply point's chest is also the stockpile; with no supply point set up, the stockpile is the
chest of the soldier's workshop (§11), fitted by the first soldier with a full load and nowhere to
put it - crafted from 8 planks at the workshop's table, placed in its cell. A soldier with a full load walks it over and puts in
the material it has a full load of (`bot.supply.Depositor`), and the rations beyond its basic load
(§4, Class I) - only that, so a soldier quarrying stone keeps the logs for its next pickaxe, which
wears out in 59 blocks. What the chest holds when it's
opened goes to the platoon leader (`PlatoonLeader.receiveStockpile`), shown with the supply point. It's drawn on
too: a mason out of blocks, when the stockpile was last seen holding a load of cobblestone, walks
over and shift-clicks stacks out until it carries a load (`Depositor.tickDraw`) before it digs any
dirt - so one squad's quarrying walls another's camp. Raw ore and ingots go through it the same way
(`bot.supply.MetalPool`): a soldier keeps the iron or copper - ore or ingots - its next piece of
kit takes only if it can make that piece with it, or with what the surface chests hold added -
which it then draws, ingots before ore, from the nearest chest last seen holding it (an outpost's,
the camp's, the main depot's) and still standing - one the world no longer shows is forgotten, not
walked to (live, a draw went seven times to a mine's old stash at y=24 that wasn't there), its armourer waiting rather than walking off to dig. Any other ore or
ingot it carries is the force's, and goes in once it has 3. Live, eleven raw copper lay in four
packs, none enough for a piece; and (field300) 74 raw copper lay in outpost and camp chests while
draws looked in the main depot's alone, and three soldiers wore no armour. Logs carried show in each
soldier's LACE (`Kit.logs`) and in the squad's LOGSTAT as materials.

Food is grown as well as gathered. The survey also reports ground to farm: the largest plot of
tillable ground, dirt or grass, with nothing solid above, at one height within 4 blocks of a water source, as a
"ground to farm" site with its cell count, at a plot cell beside the water (`bot.farm.FarmPlan`).
An objective's task can be `FARM`. The squad keeps the plot round the water nearest the objective,
the same plot while the objective stays put. Each soldier does the most pressing job left on it,
one cell at a time (`bot.supply.Farmer`), in this order:
1. Its earthwork (`Farmer.earthwork`): digs off any cobblestone or soil on a plot cell, and cuts
   the ground beside the plot down to its level, up to two blocks - an apron, so every cell is
   walked onto on the flat - but never a step the squad built (`SoldierRunner.scaffolding`): in
   the squad simulation a farmer dug its own step out of the hollow as dirt over the plot, its
   route built it again, and every jump up it that fell back trampled the farmland. Landing on
   farmland from over half a block tramples it back to dirt,
   and a route steps down onto it only at a price - a detour of 40 blocks is taken first
   (`Pathfinder.TRAMPLE_COST`), never a wall: with the farm tilled to its rim, every soldier at the
   camp stood with "no route" to the keep - and never builds a step on it, so a farm in a hollow
   was reached only where the ground happened to be level, and steps built out of it buried a
   third of the camp's farm.
2. Harvests ripe wheat, for wheat and seeds to plant again.
3. Plants seeds on bare farmland.
4. Tills the plot with a hoe, breaking first any grass or flower on the cell: the server tills
   only under open air.
5. With no seeds, breaks grass for them, since one in eight drops some - out to 32 blocks, as a
   squad soon clears what grows near.

A plot cell whose job fails - its place not walked to, the server not answering, a ripe crop it
can't reach - is passed over for two minutes, longer each time it fails again, by every soldier
farming there, and the rest of the plot is worked first. A walk to a cell with no route at all is
given up at once (`Farmer.unreachable`), not after the walk's allowance. A cell with a block at head height over
it - a hut's wall over the farm's edge - isn't tried at all: no one can stand on it.

Each job is an expect script: stand on the cell - landed, not still coming down onto it - item in hand, use it on the cell, and done when
the server's block update shows farmland or a sprout. A soldier without a hoe makes a wooden one,
and one carrying three wheat bakes it into bread, the ration every soldier eats (§11) - on an
errand of its own, never held up by other kit it's short of the makings of; a bare-handed soldier's
weapon still comes first. It reports
`Farming`. Each ripe wheat gives seeds back, more than its cell takes to sow again, so a soldier
keeps two stacks (128) and, at three or more, throws the rest away a stack at a time
(`bot.supply.Pack`): live,
farmers carried up to 580 seeds, packs full, and a harvest's wheat had no slot to go to. And a
pack down to 2 free slots throws away its smallest stack of what no routine uses - rotten flesh,
bones, gravel, phantom membrane: an item of no class (`bot.world.ItemClassRegistry`) - never the
one in hand, and an item it threw away and picked back up isn't thrown again for 5 minutes, a drop's life on the ground:
live, one standing still picked it straight back up, and threw it 454 times. With none of that to
shed, a full pack sheds its bulk, worth least first: dirt past two stacks (`Pack.DIRT_KEPT`), then
seeds past theirs, then cobblestone past five stacks (`Pack.COBBLESTONE_KEPT`) - never ore, ingots
or tools (operator, 2026-11-15: almost never dirt over iron; live, a soldier with twelve raw iron
carried 586 dirt and had no slot for the pickaxe it was crafting). A keep builder keeps less in
its pack: a stack of dirt to climb on, a stack of seeds, 16 saplings, no fences, 8 string, 16
flint, one crafting table (`SoldierRunner.KEEP_PACK`; operator, 2026-11-17: fill a slot, dump it
when something better comes along, and optimize the travel away - live, builders walked a few
dirt, three slots of seeds and five tables to the depot and back). Past that, what the depot is
under its most of goes in on the builder's next trip, and only what it's at its most of is thrown -
from one slot more than the load trip's mark, so the trip carries what's worth the walk (the
stockpile's levels, § 4). Everyone else stores what it carries past its own keep the same way
(`SoldierRunner.CARRIED`, `MINE_CARRIED`). What's thrown is left where it lies, no squadmate's
scavenger fetching it back to be shed again (`SoldierRunner.thrownNear`). And part-stacks of one item are
merged, the smaller onto the larger (`Pack.mergeable`), for what stacks to 64 only - a tool put on
a tool swaps them - and an item merged isn't merged again for 5 minutes, so one the server
didn't merge never holds off the rest of the pack's keeping. The hotbar is kept as the operator keeps
his (2026-11-15; `bot.supply.Hotbar`): the best weapon in the first slot, the best pickaxe in the
second, food in the second to last, torches in the last - only what's switched to in a hurry. The
slots between hold the quick tools (shovel, axe, hoe, bucket) and what a routine swaps in for the
moment - building blocks while it builds; anything else there, an emerald or a spare sword, goes
to the main pack. With its hands free a soldier makes a swap a tick till it's laid out. Food to
eat, a block to place, a hoe are swapped into a slot between first (`Inventory.spareHotbarSlot`,
one rule for every routine), else a second of a kind carried elsewhere - never the one in hand:
live, a soldier whose hotbar was nine weapons and tools couldn't eat or build. And no
position goes to the server with a coordinate or angle that isn't a finite number: it's said as an
error with what the soldier was at, and not sent - the server kicks for it, and live one soldier
was kicked and rejoined 671 times in forty minutes.

Wood is grown when it runs out. An objective's task can be `TREE_FARM`: the squad plants saplings
round the objective, on the world's grid of every fourth block both ways - x and z each two past a
multiple of four, so groves sited apart or again line up (operator, 2026-11-14) - of open dirt or
grass within 8 blocks, with seven blocks of air above each for the trunk (`bot.farm.TreeFarm`).
Birch is the farm's first tree - one straight trunk to a predictable height, with no giant form -
and spruce its second. A soldier plants birch if it carries any, then spruce, then any other, and
with no sapling breaks leaves for one, birch leaves first, then spruce; one leaf block in twenty
drops a sapling (`bot.supply.Forester`).
Planting is an expect script like the farm's. The trees that grow are logs like any others, for a
squad sent for WOOD there. A squad that sees no trees is told, on its resources line, that its
wood has to be grown. The soldier at it reports `Planting`.

## 13. Cross-leveling

| Practice | Content | Source |
|---|---|---|
| **Reorganization** | After contact the leader redistributes ammunition and equipment, so no one fighter runs dry while others have plenty. | ATP 3-21.8, consolidation and reorganization |
| **Cross-leveling** | Supplies move laterally, between units at the same echelon, rather than waiting on resupply from above. Doctrine describes it at company level and up; here it's the same idea within a squad. | FM 4-0; FM 4-93.51 ch. 9 |

*How we use it:* a soldier that needs logs for a tool (§11, §12), has tried to chop them and found
no tree in reach, is short; so is one below its resupply mark of 32 rations (§4). `command.CrossLevel`
pairs each with the nearest squadmate carrying more than it needs: a giver keeps 3 logs, enough for
its own next pickaxe, or 15 rations, and hands over the logs short or one ration - rations go across
squads too (§4, Class I). A bare-handed
soldier - a replacement respawns with nothing - gets a squadmate's spare melee weapon: the worst
of those it carries beyond its best (`Inventory.spareWeaponSlot`), never the one in hand. Weapons
are paired first, then logs, then rations, and a soldier is in one hand-over at a time, giving or
receiving. Then the tool load (§11): a soldier with fewer than two pickaxes or two shovels gets one
from the nearest squadmate with more than two, the worst it carries (`Inventory.spareToolSlot`),
one at a time. A weapon goes only to one bare-handed: two soldiers each holding one would hand a
sword back and forth. A replacement still linking up gets nothing until it has: the squad reorganises round
it once it's back. Nor does a soldier making its kit (`Rearming`): mid-craft its weapon can be on
an open window's cursor, where it isn't counted. Nobody cross-levels while
the squad is in contact, or from a soldier linking up - nor with one more than 6 blocks up or down
(`CrossLevel.HANDOVER_HEIGHT`): a hand-over is a walk, not a climb, and live a giver built steps up
a hillside to a squadmate fourteen blocks over it, for one ration.

The giver (`bot.supply.Handover`) walks to 2 blocks short of the receiver, faces it, and throws the
logs across one at a time (a THROW click on its inventory). It then backs off, because the server
gives a thrown stack, two seconds after the throw, to whichever player touches it first. The
receiver walks onto dropped items within 4 blocks of it. With the logs in hand, its tool attempt
restarts at once instead of waiting out the minute's cooldown after a failure.

The giver reports `CrossLeveling`; each soldier's LACE shows the logs move. The squad leader isn't
asked: pairing is bookkeeping, done deterministically like the other drills.

## 14. Patrol base

| Practice | Content | Source |
|---|---|---|
| **Patrol base** | A defensible position held for a set time. Security first, then the leader's *priorities of work*, each a task with a time and a measurable standard. | ATP 3-21.8 ch. 7; FM 3-21.8 (2007) |
| **Survivability** | Positions start hasty and are improved toward deliberate; overhead cover matters most. | ATP 3-37.34 |

*How we use it:* the platoon leader knows the time of day: Set Time's overworld clock, advanced with the
game time between the updates that carry it (`app.OverworldClock`), becomes `command.TimeOfDay`. Dark runs from when the server's day timeline dims sky light enough for monsters to
spawn in the open, tick 13178, to sunrise, tick 24000: spawning stops at 22822, but what spawned
walks until the sun burns it, so a squad stays in until then - the stand-to before first light. The platoon leader's and squad leaders' prompts say how many
minutes until that changes. At night, each squad not sealed in a patrol base is told so on its own
line of the platoon leader's prompt, rather than left to be worked out from the time and its state; by
day, with more than two minutes of light left, each squad still in or ordered to a patrol base is
told the night is over.

An objective's task can be `PATROL_BASE`. `command.PatrolBase` lays out a hasty hut around the
squad: a 2x2 floor for up to four soldiers (3x3 for up to nine), walls two high, and a roof over
walls and floor, so each roof block has a neighbour to go against. That's 40 blocks for a squad of
four. The site is the nearest level footing within 8 blocks of the platoon leader's point: every floor
cell standable at one height and walkable to from where the squad is - not an old hut's roof two
blocks up - never rubble or a slope, since the model's point is a guess - and a
block clear of any other squad's hut, since the platoon leader may send two squads to one point. Each
block of the hut already standing counts as a block nearer (`PatrolBase.Plan.standing`), so a hut
built before a restart, its doorway cut on the way out, is taken back and only the doorway rebuilt.
A re-issued objective whose search square holds the site already laid out keeps it, so the
outline doesn't move under the soldiers.

Each soldier goes to its own floor cell. Once all are inside, each builds from where it stands
(`bot.supply.Builder`): cobblestone first, then dirt, then planks, then logs. A soldier carrying
less than its share of the hut's blocks first digs dirt by hand a few blocks off the site, clear of
the walls (`Resource.SOIL`), and one that runs out with the hut still open goes out for more, so a
squad with nothing to build with still shelters by night; the
platoon leader's LOGSTAT counts dirt, and tells it so. Each block is placed against a
solid neighbour's face and checked against the server's block updates; a cell that won't take a
block after three tries is skipped; after the first miss the soldier digs the cell, since a flower
or sapling there blocks a placement until it is broken. A soldier at it reports `Fortifying`, or CANTCO when it runs
out of blocks. The platoon leader's LOGSTAT says whether the squad carries enough for the base it still
has to build - a whole one, what's left of one under way, or nothing once it's built - so the model
never has to add up the numbers itself, nor read blocks spent on walls as a shortage.

The standard is re-checked every second while the squad holds its base, not just once, since
anything can break a wall after it's built (`PatrolBase.Plan.breaches`). A flood fill from the
floor finds every way in: a missing wall or roof block, a dip under a wall, a hole in the floor.
Each goes back on the build list. A soldier whose finished build loses a block logs the repair,
usually within a second. Soldiers stand on the exact centre of their places, since a body off
centre leans into the wall cell beside it and the server won't place a block there. While the
squad holds its sealed base, its squad leader isn't asked to decide anything and the squad reports
InPosition. From inside a sealed hut no ground is reachable, which would otherwise read upward as
CANTCO.

Where the operator sets it, no squad spends the night in the open: every squad works the night
shift down the camp's mine, and comes up as the sun does. The rule and its timing are in
docs/echelons/platoon-leader/operator-rules.md.

The base is lit as well as sealed. Light is checked alongside breaches (`PatrolBase.Plan.unlit`):
a torch gives its cell block light 14, which falls by one a block through open cells. A floor cell
left at 0 is ground a monster can spawn on once no soldier is within 24 blocks. Overworld monsters
spawn only at block light 0, and never within 24 blocks of a player.

A squad with a base to light and no torch has one soldier make them before it seals itself in
(`bot.supply.Torchmaker`), using what squads carry: 3 logs, and 8 cobblestone if the workshop has
no furnace yet. The soldier makes them at its squad's workshop, sited clear of the hut (§11):
1. Planks and sticks in the 2x2 grid.
2. The workshop's table and furnace, fitted if they aren't standing.
3. A log smelted to charcoal, with a plank for fuel.
4. Charcoal over a stick: 4 torches.
Contact pauses the work as it does a sword
(§11). A furnace keeps smelting with nobody at it, so the soldier collects what it left there before
anything else when it comes back. The others wait inside, since building starts
only once all are in, and a bare-handed one may make its sword meanwhile. Once the base is sealed
and dark, a soldier inside with a torch puts it down where it stands.

A squad sealed in with no torch - after a restart, or after its torchbearer's run failed - lights
the base the same way. A soldier who carries the makings cuts a doorway toward the workshop
and goes out; the doorway stays off the build list until a torch is back inside, then is rebuilt
and the torch goes down.

The log reports "SEALED and LIT", or "UNLIT at" with the dark cells. While the squad holds the
base, its walls and roof are off-limits to digging: a soldier short of wood looks for a tree, not
the log wall it shelters behind.

When the platoon leader moves the squad on, the soldier inside nearest the doorway breaks a doorway out
(`bot.supply.Breacher`): two wall blocks, on the side facing the platoon leader's new objective, preferring
a side whose outside cell has room to stand and ground under it (`PatrolBase.Plan.doorway`). It
uses a pickaxe if it has one (1.5 seconds a block), bare hands if not (10). A fight only pauses the
base: the squad doesn't cut its way out mid-contact, and it keeps the site, so when the fight is
over it goes back to the hut it was building rather than laying out another in the open. A squad needn't remember its base to leave
it: a soldier the world shows shut in a small room (`PatrolBase.enclosing`: a flood fill from where
it stands, through gaps a body fits through, down into a hole and up a step with headroom, runs
out within 16 cells - a pit in the floor is part of a roofed room, not a way out, though a drop
in the open is: a trench by a wall is one a soldier pillars out of) cuts
its way out the same way, so a squad that wakes up sealed in after a restart, or walled in by a hut
it never finished, walks out - but not one down its mine, whose tunnels wall every soldier in them
(live, a miner at y=30 was taken for shut in, the mine for the squad's base, and its way out cut
from the stairs' top for an hour), nor anyone on the flatten's ground or its bench, walled by its
trenches and steps; one of either boxed in is the recovery drill's. And a soldier the squad's drill misses - reporting "no route" from a
pocket it can walk no more than 128 cells of by the route planner's own moves, under the walled
camp's own floor - breaks the wall
nearest where it's sent from where it stands (`bot.supply.WayOut`), keeping to that break until
it's made however its reports change, and never through a wall's line, the camp's included. Only
toward a goal within 3 blocks of its own level: one far above or below isn't a wall away, and the
way there is the recovery drill's stair (§7) - in the squad simulation a soldier in a gallery 25
blocks down broke tunnel after tunnel sideways toward its slot on the surface. A
pocket with a drop beside it is a ledge, not a box: that soldier builds its stair down instead: live, after a restart, three
soldiers stood a day in a three-cell pocket the drill never saw, the squad still holding its night
base's record; and three more in a trench between the camp's wall and old cobblestone heaps. And a
soldier stood in a block breaks it first, whatever its orders: one at its head, or one at its feet
alone that lasts two looks a second apart - a step it puts under itself mid-jump is at its feet for
a tick. Live, four soldiers stood a day and a night in dirt at the farm, suffocating as fast as they
healed; and two stood a day waist-deep in gravel fallen into the pocket at the camp mine's bottom,
heads clear, nothing to reach a stand from, every route "no route".

## 15. Securing the camp

*Patrol* (ATP 3-21.8, local security patrols): a squad holding ground it works on doesn't stand -
it walks a round so trouble is met before it reaches the workers. An objective's task `PATROL`
lays a round out: the corners and midpoints of a square 6 blocks out from the camp's ground, just
inside its wall line, each on the ground there. Each soldier starts at its own point of the round,
spread round it by its place in the squad, and walks waypoint to waypoint, turning for the next
within 2.5 blocks of one, reporting `Patrolling`. Contact interrupts it like any order. It never
reports DONE: the platoon leader gives the squad other work when the camp needs it more.

| Practice | Content | Source |
|---|---|---|
| **Secure** | A tactical mission task: prevent a unit, facility or location from being damaged or destroyed by enemy action. | ADP 3-90 |
| **Base camp** | An evolving area that supports operations over time, as against a patrol base held for a night. Security is continuous and improved. | ATP 3-37.10 |
| **Priorities of work** | Security first, then everything else, each with a measurable standard. | ATP 3-21.8 ch. 7 |

*How we use it:* a place the squads come back to - the camp, its patrol bases, its farm - is
secured with light, the Minecraft form of local security. An overworld monster spawns only where
block light is 0. A torch gives 14, falling by one a block, so ground lit everywhere grows nothing,
and the monsters that come are the ones that walk in from the dark beyond.

An objective's task can be `SECURE`. Each squad-leader cycle, `bot.supply.Lighting` plans the
torches the ground within 12 blocks of the objective still needs - of the camp's ground, for an
objective within 12 blocks of the camp, so the task lights exactly what the foothold's standard
measures (§ 17) - every cell the squad can walk to that isn't within 7 blocks (taxicab) of a torch. That's half a torch's reach, so a wall or a step
between them still leaves the cell lit. Torches go first on the lattice where taxicab circles of
that radius tile the ground exactly, then on any cell the lattice left dark. Torches already
standing count, so the plan shrinks as torches go down and a secured place needs nothing more.
The plan is worked out again every cycle, so a torch that's gone - washed away, broken, dug out -
is planned again. And the camp is kept lit whatever the task: by day, a soldier within 24 blocks of
the camp with torches in its pack and no other work - not farming, walling, planting or gathering
(a gather that's found nothing isn't work) - puts one down wherever the camp's ground has gone dark.
Farmland and leaves are left bare: a torch won't stand on either, and nothing spawns on leaves. Light is worked out from where torches stand, not read from the server's
light data.

No torch is planned on a wall's line, one laid out or the camp's still to be, where a torch
would hold a cell the mason needs: a cell beside the line is lit from the nearest cell off it, and
the line's own cells aren't counted dark. Each soldier takes the nearest planned cell no squadmate
has taken - nearest counting a block up or down as two across - walks onto it, given 20 seconds and
8 ticks a block of the way (live, cells down the mine 70 blocks off were given the 20 seconds a cell
beside it gets, and every one failed), and puts a torch down where it stands (`bot.supply.Lamplighter`). The server's block update confirms it. A soldier
out of torches makes eight at a time where it stands (`bot.supply.Torchmaker`, the same run as a
patrol base's), and reports `Securing` meanwhile. One short of the makings gathers them first, where
its squad last saw them nearest the objective: logs, then the cobblestone for a furnace (making
itself a pickaxe for it). One that
still can't make any reports CANTCO with why.
The log says how many torches are still to put down. A plan down to 2 torches or fewer that hasn't
changed in 2 minutes is done, its progress saying how many cells no soldier reached: live, a squad
stood a day in a lit camp for one torch it couldn't put down.

A lit camp still lets in what walks in from the dark beyond the light. An objective's task can be
`FORTIFY`, which makes the camp a deliberate base: a wall two blocks high - one more than a
jump - on a square 8 blocks out from the objective, laid on the ground of each of its columns, with
a doorway a column wide in the middle of its south side: the squads' way in and out, rather than a
step cut through the wall (`bot.supply.Perimeter`). It's laid out once, by whichever squad comes to
it first, and shared, and it's kept by the objective's x and z, not by the ground it was laid on:
that ground moves as the pits are dug and the wall goes up, and laid out again it would lose what's
built. So the
layout is kept on disk too (`app.WallStore`, under `minecraft.state-dir`), and a restarted garrison
builds the wall it laid out before rather than a second one on top of it. The foothold's walled
standard (§ 17) reads it from there too, whether or not a squad is ordered to FORTIFY that run -
of the layouts kept round the camp, one for each place FORTIFY was ordered, the one the world shows
most built, then most of stone. And FORTIFY ordered near the camp works that same layout, so the
standard and the work are one wall. Each layout is written whole or not at all. Its
cells join the ones every dig skips, so nobody quarries it, and so does the camp's ground inside
it, solid when the wall is laid out: a dig takes the nearest dirt first, and a pit outside the wall
would otherwise eat in under it. Where the ground under a wall cell has gone anyway, the cells
down to what's left are its footing, built first (`Perimeter.open`). Each soldier takes the nearest open cell no
squadmate has taken that has a block to go against - so each column rises from its own ground,
and on a slope the rows stand at many heights - stands just inside it (or just outside, or a
column along either way, where the camp has been built over its own; never one it lately couldn't
get to), within 0.15 of the place's centre as a hut builder does, and puts a block there,
cobblestone first, then dirt, breaking first whatever stands in the cell that a block can't
replace - a torch, a flower (`bot.supply.Mason`); one down to fewer than 4 digs dirt outside the
wall, each mason on its own side of the camp so no one pit is dug out under all of them, and once
digging it digs a load of 32 before it builds again, rather than walking back with every fourth. A cell that fails - its place not walked to, inside an old hut or across water, or its block
refused - it passes over for two minutes, longer each time it fails again, and builds the rest. On top goes a lip, a
ring of blocks one out from the wall's top row, against its outer face: an overhang a spider
climbing the wall can't get round. None outside the doorway, where it would stand at head height
in the way out. A mason places each lip block from the column nearest it just inside the wall,
never from the wall's top; where the ground inside has been dug away, from the column just outside.
A stand counts only if a route reaches it from where the mason stands, as it gets about - on foot,
pillaring on the blocks it carries, cutting steps with a pickaxe: one sealed in a hill is passed
over at once for the next, rather than walked at for 400 ticks by every mason in turn.
A mason with blocks and cells left that it can't build says why: how many have nothing to go
against, and how many nowhere to stand. The log says how many blocks are still to place. Seen live in `wall-the-camp`: two squads close a 128-block wall
together.

Fortifying starts with the sky. Zombies and skeletons burn in daylight only under open sky, so a
tree over the camp is shade for whatever the night left inside the wall.

The ground work is handed down as concrete commands, not "level the camp": the engineer's two
boxes (`command.Groundwork`) - a dig box from the camp's feet level 24 up, and a fill box from 16
below up to the ground, both inside the wall line - and the squad's drill (`command.Tasking`)
splits the work left in them into a strip for each soldier. Digging first: while anything in the
dig box is left, each soldier's command is `Order.Dig(box, except)`, every block in its strip that
breaks by hand within 20 seconds (`Clearing.diggable`) - soil, stone, sand, glass, planks, a chest
or a table left there (operator, 2026-11-13: the squad's own are `except`; live, six tables and two
chests left on the keep held eight columns of it, 126 blocks, since what a dig leaves, the ground
under it is left too) - and
the cobblestone put down over the floor - old huts' walls, pillar steps - which shut the camp's
chest in on three sides; that, and the steps the soldiers have built and not yet taken down
(`SoldierRunner.scaffolding`, known by where each went, since a dirt step reads as the ground's
dirt; a gatherer never digs one for its dirt either), only while no wall is going up,
since the masons stand on steps inside it. Then filling: `Order.Fill(box, except)`, every open cell in each
column of its strip from the top down to the first solid block or water. The strips are cut from
where the work is left, not the whole site, so no soldier is handed a strip with nothing in it.
`except` is what neither touches: the squads' huts, built or going up, the workshops, the farm, the wall line, the
mine - the 3x3 columns round its head, where the night shift cuts it, and its walkway and
landing - so it's the camp's whatever the squads' tasks. A soldier reports `Working(command, cells left)`.

A dig (`bot.engineer.Clearing.toClear`, `bot.supply.Clearer`, a `Digger` job; it outranks the
mason for the hands) fells a tree from the bottom and takes the ground from the top down. It
stands on the ground, never on the tree or the block, and reaches a block whose nearest face is
within 5 blocks of its eyes: from the ground, six up. Leaves out of reach go by themselves once
their tree's logs do, since cut-off leaves decay. One already up in a canopy cuts what it reaches
from where it stands, never the block under its feet, and so works its way down: sent to stands
on the ground, a soldier in a canopy over the camp had no way to them. One on the force's own
steps does the same, the step under its feet too - straight down onto known ground - and so
takes its climb down with it (live, a builder on its steps, sent to a stand beside the step under
it, built another to reach that: twenty steps in two minutes); a hump of ground is still dug from
beside it, not from on it. A block it couldn't dig - no tool for it, out of
reach - it leaves for longer each time (`Setbacks`). FORTIFY isn't done while the dig box holds
anything: its progress reads "N blocks over the camp's ground to dig" before the wall's count, so a
tree too tall to fell from the ground shows as the task stalled.

A wall of dirt isn't finished: an enderman picks a dirt block out of it and opens a hole. So each
of the wall's blocks of dirt or grass is replaced with cobblestone (`Mason.repoint`): a mason
carrying some, with no open wall cell it can build, stands inside the wall by the block, digs it
out by hand for the server's break time, and puts stone in its place, each step confirmed by the
block update. "The camp walled in" isn't met, and FORTIFY isn't done, while any dirt is left in
the wall - but for a block buried, covered over with the camp's ground filled in against it,
which opens no way in if taken and which nobody can reach; its progress says how many blocks. A mason on it with no cobblestone, the wall
otherwise closed, draws some from the stockpile when it was last seen holding stone, or quarries
some where its squad saw stone nearest the camp - as one short of blocks for a wall going up digs
dirt. One soldier is the wall's while a closed wall has dirt in it - the one carrying most stone,
then the nearest - and is off the camp's ground work meanwhile: that work outranks the mason and
the quarry for a soldier's hands, and fills with the stone the wall wants. The rest keep the
ground. A gather's stands are ones the soldier can get to on foot, on the blocks it carries or
cutting steps - from down a pit it was filling, too.

A fill (`Clearing.toFill`, `bot.supply.Filler`) puts cobblestone or dirt in its strip's cells,
the lowest it can reach first, nearest first, so a pit under the wall line becomes footing again;
a block put in high can seal a pocket of air under it, underground and harmless. Never a cell over
its own head - above its feet in its own column or one beside it: it fills round itself at its own
level and stands on what it laid; in the squad simulation a soldier filling a pit from inside
walled itself into a shaft. A cell it can't
reach, or whose stand it doesn't get to within 20 seconds, is skipped for the next. A flooded pit
is filled too, its water the floor: capped from the rim, since nobody stands in water to fill it
from below - live, the camp's mine shaft flooded with soldiers afloat in it. And no dig breaks
ground with water or lava beside or above it (`Clearing.letsInWater`, for every dig): opened, the
water flows into the hole, which is how that shaft was flooded. A dig goes toward what's known
(`Clearing.safeFrom`, operator 2026-11-13): to the side always; the block a soldier stands on only
with solid ground a block under it, so the drop is one block - its own pillar comes down that way;
the block over its head only with nothing over that to fall, sand or gravel. The world's chunk data
is what's known: every block of a loaded chunk, water and lava included, is seen before a cut. The
one block a soldier writes into that model itself is a step it has just placed, as cobblestone, so
it lands on it before the server's update says what went in (`ChunkCache.standIn`) - over an open
cell only, and taken back after two seconds unconfirmed: a placement the server refused is never
confirmed, and live one held in the model for hours as the top of a keep builder's strip, every
strip beside it waiting on a block nobody could dig. The reverse case - a block the server has and
the model doesn't (backlog/world-model-stale.md) - is read from the body: a soldier stuck short of
the same cell three times (`DeterministicSoldier.SNAG_TRIES`; stalled at it, or put back by the
server) has hit something, and the model takes that cell as stone until the server says otherwise -
a dig timed for stone outwaits anything softer - and the soldier breaks it at once, since what
stopped its body three times is in its way; live, marked and left to the planner, it stood on with
"no route". Not when a squadmate stands in the cell - live, two soldiers held each other in a
one-wide passage of the camp's mine for a quarter of an hour - and taken back if the server never
breaks it, or the phantom would wall the soldier in for good. Live, one soldier down the camp's mine was stuck short of one cell 161 times in a
quarter of an hour. Progress reads "N cells under the
camp's ground to fill". Live, a pit 13 deep under the east wall line left 42 wall cells nothing to
stand on.

Its blocks are the squad's own: no supply is issued for the fill. A soldier with fewer than 4
blocks is sent off base instead, `Order.Gather(SOIL, box, 32)` - a borrow pit 6 wide and 3 deep,
3 out past the wall line on its own side of the camp - and keeps at it until it carries 32, so it
doesn't walk back for every fourth block. A mason back outside the wall line from a dirt run builds
from the outside first, and claims its stand with its cell, so two masons never contest one stand.

A soldier with nothing else to do keeps the camp the same way, whatever the squad's task - but for
leaves more than 5 blocks over the camp's ground, which decay once their trunk is cut (live, six
soldiers stood under a canopy 11 blocks up with no log under it) - by day,
out of contact and within 24 blocks of the camp, and within 6 of its ground's height, not down
the mine under it - or by night inside the wall line of a camp lit
to within 8 dark cells, since nothing spawns on lit ground, if it's armed and at half health or
more and its squad isn't sent to its patrol base: phantoms come down out of the sky on anyone
under it, and live one killed an unarmed soldier at 4 health digging the camp - with no other work handed to it, it is
handed the same drill's commands round the camp (`SoldierRunner.campUpkeep`), so an idle squad flattens the
camp rather than stand in it. Live, three soldiers stood a day in a lit camp with their task all
but done. A gather that found nothing within reach isn't work for 5 minutes - a squad sent for
torch makings with no tree in reach keeps the camp meanwhile, while one carrying torches or their
makings lights the camp first - and a soldier on a ground-work
command reports `Working`, between one cut and the next too, so a CANTCO it is waiting on isn't
raised anew every cycle.

## 16. Mining down

| Practice | Content | Source |
|---|---|---|
| **Engineer reconnaissance** | Engineers check a route for obstacles and hazards before a unit uses it, and report what stops them rather than guessing past it. | ATP 3-34.81 |
| **Class IV and the supply chain** | Construction materials come from where they are, and the supply chain carries them to where they're needed. | FM 4-0 |

*How we use it:* stone near the surface runs out round a camp, and iron and diamonds lie deep.
An objective's task can be `MINE`: the squad cuts a staircase down to its y, the only task whose
y is a depth rather than the ground's height. The stairs go in the chunk holding the objective's
x and z, or the nearest of the eight round it, where every step can be cut clear of water and lava
and over a floor, as the loaded chunks show them (`Staircase.siteWell`). Beside a pond, the shaft moves off it rather
than stopping at its first step. A head is on the ground: the highest standing cell near the
camp's ground level that isn't on a tree, so an oak grown over the head doesn't lift it onto the
canopy. When none can be sited, the log says why at the objective itself - the first step's
water, lava or open cave.

Nothing is built in a staircase's steps: every squad's stairs and the camp's are kept open
(`WorldView.noBuild`), and no route puts a pillar or a step in them - live, soldiers crossing a
shaft's open top put their steps into its stairs, and those left there blocked the way up, the
miners on them stranded below.
The camp's staircase is kept on disk, as the wall and the farm are (its head and last step,
`app.WallStore` kind `stairs`), and after a restart it's cut on down where it was sited rather
than sited again - as it is for any mine ordered within 24 blocks of the camp, the platoon leader's
point being a guess: live, it moved the order to the camp's centre, and a new staircase started
inside the wall was kept in place of the one cut to the bottom. Sited afresh each time, the ground over a part-cut shaft reads differently -
a block dropped on the head, a soldier standing down the shaft - and each new staircase is
started across the old ones, until no clear one is left near the head.

The staircase (`bot.supply.Staircase`) is cut to a standard (operator, 2026-11-15): a 4x4 well
centred in its chunk - chunk-local x and z 6..9, as `floorMod(x, 16)` reads them, so world 6..9 in
chunk 0 and -10..-7 in chunk -1 - round a 2x2 pillar at 7..8, its walkway the twelve cells round
the pillar, one block down a step, twelve a turn. Its north-east corner, chunk-local (9, 6), comes
round at every y = 12k, and the landing is there: the deepest y = 12k no deeper than asked, so a
mine ordered to y=16 lands at y=12. Each step clears three cells: the feet, the head, and the head
room a body needs to step down onto it. Its floor is left standing. Mines cut before the standard
keep the 3x3 spiral they were cut to - eight cells round a one-block pillar - kept on disk under a
key of their own (`well ` before a well's). The cutter keeps its well whole: an open cell in the
pillar, the wall or the floor beside a step it cuts, under rock - something solid over it within
24 blocks, so a cave's mouth and not the open hillside the top steps cut into - is filled as it passes, and ore in the wall is
mined, its vein followed, and its cell put back in cobblestone once nothing more is in sight - a
hole the server won't take is left after 30 seconds. A soldier walks down it and jumps back up it, with no ladder, and a slip is a fall of
one block. It's cut no deeper than y=-58, clear of bedrock.

One soldier, the first in squad order carrying a pickaxe, cuts it (`bot.supply.Miner`). It stands
on the step above the first not yet cut, breaks that step's cells, walks down onto it, and cuts
the next. By day the rest hold the top, clear of the shaft. By night they follow it down, within 3
blocks: the top is open ground in the dark, and on the stairs no monster spawns within 24 blocks
of a player. With the mine cut to the bottom, a cutter down at the landing for the night works it
again for ore: any it can see from a cut's stand - passed by, or not harvestable with the pickaxe
it had then - is mined before the mine counts as done (`Miner.prospect`); live, no miner mined ore
in twelve runs, the tunnels cut and nobody going back down them. From the top, a mine cut to the
bottom is done, and the squad holds the top: worked from there, the squad's task stayed open and
its soldiers were sent to the bottom by a route that failed. With nothing left to cut or mine, they wait the night out at
the landing. Until one of them has a pickaxe, every one makes one.

Nothing is crafted on the stairs. At the bottom, beside the main tunnel's first cell, the cutter
cuts a **landing**: a recess each side (`Staircase.landing`), the mine base's table and furnace
(§11), never dug out again once they stand - in a recess only: a table or furnace standing in a
step or a tunnel is in the way and is cut like any block (`Staircase.Cut.toCut`; live, the camp's
stairs were laid through its workshop's table, and the miner stood at step 1 of 184), and only a
chest, whose contents would spill, is left wherever it is. A step on, beside the main tunnel's second cell, it cuts
the **store** (`Staircase.store`): the mine's stash, a chest in its recess. A soldier down the mine
carrying 8 or more raw iron, raw copper and diamonds fits the chest if it isn't standing - as a
workshop's is (§ 12), from a chest carried down or with 4 logs or more, so a pickaxe's handles are
left - and puts them all in, and goes back to the cutting, rather than up the stairs
with every pack; coal stays in its pack for torches. What the stash holds is the mine's, logged as
`[Mine] the stash at ... holds ...`, not the camp's stockpile - filled by night only. By day a
soldier still down the stairs with room in its pack draws the stash's raw copper, then its iron, a
stack at a time, and carries it up: the metal pool puts what it doesn't need into the depot, where an
armourer draws it (operator, 2026-11-15: raw material up the stairs so everyone can use it; live, 42
raw copper lay in the stash while nine soldiers of sixteen had no armour). The stairs and tunnels are lit as they're cut, so the
mine spawns nothing: a torch on a step once a turn, halfway round, one at the landing's stand, one
every 6 blocks along the main tunnel and one 4 blocks into every other pair of branches - within 7
blocks of every tunnel cell (`Staircase.lights`), put down by the squad holding the top - those carrying torches, or the
makings and a furnace's 8 cobblestone, so lighting sends nobody off with nothing to make them
from. A cutter wanting a tool goes to its workshop - the mine base once the landing is cut, the
squad's at the top until then - and the stairs wait while it makes the tool there. The tool is the
ladder's next rung, or a stone pickaxe once it carries the cobblestone, since iron ore drops
nothing to a wooden one. A soldier
whose pickaxe wears out is no longer the cutter, and the next with a pickaxe takes over; the worn-out
one makes a stone pickaxe from the cobblestone it carries, or a wooden one from logs if it has
fewer than three. The stairs and tunnels are claimed (`Staircase.walkway`):
no table or furnace is put down in them, and no other soldier digs them. The cutter's hands are
free between blocks, so a routine ranked above mining - making its kit - gets its turn.

The foothold's stone-and-iron standard (§ 17) reads the camp's kept staircase from the world -
the steps from the top that stand open (`Staircase.stepsCut`) - as well as a cutter at work, so a
mine cut before a restart counts after it.

The camp's mine is the night shift's work (docs/echelons/platoon-leader/operator-rules.md). Once the
stairs reach the iron, their tunnels are the mining. The stairs' state is the world's, so a run stopped by a fight or a restart picks up
where they end. A dig waits out the block's own break time: its hardness, and the pickaxe in hand
against the least tier that gets its drop. Both come from the server jar (`bot.world.Hardness`;
DEVELOPMENT.md § 7). Ore it can see on the way - beside its body or the step it cuts, outside the
shaft's 3x3 so no step or pillar is holed - it mines first, when its pickaxe gets the drop and
nothing liquid lies behind it; one the server won't break in 30 seconds is left. The cutter reports
`Mining` with its step. A step over an open cave is floored as a tunnel is (below), and the stairs
go on; live, the stairs met a cave at step 11 and stopped, the CANTCO went unanswered, and the mine
under it was out of everyone's reach. It stops with CANTCO where going on would let in water or
lava, or over a cave it can't floor - nothing carried to floor it with, or wider than 24 blocks.

At the bottom the cutter goes on into the tunnels the ore is mined from: a main tunnel 24 blocks
straight out from the last step, away from the shaft, then strips off it both ways wherever the
world's coordinate along it is a multiple of 3 (`Staircase.BRANCH_EVERY`; the rules of a mine's grid),
each 8 long, so every block between two strips is seen from one of them. All are three high
(`Staircase.TUNNEL_HEIGHT`), at the level's depth, with their floors left standing. A squad's first
two with a pickaxe cut its level, one to each side of the spine (`SoldierRunner.CUTTERS_A_LEVEL`):
the first the stairs, the spine and its side's strips, the second the other side's. Ore is taken as it's seen: touching a
cut, or anywhere in reach (4.5 blocks, two across) with a face open - the rest of a vein behind its
first block, a cave's wall - nearest first, so a vein of coal or copper is mined out, not just
where it meets the tunnel. Ore under a tunnel's floor is
left so as not to hole it. A tunnel over an open cave is floored: cobblestone put in the gap under
its next cell, against the solid block beside it - or, in a wide cave with none, against a block put
beside the gap first, built out from the stone it touches - and the tunnel goes on - 24 blocks a tunnel at the
most (`Miner.BRIDGE_TRIES`), with cobblestone or else dirt; with neither carried the tunnel waits
while the others are cut, and their stone floors it. Live, every branch met a cave and was given
up, and the squads spent their nights with nothing left to cut. A tunnel that would let in water or lava, or whose cave is
wider than that, is given up alone, and the rest are cut; so are branches off a stretch of main
tunnel that was given up. The camp's mine standard (§ 17) is met once the stairs reach the bottom.
Its yield is checked (docs/conventions.md, check the outcome): at dawn, the ore blocks the mine's
cutters took through the night against 16 (`SoldierRunner.NIGHT_ORE`) - "[Outcome] the night's
mining: 3 ore block(s) taken, 16 expected - SHORT" - and a short night says each mine's level, length and whether its
cutters were all done. A mine is worked while ore is wanted - the force short of iron, or any
soldier in less than a full set of armour: worked out, its main tunnel is run on a stretch, and
past its last stretch it's cut a level deeper off the same stairs, 16 blocks down, a fresh main
tunnel there (`Staircase.deeper`; operator, 2026-11-16: go deeper, or pick other sites) - "[Outcome]
the mine at ... is worked out at y=16 - its next level cut at y=0" - down to just above bedrock; past
that it's said dried up, once - by a squad's second pair too, when its own tunnel is run to 192 and no level is left under theirs, and blocked when water or lava beside a step of its stairs stops any cutter on it (live, a fresh well stopped at its 58th step, every level under it); water or lava a cut meets is blocked first with the junk the cutter carries - from the cut's stand, walked to if it's seen from down the tunnel (live, one closed a tunnel at water 70 off and blocked nothing) - and that path closed for good - a tunnel given up, the stairs no deeper (operator, 2026-11-17) - and
a site where no well can go down at or round it, every chunk seen, is done with too (live, a hill over an open cave); a squad the flatten doesn't want, with nothing short, cuts the next site's stairs by day ahead of time (`ChainOfCommand.nextMineSite`; operator, 2026-11-17: dig deeper, but also cut the next mine); its well is kept on disk as dead, never sited again even from the chunk beside a fresh site (live, a mine moved 32 west was sited back in the blocked well next door, and moved on again uncut), and the keep's mine moves on (a squad's Mine task reads DONE only when every one of its cutters on its level is done; and by day any of the squad still down its last mine, within 64 of its head, climbs its stairs to the head before the day's work - live, a squad at a fresh mine's landing at dawn was sent to wedge places in the rock): the night shift and a squad released for iron are sent to a fresh site, the nearest the
keep untried on a 32-block grid round its first mine, up to 192 out every way, its head 8 clear of
the flatten site and its bench (`ChainOfCommand.onMineDriedUp`, "[PL] the keep's mine at ... is dried
up - the night shift's mine moves to ..."; the site and those tried kept on disk, so a restart keeps
it where it went - live, stepping only west put it 160 blocks off past a lake, a blocked well and a hill). A mine off the camp is headed on the ground there, read from the
sky down at the nearest column within 8 of its site that reads - a lake's or lava's column reads
none, never the cave under its bed (live, a site's lake read through to a cave at y=24) - and its
workshop, before the stairs are cut, on that ground, not at the order's depth; with none read, nothing is sited
until one is, never at a soldier's height (live, a squad down the old mine sited the new one's head
in its tunnels at y=-23). A staircase kept on disk - the camp's or another - with its head 16 or
more under the ground over the head itself is let go, every squad's level off it with it, and
sited again: a shaft is open over its head, a buried head has the rock over it. A mine worked out - every tunnel cut, the ore in sight of them taken - while nobody carries an iron
pickaxe and the force hasn't the three iron for one is run on: its main tunnel 24 blocks further,
branches along it, up to 192 (operator, 2026-11-17: a mine is run wider before it's left - each move cost a night of stairs and a walk back), a stretch at most every half minute a mine (`Staircase.extended`, `[Squad] MINE: worked out, the force short of
iron - its main tunnel run on to 48 blocks`), and kept on disk by the tunnel's end. Live, the mine
read DONE all night with one raw iron in the force.

**The rules of a mine (operator, 2026-11-16; standing - the mine is built to them, and a change
that breaks one is a step back):**
1. **Work in parallel.** Each worker has a share no one else touches, held till it's done; nobody
   waits on another or stands in another's way; a little coordination - who has which share -
   and no more (docs/echelons/tenets.md, work in parallel).
2. **A grid that sees every block, with the least digging.** A level is at a height a multiple of
   4 (y % 4 == 0): a spine out from the shaft's landing, and strips off it both ways at every third
   block of the world's grid along it - x % 3 == 0 for a spine along x, z % 3 == 0 for one along z.
   Every tunnel is three high (y to y + 2), so the two columns between strips are each seen from a
   strip, and the layer between two levels (y + 3) is one's ceiling and the other's floor: every
   block seen once, none dug twice (2026-11-16).
3. **Two soldiers to a level, each to a side.** A level is worked by two: one cuts the strips on
   the spine's one side, the other the other's - never two in one strip. A squad of four takes two
   levels (2026-11-16).
4. **The whole vein, then backfilled.** Ore seen in a wall is followed block to block while it's in
   reach, and the holes it leaves are filled back with the cobblestone or dirt it carries for that -
   the grid's strips themselves left open - so the walls stay whole and nothing spawns in them.
5. **The ore comes first in the pack.** Cobblestone and dirt are carried only to what the backfill
   takes, about a stack; past that they're left, never carried at the cost of a slot for ore,
   ingots or tools.
6. **Torches enough.** Every miner goes down with torches enough for its strips, and lights them as
   they're cut.
7. **The yield checked.** Each level's ore is checked against what it should give, and one dried
   up is said so (docs/conventions.md, check the outcome).
Built today: a squad to a level - each squad on a mine off the camp works its own level of the
shaft at y % 4 == 0, the base's if nobody else does, else the first free one under every other
squad's, kept on disk by squad; a well's level runs out through the wall its walkway's on at that
height, so levels one over another run east, south and west in turn; a level's crew is its own
squad's cutters, and a level worked out goes to the next free one under the rest. The grid:
strips on the world's x % 3 or z % 3 off each level's spine, three high, its two cutters one to
each side. Ore in reach taken, holes in the well filled, the tunnels lit, the yield checked at
dawn. A mine's cutter keeps only its backfill's bulk with its pack full - a stack of cobblestone
and half of dirt (`Pack.MINER_COBBLESTONE_KEPT`) - the rest thrown before anything else, so a slot
for ore is never taken by it. A cutter under 8 torches makes up to 32 from the coal it mines
(`SoldierRunner.MINER_TORCHES`). A vein's holes, on the stairs or off a tunnel, are filled
back once it's taken, never one of the mine's own cells nor one the force keeps open - every squad's
stairs (`WorldView.noBuild`): live, a step under one squad's level was found solid, and two
soldiers stood above it with no way down to theirs. A squad with three or more carrying a pickaxe takes a second level, the
next free one under every other's, for its second pair (`SoldierRunner.secondLevel`), kept on disk
as the squad's second and moved to the next free one under it when it's worked out - by its own
crew's word, once they've had it 30 seconds: a crew newly given it reports what it last did on
another (live, a second level read worked out the moment it was given, and was given again every
cycle).

Squads on one mine each work their own level (the rules of a mine, above): live, squads sharing
one level's stretches took one another's stale DONE for their own, and it was run on with nothing
cut.

A mine is shared among the cutters on it - every squad's, in order by name (`Staircase.share`):
the first cuts the stairs and the main tunnel, and the branch roots along the main tunnel are cut
into contiguous stretches, one a cutter, both sides, so each works a part of the mine of its own.
The rest wait on the stairs, each a step of its own - three behind its cutter and up, then the
last steps above the landing, and with too few steps cut for the line, the rest beside the head,
four abreast - but never one with no pickaxe, which stays at the head, beside the shaft, making
itself one if it can (operator, 2026-11-17: we can't go into the mines without a pickaxe - be
prepared to mine; live, three with none and two of the farm squad stood the night on the stairs,
the cutters queued behind them, and the night's ore was 0 of 16) - until the main tunnel is cut through - started from
a root its cutter still stood on, two stood on one cell - and the mine is worked out only when
every cutter's share is. Live, four squads' cutters each worked the whole mine and stood on one
another.

## 17. The lodgment

| Practice | Content | Source |
|---|---|---|
| **Airhead** | After the drop, the assault force seizes objectives and consolidates an initial airhead, develops it out to an airhead line it can hold, then builds up combat power within it. | FM 90-26 (superseded by FM 3-99) |
| **Lodgment** | An area in hostile territory that, seized and held, makes the continuous landing of troops and materiel possible and gives maneuver space for subsequent operations. | JP 3-18 |
| **After-action review** | A review at the end of an action of what was meant to happen, what did, why, and what to do differently. | TC 25-20 |
| **Unit status** | A unit reports its readiness as a C-rating, C-1 (fully ready) to C-4 (not), from the share of its personnel and equipment on hand and fit: at least 90, 80 and 70 per cent for C-1 to C-3. | AR 220-1 |
| **Excavation protective systems** | Each worker in an excavation is protected from cave-ins by sloping (its sides cut back by soil type: vertical in stable rock, 3/4:1 in Type A, 1:1 in Type B, 1 1/2:1 in Type C, under 20 feet deep), benching (its sides cut in steps), shoring or shielding - unless it's entirely in stable rock, or under 5 feet deep with no sign of cave-in to a competent person. | 29 CFR 1926.652(a)(1) and Appendix B, Table B-1 (OSHA) |
| **Means of egress** | A trench 4 feet deep or more has a stairway, ladder, ramp or other safe way out within 25 feet of lateral travel of every worker in it. | 29 CFR 1926.651(c)(2) (OSHA) |
| **Spoil and loads at the edge** | What's dug out, and equipment, is kept back from the edge - at least 2 feet - so it can't fall or roll in. | 29 CFR 1926.651(j)(2) (OSHA) |

*How we use it:* a garrison's camp (`minecraft.camp`, as `x,z`) is built up the way an airborne
force builds a lodgment after the drop. Its phases:
1. Shelter: a patrol base each night (§14).
2. The airhead - safety first, the operator's: the camp's ground lit (§15, SECURE) and walled
   (§15, FORTIFY), and every soldier armed with a stone sword or better (§11, STONE). Food that
   grows (§12, FARM) is buildup, unless survival is at stake - more than half the soldiers hungry
   with nothing to eat, or one starving at death's door (food 0, health 6 or less, no rations) -
   when it stands here beside the wall, and the platoon leader does what it takes to eat
   (PlatoonLeaderScenarioIT's `starving-airhead`). Food isn't met while more than half are hungry,
   however much is planted - wheat feeds nobody for days - and the standard names where the
   camp's farm ground is, so the squad is sent there. With no logs
   carried to make torches from and no tree in sight, the lit standard's task is TREE_FARM: the
   wood is grown first.
3. The buildup, in this order: food while the force eats; wood that grows back (§12, TREE_FARM),
   right after it - 16 trees growing in the camp's grove and the keep's together (operator,
   2026-11-14: a camp goes through a lot of wood); stone and iron from a mine (§16, MINE); and
   last the camp's floor paved (FORTIFY): once the wall is closed and of
   stone, its repointer replaces the soil of the floor inside it with cobblestone, block by block,
   at the camp's measured height - until the base is flattened to one height above sea level
   (backlog/cells.md).
4. Armour: every soldier armoured (§11).

Every site is measured the same way, in the lodgment's levels - SHELTER, AIRHEAD, BUILDUP,
ESTABLISHED, SEMI-PERMANENT (`command.Site`; backlog/maturity-model.md) - each kind filling in what
a level means there. A site is at the highest level whose standards, and every lower level's, are
met, up to the highest level it's measured for; a level with no standards of its kind is met as it
stands. The camp's are its phases above, phase 4 being ESTABLISHED. The keep's: AIRHEAD, the depot
stocked - its chest standing and 16 logs between its builders and the stockpile; BUILDUP, the ground
flattened and the slab laid. The mine base's: SHELTER, the stairs cut down; AIRHEAD, its landing's
table and furnace. The log says each site's level when it changes (`[Sites] the keep: AIRHEAD;
BUILDUP open: the ground flattened - 1846 blocks to dig`), and the dawn's after-action review lists
them.

The force's supply is counted the Army's way, in days (`command.Supply`; FM 4-0, FM 10-27): each
class's stock - Class I the rations every soldier carries and the stockpile holds, Class IV the
logs - over its day's burn. Upkeep is measured, not guessed: every fall in a class's stock between
two foothold checks is consumption (eaten, worn out, lost) and every rise production, which isn't
counted; a day's upkeep is the falls of the last three in-game days over the time they span, at
least one. Its levels follow: the safety level two days of upkeep; the reorder point that and what
burns while more is made (a day, first figures, for wheat and for wood); the stockage objective that
and a day of the work committed - for Class IV, the logs the keep's estimate draws a day (below). At the
objective a class is ADVANCE, below the safety level PAUSE, HOLD between; the log says each change
(`[Supply] Class I (rations): 340, 4.2 days at 81 a day; safety 162, reorder 243, objective 243 -
ADVANCE`), and the dawn's after-action review lists them. The platoon sergeant acts on it (§ 4).

The keep's mission estimate (`command.MissionEstimate`; ADP 5-0, FM 4-0's logistics estimate) is
worked out at each foothold check: its work - the blocks left to dig and the ground's open cells to
fill, all 1,089 of its top until the ground is flat - at the rate measured from that work's own falls; its bill of
materials - a stone tool for every 132 blocks dug and a log's handles for every four tools, the
slab's cobblestone kept in chests till it's laid, two logs a chest, and its days' rations at the
force's measured burn a soldier, and with ore set aside (§ 11) and no iron pickaxe in the force,
an iron pickaxe; and its shortfall, the logs the bill draws beyond those above Class IV's reorder
point, and the iron the pickaxe takes beyond what's carried and stockpiled. Feasible with none; otherwise "PREPARE first". The log says when it turns
one way or the other (`[Estimate] the keep: 1846 blocks to dig, 4096 cells to fill - 2.3 days; bill
...; PREPARE first: 6 logs`), the dawn's after-action review lists it, and the logs it draws a day
are Class IV's committed burn.

Every ten seconds the runner measures each phase's standards at the camp - from its ground, the
typical height of the ground round it found once, so a heap of old huts at its centre is neither
where the wall is laid nor where the light is measured - from the world rather
than from reports: dark cells left, wall blocks left, farm cells growing wheat and soldiers hungry
with nothing to eat, saplings and trees planted, steps cut down the mine, swords and armour
carried (`command.Foothold`). The platoon leader reads them in `<foothold>`, which ends by naming the
earliest phase not met and its open standards, one squad on each, by day. The log says each time a
standard moves (`[Foothold]`). Like the squad leader's terrain lookup, this is the arithmetic done for the
model, so its judgement goes into who does what, not into working out where the camp stands.

Safety goes with the ground, not the squad. Each squad's readiness (`command.Readiness`) is the
lodgment's phase where it stands: at the camp - its middle soldier within 24 blocks - the phases the
camp has met (SHELTER, AIRHEAD, BUILDUP, ESTABLISHED); away from it, SHELTER if it is sealed in a
patrol base and otherwise EXPOSED, the first night of an air drop, whatever the camp has. Beside it
is a C-rating, the worst of the shares of the squad armed, armoured, fed (at food 18, where health
comes back) and above half health. Both are worked out from where the soldiers stand and what they
carry, and the log says each time either changes and the change holds three SITREPs running
(`[Readiness]`) - soldiers ranging in and out of the camp would flip it back and forth. The platoon leader isn't shown them:
with the foothold in view it already keeps an exposed squad near shelter (PlatoonLeaderScenarioIT's
`exposed-far-out`).

Each dawn the log carries an after-action review (TC 25-20) of the day and night before (`[AAR]`,
`app.AfterActionReview`): the camp's standards, met and open, each scored over the day - met if it
held at 90% or more of the day's foothold checks (`AfterActionReview.STEADY`), with the share it held
and, if open now, how far along; the deaths, by the server's cause, with who; and who raised an idle
alarm by day (§ 3). Scored at the dawn's one look, the lit ground and the wall, going in and out of
met 36 times a run, decided the day by when the look fell.

The same dawn scores the day on the garrison's scoreboard (`app.Scoreboard`, `scoreboard.jsonl` in
the state directory, one JSON line a day kept across restarts): the earliest phase not met over the day, the
standards met of all, the open ones earliest phase first, the deaths, the shares of soldier-time
standing about (`Idle`, `InPosition`) and unable (`CANTCO`), the soldiers hungry at dawn, and how
hard the camp is to get about (`bot.nav.RouteEffort`): the walk on foot from its centre to the
workshop, the farm and the mine head, against a straight walk over level ground - its share of
the straight line, and the blocks climbed and dropped on the way, measured each foothold check
and logged as they change (`[Routes]`); and the work churned - taskings ended unfinished within 30
seconds of being given (`TaskRegister.CHURN_MILLIS`), work taken back before it could get anywhere.
The log's `[Scoreboard]` line sets the day beside the day
before and names the earliest open standard - the next thing to work on.

The main base is built beside the foothold, which stays the base until it's done
(backlog/cells.md): first its keep (`command.Keep`), the task `KEEP` - x and z from -16 to 16,
taken down to y=63 (operator, 2026-11-15), its floor walked at feet 64. Flattening is a primitive
of its own, not the keep's (operator, 2026-11-15): the task digs whatever ground is set to flatten
(`command.Flatten`, a rectangle and a level) - the keep's first ground to begin with, then any the
operator names with `!flatten` - with its depot off the site's west edge, sited again when the
site moves away from the one standing. Its crew is sized to its level (backlog/flatten-campaign.md):
at each new level the foreman says the builders its blocks want, one to 24 blocks
(`SoldierRunner.BLOCKS_A_BUILDER`) of the widest of it and the three levels under it
(`SoldierRunner.CREW_LOOK_AHEAD`) - live, a hilltop's last blocks let all but two go, and the next
level, 1,038 blocks, was dealt to those two - and only as many squads as that takes are sent to the ground, in
the order of battle, the farm squad never; the rest are released to what the force is short of
(operator, 2026-11-16; `PlatoonSergeant.shortfalls`): the camp's farm while a squad is Class I RED
or the surface chests hold under the bread reserve, the nearest trees seen while the keep's
builders ask for logs or the chests hold under the logs' reserve, the keep's mine while they hold
under 24 raw iron - every surface chest's last look summed, not one chest's. With more than one in
alarm the released squads are dealt across them in turn, food first, so an odd one out or a lone
squad farms (operator, 2026-11-17: more people means more throughput, not less latency - the wheat
and the trees grow at their own pace, and more hands in parallel bring in more at a time) ("[PL -> Bravo] released from
the flatten ...: to the force's IRON shortfall"); stalled on that work - trees already cut,
"nothing left to gather" - it's given the platoon leader's objective instead. Short of nothing, to the platoon leader's own
objective for them, or the camp's work where that's the ground too. They're called back as the
levels widen (`ChainOfCommand.onFlattenCrew`, "[PL] the ground's level wants N builder(s)"). The heights are the
plan's, not the ground's: everything from y=64 up into the sky over it is dug out, and the
ground left as it is - not built up for now (operator, 2026-11-15; `Keep.FILL`). Built up, the
ground's top at y=63, and any hollow open under it within 8 blocks, is filled in cobblestone only
(`Order.Fill`'s `stoneOnly`, a filler of its own), never dirt, which endermen take. A strip each
(`Tasking.keep`), each squad on it its own share of the site - west to east in the roster's order
- and each of its soldiers a fixed strip of that, its own range of x and z (operator, 2026-11-13):
the sky over it cleared to the top of the world, top down, as ground is dug only from an open top.
**We follow the excavation standard** (operator, 2026-11-15): OSHA's 29 CFR 1926 Subpart P
(1926.650-652), the rules US construction digs to - protected sides, a way out within reach of
every worker, spoil kept back from the edge - read for a world of one-block steps and builders
who fight. Where a rule of a clearing below meets the standard, it says which part.

**The rules of a clearing (operator; standing - don't trade them away for a fix elsewhere):**
1. **The highest block first.** A region is cleared from its highest block down, a level at a
   time, every builder on the same level, nobody lower till the level's gone (2026-11-13/14).
2. **Fair slices each level.** Each level is shared out fresh among the builders on hand in
   rectangles that don't overlap, near-equal in work - a scheduler's slices, not regions kept from
   level to level, which go uneven: last level's full region is empty this one (2026-11-14/15).
3. **Scaffolding to reach.** A builder builds up to a high block - steps, a pillar - to take it.
   Scaffolding is part of the job; it's ground in the dig, and comes down top first with the
   levels (2026-11-13, 2026-11-15).
4. **Never leave a high block.** The level doesn't move on past a block over it - not for one
   that's out of reach, not for one nobody can get to yet: a block left high is where zombies hide
   under. A block hard to reach is the problem to solve, not to skip (2026-11-15).
5. **One haul road.** A site has one way in and out that everyone takes - from its depot down to
   its working floor, steps no more than a block, carved into the hillside or the ground round
   the dig rather than built, so the pathfinder prefers it to any stairs of its own; cut again as
   the floor goes down, and taken out last (2026-11-15). As an excavation's ramp is (29 CFR
   1926.651(c)(2): a stairway, ladder or ramp within 25 feet of every worker in a trench 4 feet
   deep or more). Built for the keep (`bot.engineer.AccessRoute`): planned every half minute from
   the depot's stand to a standing cell on the keep's floor - the cheapest walk of one-block steps,
   a block dug costing three steps and one put down twenty, never through water, lava or the
   depot's table, furnace and chests, out of the keep's dig till its last cell; while any of it is
   uncut, the keep's hand nearest its start cuts it - the same hand till it's cut, or the crew would change and the level be shared out again each time another came nearer - and has no rectangle meanwhile (`[Keep] the
   haul road: 9 steps from the depot at ... to ..., 16 block(s) to cut`).
6. **Benched edges.** The ground just round the dig may be lowered as it goes - benched, as an
   excavation's sides are cut back in steps or sloped (29 CFR 1926.652) - so its edge is never a
   cliff over the work (2026-11-15). Built (`Flatten.bench`): a ring twelve wide round the site, its
   row d out dug down to d over the floor - Table B-1's 1:1 slope for Type B soil, a walkable
   staircase - its ground the level's like the site's, highest first, and in the squads' shares;
   past the ring the ground stands. A fence or wall is dug like any block: the cell over it reads
   solid to a body, not to the dig's open top (`WorldView.solidHere`; live, a fence on the hilltop and
   the ground under it stood while the site round it was flattened). Twelve (operator, 2026-11-17: slope the hills round the edge so
   soldiers can climb up), the depots sixteen off the west edge past it and the borrow pits past it
   too, so neither stands on nor holes the slope. Live, at three, soldiers put steps up the wall the
   big site was cut against, each one a level of its own, and builders on the hill had no walk down.

Level by level, in lock step, planned by the keep's foreman (`command.LevelPlan`, operator
2026-11-14): one soldier the squad leader appoints - the first builder of the first squad on the
keep, again whenever that one is off it. The site's level is the highest ground left anywhere on
it - read live from the world over the site's scan, a column cut since followed down a few blocks
to what's left of it (`SoldierRunner.LIVE_TOP_DROP`), past any of the force's own steps, not dropped, so a tower being cut doesn't
drop the level and raise it again at each block - and nobody digs lower till it's gone, so the site comes down as one flat floor: nobody climbs
over a neighbour's ground. A builder's own step or pillar (`SoldierRunner.scaffolding`, kept on disk
across restarts - lost, every step left standing read as ground, and the dirt tower's leftovers set
the level back up to y=91 after every restart), and a
tree's logs and leaves, are no ground and don't set the level - counted as the highest, it held every builder at its level for
one block while eight stood by - but every level shares out any step standing at or over it with
its ground - a step isn't dropped from the record the moment it reads open, for 20 seconds, since
one the server hasn't confirmed reads open a moment and would land as ground - so it's dug with the
level under it, and once the ground is gone what's left of them
is the level: none is left high. A column whose chunk nobody has loaded counts at its last known
top (`WorldView.isLoaded`), not as gone: live, the big site's east edge was a chunk loaded and let
go as builders moved, and the level went 86, 87, 86 a minute. At the start of each level the foreman
shares what's left of it out among the builders on hand, every squad's: a rectangle of x and z
each, none overlapping, near-equal in the time they take to dig - each block's break time with the
crew's stone tools, on the ground, and a few ticks a block for the stand and pickup
(`bot.world.BreakTime`: a block's hardness, tool and the tier its drop wants, then the tool's
speed, its Efficiency, Haste and Fatigue, a fifth underwater and a fifth off the ground, in
vanilla's order; operator, 2026-11-17) - not in blocks, a strip of stone being several of dirt -
each to the builder nearest it. With less work than
four blocks a builder, fewer are sent - a spire one block across is the nearest builder's - and the
rest wait where they stand. Then it holds the plan (operator, 2026-11-15): each builder is given
one or more touching rectangles and keeps them till they're cleared - its completion, logged as
"[Keep] Bob done with ...", read off the world as it is, not the site's scan of up to a second
before: live, on hilltop levels a builder stood that second after every rectangle - or till they're due: a minute to get there, six seconds a block, and five a block of climb from where
the builder stood up to the level (live, a hilltop's last cells 13 over the builders timed out on the
climb and were handed on, the next builder climbing from the bottom again)
(`SoldierRunner.dueMillis`); due and not done, they're given to the nearest builder who's done
("[Keep] Bob's ... timed out - given to Carol"), as a builder's are when it goes off the crew. A
builder with nothing to cut - given nothing yet on the level (come onto it once it was dealt, a
squad called back to a wide one, or not there when it was), or done with its own (operator,
2026-11-16: do a better job assigning) - takes half of the most that's left of one builder's,
once that's 16 blocks or more (`SoldierRunner.SPLIT_AT`): "[Keep] Carol is done with its own:
Bob's split, ..." Held work is split no other way. Ground left that nobody on
the crew holds - its holders all gone off it, at nightfall say - has the level dealt again, fresh,
once there are builders to deal it to: live, at dawn a level's 269 blocks were nobody's for half
an hour. Nothing is dealt again while
it's held: live, the level was shared out again every half minute and each time a builder
finished, and builders walked lane to lane and never finished one. Supplies come between levels:
a builder low on rations with food in the stockpile, or with no pickaxe, sits the new level out
to get them and is dealt in at the next. On hand means on the keep, fed,
not fighting and not off keeping the farm: live, a level's last three blocks went to a builder who
then walked 80 blocks to the farm, and seven stood by for her. Nothing is kept from level to level, or overnight. The foreman says each plan on
the net, point to point (`/msg`): only a line new to its builder, the latest overtaking one still
queued, and one every two and a half seconds at most - vanilla counts 20 for each line and takes
one off a tick, kicking past 200, so a line a second sustained is its decay exactly and the
foreman was kicked for spamming every minute and a half. The radio - FRAGOs, CANTCOs and the
platoon leader's traffic, keyed out over the radio operator's connection - goes through the same
pacing on that connection, a squad's next FRAGO overtaking one still queued and a repeat not sent;
keyed out unpaced beside the net, four squads' FRAGOs had the radio operator kicked. The log reads "[Keep] level y=77: 41 block(s) left, 6 of 8 builder(s) on it"
and "[Keep] Alice -> Bob: KEEP DIG 77 3 -4 9 2". Ground within 12 blocks of a builder's fight, and
for half a minute after it, is nobody's: not dealt out, but counted for the level, so the level
never drops under the highest ground (the clearing rules: never a high block skipped) - with
only the fight's ground at the top, the rest wait on it. Live, counting it out dropped the level
from 100 to 97 and back three times in two minutes, the crew dealt lower ground each time. A builder with ground work isn't sent to
support a squadmate's fight (§8), and digs on with a hostile in sight that isn't on it. Tried
first: a fixed strip each, a step under its neighbours' edges - builders walled each other in with
the steps they built to climb the terraces; then fixed strips in lock step - with the high ground
in two strips of eight, six builders stood by. Left free for other work while they waited, builders walked
60 blocks to the camp's upkeep and were there when their floor moved. The log reads "the
keep: N blocks to dig, its top y=T, the keep's y=K". A builder stands only in its own strip's columns, a neighbour working the
next. Within its strip it digs one level at a time, the highest first - no lower block is on offer
while the top level has one left - and sweeps the level in rows from its north edge, each row the
other way from the last (`Clearing.next`, `Clearing.sweep`: a boustrophedon, so each block is
beside the last, not the nearest next; operator, 2026-11-13: no random walks). With nothing on the
ground in reach, it cuts the top layer from the air
(`Clearing.fromTheAir`): a stand beside the highest block - in its strip or the ring a cell out
from it, since the rectangles keep the blocks apart, not the builders (live, a tower's last block
was a rectangle one cell wide with every stand beside it outside, and went from builder to builder
as each timed out); the force's own steps are never the block - its eyes level with it, that its route
builds a dirt pillar up to (`Pathfinder` offers pillar steps in open air under a goal overhead).
The pillar is the force's own steps: no top layer of its own while ground is left (`Clearing.next`) -
a step over the level is cut only from on it, its builder's way down, and the rest once the ground
is gone (live, a builder's step a block over a hilltop was taken for the level, cut from beside it,
and another put up, two stands in turn for two minutes). A
builder carrying fewer than 4 blocks to climb on - with a rectangle or waiting for one - draws a load of the dig's cobblestone from the nearest chest holding it while the surface chests hold 32 or more between them (live, every pit round the keep was dug out in turn, and builders stood a minute at a time finding no dirt), else fetches a load of 32 dirt from a borrow pit off
its squad's side of the keep first - on the ground's surface there, found from above, and its dirt
looked for within 16 of the pit's point nearest the fetcher, the pit running the side's whole length
(`SoldierRunner.nearestIn`) - and when
its fetchers find nothing left, the squad's next pit is off another side, round the keep in turn
(never west, the depot's side, where a pit takes the ground from under the workshop): live, one with a single block stood all day 15 under its
strip's top, the hilltop it dug being stone. And one stranded up a pillar of its own - called off
it, say, to feed itself - digs it down from on top (`Recovery`, `Stairway.pillarUnder`), its
episode held until it's at the pillar's foot. The register follows the keep's dig and fill by
their own names (`dig the keep`, `fill the keep`), each builder measured by its squad's share, not
its own strip: the dirt steps it climbs on are in its strip too. Food first: a builder hungry with
nothing to eat (`Subsistence.mustFeed`) gets no keep work, and feeds itself first; one short of
rations but fed works on and eats what it carries - live, two builders held off for carrying 8 and
9 rations at food 20 dug the camp's ground while their strips stood. Its builders'
workshop and chest are the keep's own, one for every squad on it, off the slab's west edge
(`Keep.DEPOT`), where nothing digs under them; a builder carries what it digs until its pack is
nearly full before taking it there. Until that chest stands, a builder's stockpile is the camp's -
the force's stores are common (`SoldierRunner.stockpileOf`) - and one short of the logs to fit it
draws them there: live, with only an unfitted chest of their own, the builders drew nothing all
day, not the logs for the chest nor for a pickaxe. A soldier of the squad off the keep's work - feeding itself at
the camp's farm - bakes and stocks at the camp's workshop, not the keep's. Its cobblestone is the mine's: miners carry their loads to the stockpile, and a
soldier on the keep short of it draws a load from there while the chest was last seen holding
some; with none there it's free for other work, and the log says the keep waits on cobblestone.

It ends with the commands' scorecard (`app.CommandScorecard`). Each soldier is sampled once a
second, and each kind of command is scored by how its soldiers spent their time: each task the
platoon leader gave (FORTIFY, MINE ...), and each kind of order a soldier carried (`MoveTo`, `Place`).
The score is soldier-minutes, the share of them under each report, the most frequent CANTCO
reasons, and the deaths. Places and numbers are taken out of a reason, so the same failure counts
as one. A command that doesn't work shows as a large share of CANTCO, or of `InPosition` while
there's work left.

## 18. Task organisation

| Practice | Content | Source |
|---|---|---|
| **Task organisation** | The commander organises the unit for its mission - who goes where, under whom, to do what - as the first thing an order says. | FM 6-0 (OPORD paragraph 1, Annex A) |
| **Detachment** | A part of a unit separated from its main body for a task of its own. | FM 1-02.1 |
| **Assembly area** | Where a force gathers to prepare for further action. | FM 1-02.1 |

*How we use it:* each soldier has a standing plan (`command.Plan`), set by its squad leader and
kept until changed: one **role** at a site, and the **directives** it does first, in order. Plans
are kept on disk (`app.PlanBook`, `plans.json` in the state directory), so a restart finds every
soldier where its squad leader put it; a soldier with none is a rifleman.

- **Roles** (`command.Role`): a `RIFLEMAN` goes with the squad - the formation and the platoon leader's
  task. Each other role is a task at its site, whatever the platoon leader's: `MINER` (§ 16, the site's y
  its depth - one on or over the ground, down to the iron at y=16), `FARMER` (§ 4), `MASON` (§ 15's ground work and wall), `FORESTER`, `WOODCUTTER` and
  `QUARRYMAN` (§ 12), `SENTRY` (§ 15's patrol). Everyone in a squad with one role works one site: a
  new site for one moves them all.
- **Directives** (`command.Directive`), each done by what the soldier or the world shows, never by
  time, so one given again once it's done is done at once:
  - `GoTo(at)`: go there. A rifleman holds - a post, an assembly area - until replaced or
    cancelled. One with a role goes on to its role's work once there, at any height over or under
    the point: a miner sent to its mine at its depth gets there on the ground above.
  - `Fetch(resource, amount, near)`: wood, stone or soil gathered round a point, done once the
    soldier carries the amount.
  - `Level(x, z box, y)`: everything from y to 12 above dug off, every open cell from 4 below to
    y - 1 filled, done once the world shows both. One carrying no blocks digs dirt within 16 first.

Each soldier's plan names its **unit** (`Plan.unit`): a rifleman with nothing directed is in its
squad's main body; one in another role in its squad's detachment for that role ("Alpha/miner"); one
with a directive in a detachment of one until it's done ("Alpha/Bob"). Every unit is led as a squad
is (`app.SoldierRunner`): the same drills (docs/echelons/squad-leader/drills.md), the same
routines, to its own objective - the main body's the platoon leader's, a detachment's its role's task at its
site or its directive's point - under the squad leader's risk, rearm and intent, in file at a tight
interval. Contact, link-up, recovery and cross-levelling run within a unit; camp upkeep for every
unit but a soldier on a directive. An escorting squad is all escort, whatever its plans.

What a soldier does for itself sits under every plan, unordered: it eats, fights back and goes
back to its work after, finds its own route, gets out of water and pits
(docs/echelons/soldier/routines.md). The plan is the work.

Each directive is a **work order** the squad follows (`command.WorkOrders`): what's left of it
is measured from the world each cycle - blocks still to go, items still to carry, cells still to
dig or fill. One whose measure hasn't fallen in 2 minutes, or that has run past its allowance (a
minute, and 2 s a block, 20 s an item, 4 s a cell it started with), is timed out: taken off the
soldier's plan, logged `TIMED OUT` with why, and shown to the squad leader for 5 minutes, during
which the same order to the same soldier is refused (`REFUSED`). Contact pauses both clocks; a
rifleman holding the point it was sent to is never timed out. The squad leader's view shows each
soldier's work order under way - for how long, how much left - or the one lately timed out.

The squad leader sets plans in its reply: `roleChanges` to change a soldier's role and site, `detached` to
replace what it does first (GO_TO, FETCH, LEVEL, CANCEL) - both usually empty, since what's set
stays set. Each order's changes are made once. It sees each soldier's plan at the end of his line.

## Sources

- FM 90-26, *Airborne Operations* (superseded by FM 3-99) - [airhead development, GlobalSecurity](https://www.globalsecurity.org/military/library/policy/army/fm/90-26/Ch8.htm)
- JP 3-18, *Joint Forcible Entry Operations* - [lodgment, FAS](https://irp.fas.org/doddir/dod/jp3_18.pdf)
- ATP 3-21.8 (2024), Ch. 3 §2 - [fire team formations](https://infantrydrills.com/manuals/fm-atp-3-21-8-infantry-rifle-platoon-squad-2024/movement-and-maneuver/tactical-movement-approach-march/movement-formations/fire-team-movement-formations/), [squad formations](https://infantrydrills.com/manuals/fm-atp-3-21-8-infantry-rifle-platoon-squad-2024/movement-and-maneuver/tactical-movement-approach-march/movement-formations/squad-movement-formations/)
- FM 3-21.9 Ch. 3 (formation intervals reduced to keep visual contact) - [GlobalSecurity](https://www.globalsecurity.org/military/library/policy/army/fm/3-21-9/chap3.htm)
- ADP 6-0 (2012, archived) - [UNT web archive](https://webarchive.library.unt.edu/web/20130213211329/http:/armypubs.army.mil/doctrine/DR_pubs/dr_a/pdf/adp6_0_new.pdf)
- FM 6-99.2 report and message formats (2007, archived) - [UNT web archive](https://webarchive.library.unt.edu/web/20130213212857/http:/armypubs.army.mil/doctrine/DR_pubs/dr_a/pdf/fm6_99x2.pdf)
- FM 34-35 App. C, SALUTE spot report - [irp.fas.org](https://irp.fas.org/doddir/army/fm34-35/Appc.htm)
- Prowords: [Procedure word (Wikipedia)](https://en.wikipedia.org/wiki/Procedure_word), [USMC MCT-COMM-1003/4 Radio Communications](https://www.trngcmd.marines.mil/Portals/207/Docs/FMTBE/Student%20Materials/MCECST/MCT-COMM-1003_4%20Radio%20Communications.doc), [SERA.14045 (UNABLE)](https://regulatorylibrary.caa.co.uk/923-2012/Content/Regs/03440_SERA14045_Transmitting_technique.htm)
- LACE: [Kentucky National Guard photo caption](https://ky.ng.mil/News/Photos/igphoto/2003751897/), [RallyPoint discussion](https://rallypoint.com/answers/what-s-the-difference-between-each-of-these-reports-salute-lace-salt-spot-report-sit-rep) (secondary sources)
- Link-up procedure: FM 3-21.9 ch. 7 - [GlobalSecurity](https://www.globalsecurity.org/military/library/policy/army/fm/3-21-9/chap7.htm)
- ATP 5-19, *Risk Management* - [Army Publishing Directorate](https://armypubs.army.mil/)
- FM 4-0, *Sustainment Operations* (classes of supply) - [Army Publishing Directorate](https://armypubs.army.mil/)
- ATP 3-21.8, patrol base and priorities of work - [study notes based on the manual](https://tac-9yd.pages.dev/patrol-base-ops) (secondary); FM 3-21.8 (2007) - [marines.mil](https://www.marines.mil/Portals/1/Publications/FM%203-21.8%20%20The%20Infantry%20Rifle%20Platoon%20and%20Squad_6.pdf)
- ATP 3-37.34, *Survivability Operations* (restricted distribution) - [Army engineer bulletin on overhead cover](https://home.army.mil/wood/contact/publications/engr_mag/Rethinking-Survivability) (secondary)
- FM 4-93.51 ch. 9 (cross-leveling ammunition within the company) - [GlobalSecurity](https://www.globalsecurity.org/military/library/policy/army/fm/4-93-51/ch9.htm)
- ATP 3-39.35, *Protective Services* - [Army Publishing Directorate](https://armypubs.army.mil/)
- ATP 1-02.1 multi-service brevity codes (CANTCO) - [ALSSA](https://www.alssa.mil/MTTPs/Brevity/)
- 29 CFR 1926 Subpart P, *Excavations* (OSHA; 1926.650 scope, 1926.651 general requirements including means of egress, 1926.652 protective systems and Appendix B sloping and benching) - [eCFR](https://www.ecfr.gov/current/title-29/subtitle-B/chapter-XVII/part-1926/subpart-P); [1926.651](https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.651), [1926.652](https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.652), [Appendix B](https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926SubpartPAppB) (osha.gov)
