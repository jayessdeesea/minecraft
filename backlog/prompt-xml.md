---
status: ready
priority: P1
---

# The models' prompts written with an XML writer

## Why
The general's and squad leaders' user prompts are XML-tagged sections - `<mission>`, `<time>`,
`<squad name="...">`, `<terrain>`, `<foothold>` - built by hand with `StringBuilder`
(`SpringAiGeneral.buildContext`, `SpringAiSquadLeader.buildContext`). Nothing is escaped: an
operator's mission or remark with a `<` or `&` in it would close a section early or open a
bogus one, and the model would read a different situation than the one sent. AGENTS.md §
Standard formats: standard formats go through standard libraries.

## Done when
- Every prompt section is written with `javax.xml.stream.XMLStreamWriter`: element names and
  attributes by the writer, text content escaped by it.
- A test sends a mission and a remark holding `<`, `>` and `&` and finds them escaped, the
  sections intact.
- The general's and squad leaders' evals pass as before (`GeneralScenarioIT`,
  `SquadLeaderScenarioIT`): the prompt's text is the same, only safe.

## Notes
- The prompt is a sequence of top-level sections, not one document: write each section as a
  fragment (or wrap them in a root the system prompt doesn't mention, and strip it), whichever
  keeps the text the model sees unchanged.
- The system prompts (`prompts/*.md`) are static text we write, not generated: they stay as files.
