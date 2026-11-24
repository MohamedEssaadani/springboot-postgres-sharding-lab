# Engineering Workflow

## Goal
A repeatable, auditable lifecycle: **Understand → Plan → Approve → Implement → Validate → Review → Fix → Re-review → Accept → Commit**. It is a process, not an automatic pipeline. Each stage stops at its authorized boundary.

## Learning mode (this project)
This is a learning project: the **developer writes the application code**; the assistant is a mentor. For each small task the assistant explains the concept and why it is needed, gives implementation requirements and acceptance checks, then waits. It reviews the developer's code, explains mistakes, and helps debug. It generates code only when the developer explicitly asks, and only for the named piece. Work one small task at a time; never implement a whole roadmap phase without approval. In the stages below, "Implement" means the developer implements unless stated otherwise.

## Understand
Read the task, repository instructions, relevant source, tests, and existing Git state. Clarify missing requirements when they materially affect correctness. Identify submission constraints and whether AI assistance is allowed.

## Plan
Use `/plan` to produce `docs/plans/NNN-short-name.md`. Record objective, scope, acceptance criteria, ordered small steps, testing strategy, risks, and open decisions. Set `PROPOSED`; do not edit application code. Wait for approval.

## Approve
The developer approves the plan or named step. Approval for Step N is not approval for Step N+1. Explicitly identify changes to interfaces, dependencies, data, or architecture.

## Implement
Use `/implement` for approved scope only. In learning mode it produces requirements and guidance, not code, unless the developer explicitly requests generated code. Set `IN_PROGRESS`; update the step checklist, notes, decisions, and validation records. Favor small changes and tests. Never automatically invoke review.

## Validate
Record exact checks and outcomes: `PASSED`, `FAILED`, `BLOCKED`, `NOT_VERIFIED`, `NOT_APPLICABLE`. A test written is not a test executed. Runtime claims require runtime evidence.

## Review
Use `/review` on a named step or diff. Select relevant reviewers rather than all five by default. Verify real reviewer execution, deduplicate findings, distinguish severity and confidence, and append a timestamped review round. Reviewer subagents do not edit files; main reviewer may append review history to the plan only.

## Remediate
Use `/fix-review` only after the developer authorizes findings to address. Preserve prior review rounds. Implement focused fixes, run validation, then stop. Invoke `/review` separately for re-review.

## Accept and commit
Developer accepts or explicitly documents permissible residual risks. Inspect diff, exclude secrets and unrelated changes, then commit only if authorized. Push, merge, release, and deploy require separate authorization.

## Blockers and recovery
Report missing dependencies, inaccessible services, incomplete agent runs, or ambiguous requirements. Do not claim success from partial evidence. On interruption, inspect the current Git state and plan before resuming. Never reset user work to simplify the task.

## Anti-patterns
Do not one-shot a large implementation without understanding requirements; do not silently extend scope; do not launch irrelevant reviewers; do not invent passing tests; do not overwrite review history; do not add process files to a submission without checking its rules.

## Source of truth
Operational behavior is in `.claude/skills/*/SKILL.md`; project constraints in `CLAUDE.md`; reviewer instructions in `.claude/agents/*.md`; current feature state in `docs/plans/*.md`. This document explains the lifecycle.
