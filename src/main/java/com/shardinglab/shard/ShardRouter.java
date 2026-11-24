package com.shardinglab.shard;

import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Decides which shard owns a user: {@code floorMod(Long.hashCode(userId), shardCount)}, mapped
 * through the sorted list of configured shard ids (so ids need not be 0..N-1).
 *
 * <p>Pure logic, no database. Two properties matter:
 * <ul>
 *   <li><b>Deterministic:</b> {@code Long.hashCode} is defined by the Java specification, so the same
 *       id maps to the same shard in every JVM run. Never use {@code Object.hashCode()} here.</li>
 *   <li><b>Never negative:</b> {@code %} can return negative values, and {@code Math.abs(Integer.MIN_VALUE)}
 *       is still negative; {@code Math.floorMod} always returns 0..N-1.</li>
 * </ul>
 * Changing the number of shards changes the result for most ids (Phase 5 measures this).
 */
@Component
public class ShardRouter {

    private final List<Integer> shardIds; // sorted, distinct, immutable

    @Autowired
    public ShardRouter(ShardRegistry registry) {
        this(registry.shardIds());
    }

    public ShardRouter(Collection<Integer> shardIds) {
        if (shardIds == null || shardIds.isEmpty()) {
            throw new IllegalArgumentException("At least one shard id is required");
        }
        TreeSet<Integer> sorted = new TreeSet<>(shardIds);
        if (sorted.size() != shardIds.size()) {
            throw new IllegalArgumentException("Duplicate shard ids: " + shardIds);
        }
        this.shardIds = List.copyOf(sorted);
    }

    /** Returns the id of the shard that owns {@code userId}. Always one of the configured shard ids. */
    public int shardFor(long userId) {
        int index = Math.floorMod(Long.hashCode(userId), shardIds.size());
        return shardIds.get(index);
    }

    public int shardCount() {
        return shardIds.size();
    }
}
