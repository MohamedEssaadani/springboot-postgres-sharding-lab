package com.shardinglab.shard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Holds one connection pool and one JdbcTemplate per shard, keyed by shard id.
 * Immutable after construction. There is deliberately no "default shard": asking for an
 * unknown id is a bug in the caller and fails loudly.
 */
public class ShardRegistry implements AutoCloseable {

    private final Map<Integer, HikariDataSource> dataSources;
    private final Map<Integer, JdbcTemplate> templates;

    public ShardRegistry(Map<Integer, HikariDataSource> dataSources) {
        if (dataSources == null || dataSources.isEmpty()) {
            throw new IllegalArgumentException("At least one shard pool is required");
        }
        this.dataSources = Map.copyOf(dataSources);
        Map<Integer, JdbcTemplate> built = new LinkedHashMap<>();
        dataSources.forEach((id, ds) -> built.put(id, new JdbcTemplate(ds)));
        this.templates = Collections.unmodifiableMap(built);
    }

    public JdbcTemplate forShard(int shardId) {
        JdbcTemplate template = templates.get(shardId);
        if (template == null) {
            throw new IllegalArgumentException(
                    "Unknown shard id " + shardId + ". Known shard ids: " + shardIds());
        }
        return template;
    }

    /** Name of the connection pool serving this shard, e.g. "shard0-pool" (useful for logs). */
    public String poolName(int shardId) {
        forShard(shardId); // fails loudly for an unknown id
        return dataSources.get(shardId).getPoolName();
    }

    public Set<Integer> shardIds() {
        return Collections.unmodifiableSet(new TreeSet<>(templates.keySet()));
    }

    /** Closes every pool, even if closing one of them fails. */
    @Override
    public void close() {
        RuntimeException failure = null;
        for (HikariDataSource ds : dataSources.values()) {
            try {
                ds.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
