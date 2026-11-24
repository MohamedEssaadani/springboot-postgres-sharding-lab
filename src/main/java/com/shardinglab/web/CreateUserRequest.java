package com.shardinglab.web;

/** Body of POST /users. The id is the sharding key and is supplied by the caller (it decides the shard). */
public record CreateUserRequest(Long id, String name, String email) {
}
