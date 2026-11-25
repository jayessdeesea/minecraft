---
status: ready
priority: P1
---

# A squad's day is chunks: check it, work it, say done, take the next

## Why
The operator (2026-11-24): the day starts, the platoon leader assigns each squad something - normally to attend a chunk
that is doing something, a sheep farm say. Its first step is the anti-entropy; then whatever harvesting it needs - for
wheat, collect and replant - divided among the squad; then it says it is done, so the platoon leader can assign
something else.

Today a built farm is "kept": a standing objective with no end, its squad on it all day whether there is work or not;
the anti-entropy is a review on a clock of its own (`FarmMissions.review`), which opens a template's step again and
takes whichever squad is free; and a squad with nothing to do stands at its point - live, four soldiers idle on a
paving held by one cell, three farm keepers with nothing ripe.

## Done when
- **A tend is a task with an end**: "tend the wheat farm at chunk (1, 3)" - one built chunk, one squad, three phases the
  squad works in order and reports: CHECK (its template against its layout: `TemplateDrift`, `Fixtures.left`, its
  footings, its sky, its clutter - and put right what is found), WORK (what that kind yields: wheat cut and replanted,
  logs felled and saplings set, animals fed, bred and culled, a supply point's loads put in their rows, a mineshaft's
  shaft a shift), CLOSE (its yield in its chest or the stores, counted; its gates shut). Each phase has a measure, as a
  mission's steps have; the tend is DONE when the last is, and says what it yielded and what it repaired.
- **Divided among the squad**: each phase's cells dealt in strips, a soldier a strip, as a floor's and a wood party's
  are - nobody waits on another's cell. Dealt among the squad's soldiers the recovery drill hasn't
  (`SquadState.hands`, as a floor, a template's ground and the keep's hands are; docs/military-doctrine.md, "A soldier
  in the recovery drill is dealt no work"): a tend's phases take the same crew. Not covered by that:
  - A soldier stuck but not in the recovery drill still holds its strip: a miner whose cut-off check says "not
    stranded" with 16,000 cells to walk, one walled in beside the chunk. That is the shared "can this soldier reach
    this work" check's to answer, not the dealing's.
  - The camp's ground work (`SiteGeometry.shareGround`) and a wood party's strips (`DutyBook.wood`) deal to every hand
    of the squad, the drill's too.
  - Eval, not built (a squad simulation's floor runs minutes): four pavers, one joining walled in a pit 40 blocks from
    the chunk with no tool to dig out - the floor is DONE all the same, the one in the pit never sent to the stockpile
    for the floor's cobblestone, and, let out, dealt a strip of the next step.
- **Done is said, and the next is given**: the squad leader's DONE goes up; the platoon leader's rule
  (`command.DrillPlatoonLeader`) gives that squad the next task from one list - a mission's open step, or a chunk to
  tend - by what is most due: a step that food or wood waits on, a chunk not checked for longest, a crop that is ripe
  by its own clock. No squad holds a chunk with nothing to do on it; none stands idle while a task is open.
- **The review's clock is the same list**: a chunk tended today has had its check; one no squad has been near in four
  days is given as a tend when a squad is free and near - the jitter is which day, not a second mechanism.
- **A squad works till it is told differently** (operator, 2026-11-24): dusk ends nothing by itself. At dusk the
  platoon leader says what each squad does - carry on, or another task - and the squad does that; a task taken off a
  squad is handed back with what is left of it.
- Evals: four squads, six built chunks and one open mission - every chunk checked inside a day, none twice while
  another waits; a squad whose tend finishes at noon is on another inside a minute; a wheat farm with a post gone and
  wheat ripe: the post put back before the wheat is cut; a tend that cannot finish says why and is given to nobody
  else until its reason changes.
- Live: the status says for each squad its task, its phase and what is left; no "no task" by day with a task open.

## Notes
- This replaces `FarmMissions.atTheFarms` (a squad "keeps" a farm) and folds `FarmMissions.review` into the list.
- The loops the soldiers report (`LoopReports`) and a tend that makes no progress are the same question, asked of the
  squad: a task whose measure hasn't moved in a quarter hour is said, with what it waits on.

## A chunk's work is a state machine the platoon leader steps (operator, 2026-11-24)
The operator, of the wheat farm: go to the farm; the anti-entropy - which may mean taking a hoe from the chest or
making one, and the hoe back in the chest after; say done. Told to harvest; say done. Told to reseed; say done. Told
to clean up, and where the wheat goes: the chest left with enough seeds and not too many, the wheat put in the supply
dump named; say done. And of the mine the same day: a load drawn first, a cache at the floor, trips, a close on the
word to stop (backlog/mining.md).

So this replaces "three phases the squad works in order" above: **each step is its own order, and its end is said.**
The squad does not go on to the next by itself; the platoon leader gives it - or gives something else, which is the
point: between any two steps the squad is free to be sent where it is needed more.

**A step**, the same shape whatever the chunk:
- **What it needs** - its kit, by kind and count, from one table by step (a hoe to till; a hoe and seeds to reseed;
  shears; an axe and saplings; a mine's load). Taken from the chunk's own chest, made at the nearest workshop where it
  isn't there, and a shortfall said before the work, not at it (backlog/kit-readiness.md).
- **Its work**, divided among the squad in strips, to a measure.
- **Its close** - what it took put back where it came from (the hoe in the chest), and DONE said: a report's type,
  with the chunk, the step, what it did and what it yielded - never a sentence read back.

**A wheat farm's steps**, each ordered:
1. MOVE to the farm.
2. CHECK - the anti-entropy: its layout against the world (fence, gates, torches, water, soil, slab, sky, clutter, its
   chest and what is in it), put right. Needs: a hoe where soil is to be tilled again, cobblestone, a fixture's makings.
3. HARVEST - the ripe wheat cut. Yields wheat and seeds, counted.
4. RESEED - every tilled cell sown. Needs: seeds - the harvest's first, then the chest's.
5. CLOSE - told where the wheat goes: the farm's chest left with seeds between its least and most (enough for a
   reseeding of the whole farm and a margin; the rest go with the wheat), the tools back in it, the wheat and the
   surplus carried to the supply point named and put in its rows, the gates shut.
A tree farm: MOVE, CHECK, FELL, REPLANT, CLOSE. A pasture: MOVE, CHECK, FEED AND BREED, CULL OR SHEAR, CLOSE. A supply
point: MOVE, CHECK (its rows, each chest's facing and what is in it), SORT, CLOSE. A mine: MOVE, CHECK (head and stair),
DRAW, DESCEND AND CACHE, CUT (a floor's side), CLOSE.

**The platoon leader's side**: one register, a row a chunk - its kind, its step now, who has it, when each step was
last done and what it yielded. Its rule each time a DONE comes up, and each morning: the next step for that squad, by
what is most due - a CHECK not done in four days, a HARVEST by the crop's own clock, a CLOSE never left hanging (a
squad taken off before CLOSE is given it first when it is back, or it is given to another with what was left said).
A step that can't be done says why and is not given again until its reason changes.

**What must be true for this not to be slow**: a DONE answered within the second. Today a squad leader's rule waits
20 s between decisions and 8 s on a new report, and the platoon leader's queues behind model-call limits though no
model is called (backlog/one-home.md, B6): those waits go for rules, or a five-step tend loses a minute and a half to
waiting.

Evals: a wheat farm with a post gone, soil trodden to dirt and no hoe in its chest - a hoe made, the soil tilled, the
post up, the hoe in the chest, CHECK done; HARVEST and RESEED each said done with counts; CLOSE with 500 seeds - the
chest left with its most, the rest and the wheat in the supply point's food row. A squad sent elsewhere after HARVEST
- the farm's row says RESEED due, and the next squad free is given it. Five steps, each DONE answered inside a second.
