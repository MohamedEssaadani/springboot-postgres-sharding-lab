package com.shardinglab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shardinglab.config.ShardProperties.Pool;
import com.shardinglab.config.ShardProperties.Shard;

class ShardPropertiesValidationTest {

    private static final Pool POOL = new Pool(5, 3000);

    private static Shard shard(int id) {
        return new Shard(id, "jdbc:postgresql://localhost:5433/db" + id, "user" + id, "secret" + id);
    }

    @Test
    void acceptsTwoDistinctShards() {
        ShardProperties props = new ShardProperties(List.of(shard(0), shard(1)), POOL);

        assertThat(props.shards()).extracting(Shard::id).containsExactly(0, 1);
    }

    @Test
    void rejectsEmptyShardList() {
        assertThatThrownBy(() -> new ShardProperties(List.of(), POOL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one shard");
    }

    @Test
    void rejectsDuplicateShardIds() {
        assertThatThrownBy(() -> new ShardProperties(List.of(shard(0), shard(0)), POOL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate shard id");
    }

    @Test
    void rejectsBlankPassword() {
        assertThatThrownBy(() -> new Shard(1, "jdbc:postgresql://localhost:5434/db1", "user1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Shard 1")
                .hasMessageContaining("password");
    }

    @Test
    void rejectsMissingUrlAndUsername() {
        assertThatThrownBy(() -> new Shard(0, null, "user0", "secret0"))
                .hasMessageContaining("url");
        assertThatThrownBy(() -> new Shard(0, "jdbc:postgresql://localhost:5433/db0", "", "secret0"))
                .hasMessageContaining("username");
    }

    @Test
    void rejectsNegativeShardId() {
        assertThatThrownBy(() -> new Shard(-1, "jdbc:postgresql://localhost:5433/db0", "user0", "secret0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositivePoolSettings() {
        assertThatThrownBy(() -> new Pool(0, 3000)).hasMessageContaining("maximum-pool-size");
        assertThatThrownBy(() -> new Pool(5, 0)).hasMessageContaining("connection-timeout-ms");
    }

    @Test
    void toStringDoesNotLeakPassword() {
        assertThat(shard(0).toString()).doesNotContain("secret0").contains("****");
    }
}
