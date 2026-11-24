package com.shardinglab.shard;

/**
 * A shard could not be reached (pool exhausted, connection refused, timeout). Carries the shard id so
 * callers can report which shard failed. The original cause stays attached for server-side logging.
 */
public class ShardUnavailableException extends RuntimeException {

    private final int shardId;

    public ShardUnavailableException(int shardId, Throwable cause) {
        super("Shard " + shardId + " is unavailable", cause);
        this.shardId = shardId;
    }

    public int shardId() {
        return shardId;
    }
}
