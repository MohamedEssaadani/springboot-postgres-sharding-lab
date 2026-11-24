package com.shardinglab.shard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.shardinglab.shard.ShardConnectivityCheck.ShardIdentity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** No database needed. */
class ShardConnectivityCheckTest {

    private static ShardIdentity identity(int id, String database) {
        return new ShardIdentity(id, "shard" + id + "-pool", database, "user" + id);
    }

    @Test
    void acceptsShardsWithDistinctDatabases() {
        assertThatCode(() -> ShardConnectivityCheck.requireDistinctDatabases(
                List.of(identity(0, "shard0_db"), identity(1, "shard1_db"))))
                .doesNotThrowAnyException();
    }

    @Test
    void failsWhenTwoShardsReportTheSameDatabase() {
        assertThatThrownBy(() -> ShardConnectivityCheck.requireDistinctDatabases(
                List.of(identity(0, "same_db"), identity(1, "same_db"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Shards 0 and 1")
                .hasMessageContaining("same_db");
    }

    @Test
    void failureOnOneShardNamesThatShardAndLeaksNoPassword() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:1/none");
        config.setUsername("user");
        config.setPassword("s3cretValue");
        config.setConnectionTimeout(250);
        config.setInitializationFailTimeout(-1);
        try (ShardRegistry registry = new ShardRegistry(Map.of(3, new HikariDataSource(config)))) {
            ShardConnectivityCheck check = new ShardConnectivityCheck(registry);

            assertThatThrownBy(() -> check.run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("shard 3")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("s3cretValue"));
        }
    }
}
