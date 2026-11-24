@AGENTS.md

# Claude Code specifics

The shared, tool-agnostic rules live in `AGENTS.md` (imported above). This file only adds what is specific to Claude Code.

## Learning mode applies to every skill
The developer writes the application code. Skills below produce plans, requirements, explanations, reviews, and debugging help. They write application code only when the developer explicitly asks for it for a named piece. See "Learning mode" in `AGENTS.md`.

## Skills (`.claude/skills/*/SKILL.md`, user-invoked)
- `/plan` creates `docs/plans/NNN-topic.md` for one small task, including the concept and why, with measurable acceptance criteria, then stops for approval.
- `/implement` for an approved step gives the developer requirements and acceptance checks, and generates code only on explicit request. It updates plan progress and actual validation, and never starts `/review` automatically.
- `/review` independently assesses the developer's code for the requested step or scope, selects relevant read-only reviewers, appends findings to the plan, and explains mistakes. It never edits application code.
- `/fix-review` explains approved findings and fixes them only if the developer asks; `/review` is then invoked separately for re-review.
- `/debug` investigates and reproduces defects before proposing a minimal fix; `/tdd` uses red-green-refactor for suitable behavior changes (developer writes the code).

## Reviewer subagents (`.claude/agents/*.md`)
`code-reviewer`, `test-reviewer`, `security-reviewer`, `architecture-reviewer`, `performance-reviewer`. They are analysis-only (`Read`, `Grep`, `Glob`) and carry the sharding-lab project context. Only the main review workflow may append review results to the plan. Never claim a reviewer ran unless it was actually executed.

## Harness documentation (load relevant files, not all by default)
- `docs/harness/workflow.md`: lifecycle, approvals, remediation, learning mode.
- `docs/harness/architecture.md`: components and boundaries.
- `docs/harness/agents.md`: reviewer selection, delegation, and reporting.
- `docs/harness/quality-gates.md`: validation, acceptance, and Git checkpoints.

## Authority
`CLAUDE.md` and `AGENTS.md` give repository guidance. `.claude/skills/*/SKILL.md` defines each workflow's operational steps; `.claude/agents/*.md` defines reviewer specialties; `docs/plans/*.md` records task state. Harness documents explain rather than override those instructions.

## Environment notes
- Windows 11 with PowerShell. Give commands in PowerShell syntax. Docker Desktop must be running for Docker Compose and Testcontainers.
- Permissions in `.claude/settings.json` ask before git commit/push/reset/clean/restore/checkout/rebase/merge/rm, file deletion, and destructive Docker commands (`docker compose down -v`, `docker volume rm`, prune), in both Bash and PowerShell. Treat these as defense in depth, not a security boundary.
- `.env` and secrets files are denied for reading. Keep database credentials out of committed files where practical.
- After editing skills, agents, or settings, start a new session so Claude Code reloads them.
