---
status: ready
priority: P1
doctrine: FM 3-99 (airborne lodgment); ATP 3-37.10 (base camps); ATP 3-21.8 ch. 7 (patrol base, priorities of work); FM 3-21.8 (defense, priorities of work); TC 3-21.76 (Ranger Handbook); TC 25-20 (after-action reviews); AR 220-1 (unit status)
---

# Roadmap: what to expect on day 1, day 2, and after

## Why
The garrison has the pieces of a lodgment (docs/military-doctrine.md § 17) but no timetable, so
nothing says whether it's on schedule or behind. Measured on 2026-11-07, after about twelve hours
of garrison running (some 36 in-game days), it is behind:
- **Casualties.** About 50 deaths. More than half were falls ("fell from a high place", "hit the
  ground too hard"), several at once; then skeletons, creepers, endermen, one phantom, and three by
  a squadmate's sword. Every death drops the soldier's kit, so gear never accumulates.
- **Gear.** Every soldier carries a wooden sword and no armour. The ladder (§ 11) reached a bow
  once. Stone is gone within reach of the camp, and no mine has been cut.
- **Sustainment.** No soldier carries a ration. Soldiers walk round at 1 to 5 health, because
  health only comes back at food 18 or more.
- **The camp.** A part-built dirt wall, scattered huts and torches. It doesn't look like a base.

Real units fix this with a timetable of *priorities of work*, each "a task, a given time, and a
measurable performance standard" (ATP 3-21.8 ch. 7; FM 3-21.8), improved continuously: a hasty
position becomes a deliberate one, and an airhead becomes a lodgment. Their leaders also run an
*after-action review* at the end of each day (TC 25-20): what was supposed to happen, what did
happen, why, and what to do differently. Both map directly onto this project. The timetable is
data the general can be measured against, and the review is the daily read of the log.

## Doctrine
- **Lodgment phases** (FM 3-99, from FM 90-26): seize the airhead, consolidate it, expand it to
  an airhead line that can be held, then build up combat power inside it. Already the shape of
  § 17's phases.
- **Base camp standards** (ATP 3-37.10): construction goes *initial*, then *temporary*, then
  *semi-permanent*. Each standard is austere at first and built up with more engineer effort as
  the camp is held longer.
- **Priorities of work, patrol base** (ATP 3-21.8 ch. 7; TC 3-21.76): security first and
  continuous; then the withdrawal plan, communications, mission preparation, weapons and equipment
  maintenance, water, mess and rest plans; stand-to at dusk and dawn. Never more than part of the
  unit eating, cleaning weapons or sleeping at once.
- **Priorities of work, defence** (FM 3-21.8): local security, then position key weapons, then
  dig fighting positions in stages. Stage 1 is sited, stage 2 is the retaining walls, stage 3 is
  dug, and stage 4 is the overhead cover that makes a hasty position deliberate. Then obstacles,
  supplementary positions, and continuous improvement.
- **Unit status** (AR 220-1): C-1 to C-4 from personnel and equipment on hand and fit. This is
  already the squads' C-rating.

## The timetable
Each phase must be met by its day, measured at that day's dusk (day tick 12000). An in-game day
is 20 minutes, and the garrison starts at a dawn as if just dropped in. "All" means every soldier
alive at the time.

| Day | Phase (§ 17) | Standard by dusk |
|---|---|---|
| 1 | Airhead seized: initial standard | All armed (a wooden sword at least) by noon. Each squad sealed and lit in a hasty hut at dusk. Two food each carried or eaten. No falls. |
| 2 | Airhead consolidated | All with a stone sword and pickaxe. The camp's ground lit (SECURE). A farm planted. Each squad's hut within the camp. Health back to full by dusk (food 18 or more). |
| 3 | Airhead line held | The wall closed, with its lip and a gate. A tree farm planted. A stockpile chest of logs and cobblestone. Shields for all. First bread. |
| 4–5 | Buildup: temporary standard | A mine cut to y=16 with a lit landing. Copper, then iron armour for half the soldiers. Two rations each carried. The huts replaced by one barracks with a bed per soldier. |
| 6–7 | Lodgment established | Iron armour, sword and pickaxe for all. The wall lit on top. A furnace, table and chests in the camp. Casualties under one a day. C-1 for both squads. |
| 8+ | Semi-permanent standard, then expansion | Branch tunnels and a base at a second mine level (backlog/mining.md). Diamonds. A Nether foothold (backlog/readiness.md). The home camp stays the biggest. |

**Daily drills, from day 1:**
- **Stand-to** at dusk and dawn: every soldier inside the walls or its hut at dusk, and not out
  until dawn's stand-to ends. This is built for huts (§ 14).
- **The after-action review** at each dawn is logged (docs/military-doctrine.md § 17). What it
  doesn't say yet: the day's own standard from this timetable, and each squad's C-rating at dusk.

## Done when
- The timetable is data in `command` (`Foothold` gains a day: each phase's due day and its
  standards), so "behind schedule" is computed, not judged. The general's `<foothold>` names the
  day and what's late.
- The AAR names the day's standard from the timetable and each squad's C-rating at dusk.
- A fresh garrison started at a dawn meets day 1's and day 2's standards in a live run, and holds
  every later standard it reaches on later days.

## What's in the way
In the order the timetable meets them:
- **Falls** (day 1). The casualty reports (§ 6) will say which routine walks soldiers off edges.
  The server also logs "standing on air" for every soldier, in bursts. Our model's ground and the
  server's may disagree, or our client may report being on the ground when it isn't.
- **Kit lost at death** (every day). Until deaths are rare, kit is rebuilt after every one. See
  Notes for the gamerule.
- **Health below full** (day 2). Food at 18 or more is the standard, not just "not hungry". Hunting
  and farming have to keep the squads above it, and rations have to be carried and eaten by day.
- **Stone runs out near the camp** (day 2). The mine (§ 16) is the camp's quarry. Cut it on day 2,
  not after the buildup phase.
- **Gate and wall light** (day 3, backlog/patrol-base.md).
- **Barracks and beds** (days 4–5, backlog/rest-plan.md): a bigger room inside the walls.
- **Branch tunnels and mine levels** (day 8+, backlog/mining.md). **Bases beyond the camp** (day
  8+, backlog/readiness.md).

## Notes
- **Needs a decision (operator).** Two server gamerules would change the timetable:
  - `keepInventory` would end gear loss at death. It makes the experiment easier, but less honest
    about what the chain of command achieves.
  - `pvp false` would end fratricide outright. A sword already holds its swing while a squadmate
    stands in its sweep (docs/military-doctrine.md § 8), which keeps friendly fire as a real
    constraint on the soldiers' drill.
  Both are the operator's world, so the operator's call.
- The days are a target to measure against, not a script. The general still decides what each
  squad does. The timetable only says where the camp should be by now.
