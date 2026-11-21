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

## Decided (operator, 2026-11-17)
- **Endermen:** engaged only close, never provoked - fought at hand and never looked in the eye (both
  built: `HostileMobRegistry.fightsOnlyAtHand`, `bot.EndermanGaze`).
- **The keep's site at dusk:** the squads leave it before dusk.
- **After dark:** no soldier walks the surface on an errand - a respawn's link-up (built), a walk to a
  workshop or the stockpile - as no resupply walk does (`SoldierRunner.nightSupply`).

## Phases
1. The matrix at contact and bypass in routing: a creeper on the route gone round is built
   (docs/military-doctrine.md, contact). Left: a hostile not on the path and not targeting
   carried past - a melee hostile in contact is closed on today whatever it's doing.
   Live (field410), Alice and Hal at Alpha's y=12 faces each closed on a skeleton in the cave
   under them and were shot 16 blocks down, 13 s after they left their cut - a route to the archer
   existed (down the cave, or by digging), so neither was "pinned" (`DeterministicSoldier.pinned`)
   and both crossed the open below its bow. Built for an archer more than 4 below (`DeterministicSoldier.ARCHER_BELOW`):
   cover, carry on; an archer in sight within 16 closed on by one armed for melee, and one it can't close on that's
   out of contact and not shooting let be, its work going on (operator, 2026-11-20, `ARCHER_ENGAGE`). Left: one across
   a cave at the soldier's own height. The nearest isn't always the danger: a creeper within 4.5 and an archer
   beside a hand-to-hand hostile out of reach are put first (built, field595-602, replayed on a dead soldier's kept
   ground - `app.RecordedContactTest`). Left: more contacts replayed so - the battle simulation's moving hostiles on
   a kept ground, not only hostiles standing where they were logged; and the drowned's trident (field604: Mia held her
   post 10.8 from a drowned 9 below in water, "carries on: no way between them on foot", and was killed - replayed,
   `contact/mia-field604`, known). Counted as one that shoots, she backs 3 blocks into the ground behind her, still in
   its sight, and dies: what's left is getting out of a thrower's sight across open ground - away along the route
   out of its line, not a step straight back.
2. The order fields and their defaults through Order, the squad leader and the soldier.
   A creeper holding a soldier inside its reach ten seconds is engaged (built); the rest of the
   both-reach cell - criteria and weapons control by order - is phase 2's.
3. Contact and capability-gap reports, and the sergeant's answer to repeated gaps. Built at the soldier: each
   contact logged with the hostile, the soldier's kit and the rule that chose its response (`bot.ContactLog`,
   `[Contact]`). Left: the report up the chain, and the sergeant's answer.
4. Each cell measured: the combat eval matrix (`CombatMatrixTest`, docs/military-doctrine.md § 8) holds each kind
   of contact to what correct is, its short cells known.

## To verify first
- Phantom spawning and whether the server counts time since rest for these clients.
- Mob follow ranges and speeds per type, for outpace and the re-evaluate thresholds.

## Done when
- No soldier is held a minute by a hostile in the matrix's ignore cells, and none chases past its
  order's budget.
