package com.shardinglab.shard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

/**
 * No database needed: an unconfigured HikariDataSource does not connect until first use.
 */
class ShardRegistryTest {

    private static ShardRegistry registryOf(HikariDataSource zero, HikariDataSource one) {
        return new ShardRegistry(Map.of(0, zero, 1, one));
    }

    @Test
    void returnsDistinctTemplatesPerShard() {
        try (ShardRegistry registry = registryOf(new HikariDataSource(), new HikariDataSource())) {
            assertThat(registry.forShard(0)).isNotNull();
            assertThat(registry.forShard(1)).isNotNull().isNotSameAs(registry.forShard(0));
            assertThat(registry.forShard(0).getDataSource()).isNotSameAs(registry.forShard(1).getDataSource());
        }
    }

    @Test
    void listsKnownShardIdsInOrder() {
        try (ShardRegistry registry = registryOf(new HikariDataSource(), new HikariDataSource())) {
            assertThat(registry.shardIds()).containsExactly(0, 1);
        }
    }

    @Test
    void unknownShardFailsLoudlyAndListsKnownIds() {
        try (ShardRegistry registry = registryOf(new HikariDataSource(), new HikariDataSource())) {
            assertThatThrownBy(() -> registry.forShard(2))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown shard id 2")
                    .hasMessageContaining("[0, 1]");
            assertThatThrownBy(() -> registry.forShard(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shardIdsCannotBeModifiedByCallers() {
        try (ShardRegistry registry = registryOf(new HikariDataSource(), new HikariDataSource())) {
            assertThatThrownBy(() -> registry.shardIds().add(5))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    void exposesPoolNamePerShardAndRejectsUnknownShard() {
        HikariDataSource zero = new HikariDataSource();
        zero.setPoolName("shard0-pool");
        HikariDataSource one = new HikariDataSource();
        one.setPoolName("shard1-pool");
        try (ShardRegistry registry = registryOf(zero, one)) {
            assertThat(registry.poolName(0)).isEqualTo("shard0-pool");
            assertThat(registry.poolName(1)).isEqualTo("shard1-pool");
            assertThatThrownBy(() -> registry.poolName(9)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void closeClosesEveryPool() {
        HikariDataSource zero = new HikariDataSource();
        HikariDataSource one = new HikariDataSource();

        registryOf(zero, one).close();

        assertThat(zero.isClosed()).isTrue();
        assertThat(one.isClosed()).isTrue();
    }

    @Test
    void rejectsEmptyPoolMap() {
        assertThatThrownBy(() -> new ShardRegistry(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
