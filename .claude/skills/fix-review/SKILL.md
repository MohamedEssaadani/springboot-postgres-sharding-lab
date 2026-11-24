---
name: fix-review
description: Remediate explicitly approved review findings for a specified plan step without expanding scope; validate and prepare a separate re-review.
disable-model-invocation: true
---

# Fix Review Findings

1. Identify plan ID, step, review round, and exact approved findings. If approval or scope is ambiguous, ask before changing code.
2. Read the plan, latest review, affected code, relevant tests, and repository instructions. Preserve existing user changes.
3. For each approved finding, explain the root cause and choose the smallest appropriate fix. Do not implement later plan steps or unrelated suggestions.
4. Implement fixes and focused regression tests; run available validation. Report actual commands and statuses.
5. Append remediation progress and validation evidence to the plan. **Do not overwrite or edit previous review assessments or review history.**
6. Stop. Recommend a separate `/review Re-review Step N of plan ID` invocation. Do not invoke review automatically, commit, push, or deploy.

**Learning mode:** the developer writes the code. By default, explain each approved finding (root cause, why it matters, what a fix must achieve) and let the developer apply it. Edit code yourself only if the developer explicitly asks you to fix it for them.

If a finding is disputed, cannot be reproduced, or requires a design change, document evidence and request a decision rather than silently dismissing it.
