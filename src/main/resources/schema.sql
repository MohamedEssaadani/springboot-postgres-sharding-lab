-- Applied to every shard at startup. Must stay idempotent (safe to run on each start).
-- id is supplied by the application (it is the sharding key): a per-shard sequence
-- would hand out the same id on different shards.
-- No UNIQUE on email: it would only be enforced within a single shard.
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
