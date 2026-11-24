package com.shardinglab.shard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/** No database needed: the failure case points at a port that refuses connections. */
class ShardSchemaInitializerTest {

    private static String schemaText() throws IOException {
        return new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void schemaIsIdempotentAndDefinesUsersWithApplicationSuppliedId() throws IOException {
        // Ignore comment lines (they explain why SERIAL/UNIQUE are absent) and normalize whitespace.
        String sql = schemaText().lines()
                .filter(line -> !line.strip().startsWith("--"))
                .reduce("", (a, b) -> a + " " + b)
                .replaceAll("\\s+", " ")
                .toUpperCase();

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS USERS");
        assertThat(sql).contains("ID BIGINT PRIMARY KEY");
        // No per-shard id generation and no per-shard-only uniqueness on email.
        assertThat(sql).doesNotContain("SERIAL").doesNotContain("IDENTITY").doesNotContain("SEQUENCE");
        assertThat(sql).doesNotContain("UNIQUE");
    }

    @Test
    void failureNamesTheShardThatFailed() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:1/none");
        config.setUsername("user");
        config.setPassword("s3cretValue");
        config.setConnectionTimeout(250);
        config.setInitializationFailTimeout(-1); // do not connect when the pool is created
        try (ShardRegistry registry = new ShardRegistry(Map.of(7, new HikariDataSource(config)))) {
            ShardSchemaInitializer initializer = new ShardSchemaInitializer(registry);

            assertThatThrownBy(() -> initializer.run(null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("shard 7")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("s3cretValue"));
        }
    }
}
