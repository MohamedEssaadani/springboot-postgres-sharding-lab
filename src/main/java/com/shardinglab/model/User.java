package com.shardinglab.model;

import java.time.Instant;

/**
 * A user row. {@code id} is the sharding key and is supplied by the caller.
 * {@code createdAt} is filled by the database, so it is null on a user that has not been stored yet.
 */
public record User(long id, String name, String email, Instant createdAt) {

    public User {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
    }

    /** A user that has not been stored yet (no creation time). */
    public static User newUser(long id, String name, String email) {
        return new User(id, name, email, null);
    }
}
