package com.shardinglab.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shardinglab.config.ShardProperties.Pool;
import com.shardinglab.config.ShardProperties.Shard;
import com.shardinglab.shard.ShardRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Builds one HikariCP pool per configured shard. Spring Boot's single-DataSource
 * auto-configuration is excluded on the application class, so nothing else creates pools.
 */
@Configuration
public class ShardConfig {

    // Spring calls ShardRegistry.close() on shutdown (inferred destroy method), closing every pool.
    @Bean
    public ShardRegistry shardRegistry(ShardProperties properties) {
        Map<Integer, HikariDataSource> pools = new LinkedHashMap<>();
        try {
            for (Shard shard : properties.shards()) {
                pools.put(shard.id(), createPool(shard, properties.pool()));
            }
        } catch (RuntimeException e) {
            // Do not leak pools that were already opened when a later shard fails.
            pools.values().forEach(ShardConfig::closeQuietly);
            throw e;
        }
        return new ShardRegistry(pools);
    }

    private static HikariDataSource createPool(Shard shard, Pool pool) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("shard" + shard.id() + "-pool");
        config.setJdbcUrl(shard.url());
        config.setUsername(shard.username());
        config.setPassword(shard.password());
        config.setMaximumPoolSize(pool.maximumPoolSize());
        config.setConnectionTimeout(pool.connectionTimeoutMs());
        // Fail fast: try one connection when the pool is created, and throw if the shard is unreachable.
        config.setInitializationFailTimeout(1);
        try {
            return new HikariDataSource(config);
        } catch (RuntimeException e) {
            // Name the shard; never include credentials in the message.
            throw new IllegalStateException(
                    "Cannot connect to shard " + shard.id() + " at " + shard.url(), e);
        }
    }

    private static void closeQuietly(HikariDataSource dataSource) {
        try {
            dataSource.close();
        } catch (RuntimeException ignored) {
            // The original startup failure is the one worth reporting.
        }
    }
}
