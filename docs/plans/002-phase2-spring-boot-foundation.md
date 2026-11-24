# Plan 002: Phase 2 - Spring Boot foundation (two shards, two pools)

- **Status:** IN_PROGRESS
- **Approved scope:** Steps 1-8 approved by developer on 2026-11-24, each step still approved and run separately; developer writes the code, assistant generates code only on explicit request
- **Owner / decision-maker:** Developer
- **Created:** 2026-11-24

## Problem and desired outcome
Phase 1 gave us two independent PostgreSQL shards. There is no application yet. We need a Spring Boot app that holds **one connection pool per shard**, creates the same `users` schema on each shard, and proves at startup that each pool talks to its own database. No routing, no REST endpoints (Phase 3).

## Concept and why
In application-level sharding the **app** is the router, so it must hold a separate connection pool for every shard. A pool keeps a bounded set of open connections to one database so requests don't pay the connection setup cost each time. One pool per shard gives:
- **Isolation of failures and load:** if shard 1 is slow or down, its pool is exhausted or failing while shard 0's pool keeps serving.
- **Explicit capacity:** each shard's `max_connections` is finite (PostgreSQL default 100), so total pool sizes across app instances must stay under it.
- **A routable handle:** later `ShardRouter` returns "shard N", and the app needs a `DataSource`/`JdbcTemplate` for that N.

Spring Boot auto-configures exactly **one** `DataSource` from `spring.datasource.*`. With two shards it backs off or misleads, so we configure the pools ourselves. Also, shard-local primary keys cannot come from a per-shard sequence (two shards would both issue id 1), so the `users.id` is supplied by the application. How to generate unique ids across shards is a Phase 7 topic; for now the sharding key `userId` is a value the caller provides.

## Learning outcomes
- [ ] LO1: Explain why each shard needs its own pool and what a pool size per shard protects.
- [ ] LO2: Explain why Spring Boot's single-DataSource auto-configuration does not fit and how we replaced it.
- [ ] LO3: Explain why `users.id` is not auto-generated per shard.
- [ ] LO4: Demonstrate, from the app's own output, that pool `shard0` is connected to `shard0_db` and pool `shard1` to `shard1_db`.
- [ ] LO5: Explain what happens at startup when one shard is down, and why that is (or is not) the behavior we want.

## Scope
**In:**
- Maven project (Java 21) with Spring Boot, `spring-boot-starter-jdbc`, PostgreSQL driver (HikariCP comes with the starter).
- Shard configuration bound from `application.yml` (a list of shards, so a third shard in Phase 5 is configuration, not new code).
- One explicitly configured HikariCP pool and `JdbcTemplate` per shard, reachable by shard id.
- `users` schema applied idempotently to every shard.
- A `User` model type.
- A startup connectivity check that logs shard id, pool name, database and user for each shard.

**Out:**
- `ShardRouter`, hashing, REST endpoints, CRUD (Phase 3).
- Testcontainers and automated tests of shard behavior (Phase 4). Phase 2 is verified by running the app and querying PostgreSQL.
- Flyway/Liquibase migrations (Phase 7), Actuator/metrics, Docker image for the app.
- README (ask before writing).

## Requirements and acceptance criteria
- [ ] AC1: `mvn clean verify` succeeds on Java 21 (command, environment and result recorded).
- [ ] AC2: Two shards are configured from `application.yml`; credentials and URLs are **not** hardcoded in Java code or committed files (they come from `.env`/environment).
- [ ] AC3: The app creates two separate pools with distinct pool names (`shard0-pool`, `shard1-pool`) and an explicit, justified `maximumPoolSize`.
- [ ] AC4: On startup the app logs, per shard, the pool name and `current_database()`/`current_user`; the output shows `shard0_db` for shard 0 and `shard1_db` for shard 1.
- [ ] AC5: After startup, `\d users` (via `docker compose exec`) shows the same table definition on both shards.
- [ ] AC6: Restarting the app does not fail or duplicate anything (schema creation is idempotent).
- [ ] AC7: With shard 1 stopped, the app fails (or reports) with a clear error naming the shard that is unreachable; shard 0 is not mistaken for shard 1. Observed behavior is recorded.
- [ ] AC8: No SQL is built by string concatenation with external input; no password appears in logs.

## Relevant context and constraints
- Phase 1 is complete: shards `shard0` (127.0.0.1:5433, `shard0_db`/`shard0_user`) and `shard1` (127.0.0.1:5434, `shard1_db`/`shard1_user`) run PostgreSQL 17. A leftover test table `probe` exists on shard 0.
- No `pom.xml` or source exists yet. The folder is not a Git repository yet.
- `.env` holds `SHARD0_*`/`SHARD1_*` (DB, USER, PASSWORD, PORT). Spring Boot does not read `.env` by default; the assistant cannot read `.env` (permission rule), the developer does.
- Constructor injection only; explicit error handling; avoid unnecessary abstractions (AGENTS.md).
- Windows 11 / PowerShell; Docker Desktop must be running.

## Proposed approach and alternatives
**Data access: Spring JDBC (`JdbcTemplate`)**, recommended. Alternative: Spring Data JPA, which needs a separate `EntityManagerFactory` and transaction manager per shard, adding noise before the sharding lesson.

**Shard configuration: a list bound with `@ConfigurationProperties`** (id, url, username, password, pool settings). Alternative: hardcode `shard0`/`shard1` fields, which would force code edits for Phase 5.

**Getting `.env` into Spring:** `spring.config.import=optional:file:.env[.properties]` in `application.yml`, with `${SHARD0_PASSWORD}`-style placeholders. This keeps one source of truth shared with Docker Compose. Alternative: set OS environment variables in the run configuration. To be confirmed by the developer when running the app (assistant has not verified this on the target Spring Boot version).

**Pools:** build `HikariDataSource` instances manually (one per shard), not via `spring.datasource.*`, and exclude the single-DataSource auto-configuration. Alternative: mark one DataSource `@Primary`, which hides the problem and silently treats shard 0 as "the" database.

**Schema creation:** an idempotent `CREATE TABLE IF NOT EXISTS` script applied to every shard at startup. Alternatives: `docker-entrypoint-initdb.d` (runs only on first volume creation, so it will not apply to the existing volumes) or Flyway (Phase 7).

**Schema (proposed):** `users(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`. Trade-off to understand: a `UNIQUE` on `email` would be enforced **per shard only**, so the same email could exist on two shards. We do not add it now, and revisit under cross-shard concerns in Phase 7.

## Implementation steps
Owner is Developer for every step. Each step is approved separately; approval of Step N is not approval of Step N+1.

- [ ] Step 1 (Owner: Developer): **Decide conventions.** Spring Boot version (current stable GA that supports Java 21), `groupId`/base package, artifact name, pool size per shard (state the reasoning, e.g. total app connections vs the shard's `max_connections`), confirm Spring JDBC, and how to feed `.env` to Spring. Record in "Developer decisions". Expected result: no open questions before coding.
- [x] Step 2 (Owner: Developer; generated at developer's explicit request "implement step 2"): **Maven skeleton.** Create the project (start.spring.io or by hand) with Java 21, `spring-boot-starter-jdbc`, the PostgreSQL driver, and the Spring Boot Maven plugin. Hint: starting the app before any DataSource exists will fail; decide how to stop Boot's single-DataSource auto-configuration. Expected files: `pom.xml`, main application class, `src/main/resources/application.yml`. Result: `mvn clean verify` passes (AC1). Dependency install needs the developer's approval.
- [x] Step 3 (Owner: Developer; generated at developer's explicit request "implement step 3"): **Shard configuration.** Properties class bound from `application.yml` with a list of shards (id, url, username, password, pool settings); placeholders for secrets; `.env` import. Requirements: constructor-bound/immutable if practical, validation that fails startup on missing values, passwords never logged. Expected files: properties class, `application.yml`. Result: the app starts and shows two configured shards (AC2).
- [x] Step 4 (Owner: Developer; generated at developer's explicit request "the step 4"): **Pools and JdbcTemplates.** One `HikariDataSource` per shard with pool name `shard<N>-pool`, explicit `maximumPoolSize`, explicit connection timeout; a small holder that returns the `JdbcTemplate` (or `DataSource`) for a shard id and fails clearly for an unknown id. Requirements: constructor injection, pools closed on shutdown, no "default shard". Expected files: one configuration class, one small registry class. Result: AC3.
- [x] Step 5 (Owner: Developer; generated at developer's explicit request "implements step 5"): **Schema and `User`.** `schema.sql` with the proposed table (idempotent) applied to every shard at startup; a `User` model (a Java record is enough). Requirements: runs on each shard independently; a failure on one shard names that shard. Expected files: `schema.sql`, `User`, an initializer. Result: AC5, AC6.
- [x] Step 6 (Owner: Developer; generated at developer's explicit request "implement step 6"): **Connectivity check.** A startup runner that, per shard, logs shard id, pool name, `current_database()` and `current_user` (use a parameter-free query). Run the app; then run `\d users` on both shards with `docker compose exec`. Result: AC4, AC5.
- [x] Step 7 (Owner: Developer; run by developer, log pasted into the session; restart of shard1 pending, see progress notes): **Failure observation.** `docker compose stop shard1`, start the app, record what happens and how long it takes (pool initialization timeout); then `docker compose start shard1`. Result: AC7 and LO5. Note: use `stop`, not a reboot, so `unless-stopped` does not restart it.
- [ ] Step 8 (Owner: Developer): **Review.** Developer requests `/review` for the new code. Assistant explains findings; no auto-fix.

## Test and validation strategy
Manual runtime evidence, recorded in "Validation results" with real output:
- `mvn clean verify` (build, Java 21).
- `docker compose ps` shows both shards `healthy` before starting the app.
- Application log shows two pools with distinct names and databases (AC4).
- `docker compose exec shard0 ...` and `... shard1 ...` with `\d users`, run on both (AC5).
- App restarted twice with no errors (AC6).
- Failure case with shard 1 stopped (AC7).
- `grep`/search of logs and source for password strings (AC8).

Automated tests (JUnit 5, Testcontainers) arrive in Phase 4; until then anything not executed is marked `NOT_VERIFIED`. Optional, if the developer wants one: a pure unit test that checks properties binding and validation without a database.

## Risks, assumptions, and open decisions
- Risk: Boot's auto-configured single DataSource fails or silently connects with default settings. Mitigation: exclude it (Step 2 hint) and verify AC4 shows both pools.
- Risk: pool sizes multiplied by instances exceed a shard's `max_connections`. Mitigation: choose a small explicit size and write the reasoning in Developer decisions.
- Risk: `.env` is not picked up by Spring, so the app starts with empty URLs. Mitigation: validation that fails fast on missing properties; alternative is OS environment variables.
- Risk: pool hangs for a long time when a shard is down. Mitigation: set an explicit connection timeout and observe AC7.
- Risk: leftover `probe` table on shard 0 makes the shards look different. Mitigation: developer may drop it (assistant asks first); the `users` table is what we compare.
- Assumption: Maven and a Java 21 JDK are installed. If not, Step 2 is `BLOCKED`.
- Open decision D1: Spring Boot version and base package/groupId.
- Open decision D2: pool size per shard and connection timeout.
- Open decision D3: `.env` import into Spring vs OS environment variables.
- Open decision D4: fail fast at startup if a shard is down (default behavior, recommended for now) vs start anyway and fail per request.
- Open decision D5: drop the `probe` table on shard 0 (yes/no).
- Open decision D6: `git init` and a first commit checkpoint for Phase 1 before starting this plan (recommended).

## Implementation progress
- 2026-11-24, Step 2: skeleton generated from start.spring.io (`starter.zip`, ~14 KB, Boot 4.2.0, Maven, Java 21, deps `jdbc` + `postgresql`), unpacked in a scratch directory, inspected, then copied into the repo without overwriting existing files (kept our `.gitignore`; skipped `HELP.md`). Files: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitattributes`, `ShardingLabApplication.java`, `ShardingLabApplicationTests.java`, `application.yml` (replaces the generated `application.properties`; only `spring.application.name`).
- Observed expected failure first: with no excluded auto-configuration, `contextLoads` failed with "Failed to determine a suitable driver class" (Boot builds one DataSource from `spring.datasource.*`). Fixed by `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`; in Boot 4 the class is `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`.

- 2026-11-24, Step 3: added `config/ShardProperties.java` (record bound from `sharding.*`: list of `Shard(id, url, username, password)` plus `Pool(maximumPoolSize, connectionTimeoutMs)`; validation in compact constructors, so no new dependency; `Shard.toString()` masks the password), `@ConfigurationPropertiesScan` on the application class, `application.yml` (`spring.config.import: optional:file:.env[.properties]`, URLs built from `${SHARDn_PORT}/${SHARDn_DB}`, user/password placeholders, pool 5 / 3000 ms), and tests `ShardPropertiesValidationTest` (8 unit tests) and `ShardPropertiesBindingTest` (3 tests, loads real yml + local `.env`, no database).

- 2026-11-24, Step 4: added `config/ShardConfig.java` (one `HikariDataSource` per shard: pool name `shard<id>-pool`, max size and timeout from `ShardProperties.Pool`, `initializationFailTimeout` 1 so an unreachable shard fails at startup; pool failure is rethrown as `IllegalStateException("Cannot connect to shard N at <url>")` with no credentials; pools already opened are closed if a later shard fails) and `shard/ShardRegistry.java` (immutable id to `JdbcTemplate` map, `forShard(id)` throws `IllegalArgumentException` listing known ids, `shardIds()`, `close()` closes every pool and rethrows the first failure; no default shard). The registry is a bean, so Spring calls `close()` on shutdown (inferred destroy method). Tests: `ShardRegistryTest` (6, no database), `ShardConfigTest` (1, unreachable port, no database). `ShardPropertiesBindingTest` now registers only the properties bean so it needs no database.
- Note: the generated `ShardingLabApplicationTests.contextLoads` now loads the full context and therefore **needs both shards running** (`docker compose up -d`). It acts as a first integration check until Phase 4 introduces Testcontainers.

- 2026-11-24, Step 5: added `resources/schema.sql` (`CREATE TABLE IF NOT EXISTS users(id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`; no sequence, no UNIQUE on email), `model/User.java` (record `id, name, email, createdAt`; `createdAt` null until stored; `User.newUser(...)`; blank name/email rejected), `shard/ShardSchemaInitializer.java` (`ApplicationRunner`, `@Order(1)`; applies the script per shard in id order via `ResourceDatabasePopulator`; failure rethrown as `Schema initialization failed on shard N`; fail fast). Tests: `ShardSchemaInitializerTest` (2, no database), `UserTest` (2).
- Defect found and fixed during Step 5: two constructors on the initializer made Spring fail with "No default constructor found"; the public one now has `@Autowired`. My first schema test also matched words inside SQL comments; it now strips comment lines before asserting.
- Known limit: fail fast means shards after the failing one are not attempted, so a partial failure can leave shard 0 migrated and shard 1 not; `IF NOT EXISTS` makes a retry safe. `CREATE TABLE IF NOT EXISTS` never alters an existing table (Phase 7: migrations).

- 2026-11-24, Step 6: added `shard/ShardConnectivityCheck.java` (`ApplicationRunner`, `@Order(2)`, constant query `select current_database(), current_user` per shard, logs `Shard N | pool <name> | database <db> | user <user>`, wraps a failure as `Connectivity check failed on shard N`, and fails startup if two shards report the same database) and `ShardRegistry.poolName(int)` (exposes the Hikari pool name; unknown id fails loudly). Tests: `ShardConnectivityCheckTest` (3, no database) and one more case in `ShardRegistryTest`.
- Design limit: the duplicate-database rule relies on this lab's convention of distinct database names per shard. In a real deployment two shards on different servers could share a database name, so a server identity would have to be compared instead. The check is a startup snapshot, not health monitoring (Phase 7).

- 2026-11-24, Step 7: developer ran `docker compose stop shard1` and started the app from IntelliJ (exit code 1). Observed: `shard0-pool` started and completed; `shard1-pool - Starting...` at 11:29:03.010; at 11:29:04.014 (about 1.0 s later) `shard0-pool - Shutdown initiated...` / `Shutdown completed.`; then `Cannot connect to shard 1 at jdbc:postgresql://localhost:5434/shard1_db`, root cause `Connection to localhost:5434 refused`. No password in the output. At 11:3x `docker compose ps --all` showed `shard1 Exited (0)`; restart and a clean second start are still to be confirmed by the developer.

## Validation results
| Date | Step | Command/check | Result | Evidence / limitation |
| --- | --- | --- | --- | --- |
| 2026-11-24 | 7 | App start with shard1 stopped (developer's pasted log) | PASSED (AC7) | fails fast, exit code 1, message names shard 1 and its URL, root cause connection refused, no password in the log. Evidence is the developer's pasted log, not re-run by the assistant |
| 2026-11-24 | 7 | Pool cleanup when a later shard fails (the Step 4 NOT_VERIFIED item) | PASSED | log shows `shard0-pool - Shutdown initiated...` and `Shutdown completed.` before the exception |
| 2026-11-24 | 7 | Time to fail | OBSERVED | about 1.0 s. A refused connection fails immediately; the 3000 ms timeout applies when a host does not answer. The source of the extra ~1 s inside Hikari was not traced |
| 2026-11-24 | 7 | `docker compose start shard1` and clean second start | PASSED (developer-reported) | developer reported "all good" after restarting shard1 and re-running; the assistant did not observe it (earlier `docker compose ps --all` showed `shard1 Exited (0)`) |
| 2026-11-24 | 6 | `.\mvnw.cmd -B -ntp clean verify`, shards up | PASSED (AC1, AC4) | `Tests run: 27, Failures: 0, Errors: 0`; `BUILD SUCCESS`. Log: `Shard 0 \| pool shard0-pool \| database shard0_db \| user shard0_user` and `Shard 1 \| pool shard1-pool \| database shard1_db \| user shard1_user` |
| 2026-11-24 | 6 | Duplicate database detection (unit test with fake results) | PASSED | message `Shards 0 and 1 both report database 'same_db'` |
| 2026-11-24 | 6 | Failure names shard, no password (unit test, unreachable port) | PASSED | message contains `shard 3`, not the password |
| 2026-11-24 | 5 | `.\mvnw.cmd -B -ntp clean verify` (first run) | FAILED then fixed | `No default constructor found` for `ShardSchemaInitializer`; schema test matched comment text |
| 2026-11-24 | 5 | `.\mvnw.cmd -B -ntp clean verify` (after fixes), shards up | PASSED (AC1) | `Tests run: 23, Failures: 0, Errors: 0`; `BUILD SUCCESS`; log: `Schema applied on shard 0`, `Schema applied on shard 1` |
| 2026-11-24 | 5 | `.\mvnw.cmd spring-boot:run` twice in a row | PASSED (AC6) | both runs: schema applied on shard 0 and 1, no errors, no duplicates |
| 2026-11-24 | 5 | `\d users` via `docker compose exec` on shard0 and shard1 | PASSED (AC5) | identical: `id bigint not null`, `name varchar(100) not null`, `email varchar(255) not null`, `created_at timestamptz not null default now()`, `users_pkey` PRIMARY KEY (id) |
| 2026-11-24 | 5 | Failure names the shard (unit test, unreachable port) | PASSED | message `Schema initialization failed on shard 7`, no password in it. Real shard-down behavior still to be observed in Step 7 |
| 2026-11-24 | 2 | `.\mvnw.cmd -B -ntp clean verify` (before exclusion) | FAILED (expected) | `contextLoads`: "Failed to determine a suitable driver class" |
| 2026-11-24 | 2 | `.\mvnw.cmd -B -ntp clean verify` (after exclusion) | PASSED (AC1) | `Tests run: 1, Failures: 0, Errors: 0`; `BUILD SUCCESS`; JDK 25.0.5.1 with `java.version` 21. Mockito prints a self-attach warning on JDK 25 (not an error) |
| 2026-11-24 | 2 | `git status --short` | PASSED | `target/` and `.env` not listed (ignored) |
| 2026-11-24 | 2 | Application run | NOT_VERIFIED | `contextLoads` test starts the context; the app itself was not launched (no pools yet) |
| 2026-11-24 | 3 | `.\mvnw.cmd -B -ntp clean verify` | PASSED (AC1, part of AC2) | `Tests run: 12, Failures: 0, Errors: 0`; `BUILD SUCCESS`. Binding test confirms two shards (ids 0, 1), distinct URLs starting `jdbc:postgresql://localhost:`, no unresolved `${`, pool 5 / 3000 ms. This also proves the `.env` import works on Boot 4.2.0 (D3 verified) |
| 2026-11-24 | 3 | `.\mvnw.cmd spring-boot:run` | PASSED | `Started ShardingLabApplication in 0.639 seconds` (exits right after; no pools or web server yet) |
| 2026-11-24 | 3 | `spring-boot:run` with `--sharding.shards[1].password=` (blank) | PASSED (failure case) | `APPLICATION FAILED TO START`; names `sharding.shards[1].password` and its origin. Note: Boot reports a blank value as "left unbound" before our constructor check runs; the constructor check still guards against other paths and is unit-tested |
| 2026-11-24 | 4 | `.\mvnw.cmd -B -ntp clean verify` with both shards healthy | PASSED (AC1, AC3) | `Tests run: 19, Failures: 0, Errors: 0`; `BUILD SUCCESS`. Log lines seen: `shard0-pool - Starting...`, `shard0-pool - Start completed.`, `shard1-pool - Starting...`, `shard1-pool - Start completed.` |
| 2026-11-24 | 4 | `ShardRegistryTest` unknown id; `ShardConfigTest` unreachable shard | PASSED | message names the shard and known ids; message does not contain the password |
| 2026-11-24 | 4 | Leak check: shard 0 pool closed when shard 1 fails | NOT_VERIFIED | code path exists, but no automated test (needs a reachable first shard); to be observed in Step 7 with `docker compose stop shard1` |
| 2026-11-24 | 4 | Pool size actually enforced (5) | NOT_VERIFIED | configured via `setMaximumPoolSize`; not measured |
| 2026-11-24 | 3 | `git grep` for real passwords in tracked files | NOT_VERIFIED | assistant cannot read `.env`; `application.yml` contains placeholders only (visual check) |

## Review results (append-only)
(no review rounds yet)

## Developer decisions / accepted exceptions
- 2026-11-24, D3: import `.env` into Spring via `spring.config.import` (default accepted; to be verified in Step 3).
- 2026-11-24, D4: fail fast at startup if a shard is down (default accepted).
- 2026-11-24, D5: drop the leftover `probe` table on shard 0 (default accepted).
- 2026-11-24, D6: `git init` and a Phase 1 checkpoint commit before Step 1 (default accepted). Outcome: `git init` done; the commit was declined by the developer at the permission prompt, so nothing is staged or committed. Commit later only when the developer asks.
- 2026-11-24, D5 executed: `drop table if exists probe` on shard0; verified absent.
- 2026-11-24, Step 1 (developer delegated the choices to the assistant; Spring JDBC confirmed over JPA):
  - Spring Boot **4.2.0** (GA, the default on start.spring.io as queried on 2026-11-24; not a snapshot).
  - groupId `com.shardinglab`, artifactId `sharding-lab`, base package `com.shardinglab`.
  - Pool: `maximumPoolSize` 5 per shard (one app instance, 10 of 100 default `max_connections` in use across both shards, small enough to observe exhaustion later); `connectionTimeout` 3000 ms (fails in seconds instead of Hikari's 30 s default when a shard is down).
- Environment findings (2026-11-24): `mvn` is not on PATH; the installed JDK is **25** (LTS), not 21.
- 2026-11-24, D7: keep JDK 25 and compile to Java 21 (`java.version` = 21). Accepted.
- 2026-11-24, D8: use the Maven Wrapper (`mvnw.cmd`) generated by start.spring.io; no Maven install. Accepted.
- Step 1 DONE. Next: Step 2 (Maven skeleton).

## Suggested reviewers (when /review is requested)
- `code-reviewer` (wiring, resource handling, error messages), `architecture-reviewer` (configuration shape, registry boundary, whether it supports a third shard without code changes), `security-reviewer` (credentials handling, logging). `performance-reviewer` for pool sizing and timeouts if the developer wants it. `test-reviewer` is optional until tests exist (Phase 4).
