package com.shardinglab.web;

/** Body of PUT /users/{id}. */
public record UpdateUserRequest(String name, String email) {
}
