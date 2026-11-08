# Military doctrine this project emulates

The chain of command here (General → Squad Leader → Soldier) deliberately borrows real military
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
heads for the objective - in the direction of travel (¶3-47: leader in front, others echeloned
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
loop a human squad runs. At the top of the chain, the general answers a new mission from the human
operator with WILCO on the radio net, and an order it doesn't recognise with SAY AGAIN.

An order is acknowledged at every echelon it passes, and the last acknowledgement is the soldiers
doing it: each operator order is traced (`app.OrderTrace`) from the general's decision through
each squad leader's FRAGO to a SITREP showing the squad's soldiers at the task - Fortifying for
FORTIFY, Securing for SECURE, and so on - each hop timed, and each one late raising an alarm.

By day a soldier is always meant to be at something, so one that hasn't moved 2 blocks in two
minutes of daylight raises an alarm in the log, once a spell (`[Idle]`, `app.IdleWatch`): its
report, its order, which routine has its hands, and its squad's task. It's logged again when it
moves on. A soldier held at a table or furnace shows its routine. One with free hands and a task
its routines can't start on is the chain of command's gap.

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
in hand and arrows, equipment is armour worn (`command.Kit`). A soldier below food 18, the level
health regenerates at, eats from its rations when it's out of contact and nothing else has its
hands (`bot.supply.Rations`): the food goes into a hotbar slot without a weapon or tool, the server
times the meal, and the weapon comes back to hand. Rations are the food items it carries, less
those that poison or are too precious to eat (`tools/item-class-generator`). A soldier with no rations
left hunts at food 14 or below, or wounded and below food 18, where it would heal: by day, out of contact and not walling in a patrol base, it
chases the nearest cow, pig, sheep, chicken or rabbit within 48 blocks of its squad's objective -
not of itself, or one hunt after another carries it off; 96 once it's starving, at the food 6 it
can no longer sprint at, since the ground round a camp is hunted out first - and within 8 blocks
up or down of it, as an animal in a cave below is near only as the crow flies. It strikes it each
time its weapon is charged and stands where it fell for the meat (`bot.supply.Hunter`); one it
gives up on after a minute's chase it leaves for two. Food comes first:
a soldier below food 18 with a ration in its pack, or wanting food with game in sight, puts down
its day's work - gathering, lighting, walling, farming, planting - at its next step, and eats or
hunts. Raw meat is a ration
like any other. It reports `Hunting`. Squad leaders aggregate their
soldiers' SITREPs into one for the general, with a LOGSTAT summed from the soldiers' LACE
(`command.Logstat`): hungry soldiers and the rations they carry (Class I), empty armour slots (Class II), bare-handed
soldiers and arrows (Class V), and casualties. These are the structured inputs the LLM-backed
echelons read - the same economy of attention doctrine optimizes for.

*Resupply:* the operator can set up a supply point - a chest at `minecraft.supply.point` - and
the general sees it next to the LOGSTAT. When the general gives a squad an objective there, the
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

*How we use it:* the general's objective to a squad is OPORD-shaped but small - situation, mission
(who / what / where / why), intent. The squad leader's frequent re-tasking is FRAGO-shaped: a new
objective, formation, or interval against the standing mission. The general orders every squad in
one decision, so it divides the mission between them; its prompt says one squad can hold or
observe while another moves, and that a squad beside one in contact or weak brings it help.

An order stands until changed, and changed for a reason. The general sees each squad's standing
order and how it is going (for a patrol base: not laid out yet, how many blocks are still to place,
or sealed with the squad inside), and for
each squad either keeps it, exactly as given, or gives a new objective with the reason why, which
is logged (`[General -> Bravo] changing the objective: ...`). A change with no reason is rejected
and the squads keep their orders. A kept order isn't re-sent: the log says `holds`.

The general's heights are guesses, so an objective is put on the ground at its x and z: the
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
- **The floor:** it never falls below the risk's minimum health (LOW allows no damage, MEDIUM keeps
  10, HIGH keeps 4).
- **The price:** each point of damage costs extra route length, halved for a well-fed soldier
  because it regenerates.

So a safe route always wins when one exists, and a fall is planned only when there's none, on
foot or by the steps it can build (docs/echelons/soldier/capabilities.md). A soldier with no such
route and nothing to build with is stranded. The squad's recovery drill (`command.Recovery`) picks
the nearest squadmate carrying 24 or more blocks - one at a time, never in contact - and sets it
to build the engineer's stair down from where the stranded one stands (`bot.engineer.Stairway`,
an `Order.Place`). The stair runs one block down and one along from the ledge's edge to the
ground, the shortest of the four headings with clear air over it and no water under it. Each step
stands on a block of its own, so it's two blocks a step, built from the ground up. Where no stair
can be laid, the squadmate is sent to the stranded one instead. A recovery lasts until the
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
  any it strikes the creeper, knocking it back out of its blast's reach. No step in a fight -
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
- **The squad's drill.** `command.ReactToContact` sends teammates within 24 blocks to support,
  ahead of the squad leader's next decision.
- **The squad leader and the general** hear of it at once: a soldier newly in contact is a contact
  report, and the squad leader decides then, not at its next scheduled look, whether to fight on,
  bypass or break contact. A squad newly in contact triggers the general the same way. Both keep
  the same minimum gap between decisions as a CANTCO does, so a running fight doesn't flood the
  models.

In the combat simulation, three zombies on one soldier's flank kill a lone soldier 64 times in
100. A squad spread 10 blocks apart without the drill still wins, but loses its flank soldier 66
times in 100. With the drill it loses nobody.

A squad closing on one hostile stands round it, and a sword's sweep hits every body within a block
of what it strikes, squadmates included. So a soldier holding a sword doesn't swing while another
player stands within 1.6 blocks of its target across and a block and a half up or down
(`DeterministicSoldier.SWEEP_REACH`). It waits until one of them moves.

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
`!escort <player>`) in game chat. `ChainOfCommand` takes it out of the general's command - the
general goes on commanding the other squads - and gives its squad leader the principal's
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
server it's sprinting (Player Command), so the server charges the food it costs. An ordinary move
sprints only while the soldier carries rations to make that food up. An urgent one sprints
whatever it carries: the ring's moves, an intercept, a teammate going to support one in contact
(§8), closing on a hostile it fights, and any move while under fire. Nobody sprints at food 6 or
below, where vanilla won't let a player, or with a shield raised. The squad leader still chooses the interval and the risk. The soldiers find the principal by its
offline-mode UUID among the entities they see; if none of them can see it, the squad holds the last
known position. Out of sight for 30 seconds - a principal who has left the game is gone from the
world - the escort ends, said on the net, and the general decides for the squad at once. `!mission`
ends the escort too and hands the squad back to the general.

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
5. A shield, worn in the off hand and raised against arrows (§8).
6. Copper armour - chestplate, leggings, boots, helmet - from copper ore mined with the stone
   pickaxe and smelted: copper lies near the surface far more often than iron.
7. Iron armour: chestplate, leggings, boots, helmet.
8. An iron sword.
9. An iron pickaxe.
10. An iron axe, which chops a log in a sixth of the bare-handed time.
11. Diamond - a sword, then the armour, then a pickaxe - once it carries the diamonds: they're ore
    the miner takes beside its stairs (§16) with an iron pickaxe, not something a soldier fetches.

The rungs are ranked by survival gained per resource spent, as `CombatScenarioTest` measures it.
Against a skeleton, a stone sword over a wooden one saves 0.3 health a fight. A shield saves
2.1 more for one iron ingot, about what a chestplate saves for eight, and full iron armour 5.9:
the arrows on the way in do the harm, not the melee. Out of contact and out of a patrol base, a
soldier walks onto whatever lies within 6 blocks - a spider's string, a chicken's feather - and
leaves for a while what it can't reach (`bot.supply.Scavenger`). A rung is held
once something as good is, and one whose materials can't be had is passed over, not waited on.
A tool that wears out is made again, since it no longer holds its rung.

It digs what it's short of and can dig itself: logs from the nearest tree within 16 blocks it can
stand beside, with its best axe if it carries one, and cobblestone or iron ore when its pickaxe is good enough and some is in reach.
It crafts planks, sticks and a crafting table in its 2x2 inventory grid, places the table (or walks
to one standing within 16 blocks), smelts ore in a furnace it makes or walks to, and crafts the rest at the
table. What's made is put on: armour worn, the best weapon in hand. And whenever its hands are
free, a soldier stands to: every two seconds it puts on any better armour it carries and takes its
best weapon back in hand, rather than the seeds or dirt its last job left there. A first wooden sword takes
about a minute. While crafting or smelting, the soldier stays where it began, since a window closes
on a player who walks away from it.

The squad climbs together. Nobody works above the lowest rung a squadmate is still working toward,
so the first soldier up the ladder doesn't dig out the ore the rest need for their shields. A
table is shared, since each player's crafting grid is its own, but a furnace isn't: whoever opens
it can take what's in it. So a soldier holds the furnace it smelts in until it has collected what
it left there, and a squadmate puts down its own.

The squad leader decides whether there's time: its order's `rearm` lets soldiers make a weapon.
Climbing past it waits until the squad isn't ordered to a patrol base, since shelter comes first
and digging iron isn't building it. Each soldier also keeps back its share of the cobblestone its
squad's base still needs, so a stone sword never costs a wall. A soldier at it reports `Rearming`,
and the squad doesn't halt for it.

Contact outranks this work, as the react-to-contact drill (§8) outranks priorities of work. A
hostile within 16 blocks, or the squad in contact, pauses the job: windows close, and the soldier
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

*How we use it:* the general can give a squad an objective with `gather: WOOD` or `STONE`
(`command.Objective.gather`). Its soldiers gather within 16 blocks of the objective on their own
(`bot.supply.Gatherer`, digging with `Digger`, shared with §11): each works a log or a block of
stone no squadmate has claimed, checks it actually broke, walks onto the drop wherever it landed
(done once it's off the ground - a squadmate working the same trees often gets there first), and
stops at its load (16 logs, 32
cobblestone) or when anything nears. Logs are chopped bare-handed - a sword wears, and digs no
faster. Stone needs a pickaxe, or it drops nothing: a soldier without one makes a wooden pickaxe
first, the way it makes a sword (§11), from logs it carries or chops. Break times are the
server's - hardness over tool speed - with a margin, since the server times each dig. A soldier at it reports `Gathering`. One that finds nothing left in reach goes, while that order
stands, to one of the places its squad saw the resource, as for torch-makings (§15); with none seen
beyond the objective's reach it reports CANTCO, which the squad leader passes up so the general
can choose another place. To choose, the general
reads what each squad can see within 64 blocks of the soldier at its middle height: for trees and for stone open to
the sky, the nearest site and the two biggest (not cave walls, which no gatherer can
reach), each with its count, distance and direction (`bot.supply.ResourceSurveyor`,
`command.SquadStatus.resources`). A soldier that can't make the pickaxe it needs - no trees in reach
- reports CANTCO too.

The supply point's chest is also the stockpile. A soldier with a full load walks it over and puts in
the material it has a full load of (`bot.supply.Depositor`) - only that, so a soldier quarrying stone
keeps the logs for its next pickaxe, which wears out in 59 blocks. What the chest holds when it's
opened goes to the general (`General.receiveStockpile`), shown with the supply point. Logs carried show in each
soldier's LACE (`Kit.logs`) and in the squad's LOGSTAT as materials.

Food is grown as well as gathered. The survey also reports ground to farm: the largest plot of
tillable ground, dirt or grass, with nothing solid above, at one height within 4 blocks of a water source, as a
"ground to farm" site with its cell count, at a plot cell beside the water (`bot.farm.FarmPlan`).
An objective's task can be `FARM`. The squad keeps the plot round the water nearest the objective,
the same plot while the objective stays put. Each soldier does the most pressing job left on it,
one cell at a time (`bot.supply.Farmer`), in this order:
1. Harvests ripe wheat, for wheat and seeds to plant again.
2. Plants seeds on bare farmland.
3. Tills the plot with a hoe, breaking first any grass or flower on the cell: the server tills
   only under open air.
4. With no seeds, breaks grass for them, since one in eight drops some - out to 32 blocks, as a
   squad soon clears what grows near.

A plot cell whose job fails - its place not walked to, the server not answering - is passed over
for two minutes, and the rest of the plot is worked first.

Each job is an expect script: stand on the cell, item in hand, use it on the cell, and done when
the server's block update shows farmland or a sprout. A soldier without a hoe makes a wooden one,
and one carrying three wheat bakes it into bread, the ration every soldier eats (§11). It reports
`Farming`.

Wood is grown when it runs out. An objective's task can be `TREE_FARM`: the squad plants saplings
round the objective, on every third block both ways of open dirt or grass within 8 blocks, with
seven blocks of air above each for the trunk (`bot.farm.TreeFarm`). Spruce is the camp's tree. A
soldier plants a spruce sapling if it carries one, any other if not, and with none breaks leaves
for one, spruce leaves first; one leaf block in twenty drops a sapling (`bot.supply.Forester`).
Planting is an expect script like the farm's. The trees that grow are logs like any others, for a
squad sent for WOOD there. A squad that sees no trees is told, on its resources line, that its
wood has to be grown. The soldier at it reports `Planting`.

## 13. Cross-leveling

| Practice | Content | Source |
|---|---|---|
| **Reorganization** | After contact the leader redistributes ammunition and equipment, so no one fighter runs dry while others have plenty. | ATP 3-21.8, consolidation and reorganization |
| **Cross-leveling** | Supplies move laterally, between units at the same echelon, rather than waiting on resupply from above. Doctrine describes it at company level and up; here it's the same idea within a squad. | FM 4-0; FM 4-93.51 ch. 9 |

*How we use it:* a soldier that needs logs for a tool (§11, §12), has tried to chop them and found
no tree in reach, is short; so is one that wants food (§4) and carries none. `command.CrossLevel`
pairs each with the nearest squadmate carrying more than it needs: a giver keeps 3 logs, enough for
its own next pickaxe, or 2 rations, and hands over the logs short or 2 rations. A bare-handed
soldier - a replacement respawns with nothing - gets a squadmate's spare melee weapon: the worst
of those it carries beyond its best (`Inventory.spareWeaponSlot`), never the one in hand. Weapons
are paired first, then logs, then rations, and a soldier is in one hand-over at a time, giving or
receiving. A replacement still linking up gets nothing until it has: the squad reorganises round
it once it's back. Nor does a soldier making its kit (`Rearming`): mid-craft its weapon can be on
an open window's cursor, where it isn't counted. Nobody cross-levels while
the squad is in contact, or from a soldier linking up.

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

*How we use it:* the general knows the time of day: Set Time's overworld clock, advanced with the
game time between the updates that carry it (`app.OverworldClock`), becomes `command.TimeOfDay`. Dark runs from when the server's day timeline dims sky light enough for monsters to
spawn in the open, tick 13178, to sunrise, tick 24000: spawning stops at 22822, but what spawned
walks until the sun burns it, so a squad stays in until then - the stand-to before first light. The general's and squad leaders' prompts say how many
minutes until that changes. At night, each squad not sealed in a patrol base is told so on its own
line of the general's prompt, rather than left to be worked out from the time and its state; by
day, with more than two minutes of light left, each squad still in or ordered to a patrol base is
told the night is over.

An objective's task can be `PATROL_BASE`. `command.PatrolBase` lays out a hasty hut around the
squad: a 2x2 floor for up to four soldiers (3x3 for up to nine), walls two high, and a roof over
walls and floor, so each roof block has a neighbour to go against. That's 40 blocks for a squad of
four. The site is the nearest level footing within 8 blocks of the general's point: every floor
cell standable at one height and walkable to from where the squad is - not an old hut's roof two
blocks up - never rubble or a slope, since the model's point is a guess - and a
block clear of any other squad's hut, since the general may send two squads to one point. Each
block of the hut already standing counts as a block nearer (`PatrolBase.Plan.standing`), so a hut
built before a restart, its doorway cut on the way out, is taken back and only the doorway rebuilt.
A re-issued objective whose search square holds the site already laid out keeps it, so the
outline doesn't move under the soldiers.

Each soldier goes to its own floor cell. Once all are inside, each builds from where it stands
(`bot.supply.Builder`): cobblestone first, then dirt, then planks, then logs. A soldier carrying
less than its share of the hut's blocks first digs dirt by hand a few blocks off the site, clear of
the walls (`Resource.SOIL`), and one that runs out with the hut still open goes out for more, so a
squad with nothing to build with still shelters by night; the
general's LOGSTAT counts dirt, and tells it so. Each block is placed against a
solid neighbour's face and checked against the server's block updates; a cell that won't take a
block after three tries is skipped; after the first miss the soldier digs the cell, since a flower
or sapling there blocks a placement until it is broken. A soldier at it reports `Fortifying`, or CANTCO when it runs
out of blocks. The general's LOGSTAT says whether the squad carries enough for the base it still
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

Where the operator sets it, no squad spends the night in the open: one holds the camp in its
patrol base and the rest work the night shift down the camp's mine. The rule and its timing are in
docs/echelons/general/operator-rules.md.

The base is lit as well as sealed. Light is checked alongside breaches (`PatrolBase.Plan.unlit`):
a torch gives its cell block light 14, which falls by one a block through open cells. A floor cell
left at 0 is ground a monster can spawn on once no soldier is within 24 blocks. Overworld monsters
spawn only at block light 0, and never within 24 blocks of a player.

A squad with a base to light and no torch has one soldier make them before it seals itself in
(`bot.supply.Torchmaker`), using what squads carry: 3 logs and 8 cobblestone. The soldier works 4-6
blocks outside the walls, so its table and furnace land clear of the hut. A table or furnace
already within reach is used rather than made:
1. Planks and sticks in the 2x2 grid.
2. A crafting table, placed.
3. A furnace from 8 cobblestone, made at the table and placed.
4. A log smelted to charcoal, with a plank for fuel.
5. Charcoal over a stick: 4 torches.
The table and furnace stay behind as the camp's kit. Contact pauses the work as it does a sword
(§11). A furnace keeps smelting with nobody at it, so the soldier collects what it left there before
anything else when it comes back. The others wait inside, since building starts
only once all are in, and a bare-handed one may make its sword meanwhile. Once the base is sealed
and dark, a soldier inside with a torch puts it down where it stands.

A squad sealed in with no torch - after a restart, or after its torchbearer's run failed - lights
the base the same way. A soldier who carries the makings cuts a doorway toward the workshop spot
and goes out; the doorway stays off the build list until a torch is back inside, then is rebuilt
and the torch goes down. A table and furnace left from the first run are used again if they're in
reach.

The log reports "SEALED and LIT", or "UNLIT at" with the dark cells. While the squad holds the
base, its walls and roof are off-limits to digging: a soldier short of wood looks for a tree, not
the log wall it shelters behind.

When the general moves the squad on, the soldier inside nearest the doorway breaks a doorway out
(`bot.supply.Breacher`): two wall blocks, on the side facing the general's new objective, preferring
a side whose outside cell has room to stand and ground under it (`PatrolBase.Plan.doorway`). It
uses a pickaxe if it has one (1.5 seconds a block), bare hands if not (10). A fight only pauses the
base: the squad doesn't cut its way out mid-contact, and it keeps the site, so when the fight is
over it goes back to the hut it was building rather than laying out another in the open. A squad needn't remember its base to leave
it: a soldier the world shows shut in a small room (`PatrolBase.enclosing`: a flood fill from where
it stands, through gaps a body fits through, runs out within 16 cells with no step up and out) cuts
its way out the same way, so a squad that wakes up sealed in after a restart, or walled in by a hut
it never finished, walks out.

## 15. Securing the camp

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
torches the ground within 12 blocks of the objective still needs: every cell the squad can walk to
that isn't within 7 blocks (taxicab) of a torch. That's half a torch's reach, so a wall or a step
between them still leaves the cell lit. Torches go first on the lattice where taxicab circles of
that radius tile the ground exactly, then on any cell the lattice left dark. Torches already
standing count, so the plan shrinks as torches go down and a secured place needs nothing more.
Farmland is left bare. Light is worked out from where torches stand, not read from the server's
light data.

Each soldier takes the nearest planned cell no squadmate has taken, walks onto it and puts a torch
down where it stands (`bot.supply.Lamplighter`) - never on a wall's line, one laid out or the
camp's still to be, where a torch would hold a cell the mason needs. The server's block update confirms it. A soldier
out of torches makes eight at a time where it stands (`bot.supply.Torchmaker`, the same run as a
patrol base's), and reports `Securing` meanwhile. One short of the makings gathers them first, where
its squad last saw them nearest the objective: logs, then the cobblestone for a furnace (making
itself a pickaxe for it). One that
still can't make any reports CANTCO with why.
The log says how many torches are still to put down.

A lit camp still lets in what walks in from the dark beyond the light. An objective's task can be
`FORTIFY`, which makes the camp a deliberate base: a wall two blocks high - one more than a
jump - on a square 8 blocks out from the objective, laid on the ground of each of its columns, with
a doorway a column wide in the middle of its south side: the squads' way in and out, rather than a
step cut through the wall (`bot.supply.Perimeter`). It's laid out once, by whichever squad comes to
it first, and shared, and it's kept by the objective's x and z, not by the ground it was laid on:
that ground moves as the pits are dug and the wall goes up, and laid out again it would lose what's
built. So the
layout is kept on disk too (`app.WallStore`, under `minecraft.state-dir`), and a restarted garrison
builds the wall it laid out before rather than a second one on top of it. Its
cells join the ones every dig skips, so nobody quarries it. Each soldier takes the nearest open cell no
squadmate has taken that has a block to go against - so each column rises from its own ground,
and on a slope the rows stand at many heights - stands just inside it, within 0.15 of the place's
centre as a hut builder does, and puts a block there,
cobblestone first, then dirt, breaking first whatever stands in the cell that a block can't
replace - a torch, a flower (`bot.supply.Mason`); one down to fewer than 4 digs dirt outside the
wall, each mason on its own side of the camp so no one pit is dug out under all of them, and once
digging it digs a load of 32 before it builds again, rather than walking back with every fourth. A cell that fails - its place not walked to, inside an old hut or across water, or its block
refused - it passes over for two minutes and builds the rest. On top goes a lip, a
ring of blocks one out from the wall's top row, against its outer face: an overhang a spider
climbing the wall can't get round. None outside the doorway, where it would stand at head height
in the way out. A mason places each lip block from the column nearest it just inside the wall,
never from the wall's top. The log says how many blocks are still to place. Seen live in `wall-the-camp`: two squads close a 128-block wall
together.

## 16. Mining down

| Practice | Content | Source |
|---|---|---|
| **Engineer reconnaissance** | Engineers check a route for obstacles and hazards before a unit uses it, and report what stops them rather than guessing past it. | ATP 3-34.81 |
| **Class IV and the supply chain** | Construction materials come from where they are, and the supply chain carries them to where they're needed. | FM 4-0 |

*How we use it:* stone near the surface runs out round a camp, and iron and diamonds lie deep.
An objective's task can be `MINE`: the squad cuts a staircase down to its y, the only task whose
y is a depth rather than the ground's height. The stairs go at the objective's x and z, or the
nearest head within 8 blocks where every step can be cut clear of water and lava and over a floor,
as the loaded chunks show them (`Staircase.site`). Beside a pond, the shaft moves off it rather
than stopping at its first step.

The staircase (`bot.supply.Staircase`) is a spiral round a central pillar: one block down a
step, round the eight cells about the pillar, so the shaft stays inside 3x3 and passes under its
own top every eight steps with five blocks of rock between turns. Each step clears three cells:
the feet, the head, and the head room a body needs to step down onto it. Its floor is left
standing. A soldier walks down it and jumps back up it, with no ladder, and a slip is a fall of
one block. It's cut no deeper than y=-58, clear of bedrock.

One soldier, the first in squad order carrying a pickaxe, cuts it (`bot.supply.Miner`). It stands
on the step above the first not yet cut, breaks that step's cells, walks down onto it, and cuts
the next. By day the rest hold the top, clear of the shaft. By night they follow it down, within 3
blocks: the top is open ground in the dark, and on the stairs no monster spawns within 24 blocks
of a player. Until one of them has a pickaxe, every one makes one.

On the stairs there's no floor for a table. So once a turn, on the step east of the pillar, the
cutter cuts an **alcove**: one cell out of the shaft's side (`Staircase.alcoves`). At the bottom,
beside the main tunnel's first cell, it cuts a **landing**: a recess each side
(`Staircase.landing`). A cutter wanting a tool goes to its workshop (`Staircase.workshop`), the
landing once it's cut and until then the deepest alcove, and the stairs wait while it makes the
tool there. The tool is the ladder's next rung, or a stone pickaxe once it carries the cobblestone,
since iron ore drops nothing to a wooden one. It goes only for the pickaxe the stairs need, and
not while making it is failing: a weapon it can't make would hold the stairs for good. A soldier
whose pickaxe wears out is no longer the cutter, and the next with a pickaxe takes over; the worn-out
one makes a stone pickaxe from the cobblestone it carries, or a wooden one from logs if it has
fewer than three. The stairs and tunnels are claimed (`Staircase.walkway`):
no table or furnace is put down in them, and no other soldier digs them. The cutter's hands are
free between blocks, so a routine ranked above mining - making its kit - gets its turn.

The camp's mine is the night shift's work (docs/echelons/general/operator-rules.md). Once the
stairs reach the iron, their tunnels are the mining. The stairs' state is the world's, so a run stopped by a fight or a restart picks up
where they end. A dig waits out the block's own break time: its hardness, and the pickaxe in hand
against the least tier that gets its drop. Both come from the server jar (`bot.world.Hardness`;
DEVELOPMENT.md § 7). Ore it can see on the way - beside its body or the step it cuts, outside the
shaft's 3x3 so no step or pillar is holed - it mines first, when its pickaxe gets the drop and
nothing liquid lies behind it; one the server won't break in 30 seconds is left. The cutter reports
`Mining` with its step. It stops with CANTCO where going on
would let in water or lava, or would leave a step with no floor over an open cave.

At the bottom the cutter goes on into the tunnels the ore is mined from: a main tunnel 24 blocks
straight out from the last step, away from the shaft, then branches off it both ways every 3
blocks, each 8 long, so every block between two branches is seen from one of them. All are two
high, at the stairs' depth, with their floors left standing, and ore under a tunnel's floor is
left so as not to hole it. A tunnel that would let in water or lava, or open onto a cave, is
given up alone, and the rest are cut; so are branches off a stretch of main tunnel that was given
up. The camp's mine standard (§ 17) is met once the stairs reach the bottom.

## 17. The lodgment

| Practice | Content | Source |
|---|---|---|
| **Airhead** | After the drop, the assault force seizes objectives and consolidates an initial airhead, develops it out to an airhead line it can hold, then builds up combat power within it. | FM 90-26 (superseded by FM 3-99) |
| **Lodgment** | An area in hostile territory that, seized and held, makes the continuous landing of troops and materiel possible and gives maneuver space for subsequent operations. | JP 3-18 |
| **After-action review** | A review at the end of an action of what was meant to happen, what did, why, and what to do differently. | TC 25-20 |
| **Unit status** | A unit reports its readiness as a C-rating, C-1 (fully ready) to C-4 (not), from the share of its personnel and equipment on hand and fit: at least 90, 80 and 70 per cent for C-1 to C-3. | AR 220-1 |

*How we use it:* a garrison's camp (`minecraft.camp`, as `x,z`) is built up the way an airborne
force builds a lodgment after the drop. Its phases:
1. Shelter: a patrol base each night (§14).
2. The airhead: the camp's ground lit (§15, SECURE) and walled (§15, FORTIFY). With no logs
   carried to make torches from and no tree in sight, the lit standard's task is TREE_FARM: the
   wood is grown first.
3. The buildup of sustainment: food that grows (§12, FARM), wood that grows back (§12, TREE_FARM),
   and stone and iron from a mine (§16, MINE). While more than half the soldiers are hungry with
   nothing to eat, food is an airhead standard instead, and isn't met however much is planted -
   wheat feeds nobody for days: a unit eats before it improves its position (GeneralScenarioIT's `starving-airhead`, 5 of 5), and the standard names where the camp's farm ground is, so the squad is sent there.
4. Equipment: every soldier armed with a stone sword or better and armoured (§11).

Every ten seconds the runner measures each phase's standards at the camp - from its ground, the
typical height of the ground round it found once, so a heap of old huts at its centre is neither
where the wall is laid nor where the light is measured - from the world rather
than from reports: dark cells left, wall blocks left, farm cells growing wheat and soldiers hungry
with nothing to eat, saplings and trees planted, steps cut down the mine, swords and armour
carried (`command.Foothold`). The general reads them in `<foothold>`, which ends by naming the
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
(`[Readiness]`) - soldiers ranging in and out of the camp would flip it back and forth. The general isn't shown them:
with the foothold in view it already keeps an exposed squad near shelter (GeneralScenarioIT's
`exposed-far-out`).

Each dawn the log carries an after-action review (TC 25-20) of the day and night before (`[AAR]`,
`app.AfterActionReview`): the camp's standards, met and open, as last measured; the deaths, by the
server's cause, with who; and who raised an idle alarm by day (§ 3).

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
