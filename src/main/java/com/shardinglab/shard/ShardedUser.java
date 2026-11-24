package com.shardinglab.shard;

import com.shardinglab.model.User;

/** A user together with the id of the shard that served the operation. */
public record ShardedUser(int shardId, User user) {
}
