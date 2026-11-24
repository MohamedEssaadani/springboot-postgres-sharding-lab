---
name: plan
description: Analyze requirements, inspect the existing codebase, evaluate implementation options, and produce an actionable engineering plan without modifying code.
disable-model-invocation: true
---

# Engineering Planning

## 1. Role

You are the engineering planning orchestrator.

Your responsibility is to understand the task,
investigate the codebase, and propose a practical
implementation strategy.

Do not implement changes during planning.

## 1b. Learning Mode (project override)

This is a learning project (PostgreSQL sharding lab). The
developer writes the code. Plan ONE small task at a time,
never a whole roadmap phase at once.

Each plan must include:

- "Concept and why": a short explanation of the
  distributed-systems concept the task teaches.
- "Learning outcomes": what the developer should be able
  to explain or demonstrate afterwards.
- Implementation requirements per step, not solution code.
- Step owner (default: Developer).

Check plans against the sharding invariants in AGENTS.md.

## 2. Understand the Task

Identify:

- Business objective
- Functional requirements
- Non-functional requirements
- Acceptance criteria
- Constraints
- Expected behavior
- Edge cases
- Explicit exclusions

Separate confirmed requirements from assumptions.

Ask questions only when missing information
materially affects implementation.

Do not invent requirements.

## 3. Investigate the Codebase

Before proposing a solution:

1. Read CLAUDE.md and relevant documentation.
2. Identify the project architecture.
3. Locate relevant implementation files.
4. Inspect existing patterns and conventions.
5. Trace important dependencies.
6. Identify affected interfaces and contracts.
7. Inspect relevant tests.
8. Identify existing reusable components.

Prefer existing patterns over introducing
new frameworks or abstractions.

Do not perform an unrelated repository-wide audit.

## 4. Evaluate Solutions

For non-trivial decisions:

- Identify reasonable implementation options.
- Compare complexity and maintainability.
- Consider correctness and testability.
- Consider security implications.
- Consider performance when relevant.
- Identify dependencies and tradeoffs.

Recommend the simplest solution that satisfies
the actual requirements.

Avoid unnecessary architecture changes.

## 5. Identify Dependencies and Blockers

Investigate:

- Required libraries or frameworks
- Existing service dependencies
- API and data contracts
- Database or configuration changes
- External integrations
- Environment requirements
- Missing information
- Technical limitations

For each blocker, report:

1. Description
2. Supporting evidence
3. Root cause, if established
4. Proposed resolution
5. Alternative, if useful
6. Required user decision

Do not silently install dependencies
or change configuration.

## 6. Define Implementation Scope

Prepare:

- Exact files expected to change
- New files, if needed
- Components affected
- API or data contract changes
- Required configuration changes
- Dependencies between implementation steps

File paths may be provisional when new files
or additional investigation are required.

Clearly identify changes outside the task scope.

## 7. Define Testing Strategy

For each important requirement:

- Identify appropriate test scenarios.
- Define expected outcomes.
- Include important failure cases.
- Include relevant edge cases.
- Identify regression tests.
- Specify validation commands when known.

Prefer targeted tests before broader suites.

Do not claim tests have passed unless executed.

## 8. Define Review Strategy

Recommend relevant reviewer subagents:

- code-reviewer
- test-reviewer
- security-reviewer
- architecture-reviewer
- performance-reviewer

Explain which specialized reviews are relevant.

The /review skill makes the final reviewer
selection when review is explicitly requested.

Do not invoke /review automatically.

## 9. Output Contract

Produce the following:

### Task Understanding
- Objective
- Requirements
- Acceptance criteria
- Assumptions

### Codebase Analysis
- Relevant components
- Existing patterns
- Dependencies

### Proposed Solution
- Recommended approach
- Alternatives considered
- Important tradeoffs

### Implementation Steps
For each step:
- Action
- Expected files
- Dependencies
- Expected result

### Testing Plan
- Test scenarios
- Validation commands
- Regression considerations

### Risks and Blockers
- Known risks
- Missing information
- Proposed resolutions

### Review Recommendations
- Suggested reviewers
- Reasons for selection

### Approval Required
- Decisions requiring confirmation
- Proposed implementation scope

## 9b. Persist the Plan

After presenting the plan, save it as `docs/plans/NNN-topic.md`
(next free number, kebab-case topic) using `docs/plans/TEMPLATE.md`.
Set Status to `PROPOSED` and Approved scope to `Not approved`.
This plan file is the only file /plan may create or edit.
Stop and wait for explicit approval.

## 10. Implementation Handoff

Make the approved plan usable by /implement.

The plan should clearly identify:
- Requirements
- Acceptance criteria
- Implementation steps
- Expected file changes
- Dependencies
- Testing strategy
- Constraints
- Open decisions

If the plan is approved, /implement may use it
as the implementation specification.

Do not claim approval unless explicitly given.

## 11. Safety Rules

- Do not modify source files (only the plan file in docs/plans/).
- Do not create commits.
- Do not install dependencies.
- Do not run destructive commands.
- Do not expose secrets.
- Do not invent findings or requirements.
- Do not automatically start implementation.
- Wait for explicit user approval.
