# Plan 001: Phase 1 - Infrastructure (two PostgreSQL shards)

- **Status:** IN_PROGRESS
- **Approved scope:** Steps 1-7 approved by developer on 2026-11-24 (developer writes the code; assistant generates code only on explicit request)
- **Owner / decision-maker:** Developer
- **Created:** 2026-11-24

## Problem and desired outcome
There is no infrastructure yet. We need two independent PostgreSQL instances that will act as shard 0 and shard 1, reproducible with one command, so later phases can route data between them. Outcome: `docker compose up -d` brings up both shards healthy, each reachable on its own port with its own credentials and data volume.

## Concept and why
Sharding splits one logical dataset across several **independent** database servers. Independence is the point: each shard has its own process, storage, credentials, and failure domain. Two schemas inside one PostgreSQL server would share CPU, memory, WAL, and crash fate, so they would not teach real sharding. Docker Compose lets us run two real servers locally. Named volumes give each shard its own durable storage (data survives container restarts). Health checks (`pg_isready`) tell us when a shard can accept connections, which later lets the app and tests wait for readiness instead of guessing with sleeps.

## Learning outcomes
- [ ] LO1: Explain why shards are separate instances rather than separate schemas in one server.
- [ ] LO2: Explain what a named volume does and what `docker compose down` vs `down -v` does to shard data.
- [ ] LO3: Explain what a health check proves and what it does not (accepting connections, not data correctness).
- [ ] LO4: Demonstrate that a row written to shard 0 does not exist on shard 1.

## Scope
**In:**
- `docker-compose.yml` with two PostgreSQL services (`shard0`, `shard1`).
- Separate database name, user, and password per shard.
- One named volume per shard.
- `pg_isready` health check per shard.
- Host ports bound to localhost only, distinct per shard.
- Manual connectivity and isolation verification with `psql` inside the containers.
- `.gitignore` entry for local secrets file, if credentials go in `.env`.

**Out:**
- Spring Boot, Maven, DataSources, schema/User table (Phase 2).
- Any application code, init SQL beyond what PostgreSQL does by default.
- Third shard (Phase 5), replication, monitoring, migrations (Phase 7).
- README (written after we have something to document; ask first).

## Requirements and acceptance criteria
- [ ] AC1: `docker compose up -d` starts exactly two PostgreSQL containers, `shard0` and `shard1`.
- [ ] AC2: `docker compose ps` shows both as `healthy` (not just `running`).
- [ ] AC3: Each shard has its own database name, user, password, host port, and named volume (no shared values).
- [ ] AC4: Each shard accepts a `psql` connection with its own credentials on its own host port, and rejects the other shard's credentials.
- [ ] AC5: A test table/row created on shard 0 is absent on shard 1.
- [ ] AC6: After `docker compose down` then `up -d`, the row on shard 0 still exists (volume persistence).
- [ ] AC7: Image tag is pinned (not `latest`); ports bind to `127.0.0.1`; no real secrets are committed.

## Relevant context and constraints
- Repository currently holds only the harness files; not a Git repository.
- Windows 11 / PowerShell; Docker Desktop must be running.
- Sharding invariants in `AGENTS.md` apply: credentials from configuration, never hardcoded in application source.
- `.env` files are read-denied for the assistant by `.claude/settings.json`; the developer creates and reads them.
- Default PostgreSQL port 5432 may already be used by a local install, so shards use other host ports.

## Implementation steps
Owner is Developer for every step. The assistant explains and reviews; it writes code only on explicit request.

- [x] Step 1 (Owner: Developer): **Decide conventions.** Choose image tag (suggest a current PostgreSQL major, pinned), host ports (suggest 5433 and 5434), database/user names (e.g. `shard0_db`/`shard0_user`), and where credentials live. Requirement: record the choices in "Developer decisions" below. Result: no ambiguity before writing YAML.
- [x] Step 2 (Owner: Developer): **Write `docker-compose.yml`** at the repo root with two services. Requirements per service: pinned image, `POSTGRES_DB`/`POSTGRES_USER`/`POSTGRES_PASSWORD` from env, port mapping `127.0.0.1:<hostport>:5432`, a named volume mounted at `/var/lib/postgresql/data`, `healthcheck` using `pg_isready -U <user> -d <db>` with interval, timeout, retries, and `restart` policy chosen deliberately. Top-level `volumes:` declares both. Result: file validates with `docker compose config`.
- [x] Step 3 (Owner: Developer): **Credentials file.** If using `.env`: create `.env` (local, ignored) and `.env.example` (committed, placeholder values); add `.env` to `.gitignore`. Result: compose reads variables; no real secret in tracked files.
- [x] Step 4 (Owner: Developer): **Start and verify health.** Run `docker compose up -d`, then `docker compose ps`. Result: both `healthy` (AC1, AC2).
- [x] Step 5 (Owner: Developer): **Verify connectivity and independence.** Using `docker compose exec <service> psql ...`: connect to each shard, confirm `current_database()` and `current_user`, create a table and insert a row on shard 0 only, confirm it is absent on shard 1, and confirm cross-credential login fails (AC3-AC5).
- [x] Step 6 (Owner: Developer): **Verify persistence.** `docker compose down` (no `-v`), `up -d`, confirm the shard 0 row survives (AC6). Do not run `down -v` unless you intend to wipe data.
- [ ] Step 7 (Owner: Developer): **Review.** Developer asks for `/review` on the compose file (and `.env.example`/`.gitignore`). Assistant explains findings; no auto-fix.

## Test and validation strategy
Manual runtime checks (PowerShell), recorded in "Validation results" with real output:
- `docker compose config` (syntax and variable resolution).
- `docker compose up -d`, `docker compose ps` (health state).
- `docker compose exec shard0 psql -U <user0> -d <db0> -c "select current_database(), current_user;"` and the equivalent for shard 1.
- Create/insert on shard 0; `select` on shard 1 shows the table is missing.
- Wrong-credential attempt fails.
- `docker compose down` then `up -d`; row still present.
Automated tests (JUnit/Testcontainers) are Phase 4, not here. Anything not executed is recorded as `NOT_VERIFIED`.

## Risks, assumptions, and open decisions
- Assumption: Docker Desktop is installed and running. If not, steps 4-6 are `BLOCKED`.
- Risk: host port conflict with a local PostgreSQL. Mitigation: use 5433/5434, check with `docker compose ps` and logs.
- Risk: `docker compose down -v` or prune deletes shard data. Mitigation: permission rules ask first; document in README later.
- Risk: changing `POSTGRES_*` after first start has no effect because the volume is already initialized. Mitigation: explained in the concept session; reset only with approved `down -v`.
- Risk: health check passes before the target database exists if `-d` is omitted. Mitigation: include `-d` in `pg_isready`.
- Open decision D1: credentials via `.env` + `.env.example` (recommended, matches the "no secrets in committed files" invariant) or inline in compose (simpler, throwaway lab values).
- Open decision D2: PostgreSQL image tag (suggest latest stable major, pinned).
- Open decision D3: `restart` policy (`unless-stopped` vs `no`).
- Open decision D4: ask whether to run `git init` before the first commit checkpoint.

## Implementation progress
- 2026-11-24, Steps 1-2 (generated at developer's request, "Generate, then explain"): conventions chosen (shard0/shard1, ports 5433/5434, `shardN_db`/`shardN_user`, volumes `shardN_data`, `SHARDN_*` env vars). Created `docker-compose.yml` and `.gitignore`.
- 2026-11-24, Step 3: `.claude/settings.json` denies the assistant from writing `.env.example` and creating `.env`. Developer created both by hand; compose resolved them. DONE.
- 2026-11-24, Steps 4-6: stack started, verified healthy, isolation and persistence checked (see Validation results). DONE.
- Step 7 (review) not requested yet.

## Validation results
| Date | Step | Command/check | Result | Evidence / limitation |
| --- | --- | --- | --- | --- |
| 2026-11-24 | Env | `docker version --format '{{.Server.Version}}'` | PASSED | Docker server 29.9.2 reachable |
| 2026-11-24 | 2/3 | `docker compose config --quiet` | PASSED | exit 0 after developer created `.env` (assistant cannot read `.env` by design; contents not inspected) |
| 2026-11-24 | 4 | `docker compose up -d`, then `docker inspect` health | PASSED (AC1, AC2) | shard0 and shard1 `healthy`; ports `127.0.0.1:5433->5432` and `127.0.0.1:5434->5432` |
| 2026-11-24 | 5 | `select current_database(), current_user` on each shard | PASSED (AC3, AC4) | `shard0_db shard0_user` and `shard1_db shard1_user` |
| 2026-11-24 | 5 | create table `probe` + insert on shard0; `to_regclass('public.probe')` on both | PASSED (AC5) | shard0 `t`, shard1 `f` |
| 2026-11-24 | 5 | wrong password to shard0 as `shard0_user`; `shard1_user` with guessed password on shard0 | PASSED (AC4) | both `password authentication failed`, exit 2. Limitation: did not test with shard1's real password (not readable by assistant) |
| 2026-11-24 | 6 | `docker compose down` (no `-v`), `up -d`, select from `probe` | PASSED (AC6) | row `1|only on shard0` survived; both shards healthy again |
| 2026-11-24 | AC7 | inspected compose file | PASSED | `postgres:17`, ports bound to `127.0.0.1`, no secrets in tracked files. `.env.example` not inspected by assistant (read-denied) |

Test data left behind: table `probe` on shard0 (to be dropped or left at developer's choice).

## Review results (append-only)
(no review rounds yet)

## Developer decisions / accepted exceptions
- 2026-11-24, D1: credentials live in `.env` (ignored by Git) with a committed `.env.example`.
- 2026-11-24, D2: pin image to PostgreSQL 17 (`postgres:17`), not `latest` (keeps AC7).
- 2026-11-24, D4: developer agreed to `git init` before the first commit checkpoint (not yet run).
- 2026-11-24, D3: `restart: unless-stopped`. Caveat: in Phase 4 failure tests, stop a shard explicitly (`docker compose stop shard1`), which Docker will not auto-restart, and avoid rebooting mid-test.

## Suggested reviewers (when /review is requested)
- `code-reviewer` (compose correctness) and `security-reviewer` (credentials, port exposure, image pinning). Test, architecture, and performance reviewers are not needed for this step.
