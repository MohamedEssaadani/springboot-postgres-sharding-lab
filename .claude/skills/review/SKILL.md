---
name: review
description: Orchestrate independent, evidence-based reviews of scoped code changes using specialized reviewer subagents.
disable-model-invocation: true
---

# Review Orchestration

## Role

You are the review orchestrator.

Your responsibility is to coordinate independent reviews,
consolidate findings, and present actionable recommendations.

Do not modify source files or automatically implement fixes.

## 0. Learning Mode (project override)

The code under review is normally written by the developer
as part of a learning project. In the final report, explain
each mistake and WHY it is a problem (concept, consequence),
not only the fix. Check the sharding invariants in AGENTS.md.

If the project is not a Git repository, review the files
the developer names instead of a diff, and say so.

## 1. Establish Review Scope

Before delegating:

1. Read CLAUDE.md and applicable project instructions.
2. Identify the task, requirements, and acceptance criteria.
3. Inspect Git status and the relevant diff.
4. Determine the correct comparison base.
5. Identify exact changed files and their paths.
6. Identify related files, interfaces, and dependencies.
7. Identify areas of uncertainty or missing context.

Never assume that the current Git diff represents the
entire task. Consider committed and uncommitted changes.

If the review scope is ambiguous, ask for clarification
before proceeding.

## 2. Select Reviewers

Always consider:
- code-reviewer
- test-reviewer

Conditionally select:
- security-reviewer: authentication, authorization,
  input validation, secrets, dependencies, external APIs,
  or sensitive data.
- architecture-reviewer: new components, changed
  boundaries, API contracts, dependencies, or major refactoring.
- performance-reviewer: database queries, concurrency,
  caching, large datasets, algorithms, or resource usage.

Use judgment to avoid unnecessary reviews.

Record which reviewers were selected and why.

## 3. Prepare Delegation Brief

For every selected reviewer, provide:

- Task description and acceptance criteria
- Exact files to review
- Relevant Git diff or commit range
- Related files and dependencies
- Specific review objectives
- Known constraints and assumptions
- Expected output format

Reviewers may inspect additional related files when
necessary but must explain why they expanded scope.

## 4. Execute Reviews

Delegate to the selected named subagents.

Run independent reviews in parallel when supported.

Ensure reviewers work independently and do not
modify source files.

If delegation is unavailable, explicitly report
that limitation. Do not claim a subagent executed
when it did not.

## 5. Reviewer Output Contract

Every reviewer must report:

### Findings
For each actionable issue:
- Unique finding ID
- Severity: Critical / High / Medium / Low
- Exact file path and line reference
- Problem description
- Supporting evidence
- Root cause
- Impact
- Recommended solution
- Alternative solutions, when relevant
- Dependencies or blockers
- Confidence: High / Medium / Low

### Investigation
- Files inspected
- Checks or commands executed
- Dependencies discovered
- Obstacles encountered
- Solutions investigated
- Unresolved questions
- Review limitations

A reviewer must explicitly state when no actionable
issues were found.

## 6. Consolidate Results

After receiving the reviews:

1. Confirm which agents actually executed.
2. Merge duplicate findings.
3. Preserve distinct findings.
4. Resolve conflicting recommendations where possible.
5. Prioritize confirmed defects over speculative risks.
6. Identify cross-cutting dependencies and blockers.
7. Separate required fixes from optional improvements.
8. Prepare an actionable remediation plan.

Do not present speculative findings as confirmed bugs.

## 7. Final Report

Return:

### Review Summary
- Scope reviewed
- Reviewers executed
- Overall assessment

### Findings
- Critical
- High
- Medium
- Low

### Dependencies and Blockers
- Discovered dependencies
- Obstacles
- Proposed resolutions

### Recommended Actions
- Fix immediately
- Fix before delivery
- Optional improvements

### Validation
- Checks executed and results
- Checks not executed and reasons

### Decisions Required
- Questions requiring user approval

## 8. Record in Plan

Append a new review round to the "Review results" section of the
plan in docs/plans/ (append-only; never edit earlier rounds). Record
reviewers requested vs. actually executed, assessment
(PASS / PASS_WITH_FINDINGS / CHANGES_REQUIRED / BLOCKED / NOT_VERIFIED),
findings, validation gaps, and follow-up. This is the only file
/review may edit.

## 9. Safety Rules

- Do not modify any file other than appending to the plan.
- Do not create commits.
- Do not execute destructive commands.
- Do not expose secrets.
- Do not invent evidence.
- Do not claim tests passed unless executed.
- Do not automatically apply recommended fixes.
- Wait for user approval before implementation.
