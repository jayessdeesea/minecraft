# Military doctrine this project emulates

The chain of command here (General → Squad Leader → Soldier) deliberately borrows real military
doctrine for *how* echelons talk to each other, not just what they're called. The point is to see
where a doctrine refined over a century for humans under stress - limited bandwidth, partial
information, subordinates who must act without asking - overlaps with what works for LLM agents
at different timescales. When code implements one of these ideas, it cites the section here, and
this doc cites the manual.

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

*How we use it:* a subordinate answers an order with **WILCO** or **CANTCO + reason**. CANTCO is
the push-back channel: the squad leader orders a spot that can't be reached and the squad *says
so*, rather than silently stalling. A soldier that can't reach its slot (fell into a pit, cut off
by water) reports CANTCO with the reason; the squad leader decides what to do about it - the same
loop a human squad runs. At the top of the chain, the general answers a new mission from the human
operator with WILCO on the radio net.

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
in hand and arrows, equipment is armour worn (`command.Kit`). Squad leaders aggregate their
soldiers' SITREPs into one for the general, with a LOGSTAT summed from the soldiers' LACE
(`command.Logstat`): hungry soldiers (Class I), empty armour slots (Class II), bare-handed
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
objective, formation, or interval against the standing mission.

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
just knowing where the squad is - our soldiers can't mistake each other for the enemy.

## 7. Risk acceptance

| Practice | Content | Source |
|---|---|---|
| **Risk levels** | Low, medium, high, extremely high: how likely and how severe a loss the mission exposes people to. | ATP 5-19, *Risk Management* |
| **Risk acceptance** | The commander decides how much risk the mission justifies, at the level that owns the mission; subordinates operate within it. | ATP 5-19 |

*How we use it:* the squad leader's order carries a `Risk` (LOW, MEDIUM or HIGH), the health the
mission justifies spending to keep moving. The priorities are the mission first, then the
soldiers' survival, then avoiding damage. Today health is spent only on falls of more than 3
blocks, at 1 health per block beyond 3; armour doesn't soften falls. Each soldier turns the order's
risk and its own condition into a fall allowance:
- **The floor:** it never falls below the risk's minimum health (LOW allows no damage, MEDIUM keeps
  10, HIGH keeps 4).
- **The price:** each point of damage costs extra route length, halved for a well-fed soldier
  because it regenerates.

So a safe route always wins when one exists. A soldier that reports CANTCO prices the riskier
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
  (`DeterministicSoldier.tick`): it closes to its 3-block reach, strikes when its attack has
  recharged, backs off inside 2.5 blocks, which is outside a zombie's reach, and holds in
  between. It gets away from creepers instead. Losing health with no hostile within 8 blocks
  means being shot at, so for 5 seconds it looks out to 16 blocks, beyond a skeleton's 15-block
  bow range, and goes for the shooter.
- **The squad's drill.** `command.ReactToContact` sends teammates within 24 blocks to support,
  ahead of the squad leader's next decision.
- **The squad leader** sees the contact in its next SITREP and can order something else.

In the combat simulation, three zombies on one soldier's flank kill a lone soldier 64 times in
100. A squad spread 10 blocks apart without the drill still wins, but loses its flank soldier 66
times in 100. With the drill it loses nobody.

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

## Sources

- ATP 3-21.8 (2024), Ch. 3 §2 - [fire team formations](https://infantrydrills.com/manuals/fm-atp-3-21-8-infantry-rifle-platoon-squad-2024/movement-and-maneuver/tactical-movement-approach-march/movement-formations/fire-team-movement-formations/), [squad formations](https://infantrydrills.com/manuals/fm-atp-3-21-8-infantry-rifle-platoon-squad-2024/movement-and-maneuver/tactical-movement-approach-march/movement-formations/squad-movement-formations/)
- FM 3-21.9 Ch. 3 (formation intervals reduced to keep visual contact) - [GlobalSecurity](https://www.globalsecurity.org/military/library/policy/army/fm/3-21-9/chap3.htm)
- ADP 6-0 (2012, archived) - [UNT web archive](https://webarchive.library.unt.edu/web/20130213211329/http:/armypubs.army.mil/doctrine/DR_pubs/dr_a/pdf/adp6_0_new.pdf)
- FM 6-99.2 report and message formats (2007, archived) - [UNT web archive](https://webarchive.library.unt.edu/web/20130213212857/http:/armypubs.army.mil/doctrine/DR_pubs/dr_a/pdf/fm6_99x2.pdf)
- FM 34-35 App. C, SALUTE spot report - [irp.fas.org](https://irp.fas.org/doddir/army/fm34-35/Appc.htm)
- Prowords: [Procedure word (Wikipedia)](https://en.wikipedia.org/wiki/Procedure_word), [USMC MCT-COMM-1003/4 Radio Communications](https://www.trngcmd.marines.mil/Portals/207/Docs/FMTBE/Student%20Materials/MCECST/MCT-COMM-1003_4%20Radio%20Communications.doc), [SERA.14045 (UNABLE)](https://regulatorylibrary.caa.co.uk/923-2012/Content/Regs/03440_SERA14045_Transmitting_technique.htm)
- LACE: [Kentucky National Guard photo caption](https://ky.ng.mil/News/Photos/igphoto/2003751897/), [RallyPoint discussion](https://rallypoint.com/answers/what-s-the-difference-between-each-of-these-reports-salute-lace-salt-spot-report-sit-rep) (secondary sources)
- Link-up procedure: FM 3-21.9 ch. 7 - [GlobalSecurity](https://www.globalsecurity.org/military/library/policy/army/fm/3-21-9/chap7.htm)
- ATP 5-19, *Risk Management* - [Army Publishing Directorate](https://armypubs.army.mil/)
- ATP 1-02.1 multi-service brevity codes (CANTCO) - [ALSSA](https://www.alssa.mil/MTTPs/Brevity/)
