---
status: ready
priority: P1
depends-on: []
doctrine: ATP 3-21.8 (actions on contact; battle drills 1 and 3); FM 3-90 (actions on contact; engagement, bypass and disengagement criteria); TC 3-21.76 (break contact)
---

# Engagement rules: when a soldier engages, bypasses or breaks contact, row by row

## Why
The operator (2026-11-24): evals for every form of combat - weapons, terrain, armour, enemies - and the manuals looked
up for when to engage and when to run. The response to a contact is one pure function now (`bot.Contact.respond`) and
`ContactEvalTest` pins what it answers for each combination. This item is what those answers should be: the rule from
doctrine, the reckoning from facts the code has, and each row group marked SAME or CHANGE. The order fields, weapons
control and the reports up the chain are backlog/engagement-doctrine.md's; this item is the soldier's own decision.

## The doctrine's decision
The manuals give the leader a decision and its factors, not a number: no source read gives a squad a threshold for
engaging or breaking contact.

- **On contact the choices are attack, bypass, defend, delay or withdraw**, chosen within the higher commander's intent
  and within the unit's capability (FM 3-90 (2023) para 1-78; ATP 3-21.8 (2016) paras 2-8, 2-160, 2-161). The leader of
  a unit under direct fire decides whether to assault, bypass or break contact after asking whether the unit must
  move out of the engagement area, whether it can gain and keep suppressive fire, and the enemy's place, size and
  flanks (ATP 3-21.8 (2016) App. J, Battle Drill 1, steps 10-13; the same drill in TC 3-21.76 (2017) ch. 8).
- **Engage** when the mission needs it and the unit has the combat power: an immediate attack is for a unit with
  enough combat power at hand to overwhelm what it met (FM 3-90 para 1-83). What is to be engaged or avoided is set
  beforehand as engagement criteria (FM 3-90 para 12-27; ATP 3-21.8 (2024) para 5-205).
- **Bypass** an enemy the criteria allow to be avoided - by its size and by whether it can interfere - keeping it
  under observation till it's handed off (FM 3-90 paras 4-21, 12-27; ADP 3-90 (2019) para 3-24). At platoon level a
  bypass needs the company commander's leave (Battle Drill 1, step 13).
- **Break contact** to avoid becoming decisively engaged - committed with no way to manoeuvre or get out - or to
  keep combat power for the mission (FM 3-90 paras 4-38, 12-28, B-24; ADP 3-90 paras 2-50, 4-28). The drill: one element
  suppresses, the other bounds back, till the enemy can neither see nor fire on the unit; then account, report, carry
  on (ATP 3-21.8 (2016) App. J, Battle Drill 3). A patrol that can't gain fire superiority breaks contact (TC 3-21.76
  paras 13-9, 13-12, 13-13 - written for the mounted patrol). A Marine squad leader decides to start or avoid contact
  by the squad's mission and whether it can engage that enemy effectively, and a patrol whose mission isn't the
  fight breaks contact as fast as it can (MCIP 3-10A.4i, "Immediate Actions Upon Enemy Contact", pp. 121-126).
- **Make contact with the smallest element**, and keep the rest free to manoeuvre (FM 3-90 paras 1-75, 4-17; ADP 3-90
  para 1-46).
- **Force ratios** - attack 3:1, hasty attack 2.5:1, defend 1:3, delay 1:6 - are staff planning figures, a start for an
  estimate and no rule on contact (FM 6-0 (2014) para 9-103, Table 9-2).
- **The soldier**: returns fire and takes the nearest cover at once (Battle Drill 1, steps 1-2), and crosses open ground
  in rushes of 3 to 5 seconds, too short to be tracked (TC 3-21.75 (2013) paras 7-6, 7-7).

Read from archive.org's text of the manuals' scans, not armypubs.army.mil, which wasn't reached; TC 3-21.76's
chapter 8 has no paragraph numbers. Not read: the 2022 drill texts ATP 3-21.8 (2024) points to, MCWP 3-11.2, FM 3-90-1.

In the soldier's terms:

| Decision | When | Today's responses |
|---|---|---|
| Engage | It blocks the work or is on the soldier, and the exchange is won - or it can't be got away from | fights, shoots, closes on the creeper |
| Bypass | It can't reach the soldier, or isn't hostile till provoked, or the route goes round it | carries on (no way, overhead, a creeper outside its blast, an archer out of contact) |
| Break contact | The exchange is lost and the soldier can get clear alive; or it can hurt the soldier and the soldier can't hurt it | gets clear, takes cover, backs off, withdraws to heal |

## Relative combat power, from what the code has
`bot.Engagement` already is this for one soldier against one hostile: seconds to kill (the walk to it, then blows)
against seconds to die (its blows through armour), with half again to spare before closing on an archer, and the cost
of getting clear at the difference in pace. What it lacks is below; each is a CHANGE further down.

- **The hostile's kind.** Every hostile is reckoned a zombie (20 health, 3 a blow, one a second, reach 2, 4 blocks a
  second) or a skeleton (20, 4 a shot, one every 2 s from 15, following at 2.2). The decision knows four kinds: archer
  (skeleton, stray, bogged, pillager), creeper, enderman, everything else. A profile per hostile - health, armour, blow,
  rate, reach or range, pace, whether armour or a shield stops it - replaces both.
- **Numbers.** One hostile is reckoned, the first; the rest and the squadmates near are not. Time to die takes the
  blows of every hostile that can reach the soldier; time to kill takes the blows of every squadmate within a blow
  of the same hostile.
- **Armour.** The kit counts pieces, each reckoned 2.5 points; leather is 7 for the set, iron 15, diamond 20 with
  toughness 8. The kit carries the points.
- **The shield.** Not reckoned. It stops arrows, blows and a creeper's blast from the front, slows the soldier to a
  fifth, and an axe knocks it down for 5 s.
- **Terrain.** Read: no walk between (`noWalkTo`, `pinned`), overhead, an archer more than 4 below, the soldier in
  water (against an archer only), boxed in, cover within 6. Not read: the hostile in water, a tunnel's width.
- **Light.** Not read, and not proposed: the undead burn by day, which is the prevention side
  (backlog/engagement-doctrine.md), not the soldier's choice in contact.

Hostiles, Java Edition, Normal (minecraft.wiki, each hostile's page; armour and explosion from its Armor and Explosion
pages). Pace and blow rate aren't on most pages - the wiki's speed attribute doesn't convert to blocks a second - so
each profile's pace is measured on the pinned server, as the weapons' were (`tools/weapon-stats-probe`).

| Hostile | Health | Damage | How it fights | Reckoned today as |
|---|---|---|---|---|
| zombie, zombie_villager | 20, armour 2 | 3 | melee walker, follows 35 | zombie |
| husk | 20, armour 2 | 3, hunger | melee walker, not burned by day | zombie |
| drowned | 20, armour 2 | 3; trident 8 from 20 off, 1 in 16 carries one | melee swimmer, some throw | zombie |
| spider | 16 | 2 | melee, leaps, climbs walls | zombie |
| cave_spider | 12 | 2, poison 6 more | melee, fits a half-block gap | zombie |
| silverfish, endermite | 8 | 1; 2 | small melee | zombie |
| slime | 16 / 4 / 1 | 4 / 2 / 0 | hops, splits | zombie |
| magma_cube | 16 / 4 / 1, armour 12 / 6 / 3 | 6 / 4 / 3 | hops, splits | zombie |
| phantom | 20 | 2 | swoops from 24-35 up every 8-12 s | zombie; let be overhead |
| vex | 14 | 9 | flies through blocks | zombie; let be overhead |
| vindicator | 24 | 13 | melee walker; its axe downs a shield | zombie |
| piglin_brute | 50 | 13 | melee walker; its axe downs a shield | zombie |
| wither_skeleton | 20 | 8, wither | melee walker | zombie |
| hoglin, zoglin | 40 | 3-8 | charges, throws its target up | zombie |
| ravager | 100 | 12; roar 6 | charges, knocks back 5 | zombie |
| warden | 500 | 30; sonic boom 10 through blocks and armour | melee and ranged | zombie |
| enderman | 40 | 7 | teleports; neutral till looked at or hit; arrows miss it | zombie, in self-defense only |
| creeper | 20 | blast to 43 at its feet, 17 at 3 off, 10 at 4, 5 at 5, none at 6 (computed from the Explosion page's formula) | lights within 3, 1.5 s fuse, goes out past 7; a shield faced to it stops the blast | creeper |
| skeleton | 20 | arrow 3-5 | shoots from 15, backs off inside 4, follows 16 | skeleton |
| stray | 20 | arrow 3-5, slowness 30 s | as a skeleton | skeleton |
| bogged | 16 | arrow 3-5, poison 3 more | as a skeleton, a shot 1.5 s slower | skeleton |
| pillager | 24 | crossbow 3-5 | shoots, follows 32 | skeleton |
| witch | 26 | potions to 6, poison to 18, through armour and shield | throws from 10 about every 3 s; heals itself | zombie |
| blaze | 20 | fireball 5 and fire, three at a time; melee 6 | flies, shoots from 48 | zombie |
| ghast | 10 | fireball 6, blast to 17 | flies, shoots from 64 | zombie |
| guardian | 30 | laser 6, part through armour | swims, laser from 15, not dodged | zombie |
| elder_guardian | 80 | laser 8 | swims, laser from 14 | zombie |
| shulker | 30, armour 20 shut | bullet 4, levitation | fixed, homing shots from 16 | zombie |
| breeze | 30 | wind charge 1, knockback | leaps, shoots from 16, turns arrows | zombie |
| evoker | 24 | fangs 6 through armour; calls vexes | casts from 16, flees inside 10 | zombie |
| ender_dragon, wither | 200; 300 | to 10; skull 8 and wither | bosses | zombie |

The skeleton's shot is every 2 s in `Engagement.ARCHER_SHOTS_A_SECOND`; the wiki's page reads every 3 s on Normal. To
be checked on the pinned server before the archer's profile is written.

## Proposed answers, by row group of `ContactEvalTest`
SAME: today's answer stands. CHANGE: the numbered change under "The changes" flips it.

| Row group | Today | Proposed | |
|---|---|---|---|
| Armed, whole, one melee walker (zombie, husk, spider, drowned) | fights | Engage | SAME |
| Armed, hurt, a melee walker, the exchange lost, a sprint gets clear | gets clear | Break contact, heal, come back | SAME |
| The same, no sprint, boxed in or it's on the soldier | fights | Engage: a fight forced | SAME |
| Bare hands, whole, one zombie | fights bare-handed | Break contact while a sprint opens the gap; fought only when forced. Fists take 20 blows to its 7 | CHANGE 4 |
| Any soldier, three zombies | as for one | Time to die takes all three: an unarmoured stone sword breaks contact from three it would fight singly | CHANGE 2 |
| Armed, a ravager, vindicator, piglin brute, wither skeleton, hoglin, warden at hand | fights | By its profile. A warden: break contact, always. A ravager or a brute against one unarmoured soldier: break contact; fought by a squad that wins the summed exchange | CHANGE 3 |
| Armed, a witch, blaze, ghast, guardian, shulker, breeze, evoker, a drowned with a trident; a walk to it | fights, closing as on a zombie | A shooter: closed on when the exchange by its profile is won, covered from when lost | CHANGE 1 |
| The same, no walk to it | carries on: no way between them | Cover, as from an archer with no route; a bow shoots it | CHANGE 1 |
| A melee hostile out of reach, no walk between | carries on | Bypass | SAME |
| The same, a bow in hand | carries on, not shot | Bypass: it can't interfere, and arrows are for what can | SAME |
| A phantom or vex overhead | carries on | Bypass till it comes down | SAME |
| A phantom overhead, a bow in hand | carries on, not shot | Shot inside the bow's range: it will come down on someone | CHANGE 10 |
| A creeper within 4.5, any kit - no armour and no sword too | gets clear, at a sprint, to 7.5 | Break contact past where its fuse goes out | SAME |
| A creeper within 4.5, the soldier boxed in or afloat, a shield carried | strikes it | Shield up, facing it: the blast is stopped | CHANGE 7 |
| A creeper 4.5 to 7.5 off, never come near | carries on, route kept round it | Bypass - unless the blast from there, through its armour, takes the soldier's health: then got clear of to 7.5 | CHANGE 9 |
| A creeper that has held the soldier ten seconds, armed | closes on it | Engage: it blocks the path | SAME |
| The same, bare hands | closes on it | Never: five seconds of fists inside its blast. Another way round, or dug | CHANGE 4 |
| A creeper outside its blast, a bow | shoots | Engage from standoff | SAME |
| A creeper with no walk to the soldier | carries on | Bypass | SAME |
| An enderman not hit by | no contact | Bypass, eyes down | SAME |
| An enderman that hit the soldier, bare hands | backs off | Break contact; forced, since it teleports - toward water or a squadmate | SAME |
| The same, armed | fights, reckoned a zombie | Engage as forced, by its profile (40 health, 7 a blow): hurt rows move | CHANGE 3 |
| The same, a bow alone | shoots | Backs off: arrows miss it | CHANGE 10 |
| An archer, no melee weapon | takes cover, flees past 20 | Break contact | SAME |
| An archer within 4 and a walk to it or in reach, the soldier boxed in (hurt where it stood a second, no step taken) | fights, armed or bare-handed (`Contact.NEAR_AMBUSH`) | Assault through: the near ambush | BUILT 2026-11-25 |
| An archer more than 4 below, the soldier boxed in | takes cover where it stands, at any health: `NEAR_AMBUSH`'s assault can't hold, since an archer more than 4 below is more than 4 away | Backs off the lip, away from it, till out of its sight (operator, 2026-11-28) | CHANGE 12 |
| An archer no walk reaches, no hurt from it nor arrow seen in 30 s | no contact, at any range (`Contact.QUIET_SECONDS`) | Ineffective fire: carry on | BUILT 2026-11-25 |
| An archer, armed, the exchange won | fights, closing at a sprint | Engage | SAME |
| An archer, armed, the exchange lost, not begun | takes cover | Break contact | SAME |
| An archer, armed, a fight begun | finishes it while it outlasts the kill; else gets clear if it can | Engage, then break contact | SAME |
| An archer, a shield carried, the exchange lost by its arrows | takes cover | The shield takes the arrows from the front on the way in: reckoned, it closes | CHANGE 7 |
| An archer, no walk to it or 5 below, in contact or shooting | takes cover | Break contact: it can, the soldier can't | SAME |
| The same, out of contact, not shooting, health 10 or more | carries on | Bypass | SAME |
| The same, health under 10 | takes cover | Break contact. The line is `HURT_HEALTH`'s till the exchange says it (backlog/engagement-doctrine.md) | SAME |
| An archer, a bow, beyond 5 or no walk to it | shoots | Engage from standoff | SAME |
| A pillager, bogged or stray | as a skeleton | By its own profile: a pillager's 24 health and 32 follow, a bogged's slower shot, a stray's slowness on the way out | CHANGE 3 |
| A soldier in water, a melee hostile, the exchange lost | gets clear, reckoned at a sprint | A swimmer outpaces nothing: fought, as against an archer already | CHANGE 5 |
| Armour: one to four pieces | each piece 2.5 points | Points by material: leather and iron rows part | CHANGE 6 |
| Kit not yet reported | armed for the rules, a fist's blows in the exchange | One reckoning: bare-handed till the kit is known | CHANGE 11 |
| A soldier breaking contact, squadmates near | straight away, or to cover | Falls back on the nearest armed squadmate within supporting distance; its squad leader is told | CHANGE 8 |

## The changes
Each its own commit, on `bot.Contact` and `bot.Engagement`, with the rows it flips; a new column or kind adds its rows
with today's answer first (docs/conventions.md, "A contact met live is a row first"). Ordered by the live deaths each
answers where one is recorded, then by reach.

1. **Hostiles that shoot are archers.** The drowned with a trident, witch, blaze, ghast, guardian, shulker, breeze and
   evoker join `HostileMobRegistry.shoots`, each with its range. Flips: "a witch 6 off" rows, and the
   "no walk between" row of each in `everyHostile` from carries-on to cover. Live: field604, Mia stood 10.8 from a
   drowned that killed her with its trident, a thrower the code takes for no archer (`RecordedContactTest.miaGetsOutOfTheDrownedsReach`).
2. **Numbers on both sides.** `Contact.Situation` already carries every threat; time to die sums those that can reach
   the soldier, and time to kill counts squadmates within a blow of the same hostile (a new fact from the shell).
   New rows: one soldier against two and three zombies, by kit; a zombie with an archer behind. Live: field663 and
   field666, Eli between a zombie and a skeleton, each answered alone.
3. **A profile per hostile.** `Engagement.melee` and `archer` take the hostile's own health, armour, blow, rate, reach
   and pace, measured on the pinned server. Flips: the ravager, vindicator, piglin brute, warden and wither skeleton
   rows of `everyHostile` "at hand"; the armed enderman's hurt rows; the pillager rows. Takes the pillager item of
   backlog/engagement-doctrine.md.
4. **Bare hands don't seek a fight.** With no melee weapon a hostile that fights hand to hand is broken from while
   the soldier can open the gap, and a creeper is never closed on. Flips: "bare hands, whole, a zombie" rows, the
   melee hostiles' "bare hands, 6 off" rows of `everyHostile`, "bare hands, a creeper that has held it ten seconds".
5. **A swimmer's pace against a melee hostile.** `Contact.pace` takes `wet` for both exchanges. Flips: "stone sword,
   at 6, a drowned 6 off, the soldier in water".
6. **Armour in points.** `Kit` carries armour points and toughness from the pieces worn; `Engagement.ARMOUR_A_PIECE`
   goes. The armour column becomes a material and a count; rows at one and two pieces of leather move toward
   breaking contact, iron toward engaging.
7. **The shield in the exchange.** Arrows and blows from the front are stopped while it's up, at a fifth of the
   pace; against a creeper with no way clear it's raised and faced. New rows: sword and shield against an archer,
   hurt; a creeper, boxed in, with and without one.
8. **Mutual support.** A soldier breaking contact heads for the nearest armed squadmate within supporting distance
   before cover or straight away, and its break is reported to its squad leader with what it lacked
   (backlog/engagement-doctrine.md, reports up the chain; `command.ReactToContact` sends the support). New column:
   squadmates near.
9. **A creeper's berth by health.** Outside 4.5 it's gone past only where its blast from there, through the armour,
   leaves the soldier standing. Flips: a hurt, unarmoured soldier's "a creeper 6 off" row, to be added.
10. **The bow where it serves.** Not at an enderman; at a phantom overhead within range. Flips: "a bow alone, an
    enderman at hand: shot", "a phantom overhead, a bow in hand".
11. **An unknown kit is one thing.** Bare-handed in the rules as in the exchange. Flips: the three "kit not reported"
    rows.
12. **Cover that holds nothing, and the edge at 4 below.** `Contact.withoutTheBow` reads an archer as below on a strict
    `y < at.y - ARCHER_BELOW` with no hysteresis, so a soldier bobbing a block on a lip flips between "takes cover" and
    "fights" every few ticks (the "a skeleton 4 below" row pins the edge: at exactly 4 it fights, though the response
    says "4.0 or more below"). And once boxed in (hurt where it stood, no step taken) nothing changes the answer: the
    near ambush's assault needs a range (3D) within `NEAR_AMBUSH` (4), which no archer more than 4 below can be, so the
    soldier stands in cover that isn't one until it dies. Live: field750, 2026-11-27 19:25:58-19:26:28Z, Fran (stone
    sword, armour 4) on a cliff lip, three pillagers below; held at (47.31, 63.02, 69.76) for 30 s, the nearest 5.7 off
    and 4.0 below, 20 health to dead (`death.attack.arrow`); her ground kept as
    `garrison-state/worlds/Fran-1795807588233.json`. Rows: "a pillager 5.4 off, 3.8 below" (a bob, fights), "a
    pillager 5.7 off, 4.02 below" and "the same, hurt where it stood, boxed in" (cover), pinned with today's answers.
    Why she didn't move: the server refused every step - "Fran moved wrongly!" 297 times, 10 a second, and put her back
    at the same mid-hop point each time (backlog/correction-loop.md); her floor (y 61) is solid in the model's kept
    ground, and the server's blocks there were not probed. The rule (operator, 2026-11-28: "Back off the lip"): drawn
    as `Contact.OFF_THE_LIP` - armed, boxed in, an archer more than 4 below: straight away from it, at a sprint, till
    out of its sight; a step the server refuses is handled as backlog/correction-loop.md says. Boxed in is measured
    over a second against a mark, a correction tick counting as no ground gained, so a step the server puts back reads
    as none - today the stall is compared inside one tick, and a correction tick never reaches it. The operator's to
    decide: what the soldier does once all its tries are refused (hold and alert, dig in, or take the drop), and
    whether the edge at 4 below gets hysteresis.

## Done when
- Every row group above is SAME, each change's rows flipped in its own commit.
- `ContactEvalTest` has columns for hostiles near, squadmates near and armour material, and no hostile is "reckoned a
  zombie" in a row's name.
- The combat matrices pass no fewer cells than before each change.

## Notes
- **Decided:** the table pins what the code answers, right or not; this file is where an answer is argued.
- Live casualty counts per change weren't taken from the logs; the order above uses the deaths the code and docs
  already name.
