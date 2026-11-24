# Plan 003: Phase 3 - Traditional sharding (router, insert/select routing, CRUD endpoints)

- **Status:** IN_PROGRESS
- **Approved scope:** Steps 1-7 approved by developer on 2026-11-24, each step still run and confirmed separately; developer writes the code, assistant generates code only on explicit request
- **Owner / decision-maker:** Developer
- **Created:** 2026-11-24

## Problem and desired outcome
Phases 1 and 2 gave us two shards, two pools and an identical `users` table on each, but the app never reads or writes a user. We need the app to decide, from the user id alone, which shard owns a user, then insert, read, update and delete that user on that shard only. Outcome: REST CRUD endpoints that store each user on exactly one shard, chosen by `hash(userId)` modulo the number of shards, and that show which shard served each request.

## Concept and why
**The sharding key.** Every sharded system needs one value that decides where a row lives. Here it is `userId`. The key must be known on every read and write, otherwise the app cannot find the row without asking every shard. That is why `GET /users/{id}` is cheap (one shard) while "list all users" is not (every shard); the latter is out of scope and is a Phase 7 topic.

**Routing function.** `shardIndex = floorMod(hash(userId), shardCount)`. Two properties are non-negotiable: it must be **deterministic** (same id, same shard, across restarts and JVMs, so no `Object.hashCode()`), and it must never be **negative** (`Math.abs(Integer.MIN_VALUE)` is still negative, so use `Math.floorMod`). `Long.hashCode(id)` is defined by the Java specification, so it is stable.

**Why modulo, and why we will break it.** Modulo is the simplest way to spread keys. Its weakness appears in Phase 5: changing the shard count changes the result for most keys, so most users must move. We implement it now so we can measure that weakness later and then replace it (Phase 6).

**Single-shard operations stay atomic.** Insert, read, update and delete each touch one shard, so PostgreSQL's normal row-level guarantees apply: concurrent inserts of the same id cannot both succeed (primary key), and there is no cross-shard transaction to worry about. Anything touching two shards (list all, joins, moving a user) would not be atomic; we deliberately do not build those yet.

## Learning outcomes
- [ ] LO1: Explain what the sharding key is and why every read and write must carry it.
- [ ] LO2: Explain why the routing hash must be deterministic and non-negative, and show where in the code that is guaranteed.
- [ ] LO3: Demonstrate with real data that each user exists on exactly one shard and that the shard equals the router's answer.
- [ ] LO4: Explain why a request for a user on a healthy shard still works when the other shard is down, and what the caller of the dead shard sees.
- [ ] LO5: Explain why "list all users" is not offered, and what it would cost.

## Scope
**In:**
- `ShardRouter`: id to shard id, pure logic, no database.
- `UserRepository`: insert, find by id, update, delete, each routed through the router and the existing `ShardRegistry`; parameterized SQL only.
- REST endpoints: `POST /users`, `GET /users/{id}`, `PUT /users/{id}`, `DELETE /users/{id}`.
- Response that includes the shard id that served the request (for learning and verification).
- Consistent error responses: 400 invalid input, 404 not found, 409 duplicate id, 503 shard unavailable.
- Unit tests for the router; controller tests with a mocked repository.
- Manual end-to-end verification against the two running shards.

**Out:**
- Listing/searching all users, cross-shard queries and joins (Phase 7).
- Server-generated ids (Phase 7); ids are supplied by the caller in Phase 3.
- Real-database automated tests and Testcontainers (Phase 4).
- Changing the number of shards, consistent hashing (Phases 5 and 6).
- Authentication, pagination, README (ask before README).

## Requirements and acceptance criteria
- [ ] AC1: `ShardRouter` returns the same shard for the same id on repeated calls and across separate JVM runs (hash is `Long.hashCode`-based, not identity-based), always a valid configured shard id, for ids including negative values, 0, `Long.MIN_VALUE` and `Long.MAX_VALUE`.
- [ ] AC2: The router works with shard ids that are not `0..N-1` (it maps through the sorted configured ids) and with a single shard.
- [ ] AC3: `POST /users` with a new id stores the user on exactly the shard the router chose and returns 201 with the shard id; the same id on the other shard does not exist (verified with `psql` on both shards).
- [ ] AC4: `GET /users/{id}` returns the stored user with the same shard id; an unknown id returns 404; a non-numeric id returns 400.
- [ ] AC5: `PUT` changes name/email on the owning shard only; `DELETE` removes the row from the owning shard only (both verified with `psql`); both return 404 for an unknown id.
- [ ] AC6: `POST` of an existing id returns 409 and leaves the stored row unchanged.
- [ ] AC7: 20 or more distinct users inserted through the API are distributed across both shards (counts per shard recorded; both shards non-empty) and every one is retrievable through the API.
- [ ] AC8: Data persists across an application restart (rows still readable).
- [ ] AC9: With one shard stopped *after* the app is running, requests for users owned by the live shard succeed, and requests for users owned by the stopped shard return 503 naming the shard, with no stack trace or credentials in the body. After the shard is started again, requests for it succeed again (record how long recovery takes).
- [ ] AC10: No SQL is built by concatenating request data; no password appears in responses or logs; `.\mvnw.cmd clean verify` passes (shards running, as today).

## Relevant context and constraints
- Phase 2 is implemented: `ShardRegistry` (`forShard(id)`, `shardIds()`, `poolName(id)`), `ShardConfig`, `ShardProperties`, `User` record (`id, name, email, createdAt`), `ShardSchemaInitializer`, `ShardConnectivityCheck`, schema `users(id BIGINT PK, name, email, created_at)`. Plans 001 and 002 steps are done; the Step 8 review of plan 002 was skipped for now by the developer and remains open.
- Nothing is committed yet; Git was initialised but the first commit was declined earlier.
- The app currently has no web server and exits after startup. Adding the web starter keeps it running, so runners will execute at startup and the process stays alive.
- The `users` table has no `UNIQUE` on email, so the same email can exist on different shards. That is accepted (Phase 7).
- Boot 4.2.0 renamed some starter artifacts; the correct web starter name and its test starter must be confirmed on start.spring.io before editing `pom.xml`.
- Sharding invariants in `AGENTS.md` apply, notably deterministic routing, `floorMod`, parameterized SQL, no silent fallback to another shard when one is down.

## Proposed approach and alternatives
**Hash:** `Long.hashCode(userId)` then `Math.floorMod(hash, shardCount)`, then map the index through the sorted list of configured shard ids. Alternative: use `userId % N` directly. That is not really hashing and would make the Phase 5 comparison trivially predictable. A stronger hash (MD5/Murmur) is not needed for modulo; it will be needed for ring positions in Phase 6. Note for the learner: with sequential ids `Long.hashCode(i) == i`, so modulo 2 alternates perfectly and hides skew; the Phase 4 "100 users" test should also use non-sequential ids.

**Shape:** controller, repository (router inside it or injected), registry. Alternative: add a service layer between controller and repository. Recommended: skip it until there is logic to put there (cross-shard work in Phase 7); `AGENTS.md` lists a service layer, so update it if the developer agrees.

**Routing location:** `ShardRouter` is a separate small class that only maps id to shard id (easy to unit test, easy to replace with the ring in Phase 6). The repository asks the router, then the registry, so no controller ever sees a shard.

**Errors:** a `@RestControllerAdvice` maps `DuplicateKeyException` to 409, unknown row to 404, `IllegalArgumentException` from `User` validation to 400, and connection failures to 503. Alternative: handle errors in each controller method (duplicated and easy to miss).

**IDs:** supplied by the caller in the request body. Server-side generation needs a cross-shard unique id strategy (Phase 7).

**Tests:** unit tests for the router (cheap, strong). MockMvc/controller tests with a mocked repository for status codes. Repository-against-real-PostgreSQL tests wait for Testcontainers in Phase 4, so Phase 3 validates the real database path manually.

## Implementation steps
Owner is Developer for every step. Each step is approved separately.

- [x] Step 1 (Owner: Developer): **Decisions.** Confirm D1-D6 below. Result: no open choices before coding.
- [x] Step 2 (Owner: Developer; generated at developer's explicit request "implement this step 2 from phase 3"): **`ShardRouter`.** Method like `int shardFor(long userId)` returning a configured shard id, built from `ShardRegistry.shardIds()` (sorted). Requirements: deterministic, non-negative, works for any id value, works with non-contiguous ids and one shard, no database access. Unit tests for AC1 and AC2, including a distribution check over many ids (tolerance, not exact). Expected files: `shard/ShardRouter.java` and its test. Result: AC1, AC2.
- [x] Step 3 (Owner: Developer; generated at developer's explicit request "implement step 3"): **`UserRepository`.** Insert, find by id, update, delete, each resolving the shard through the router and running parameterized SQL on that shard's `JdbcTemplate`. Requirements: return the shard id alongside results (decide how), distinguish "not found" from "duplicate", never catch and hide connection failures, no string-built SQL. Expected files: `shard/UserRepository.java` or `model` package placement (developer decides). Result: groundwork for AC3-AC6.
- [x] Step 4 (Owner: Developer; generated at developer's explicit request "Yes, generate Step 4"): **Web layer.** Add the web starter (and its test starter) after confirming artifact names; request/response types; controller with the four endpoints; `@RestControllerAdvice` for 400/404/409/503. Requirements: 201 with `Location`, bodies never include credentials or stack traces, 503 names the shard only. Controller tests with a mocked repository. Expected files: `pom.xml`, controller, DTOs, advice, tests. Result: AC4-AC6 status codes. Adding dependencies needs the developer's approval.
- [x] Step 5 (Owner: Developer; checks run by the assistant at the developer's request "Start Step 5"): **End-to-end check.** Start the app and, with `Invoke-RestMethod` or `curl.exe`, POST 20+ users (use ids that are not just 1..20), GET each, PUT, DELETE, a duplicate POST, an unknown id, a bad id. Then compare with `psql` on both shards (counts and which ids are where). Restart the app and read again. Result: AC3-AC8, evidence recorded in this plan.
- [x] Step 6 (Owner: Developer; checks run by the assistant at the developer's request "Start Step 6"): **Failure isolation.** With the app running, `docker compose stop shard1`; request a user on shard 0 (succeeds) and a user on shard 1 (503); start shard 1; measure when requests succeed again. Result: AC9 and LO4. Use `stop`/`start`, not a reboot.
- [ ] Step 7 (Owner: Developer): **Review and checkpoint.** Developer requests `/review` (covering plans 001-003 if wanted) and decides on a commit. Assistant explains findings; no auto-fix, no auto-commit.

## Test and validation strategy
Evidence recorded in "Validation results" with real output:
- `.\mvnw.cmd clean verify` (router unit tests, controller tests, existing tests; shards running because `contextLoads` needs them).
- Per-shard row checks: `select id from users order by id` on `shard0` and `shard1` through `docker compose exec`, compared with the ids the router should choose.
- API calls with status codes and bodies for each endpoint and error case.
- Restart persistence, and the shard-down experiment.
- Search of logs and response bodies for password strings.
Anything not executed is `NOT_VERIFIED`. Automated real-database repository tests are Phase 4.

## Risks, assumptions, and open decisions
- Risk: Boot 4 starter names differ from older tutorials. Mitigation: confirm on start.spring.io metadata before editing `pom.xml`.
- Risk: sequential ids hide distribution problems. Mitigation: also use scattered ids in Step 5 and in Phase 4 tests.
- Risk: after a shard outage Hikari may take time to hand out healthy connections again. Mitigation: measure it in Step 6; it is the connection timeout and validation settings we already chose.
- Risk: the web server makes the app a long-running process; the `contextLoads` test starts a mock web environment. Mitigation: confirm tests still pass after adding the starter.
- Risk: the app's JSON shape (shard id in the response) becomes a contract. Mitigation: it is a learning aid; mark it as non-final.
- Assumption: ids are non-negative in practice, but the router must still handle any `long`.
- Assumption: shard count equals the number of configured shards; changing it is Phase 5.
- Open decision D1: routing hash `Long.hashCode` + `floorMod` (recommended) or something stronger now.
- Open decision D2: ids supplied by the caller in the request body (recommended; generation is Phase 7).
- Open decision D3: skip a separate service layer for now (recommended) or keep controller, service, repository as `AGENTS.md` lists.
- Open decision D4: include the shard id in API responses (recommended, for verification).
- Open decision D5: reuse the `User` record's validation and map `IllegalArgumentException` to 400 (recommended, no extra dependency) or add the validation starter.
- Open decision D6: make a first commit of Phases 1 and 2 before starting (recommended, since a lot is uncommitted).

## Implementation progress
- 2026-11-24, D6 executed: three commits on `master` (`9c4f6af` harness, `d771617` Phase 1 infrastructure, `366518b` Phase 2 app plus plans). `.env` and `target/` confirmed ignored before committing. Git reported LF to CRLF line-ending warnings only. This record edit is not committed.
- Step 1 (decisions) done.
- 2026-11-24, Step 2: added `shard/ShardRouter.java` (`@Component`; `shardFor(long userId)` = `shardIds.get(Math.floorMod(Long.hashCode(userId), count))` over the sorted, distinct configured shard ids; constructor from `ShardRegistry` for Spring and from a `Collection<Integer>` for tests; rejects null, empty or duplicate ids; `shardCount()`) and `ShardRouterTest` (8 tests, no database). Pure logic, no state, immutable, no database access.

- 2026-11-24, Step 3: added `shard/UserRepository.java` (`@Repository`; constructor takes `ShardRegistry` and `ShardRouter`; `insert(User)` returns `ShardedUser`, `findById(long)` and `update(long, name, email)` return `Optional<ShardedUser>`, `delete(long)` returns boolean; one statement per call on the router's shard; SQL uses `?` parameters, `RETURNING` reads back `created_at`; `update` validates input through `User.newUser` before touching any shard; duplicate id surfaces as `DuplicateKeyException`, connection failures propagate and are never retried on another shard), `shard/ShardedUser.java` (record `shardId`, `user`), and `UserRepositoryTest` (9 tests using fake JdbcTemplates, no database).
- 2026-11-24, Step 4: `pom.xml` gained `spring-boot-starter-webmvc` and `spring-boot-starter-webmvc-test` (approved in the plan). New `web` package: `UserController` (POST 201 + `Location`, GET, PUT, DELETE 204; no shard or SQL logic), `CreateUserRequest`, `UpdateUserRequest`, `UserResponse` (includes `shardId`), `ErrorResponse`, `UserNotFoundException`, `ApiExceptionHandler` (400 for `IllegalArgumentException`, bad path value and unreadable body; 404; 409 for `DuplicateKeyException`; 503 naming only the shard; generic 500; causes logged server-side only). `UserControllerTest` (14 tests, `@WebMvcTest` with a mocked `UserRepository`).
- **Deviation from Step 3:** to let a 503 name the shard, `UserRepository` now wraps `DataAccessResourceFailureException` (includes `CannotGetJdbcConnectionException`) in new `shard/ShardUnavailableException(shardId, cause)`. The failure is still explicit and never falls back to another shard. `UserRepositoryTest` updated (connection-failure test now expects `ShardUnavailableException` with `shardId()==1`; added a test covering insert/update/delete).
- 2026-11-24, Step 5: ran the app (`spring-boot:run`), exercised the API with `curl.exe`, compared with `psql` on both shards, restarted the app and re-read. A first POST attempt returned 400 because Windows PowerShell 5.1 strips quotes in native-command arguments; bodies were then sent through stdin (`--data-binary @-`). Side effect of that first attempt: one request with escaped quotes created id 5, which was deleted and re-created cleanly. App stopped by pid from port 8080 and restarted. The app is currently left running on port 8080.
- 2026-11-24, Step 6: with the app running, `docker compose stop shard1`, requests, `docker compose start shard1`, polled recovery every 500 ms. Temporary row id 1002 (shard0) was created during the outage and deleted afterwards (204).

## Validation results
| Date | Step | Command/check | Result | Evidence / limitation |
| --- | --- | --- | --- | --- |
| 2026-11-24 | 6 | shard1 stopped: GET ids 4, 88 (shard0) | PASSED (AC9) | 200 in 33 ms and 26 ms |
| 2026-11-24 | 6 | shard1 stopped: GET ids 5, 987654321012 (shard1) | PASSED (AC9) | 503 `Shard 1 is unavailable`, about 3.0 s each (the 3000 ms Hikari connection timeout); no fallback to shard0 |
| 2026-11-24 | 6 | shard1 stopped: POST id 1001 (shard1) and id 1002 (shard0) | PASSED | 1001 returned 503; 1002 returned 201 on shard0. After recovery GET 1001 is 404, so the failed write left nothing behind on any shard |
| 2026-11-24 | 6 | `docker compose start shard1`, poll GET 5 | PASSED (LO4) | first 200 about 1.1 s after the start command was issued (0 failed polls before it); data intact, PUT change visible. Single measurement, no app restart needed |
| 2026-11-24 | 6 | Limitation | NOTE | recovery time measured once with a clean container stop; a crash, network partition or slow DB could behave differently |
| 2026-11-24 | 5 | POST 22 users (scattered ids incl. 987654321012 and 6000000007), GET each | PASSED (AC3, AC4) | all POST 201; GET returned the same user |
| 2026-11-24 | 5 | `psql` row ids per shard after deleting id 42 | PASSED | shard0: 8 rows `2,4,8,64,88,250,3141592,6000000007`; shard1: 13 rows `5,9,13,17,31,55,71,77,99999,123457,1000003,1234567,987654321012`; each id appears on exactly one shard (21 total) |
| 2026-11-24 | 5 | Recomputed `floorMod(Long.hashCode(id), 2)` independently in PowerShell for all 21 ids | PASSED | 0 mismatches between expected and actual shard |
| 2026-11-24 | 5 | PUT 5 (200, updated), DELETE 42 (204), GET 42 (404), duplicate POST 5 (409), unknown 777 (404), `/users/abc` (400), blank name (400), missing id covered at unit level | PASSED (AC5, AC6) | bodies contain only status/error/message |
| 2026-11-24 | 5 | App restarted, GET ids 4, 5, 987654321012, 6000000007 (same shards, PUT change persisted), 42 still 404 | PASSED | routing and data survive a JVM restart |
| 2026-11-24 | 5 | Search of both app logs for "password" or "secret" | PASSED | 0 matching lines. `.env` was not read, so the actual password values were not searched for |
| 2026-11-24 | 5 | Distribution observation | NOTE | 8 vs 13 rows on 21 ids is a small, non-uniform sample (many of my ids are odd); not evidence of skew. The 100-user distribution check is Phase 4 |
| 2026-11-24 | 4 | `.\mvnw.cmd -B -ntp clean verify`, both shards healthy | PASSED | `Tests run: 59, Failures: 0, Errors: 0`; `BUILD SUCCESS`; `UserControllerTest` 14/14, `UserRepositoryTest` 10/10, `contextLoads` still passes with the web starter |
| 2026-11-24 | 4 | Status codes: 201+Location, 400 (missing id, blank name, malformed body, non-numeric id), 404 (GET/PUT/DELETE), 409, 503, 500; 409/503/500 bodies contain no SQL, JDBC URL, secret or exception text | PASSED | controller slice with mocked repository only; not yet against real HTTP and PostgreSQL (Step 5) |
| 2026-11-24 | 3 | `.\mvnw.cmd -B -ntp clean verify`, both shards healthy | PASSED | `Tests run: 44, Failures: 0, Errors: 0`; `BUILD SUCCESS`; `UserRepositoryTest` 9/9 |
| 2026-11-24 | 3 | Routing in `UserRepositoryTest` (id 4 on shard 0, id 5 on shard 1): only the owning fake JdbcTemplate is called, args are bound parameters, SQL text does not contain the values | PASSED | unit level only; uses fakes, not PostgreSQL |
| 2026-11-24 | 3 | Connection failure on the owning shard propagates; the other shard is not queried (no fallback) | PASSED | unit level (fake throws `CannotGetJdbcConnectionException`) |
| 2026-11-24 | 3 | The four SQL statements run against a real shard via `psql` (test id 987654321012 on shard0: INSERT/UPDATE with RETURNING, SELECT, DELETE; duplicate insert) | PASSED | RETURNING returned the row with `created_at`; `UPDATE 1`, `DELETE 1`; duplicate raised `users_pkey` unique violation; test rows deleted (`remaining 0`). This checks SQL syntax only, not the repository class |
| 2026-11-24 | 3 | Repository class against real PostgreSQL (row mapping of `created_at`, translation of the unique violation to `DuplicateKeyException`) | NOT_VERIFIED | first exercised end to end in Step 5; automated real-database tests are Phase 4 |
| 2026-11-24 | 2 | `.\mvnw.cmd -B -ntp clean verify`, both shards healthy | PASSED (AC1, AC2) | `Tests run: 35, Failures: 0, Errors: 0`; `BUILD SUCCESS`; `ShardRouterTest` 8/8. `contextLoads` creates the `ShardRouter` bean from the real registry |
| 2026-11-24 | 2 | Fixed expectations (id 4 to shard 0, 5 to 1, -1 to 0; with three shards 10 to 1, 11 to 2, 12 to 0; non-contiguous `{3, 7}`) | PASSED | values worked out by hand before running |
| 2026-11-24 | 2 | Extreme ids (`Long.MIN_VALUE`, `Long.MAX_VALUE`, 0, negatives) stay within configured shards | PASSED | for 2 and 3 shards |
| 2026-11-24 | 2 | Distribution of 10,000 random ids over 2 shards | PASSED | share on shard 0 within 45%-55% (fixed seed 42) |
| 2026-11-24 | 2 | Determinism across separate JVM runs | NOT_VERIFIED | relies on `Long.hashCode` being specified by Java; the fixed-value tests would detect a change between runs of the build, but no cross-process test exists |

## Review results (append-only)
(no review rounds yet)

## Developer decisions / accepted exceptions
- 2026-11-24, D1: routing hash is `Long.hashCode(userId)` then `Math.floorMod(hash, shardCount)`, mapped through the sorted configured shard ids (defaults accepted).
- 2026-11-24, D2: ids are supplied by the caller in the request body (defaults accepted).
- 2026-11-24, D3: no separate service layer for now; `AGENTS.md` to be updated when the controller/repository code exists (defaults accepted).
- 2026-11-24, D4: API responses include the shard id (defaults accepted).
- 2026-11-24, D5: reuse the `User` record validation and map `IllegalArgumentException` to 400; no validation starter (defaults accepted).
- 2026-11-24, D6: commit Phases 1 and 2 before starting (defaults accepted); outcome recorded in Implementation progress.
- Step 1 (decisions) DONE.

## Suggested reviewers (when /review is requested)
- `code-reviewer` (router correctness, SQL, error mapping), `architecture-reviewer` (router/registry/repository boundaries, readiness for Phase 5-6), `security-reviewer` (input handling, SQL, error bodies), `test-reviewer` (router and controller coverage, what is deferred to Phase 4). `performance-reviewer` is optional until there is a workload.
