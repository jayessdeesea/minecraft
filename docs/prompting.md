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
