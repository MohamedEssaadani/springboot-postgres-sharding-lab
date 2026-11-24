package com.shardinglab.shard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Applies schema.sql to every shard at startup, one shard at a time, in id order.
 * Fails fast: if a shard fails the application does not start, and the error names that shard.
 * The script is idempotent, so a retry after a partial failure is safe.
 */
@Component
@Order(1)
public class ShardSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShardSchemaInitializer.class);

    private final ShardRegistry registry;
    private final Resource schema;

    @Autowired
    public ShardSchemaInitializer(ShardRegistry registry) {
        this(registry, new ClassPathResource("schema.sql"));
    }

    ShardSchemaInitializer(ShardRegistry registry, Resource schema) {
        this.registry = registry;
        this.schema = schema;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (int shardId : registry.shardIds()) {
            try {
                new ResourceDatabasePopulator(schema).execute(registry.forShard(shardId).getDataSource());
            } catch (RuntimeException e) {
                throw new IllegalStateException("Schema initialization failed on shard " + shardId, e);
            }
            log.info("Schema applied on shard {}", shardId);
        }
    }
}
