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

## The exchange, not thresholds (operator, 2026-11-21)
Each death so far got a rule of its own (`HURT_HEALTH`, `ARCHER_CLOSE_HEALTH`, the health a contact was met at); a
piece of armour breaking mid-fight is one no threshold sees. In their place, one pure decision (`bot.Engagement`, as
`Strike` is) over the exchange: its time to kill against its time to die - the approach under fire counted, its armour
and each piece's wear, its weapon's and the hostile's damage a second - and what breaking off costs (shots taken
running, slower in water). Its states: **engage** (melee, or the bow with arrows where that trades better), **hold or
cover** (can't win from here, can hold - a creeper in a shaft, no arrows), **withdraw** (loses the exchange and breaking
off costs less than finishing: out of contact, shelter, eat - 1.6 s an item - and heal, fast at full food), **re-engage**
(healed past its line, the hostile still in the way of the work or the squad), and **decline** (not prepared - no weapon,
no armour, ground unsafe - said up the chain, not fought).

Built: `bot.Engagement`'s exchange - time to kill against time to die, armour as the server cuts a blow, a fight begun
finished while it outlasts the kill - deciding whether an archer out of reach is closed on, in place of a health line.
And the battle simulation's body for one soldier's fights (`scenario.Body`, the combat and pairs matrices): food,
saturation and exhaustion; regeneration as the server's (saturated, up to a point a 0.5 s; at 18 food, a point a 4 s);
eating below 18, a loaf after 1.6 s; iron armour worn down max(1, blow/4) a piece a hit - all read from the pinned server.
With it one more pairs cell passes (creeper and zombie, stone sword, hills); without eating it, three archer cells on
hills fell to 83%, a soldier's food spent by its healing.
Tried and not landed - hand to hand on the exchange, backing off when lost: the matrix showed what the exchange lacks.
Bare hands lose to a zombie by the numbers (20 blows), so the soldier backed off for good and two zombies caught it on
hills (zombie+zombie, bare hands, hills: 67%); a soldier backing up stepped into a squadmate's sweep. Breaking off is
priced as a few blocks clear, true on open ground and not with pursuers on rough ground - the exchange wants the
escape's own cost, from the terrain and what follows, before hand to hand is on it.
Then built: the escape priced by pace and pursuit (`Engagement.escape`), and hand to hand on the exchange with it -
fought unless lost outright and got away from at a sprint; the matrices hold.
Then built: withdraw, eat, heal and re-engage (docs/military-doctrine.md § 8) - the body eating only out of contact, as
the runner does, the escape priced at the soldier's pace against an archer that follows, a stalled close reckoned as
striking nothing.
Left: the other branches on the exchange (creepers, several hostiles at once, the bow); a pillager its own archer -
6 off, following at a walk, a crossbow's 8 - and the hills cells it costs (pillager, wooden sword or sword and shield,
hills: 75%, a soldier stalled closing on one or in cover its crossbow still reaches; a pillager reckoned as a skeleton
in the exchange cost a third cell); the matrix grown by starting health, armour and terrain.

**Deaths as a check on the model, not a case each.** Each death is read for what the evals lack - is that dimension
(the armour's wear, water, a shaft, eating time, regeneration) in the battle simulation at all? - and the missing
dimension is what's added, the matrix grown by it, not a scenario to pass. Not every battle is won: the measure is the
war's (backlog/sites.md, losses priced in) - the loss rate an activity carries against its yield.

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
   its sight, and dies. Taking cover by the walk to it - the nearest cover cell with a walk there, followed - doesn't
   save her either, nor cover looked for out to 12: from where she stood no walk reaches any cover cell, so she's
   boxed there by the ground; what's left is cover made, not walked to - a block put between her and the thrower, or
   dug down out of its line.
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
