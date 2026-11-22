---
status: needs-decision
priority: P1
depends-on: [authority-and-missions]
doctrine: ATP 3-21.8 (infantry platoon and squad: duties of the platoon leader, platoon sergeant and squad leader; sustainment; troop leading procedures); ADP 5-0 / FM 5-0 (commander's critical information requirements, decision points); ADP 6-0 (mission command)
---

# Fleet command: what's left of a platoon with a model only at the top

## Why
The goal is a fleet of bots that works (operator, 2026-11-14): one model, the platoon leader, and everything under it
code organised as an infantry platoon is. The platoon leader, the platoon sergeant, the squad leaders' drills, the
roster of sixteen and the platoon leader's wakes stand. What doesn't: the squad leader's model and prompt are still in
the code, some of the things the platoon leader should be woken for can't wake it, a dead soldier's replacement is
nobody's job, and the sixteen are four like squads where this item planned four elements by function.

## What's left
- **The squad leader's model removed** (operator, 2026-11-15): `command.ai.SpringAiSquadLeader`, its prompts and
  `minecraft.squad-leader.decides-with: model` go once the data says they're not needed - a full live day with the
  drills leading, no more soldier-stuck alerts, idle soldiers or standards held than under the model's days in the
  scoreboard. With it go the two "the general"s in `squad-leader-system-prompt.md` (unedited till then: an unmeasured
  prompt edit isn't safe, docs/prompting.md) and the tenet "Thinks in few round trips" (docs/echelons/tenets.md).
- **Wakes.** The platoon leader's decision changing only on events is backlog/authority-and-missions.md step 5, which
  continues what's built (`[PL] wakes: <cause>`). Wanted, and not in that step's list: a site entering PAUSE or
  RECONSTITUTE (backlog/sites.md), a level behind its due day at dusk, deaths over a threshold in a day, the
  information requirements the platoon leader sets itself, and a CANTCO carrying what the drills tried.
- **Replacements after deaths** are the platoon sergeant's and aren't built; nor do the equipment ladder and the ore
  pool sit with it yet (docs/echelons/platoon-sergeant/decisions.md, "Still elsewhere").
- **The README's "What it is"** names the platoon sergeant, and each element below as it lands.
- **Four elements by function** - to decide (Notes).

## At sixteen: four squads by function
To decide (Notes). As planned (operator, 2026-11-14), the sixteen organised by what the work needs:

| Element | Soldiers | Owner | Does |
|---|---|---|---|
| 1st squad (rifle) | 4 | the platoon leader's missions | recon, expeditions, escorts, security for work outside the walls |
| 2nd squad (rifle) | 4 | the platoon leader's missions | the same, alternating with 1st: one out, one at the camp |
| 3rd squad (engineer) | 4 | the platoon leader's missions; the foreman is its squad leader | walls, outposts, flattens: work that lasts days |
| Sustainment section | 4 | the platoon sergeant | production: wood, food, stone, ore |

A real infantry platoon is three rifle squads of nine and a weapons squad, supplied from the company: the engineer
squad and the sustainment section are brought down from company and battalion level, and said so.
- **The sustainment section is a pool, not four trades:** each soldier to the class of supply projected furthest below
  its reorder point (backlog/sites.md). Reassigned at the dawn decision point, and at once on a PAUSE; outside a
  PAUSE at most one soldier moves a decision, and a soldier finishes the trip or harvest pass it's on first. A move
  costs the walk and the swap of work load at the new site's depot.
- **A rifle squad surges into production on a PAUSE** - the squad at the camp first, never the engineers mid-work. A
  rifle squad covers work only where self-defence isn't enough: outside the walls and at night (two work, two watch),
  on an expedition, or at an outpost before its AIRHEAD.

## Done when
- The squad leader's model, prompt and configuration key are gone, the data above in the scoreboard.
- Each wake above has its cause in a `[PL] wakes:` line, in the squad simulation.
- A dead soldier's replacement is kitted by the platoon sergeant's rule, in the squad simulation.
- If the elements stand: the roster is the four elements; the sustainment section is reassigned at dawn by projected
  shortfall, no more than one move a decision outside a PAUSE; a rifle squad is surged into production on a PAUSE.
- Live: a full day, the platoon leader woken only at dawn and on escalations, the platoon on schedule.

## Notes
- **To decide (operator):** does "four squads by function" still stand? backlog/authority-and-missions.md steps 3 and
  4 treat the squads as like units, each given a task with its own area of operations and a mine site of its own by
  night, its squad leader splitting that area into sectors; nothing there keeps a standing sustainment section the
  platoon sergeant re-tasks, or a rifle squad taken for production by a rule.
  - **Stands:** the section above stays, and says how the platoon sergeant's moves are orders under the one authority
    (the platoon leader's task to the section, the platoon sergeant its squad leader), not a standing rule rewriting
    them.
  - **Retired:** the "At sixteen" section and its Done-when bullet are deleted; production is tasks the platoon leader
    gives like squads (steps 3 and 5), and this item is the model's removal, the wakes, replacements and the README.
- **Not now** (operator, 2026-11-15): detachments as data on the platoon leader's objective, and a squad leader's
  decision every second - the soldiers' drills react every tick, and each decision is a FRAGO on the net.
- The operator is the company commander, not a role inside the fleet: the platoon leader's intent is always the
  operator's mission restated, never one it made up (backlog/mission-estimate.md, the missions in order).
