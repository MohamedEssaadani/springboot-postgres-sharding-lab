package com.shardinglab.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shard topology and pool settings, bound from the "sharding" section of application.yml.
 * Validation lives in the constructors so an invalid configuration stops startup immediately.
 */
@ConfigurationProperties(prefix = "sharding")
public record ShardProperties(List<Shard> shards, Pool pool) {

    public ShardProperties {
        if (shards == null || shards.isEmpty()) {
            throw new IllegalArgumentException("sharding.shards must define at least one shard");
        }
        if (pool == null) {
            throw new IllegalArgumentException("sharding.pool must be defined");
        }
        Set<Integer> seen = new HashSet<>();
        for (Shard shard : shards) {
            if (!seen.add(shard.id())) {
                throw new IllegalArgumentException("Duplicate shard id in sharding.shards: " + shard.id());
            }
        }
        shards = List.copyOf(shards);
    }

    public record Shard(int id, String url, String username, String password) {

        public Shard {
            if (id < 0) {
                throw new IllegalArgumentException("Shard id must not be negative: " + id);
            }
            requireText(id, "url", url);
            requireText(id, "username", username);
            requireText(id, "password", password);
        }

        private static void requireText(int id, String field, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Shard " + id + ": " + field + " must not be blank");
            }
        }

        /** Never include the password: this object may end up in logs. */
        @Override
        public String toString() {
            return "Shard[id=" + id + ", url=" + url + ", username=" + username + ", password=****]";
        }
    }

    public record Pool(int maximumPoolSize, long connectionTimeoutMs) {

        public Pool {
            if (maximumPoolSize <= 0) {
                throw new IllegalArgumentException("sharding.pool.maximum-pool-size must be positive");
            }
            if (connectionTimeoutMs <= 0) {
                throw new IllegalArgumentException("sharding.pool.connection-timeout-ms must be positive");
            }
        }
    }
}
