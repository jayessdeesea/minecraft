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
  are - nobody waits on another's cell.
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
