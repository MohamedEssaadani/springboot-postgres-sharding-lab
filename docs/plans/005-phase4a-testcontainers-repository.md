# Plan 005: Phase 4a, Testcontainers integration tests for the repository

- **Status:** APPROVED (Steps 1-6, defaults D1-D4; the dependency change in Step 2 is covered by this approval, limited to the two Testcontainers test-scope artifacts and, if D3 needs it, the failsafe plugin)
- **Approved scope:** Steps 1-6 as written, recommended defaults
- **Owner / decision-maker:** Developer
- **Created:** 2026-11-24

> First slice of roadmap Phase 4 (Stage 1 of plan 004). The later slices (100-user distribution test, database-failure test) get their own plans once this one is done.

## Problem and desired outcome
`UserRepository` has only been tested with fake `JdbcTemplate`s and by hand through `curl.exe` against the long-running dev containers. Plan 003 still lists "repository against real PostgreSQL" as an unautomated check. We need repeatable tests that start throwaway PostgreSQL containers, so the full routing and SQL path is verified in `.\mvnw.cmd verify` without touching the dev shards or their data.

## Concept and why
**Testcontainers** starts real Docker containers from test code and removes them afterwards. For a sharding system this matters because the interesting behavior (routing to the right instance, one row on exactly one database, unique-violation translation, `created_at` mapping) only exists with real, independent PostgreSQL instances. Mocks cannot show that a row lands on shard 1 and not shard 0. Using separate containers also keeps tests from deleting or polluting your dev data, and removes the "shards must be running by hand" requirement for these tests.

Concurrency and consistency note: each test shard is an independent database; a test touching two shards is still not atomic. Tests must not assume ordering or shared state between containers.

## Learning outcomes
- [ ] LO1: Explain why mocks cannot prove sharding correctness and what a real-database test proves that the fake-`JdbcTemplate` test does not.
- [ ] LO2: Explain how Testcontainers manages container lifecycle and why test data must be isolated from the dev shards.
- [ ] LO3: Show, from test output, that an inserted user exists in exactly one test shard and that it equals the router's answer.

## Scope
**In:** add Testcontainers dependencies (needs approval), a reusable way to start two PostgreSQL containers for tests, building a `ShardRegistry` from them, integration tests for insert/find/update/delete, duplicate id, not-found and shard ownership, and recording the surefire/failsafe decision.

**Out:** 100-user distribution test and database-failure test (next plans), controller/HTTP end-to-end tests, consistent hashing, CI configuration, changing production code unless a testability problem is found (then ask first).

## Requirements and acceptance criteria
- [ ] AC1: Tests start two throwaway PostgreSQL containers using the same image tag as `docker-compose.yml` (17), with different database names, and stop them afterwards.
- [ ] AC2: The tests use the real schema (`schema.sql` is the single source) applied through the existing initializer logic or equivalent, not a copy of the SQL.
- [ ] AC3: Insert then find returns the same user with a non-null `createdAt` and the router's shard id; update and delete change/remove the row; update and delete of an unknown id report "not found".
- [ ] AC4: For several ids (including ones routed to each shard), a direct query against each container shows the row on the owning shard and absent on the other.
- [ ] AC5: Inserting an existing id throws `DuplicateKeyException` and leaves the stored row unchanged.
- [ ] AC6: The tests do not touch the dev containers (`shard0`/`shard1` on ports 5433/5434) or `.env`; they pass with the dev containers stopped.
- [ ] AC7: `.\mvnw.cmd clean verify` passes; if Docker is not running, the failure is clear (or the tests are skipped explicitly, decision D2).

## Relevant context and constraints
- Boot 4.2.0, JUnit 5; `spring-boot-starter-jdbc-test` already present. Testcontainers dependencies are not in `pom.xml` yet.
- `ShardRegistry`, `ShardProperties`, `ShardSchemaInitializer`, `ShardRouter`, `UserRepository` already have constructors usable from plain tests (the unit tests build them without Spring).
- Existing `ShardingLabApplicationTests.contextLoads` needs the dev shards and a local `.env`; this plan does not change that.
- Docker Desktop must be running. Test containers need free random ports (Testcontainers handles this).
- AGENTS.md: parameterized SQL only, explicit pools, credentials never hardcoded in committed real configuration (Testcontainers' generated test credentials are fine).

## Proposed approach and alternatives
**Recommended:** plain JUnit 5 with the Testcontainers JUnit extension and two `PostgreSQLContainer`s started once per test class (or shared across classes). Build `ShardProperties` from the containers' JDBC URL/user/password and create `ShardRegistry`, `ShardSchemaInitializer`, `ShardRouter` and `UserRepository` by hand. This is the simplest and keeps the sharding wiring visible.

Alternatives: (a) Spring Boot test slice with `@DynamicPropertySource` overriding `sharding.shards[*]` and the full context: closer to production wiring but hides the lesson and is slower; (b) Boot's `@ServiceConnection`: built for a single DataSource, a poor fit for two shards. Revisit (a) for a full end-to-end test later.

**Naming/phases:** these tests need Docker, so `*IT` classes under failsafe (`verify`) keep `.\mvnw.cmd test` fast and Docker-free. Surefire/failsafe split is still `[DISCOVER]`.

## Implementation steps
Owner is Developer for every step. Each step is approved separately.

- [x] Step 1 (Owner: Developer): **Decisions.** Confirm D1-D4 below. Result: no open choices before coding.
- [ ] Step 2 (Owner: Developer): **Dependencies.** Add the Testcontainers PostgreSQL module and JUnit 5 integration (test scope), using the Boot-managed version if the BOM provides it. Requirements: confirm exact artifact names for the Testcontainers version Boot 4.2.0 manages `[DISCOVER]`; no unrelated dependencies; `pom.xml` is the only file changed. Needs developer approval for the dependency change. Result: project still builds, container classes resolve.
- [ ] Step 3 (Owner: Developer): **Test fixture.** One helper that starts two PostgreSQL 17 containers with distinct database names, builds the shard properties and `ShardRegistry`, applies the schema, and exposes the registry, router and repository to tests. Requirements: containers stopped after the tests; pools closed; no dependency on `.env` or the dev shards. Expected files: test-scope helper class under `src/test/java/com/shardinglab/shard/`. Result: AC1, AC2, AC6.
- [ ] Step 4 (Owner: Developer): **Repository tests on real PostgreSQL.** Cover AC3, AC4 and AC5, including an id that routes to each shard, negative and very large ids, and not-found cases. Requirements: assert ownership by querying the containers directly (not through the repository); each test uses its own ids or cleans up so tests do not depend on order. Expected files: an `*IT` class beside the existing repository test. Result: AC3-AC5.
- [ ] Step 5 (Owner: Developer): **Verify and record.** Run `.\mvnw.cmd clean verify` with the dev containers stopped, then again with them running; confirm the dev shards' data is untouched. Update plan 003's `NOT_VERIFIED` row for the repository against real PostgreSQL. Result: AC6, AC7.
- [ ] Step 6 (Owner: Developer): **Review and checkpoint.** Developer decides on `/review` (test-reviewer is the most relevant) and on a commit.

## Test and validation strategy
- `.\mvnw.cmd clean verify` with Docker running and the dev shards stopped: container tests pass, unit tests unchanged.
- Same command with dev shards running: dev rows (for example ids in `users`) before and after are identical.
- Docker stopped: observe and record the failure or skip behavior (AC7).
- Mutation check: temporarily break routing (for example make the repository always use shard 0) in a scratch change and confirm the ownership test fails; then revert. Record the result.
- Anything not run is `NOT_VERIFIED`.

## Risks, assumptions, and open decisions
- Risk: Testcontainers artifact names and package layout changed in recent major versions. Mitigation: confirm before editing `pom.xml` (Step 2).
- Risk: Testcontainers on Docker Desktop for Windows may need configuration. Mitigation: record the actual behavior; do not change Docker settings without approval.
- Risk: container start time (several seconds) slows the build. Mitigation: start once per class, keep these as `*IT` under `verify`.
- Risk: tests accidentally using the dev shards. Mitigation: AC6 check with dev containers stopped.
- Assumption: the schema initializer can be reused from tests (it takes the registry and `schema.sql`).
- Open decision D1: plain JUnit with hand-built wiring (recommended) or Spring slice with `@DynamicPropertySource`.
- Open decision D2: when Docker is unavailable, fail clearly (recommended, no silent green) or skip with `disabledWithoutDocker`.
- Open decision D3: use `*IT` + failsafe so `.\mvnw.cmd test` stays Docker-free (recommended) or plain `*Test` under surefire.
- Open decision D4: share the two containers across test classes (faster, shared state) or one pair per class (slower, isolated). Recommended: one pair per class for now.

## Implementation progress
- [Date, step, files, decisions, actual outcome, blockers]

## Validation results
| Date | Step | Command/check | Result | Evidence / limitation |
| --- | --- | --- | --- | --- |

## Review results (append-only)
No review requested.

## Developer decisions / accepted exceptions
- 2026-11-24, D1-D4: recommended defaults accepted. D1 plain JUnit with hand-built wiring; D2 fail clearly when Docker is unavailable (no silent skip); D3 `*IT` classes under failsafe so `.\mvnw.cmd test` stays Docker-free; D4 one container pair per test class.
- 2026-11-24, [DISCOVER] resolved for Step 2: Boot 4.2.0's BOM manages Testcontainers `2.0.5`. Testcontainers 2.x artifact names are `org.testcontainers:testcontainers-postgresql` and `org.testcontainers:testcontainers-junit-jupiter` (no `junit-jupiter` or `postgresql` short names; test scope, no version needed). `pom.xml` has no surefire or failsafe configuration yet, so D3 needs the failsafe plugin declared with `integration-test` and `verify` goals (Boot's parent manages the version).
