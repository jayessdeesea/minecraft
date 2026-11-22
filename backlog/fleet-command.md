---
status: ready
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
nobody's job.

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

## Done when
- The squad leader's model, prompt and configuration key are gone, the data above in the scoreboard.
- Each wake above has its cause in a `[PL] wakes:` line, in the squad simulation.
- A dead soldier's replacement is kitted by the platoon sergeant's rule, in the squad simulation.
- Live: a full day, the platoon leader woken only at dawn and on escalations, the platoon on schedule.

## Notes
- **Decided (operator, 2026-11-22): four like squads**, each given its task with its own area of operations
  (backlog/authority-and-missions.md steps 3 and 4). The four elements by function planned here (two rifle squads, an
  engineer squad, a sustainment section the platoon sergeant re-tasks, a rifle squad surged into production) are
  dropped: production is tasks the platoon leader gives like squads.
- **Not now** (operator, 2026-11-15): detachments as data on the platoon leader's objective, and a squad leader's
  decision every second - the soldiers' drills react every tick, and each decision is a FRAGO on the net.
- The operator is the company commander, not a role inside the fleet: the platoon leader's intent is always the
  operator's mission restated, never one it made up (backlog/mission-estimate.md, the missions in order).
