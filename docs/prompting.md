# Writing the echelons' prompts

The general's and the squad leader's prompts, and what's been measured about writing them. Their
place in each decision (`buildContext` -> `callModel` -> validate) is in
[architecture.md](architecture.md#behavior-modeling-functional-core-imperative-shell).

## Where prompts live
The role, doctrine and coordinate conventions are each echelon's fixed system prompt, never rebuilt
per call. Both prompts are files that configuration names (`minecraft.<echelon>.system-prompt`,
`.user-prompt`), with no defaults in code or in the `ChatClient`: each call passes its system
prompt and fills its user template's `«name»` parameters with `.params(...)` (`PromptTemplate`).
A prompt's wording lives in its file; Java works out the facts that go into it.

## What goes into them
- **Precompute lookups** (which nearby ground is reachable, `<objective_ground>`) rather than hand
  the model a grid to decode. Measured: no local model that fits on a 16 GB GPU read reachability
  off the terrain table, and with the lookup done for it `qwen3:14b` chose correctly.
- **When an echelon decides wrongly, read its prompt before blaming the model.** Every bad
  hold-or-leave call the local general made traced to a line that misled it (a built base reported
  as a shortage, a soldier's failed sword reported as the squad's CANTCO), and with those fixed it
  passed every `GeneralScenarioIT` situation. Turn each live miss into a situation there first.
- **A coordinate written out is a coordinate the model copies.** Give positions it shouldn't reuse
  (an old order's, a resource's) relative to the squad, not as a y.
- **Worked examples are copied the same way**, their coordinates and their choices: one example of
  a sealed squad sent to SECURE pulled every sealed squad out.
- **Put a rule in the paragraph of the task it sets**, not in a general section after it.
- **Show the state, don't add a rule about it.** Shown a stranded soldier's CANTCO, the squad
  leader reported the mission CANTCO (6 of 14). A rule that he's the recovery drill's made it
  worse (3 of 14): the model wrote the rule's own words into its CANTCO. Shown him as
  `Recovering` instead, it got 14 of 14.
- **A section that explains itself needs no paragraph about it:** a two-line note on `<foothold>`
  in the doctrine took the night rule from 6 of 6 to 0 of 6.
- **Some calls the squad leader won't make, however they're shown.** With every soldier in a role
  and the mission left with nobody (`SquadLeaderScenarioIT`'s `alldetached`), `qwen3:14b` put none
  back with the squad: 0 of 7 shown the state ("every soldier has a role of its own"), and 0 of 7
  with the role paragraph saying to put one back - which also cost the miner situation 3 of 7. The
  situation stays in the evaluation as the open miss. The general won't make the same call one
  echelon up: in `GeneralScenarioIT`'s `wall-open-alpha-detached`, with the wall open and every
  soldier of Alpha in a role, it gave the wall to Bravo 3 of 5 shown nothing, 3 of 10 shown a line
  saying so, and 0 of 3 shown Alpha's CANTCO - it takes the wall off Alpha and gives Bravo none.
- **Food in the airhead costs the morning.** With food moved from the buildup into phase 2 beside
  the lit ground and the wall (the operator's food, shelter and weapons first), `morning-wall-open`
  - the wall open just after dawn, food met - fell from 6 of 10 to 5 of 20: the general sends the
  squads to a patrol base in the morning. Calling phase 2 "first needs" instead of "airhead" made
  no difference (4 of 10). The situation stays as the open miss.
- **The squad leader's miner situation moves with any edit to its paragraphs of standing kit.**
  Correcting the rearm paragraph to the basic load as built (soldiers make stone tools and copper
  or iron armour unasked) took `miner` from 5 of 7 to 1 of 7 at one seed; reverted. The prompt
  still says the basic load ends at a stone sword: the soldiers make more, whatever it says.
