---
status: ready
priority: P1
depends-on: []
doctrine: the operator's engagement doctrine for the bot fleet (2026-11-16)
---

# Engagement doctrine: engage, bypass, avoid or break contact

## Why
The mission comes first; a fight is worth having only when it serves the mission or protects the
force (operator, 2026-11-16). A soldier that drops its task to chase or watch a threat it needn't
or can't fight has been fixed by it. Live (field277-284): a squad stood a night in a cave 4 to 8
blocks from creepers that never came on, and a builder fought its way 75 blocks east through the
caves, one hostile after the next. 541, 549 and 550 built the matrix's bottom-right cell ad hoc.

## The doctrine, as given
- **Soldier tier is deterministic**: it checks the criteria its order carries; judgement is the
  order-writing tier's (squad leader, platoon sergeant).
- **Order fields**, defaults in brackets: `movement_mode` MOVE | ATTACK_MOVE | HOLD_POSITION
  [MOVE]; `weapons_control` WEAPONS_FREE | WEAPONS_TIGHT | WEAPONS_HOLD [WEAPONS_TIGHT];
  `engagement_criteria` [blocking the path or targeting this bot]; `bypass_criteria` [neither];
  `disengagement_criteria` [health below a threshold or outnumbered past one]; `report_contacts`
  [true].
- **Reachability matrix**, asked first: can it reach me, can I reach it.
  - Both: apply engagement and disengagement criteria.
  - It can, I can't: passive defense - cover, carry on if possible - report contact and the
    capability gap.
  - I can, it can't: engage only if the criteria are met and the fight serves the task; else ignore.
  - Neither: ignore, keep working, re-check when things change.
- **On contact, in order**: self-defense (attacked and can't break contact: fight, over
  WEAPONS_HOLD); disengage if a criterion is met; classify by the matrix; engage if weapons control
  and criteria permit, within a time or distance budget; else bypass - carry on, routing round a
  hostile near the path, keep moving; report.
- **Bypassed threats** get a follow-on disposition: outpace, hand off, or re-evaluate after N
  seconds or within M blocks of the destination [re-evaluate].
- **Reports**: contact (type, position, count, matrix cell, action, pursuing?) and capability gap
  (e.g. RANGED_WEAPON) - the platoon sergeant turns repeated gaps into production or loadout.
- **Prevention beats engagement**: sleep against phantoms, lighting and walls against ground mobs.

## Phases
1. The matrix at contact and bypass in routing: a path cost near hostiles, a creeper on the route
   gone round, not fought; a hostile not on the path and not targeting carried past.
2. The order fields and their defaults through Order, the squad leader and the soldier.
3. Contact and capability-gap reports, and the sergeant's answer to repeated gaps.

## To verify first
- Phantom spawning and whether the server counts time since rest for these clients.
- Mob follow ranges and speeds per type, for outpace and the re-evaluate thresholds.

## Done when
- No soldier is held a minute by a hostile in the matrix's ignore cells, and none chases past its
  order's budget.
