---
name: tdd
description: Implement an approved behavior change with test-driven development using red, green, and refactor cycles and recorded evidence.
disable-model-invocation: true
---

# Test-Driven Implementation

1. Confirm requirements, acceptance criteria, authorized plan step, and existing test conventions. No unapproved substantial changes.
2. Select one observable behavior or edge case. Write the smallest meaningful test that expresses it.
3. **Red:** Run the targeted test and confirm it fails for the expected reason. If it cannot run, report `NOT_VERIFIED` rather than claiming red.
4. **Green:** Implement the simplest correct production behavior; run the test and record its result.
5. **Refactor:** Improve clarity without changing behavior; rerun relevant tests.
6. Repeat for the remaining approved cases. Cover important errors and boundaries without chasing artificial coverage numbers.
7. Run relevant broader validation and update plan progress with actual evidence. Stop at the approved step. Do not automatically invoke `/review` or commit.

**Learning mode:** the developer writes the tests and production code. Propose the next behavior to test and what the assertion should prove; write code only on explicit request. Integration tests that need PostgreSQL use Testcontainers and require Docker to be running; report `BLOCKED` if it is not.

Do not add artificial failing tests, brittle mocks, or unnecessary abstractions merely to simulate TDD.
