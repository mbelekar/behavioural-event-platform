# Development Workflow

## Planning before implementation

For any non-trivial task (new feature, multi-file change, refactor):

1. Do not write or edit code immediately. First explore the relevant parts of
   the codebase to understand current behavior and constraints.
2. Ask clarifying questions if requirements are ambiguous - one at a time,
   not as a giant list - before proposing a design.
3. For changes that affect system behaviour, create or update the relevant
   feature spec as described in "Specs, decisions, and plans".
4. Record any significant architectural decisions as ADRs where required.
5. Produce a written implementation plan that includes:
   - A short summary of the change, referencing the relevant spec and ADRs
   - A numbered list of concrete tasks, each small enough to be described
     completely on its own, with a clear definition of done
   - Files/modules each task touches
   - Implementation-specific risks, edge cases, or assumptions
6. Save the plan to `docs/plans/<short-name>.md` and show it to me.
7. Stop after presenting the plan. Do not implement anything until I
   explicitly approve it (e.g. "approved", "go ahead", "implement this").

For trivial changes (single-line fixes, typo corrections, config tweaks),
skip planning and just make the change - but say so explicitly
("this is small enough to skip the plan step").


## Specs, decisions, and plans

The documentation has distinct purposes:

- `docs/specs/` describes WHAT the system must do.
- `docs/decisions/` describes WHY significant architectural decisions were made.
- `docs/plans/` describes HOW an approved change will be implemented.
- `docs/Design.md` describes the overall system architecture.

Do not duplicate the same information across these artifacts.


### Feature specs

For non-trivial features or changes in externally meaningful behaviour,
maintain a lightweight feature spec under `docs/specs/`.

During planning, after requirements have been clarified and before producing
the implementation plan:

1. Identify whether the change creates or modifies externally meaningful
   system behaviour.
2. If so, create or update the relevant spec under `docs/specs/`.
3. Keep specs implementation-independent where practical.

A feature spec should contain:

- Purpose
- Requirements
- Acceptance criteria
- Important failure or edge-case behaviour

Do not include:

- Implementation tasks
- File or class structure
- Implementation sequencing
- Detailed architectural rationale

Small refactors, internal implementation changes, dependency updates, and
bug fixes that restore already-specified behaviour do not require a new spec.


### Architectural decisions

During design and planning, identify decisions that materially affect:

- System architecture or component boundaries
- Data contracts
- Reliability or delivery semantics
- Security or privacy
- External integrations
- Significant technology choices
- Constraints on future architectural choices

Record these as ADRs under `docs/decisions/`.

Do not create ADRs for routine implementation details, class structure,
test organisation, minor library choices, or implementation sequencing.

ADRs explain WHY a decision was made, including meaningful alternatives and
trade-offs. Implementation details belong in the plan.


### Plans

Once the relevant spec and ADRs are up to date, produce the implementation
plan under `docs/plans/` as described in "Planning before implementation".

The plan should reference the relevant spec and ADRs rather than duplicating
their content.


### After implementation

Before declaring the work complete:

1. Verify the implementation and tests satisfy the relevant spec.
2. If implementation revealed that an agreed requirement or architectural
   decision needs to change, update the spec or ADR explicitly.
3. Do not silently change a spec to match accidental implementation behaviour.
4. Report any discrepancy between the implementation, spec, and ADRs.


## Simplicity

Prefer the smallest implementation that completely solves the approved problem.

- Do not add features, abstractions, configuration, or extension points that
  are not required by the current task.
- Do not add defensive handling for scenarios that cannot occur under the
  system's documented invariants.
- If a simpler approach exists, explain it before proposing a more elaborate one.
- Push back when a request would introduce unnecessary complexity or conflict
  with an existing architectural decision.
- State assumptions explicitly. If several reasonable interpretations exist,
  present them before choosing one. Do not silently resolve ambiguity.
- Before finishing, check whether the implementation can be made materially
  smaller without weakening correctness, readability, or testability.


## Keeping changes surgical

Every changed line should be directly connected to the approved task.

- Do not refactor, reformat, rename, or clean up adjacent code unless the
  approved plan requires it.
- Match the style and patterns already used in the affected module.
- If the change makes an import, variable, function, test, or file obsolete,
  remove that newly orphaned code.
- Do not remove pre-existing dead code or fix unrelated issues. Record them
  separately for possible follow-up.
- Keep unrelated changes out of the same commit.


## Executing an approved plan

- Work through the plan's tasks in order.
- After each task, briefly report what changed and run relevant tests or
  checks before moving to the next task.
- If you discover the plan was wrong or incomplete once you're implementing,
  stop and tell me rather than silently improvising a different approach.
- Don't expand scope beyond what the plan describes. If you notice something
  else worth fixing, note it at the end instead of doing it unprompted.


## Debugging

When investigating a bug, follow this order and do not skip ahead:

1. **Reproduce it first.** Confirm you can trigger the actual failure before
   changing anything.
2. **Find the root cause**, not the first symptom. Trace the failure back
   through the code path to where it actually originates.
3. **Check for the same pattern elsewhere.** If this root cause could affect
   other similar code paths, check them before fixing just the one you found.
4. **State your hypothesis explicitly** before writing a fix - what you
   believe is wrong and why - and verify it (e.g. with a log statement,
   a minimal repro, or a targeted test) before changing code.
5. **Only then implement the fix**, and re-run the original repro to confirm
   it's actually resolved.

Do not guess-and-check by making speculative changes and re-running to see
if they help. If you're not sure of the root cause after step 2, say so and
explain what you'd need to check next, rather than trying a fix anyway.


## Before declaring anything done

Run the test suite (or the relevant subset) and/or the specific
reproduction steps for a bug fix. Do not say a task is complete based on
the code "looking correct" - show the verification you actually ran.