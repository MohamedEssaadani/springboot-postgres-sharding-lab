package com.shardinglab.web;

import java.time.Instant;

import com.shardinglab.shard.ShardedUser;

/** A user plus the shard that served the request (a learning aid, not a final API contract). */
public record UserResponse(long id, String name, String email, Instant createdAt, int shardId) {

    public static UserResponse from(ShardedUser sharded) {
        return new UserResponse(
                sharded.user().id(),
                sharded.user().name(),
                sharded.user().email(),
                sharded.user().createdAt(),
                sharded.shardId());
    }
}
