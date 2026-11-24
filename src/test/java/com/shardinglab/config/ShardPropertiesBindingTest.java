package com.shardinglab.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import com.shardinglab.config.ShardProperties.Shard;

/**
 * Loads the real application.yml and the local .env (working directory = repository root).
 * Only the properties bean is registered, so it does not create pools or connect to PostgreSQL.
 */
@SpringBootTest(classes = ShardPropertiesBindingTest.BindingOnly.class)
class ShardPropertiesBindingTest {

    @Configuration
    @EnableConfigurationProperties(ShardProperties.class)
    static class BindingOnly {
    }

    @Autowired
    private ShardProperties properties;

    @Test
    void bindsTwoShardsWithResolvedValues() {
        assertThat(properties.shards()).extracting(Shard::id).containsExactly(0, 1);

        for (Shard shard : properties.shards()) {
            assertThat(shard.url()).startsWith("jdbc:postgresql://localhost:").doesNotContain("${");
            assertThat(shard.username()).isNotBlank().doesNotContain("${");
            assertThat(shard.password()).isNotBlank().doesNotContain("${");
        }
    }

    @Test
    void shardsUseDifferentUrls() {
        assertThat(properties.shards()).extracting(Shard::url).doesNotHaveDuplicates();
    }

    @Test
    void bindsPoolSettingsFromYaml() {
        assertThat(properties.pool().maximumPoolSize()).isEqualTo(5);
        assertThat(properties.pool().connectionTimeoutMs()).isEqualTo(3000);
    }
}
