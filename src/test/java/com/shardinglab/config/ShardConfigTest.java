package com.shardinglab.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.shardinglab.config.ShardProperties.Pool;
import com.shardinglab.config.ShardProperties.Shard;

class ShardConfigTest {

    @Test
    void unreachableShardFailsFastNamingTheShardWithoutLeakingThePassword() {
        // Port 1 on localhost refuses connections immediately; no database is involved.
        Shard unreachable = new Shard(0, "jdbc:postgresql://localhost:1/none", "user0", "s3cretValue");
        ShardProperties properties = new ShardProperties(List.of(unreachable), new Pool(2, 250));

        assertThatThrownBy(() -> new ShardConfig().shardRegistry(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shard 0")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("s3cretValue"));
    }
}
