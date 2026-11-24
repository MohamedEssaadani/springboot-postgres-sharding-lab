# Quality Gates

A gate is an evidence-based decision point. Possible gate outcomes: `PASS`, `PASS_WITH_EXCEPTIONS`, `FAIL`, `BLOCKED`, `NOT_VERIFIED`. These are distinct from plan status and review assessment. Apply proportionate rigor to change risk.

## G1 Planning readiness
- Problem, scope, out-of-scope, acceptance criteria, ordered steps, assumptions, risks, and validation strategy are documented.
- Relevant existing behavior has been inspected.
- Output: `PROPOSED` plan ready for developer approval, not implementation authorization.

## G2 Implementation readiness
- Plan and requested step are identified; developer approved that scope; prerequisites and repository state are understood.
- Existing user changes are protected; no unapproved destructive or breaking change is required.

## G3 Implementation completion
- Approved behavior and applicable tests are implemented; changes stay within scope; plan checklist and progress reflect actual work.
- A checked implementation step is not automatically reviewed or accepted.

## G4 Validation readiness
- Run applicable build, lint, unit, integration, contract, runtime, security, and performance checks according to risk.
- Record command, environment, result, and limitations. Valid statuses: `PASSED`, `FAILED`, `BLOCKED`, `NOT_VERIFIED`, `NOT_APPLICABLE`.
- Never convert unavailable runtime evidence into a passing result.
- Project checks (PowerShell): `docker compose ps` (both shards `healthy`), `.\mvnw.cmd test` (unit), `.\mvnw.cmd verify` (integration with Testcontainers; `BLOCKED` if Docker is not running). Data-distribution and redistribution claims need measured output (counts per shard, number of users reassigned).

## G5 Review acceptance
- Review scope and acceptance criteria are clear; relevant reviewers executed or limitations were reported.
- Findings have evidence and severity; critical issues block acceptance; high issues ordinarily block; medium issues require remediation or explicit permissible risk acceptance; low issues may be deferred.
- `CHANGES_REQUIRED` is not a pass. `PASS_WITH_FINDINGS` needs explicit evaluation of remaining findings.

## G6 Git checkpoint readiness
- Inspect diff; exclude secrets and unrelated changes; confirm intended validation/review state; get authorization for commit.
- Push, merge, release, and deployment are separate decisions.

## Remediation gate
- Approved finding addressed at root cause; relevant regression validation performed; re-review verifies the result; old review rounds preserved.

## Risk and exceptions
A documented exception must include affected gate, missing evidence, impact, mitigation, decision-maker, and follow-up. An exception cannot silently waive mandatory security or correctness controls. Small documentation changes may use lighter review; sensitive data or destructive changes require stronger gates.

## Reporting template
```text
Plan/step: ...
Implementation: ...
Validation: PASSED | FAILED | BLOCKED | NOT_VERIFIED
Review: PASS | PASS_WITH_FINDINGS | CHANGES_REQUIRED | BLOCKED | NOT_VERIFIED
Gate: ...
Evidence: commands and observed results
Open findings / accepted exceptions: ...
Next authorized action: ...
```

Use existing plan sections to record evidence; avoid conflicting duplicate state.
