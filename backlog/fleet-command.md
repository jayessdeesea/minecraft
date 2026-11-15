---
status: ready
priority: P1
depends-on: []
doctrine: ATP 3-21.8 (infantry platoon and squad: duties of the platoon leader, platoon sergeant and squad leader; sustainment; troop leading procedures); ADP 5-0 / FM 5-0 (commander's critical information requirements, decision points); ADP 6-0 (mission command)
---

# Fleet command: a platoon that works, a model only at the top

## Why
The operator's decision (2026-11-14): the goal is a fleet of bots that works, no longer the data
on how to tier AI agents. The README's goal changes with it. What the tiering has taught stays:
every bad decision traced so far came from a model doing arithmetic or working out state it should
have been shown (docs/prompting.md), and the deterministic parts - the foreman's rectangles, the
equipment ladder, the drills - do their jobs better than a model asked to. So:
- **One model, at the top**, where judgement is needed and the cost of a call is low: turning the
  operator's missions into objectives and intent, choosing among missions already costed, setting
  priorities for scarce materials, and deciding what's escalated to it.
- **Everything else deterministic**, organised as a real infantry platoon is, so doctrine for each
  job comes from the manual for that job.
- **The model wakes on events, not on a clock**, as a commander is told what he's asked to be told
  and the rest runs on standing procedures.

## The organisation
Two squads of four are an understrength platoon. Named for the platoon the roster grows into
(four squads, cells.md):

| Role | Today | Becomes | Thinks with | Doctrine |
|---|---|---|---|---|
| Company commander | the operator | the operator: issues missions, sets the timetable, decides the decided-by-operator lines | a person | the higher headquarters' order |
| Platoon leader (PL) | the general | intent, missions chosen, priorities set, escalations decided | the model | "responsible for all the platoon does or fails to do" |
| Platoon sergeant (PSG) | parts of `command`, `Armourer`, `CrossLevel` | sustainment: the supply gate, reorder points, producers tasked, kit by role, pre-combat checks, replacements after deaths | code | the platoon's primary sustainment operator |
| Squad leader (SL) | a model every 20 s, then drills | task organisation within the squad: who has which role where, from the PL's objective and the PSG's detachments | code | owns the squad's plan |
| Engineer / foreman | the keep's foreman, the engineer's siting | as now: sites workshops, shares out dig work level by level | code | general engineering |
| Soldier | code | as now | code | |

The staff work a battalion would do - the mission estimate, the survey, the timetable - is done
for the PL by code, as tools of its troop-leading procedures, never by the model.

**What the squad leader's model does today, and what replaces it.** Its allocation every 20
seconds - who, where, how many (docs/echelons/squad-leader/decisions.md) - becomes a deterministic
allocator over the squad's soldiers, the way the foreman shares out the keep: roles from the
objective's task, soldiers from the nearest fit and fed, detachments from the PSG. Each decision it
makes today becomes a rule or an escalation; nothing is dropped without one. Its measured misses
are part of the case: `alldetached` passed 0 of 7, and its false CANTCOs moved squads on many
times an hour (backlog/scenario-matrix.md).

## At sixteen: four squads by function
The roster grown to sixteen (operator, 2026-11-14), organised by what the work needs rather than
as four like squads:

| Element | Soldiers | Owner | Does |
|---|---|---|---|
| 1st squad (rifle) | 4 | the PL's missions | recon, expeditions, escorts, security for work outside the walls |
| 2nd squad (rifle) | 4 | the PL's missions | the same, alternating with 1st: one out, one at the camp |
| 3rd squad (engineer) | 4 | the PL's missions; the foreman is its squad leader | the keep, walls, outposts: campaigns that last days |
| Sustainment section | 4 | the PSG | production: wood, food, stone, ore |

A real infantry platoon is three rifle squads of nine and a weapons squad, with its supply from the
company. The engineer squad and the sustainment section are brought down from company and battalion
level: right at this size, and said so here rather than claimed as a platoon's organisation.

**The sustainment section is a pool, not four trades.** Its four soldiers are reassigned among the
classes of supply by the PSG, each to the class projected furthest below its reorder point
(maturity-model.md): all four mining when only ore is short, as the camp matures and the keep's
4,096 cobblestone and iron for the work loads lead; a forester taken off for a grove's harvest pass
and back (renewables.md, the harvest cycle); a farmer between harvests on something else.
- **When:** at the dawn decision point, and at once on a PAUSE.
- **How much:** outside a PAUSE, at most one soldier moves a decision, and a soldier finishes the
  trip or harvest pass it's on before it does.
- **What a move costs:** the soldiers find their own routes and fight what comes at them
  (docs/echelons/soldier/capabilities.md), so a move costs only the walk and the swap of work load
  - pickaxes for axes - drawn at the new site's depot.

**The rifle squads surge into production.** Since every soldier defends itself, a rifle squad held
at a lit, walled camp is a reserve, not a guard: on a PAUSE, the PSG takes the squad at the camp
first, never the engineers mid-campaign. Security as a task stays where self-defence isn't enough:
- **Outside the walls and at night,** where contact is likely: a soldier that drops its work to
  fight isn't producing, and one fighting at low health dies and drops its kit, which is the worst
  drain on supply there is (backlog/roadmap.md: about 50 deaths in the first twelve hours, every
  kit lost). There a rifle squad covers the work - two work, two watch - as doctrine keeps security
  continuous rather than leaving it to each soldier's reflex.
- **On an expedition or at an outpost** before its AIRHEAD.

## The platoon leader, event-driven
**Troop-leading procedures** (ATP 3-21.8) are the PL's process for a mission: receive the mission,
issue a warning order, make a tentative plan, initiate movement, conduct reconnaissance, complete
the plan, issue the order, supervise and refine. Here:
- *Receive the mission:* an operator order, in the operator's words.
- *Make a tentative plan:* the candidate missions' estimates (backlog/mission-estimate.md); the PL
  picks one.
- *Conduct reconnaissance:* an estimate that can't be completed for want of facts asks for a recon
  mission (the survey's information requirements).
- *Issue the order:* objectives and intent per squad, as `Objective` today.
- *Supervise and refine:* the standing view, refreshed at each wake.

**When it wakes.** The PL sets, and the operator may add to, its commander's critical information
requirements - what it must be told - and the deterministic tier escalates only those:
- An operator order.
- The dawn decision point: the AAR, each site's level and supply state, the day's timetable
  (the only time a site advances; maturity-model.md).
- A site entering PAUSE or RECONSTITUTE.
- A mission found infeasible or out of reach, or finished.
- A level behind its due day at dusk.
- A CANTCO the squad leader's drills couldn't clear, with what they tried.
- Deaths over a threshold in a day.
Otherwise it sleeps, and the platoon runs on its standing orders.

**What it's shown.** The standing view (`<sites>`, `<missions>`, priorities in force) and the
escalation that woke it, computed as `<foothold>` is now. Each wake is a log line with its cause
and its decision (`[PL]`), so every model decision is accounted for.

## Done when
- The README's "What it is" names the organisation above, as each part of it lands (its goal, a
  fleet of bots that works, is stated: README, AGENTS.md).
- The echelons are named as above in code, docs and prompts (`General` → `PlatoonLeader`;
  `docs/echelons/general` → `platoon-leader`; a `platoon-sergeant` echelon added), with
  `GeneralScenarioIT` run before and after the rename: the name is in the system prompt, and a
  prompt edit is measured, not assumed safe (docs/prompting.md).
- The PSG owns sustainment as one component: the supply gate and reorder points
  (maturity-model.md), producers detached within the PL's priorities, kit by role, cross-leveling,
  replacements - pulled together from where they live now.
- The squad leader's model is replaced by a deterministic allocator; `SquadLeaderScenarioIT`'s
  situations become its tests, and each one it can't pass is an escalation to the PL instead.
- The PL wakes only on the triggers above; the log shows each wake and its cause; a day with
  nothing escalated has only the dawn wake.
- At sixteen soldiers, the four elements above; the sustainment section reassigned at dawn by
  projected shortfall, no more than one move a decision outside a PAUSE, and a rifle squad surged
  into production on a PAUSE.
- Live: a full day with the keep under way, the PL woken only at dawn and on escalations, and the
  platoon on schedule.

## Notes
- **Tenet changes** (docs/echelons/tenets.md, on landing): "The general balances it, as a
  cross-squad matter" becomes the PSG's, within the PL's priorities. "Never names a soldier" holds:
  the PSG and the squad leaders name soldiers; the model never does. The squad leader's
  "thinks in few round trips" goes with its model.
- **Sequencing:** the PSG first, since the supply gate needs an owner and is the fix for running
  out; the squad leader's model next; the PL's event loop with them.
- The operator is the company commander, not a role inside the fleet: the PL's intent is always
  the operator's mission restated, never a mission the PL made up (mission-estimate.md, the
  operator's list first).
