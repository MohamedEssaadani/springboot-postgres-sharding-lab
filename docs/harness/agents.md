# Agent Roles and Delegation

## Orchestrator
The main Claude Code session chooses a skill, confirms scope, reads relevant context, and reports actual execution. It may delegate review analysis, but it owns consolidation and plan review-history updates. It must not claim a configured subagent executed unless execution is observed.

## Reviewer selection
| Change | Reviewers typically useful |
| --- | --- |
| Business logic | Code, Test |
| API and integrations | Code, Test; Security if trust boundaries change |
| Authentication, authorization, secrets | Security, Code, Test |
| Architectural refactoring | Architecture, Code, Test |
| Database or resource-sensitive work | Code, Test; Performance/Architecture as justified |
| Shard routing / hashing (modulo, hash ring) | Code, Test; Architecture for new strategies; Performance for ring lookup and distribution |
| DataSource, pooling, Docker Compose, credentials | Code, Security; Performance for pool sizing |
| Cross-shard operations, transactions, migrations | Architecture, Code, Test |
| Performance optimization | Performance, Code, Test |
| Documentation only | Main session focused review; subagents often unnecessary |

## Reviewers
- `code-reviewer`: correctness, errors, edge cases, regressions, maintainability.
- `test-reviewer`: acceptance coverage, assertions, boundaries, failure cases, deterministic tests.
- `security-reviewer`: trust boundaries, access control, injection, secrets, unsafe defaults.
- `architecture-reviewer`: contracts, coupling, dependency direction, design tradeoffs.
- `performance-reviewer`: complexity, allocations, I/O, concurrency, scaling; separate measured from hypothetical issues.

## Delegation brief
Each reviewer should receive the plan ID and step, precise files/diff, acceptance criteria, applicable project instructions, validation evidence, relevant focus, and read-only restriction. Subagents should return evidence-backed findings with severity, location, impact, recommendation, confidence, and verification limitations. If no findings, say what was examined and what remains unverified.

## Learning mode
Reviewers examine code the developer wrote. The consolidated report explains each finding's concept and consequence, not only the fix, and does not rewrite the developer's code. Each reviewer definition carries a "Project Context" section for this sharding lab.

## Consolidation
The main reviewer checks duplicates and disagreements against evidence, records which roles actually ran, and appends a review round to the plan. Do not silently replace a failed reviewer with an invented successful run. If fallback analysis is used, label it clearly.

## Permissions and model strategy
Reviewer definitions restrict tools to `Read`, `Grep`, and `Glob`; no `Write`, `Edit`, or `Bash`. Actual enforcement depends on the installed Claude Code runtime and should be tested. Reviewers inherit the parent model by default for portability; choose explicit model overrides only if supported and justified.

## Assessments
`PASS`, `PASS_WITH_FINDINGS`, `CHANGES_REQUIRED`, `BLOCKED`, `NOT_VERIFIED`. These are not substitutes for runtime validation results or human acceptance.
