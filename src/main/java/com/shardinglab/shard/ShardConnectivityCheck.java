package com.shardinglab.shard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * At startup, asks every shard which database and user it is connected as, and logs the answer.
 * Fails startup if a shard cannot answer, or if two shards report the same database name.
 *
 * <p>Same-database detection is a convention of this lab (each shard has its own database name).
 * In a real deployment two shards on different servers may legitimately share a database name; then
 * compare a server identity instead. This check is a startup snapshot, not ongoing health monitoring.
 */
@Component
@Order(2)
public class ShardConnectivityCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShardConnectivityCheck.class);

    // Constant query: no parameters, nothing to inject.
    private static final String IDENTITY_SQL = "select current_database(), current_user";

    record ShardIdentity(int shardId, String poolName, String database, String user) {
    }

    private final ShardRegistry registry;

    public ShardConnectivityCheck(ShardRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ShardIdentity> identities = new ArrayList<>();
        for (int shardId : registry.shardIds()) {
            ShardIdentity identity = identify(shardId);
            log.info("Shard {} | pool {} | database {} | user {}",
                    identity.shardId(), identity.poolName(), identity.database(), identity.user());
            identities.add(identity);
        }
        requireDistinctDatabases(identities);
    }

    private ShardIdentity identify(int shardId) {
        try {
            return registry.forShard(shardId).queryForObject(IDENTITY_SQL,
                    (rs, rowNum) -> new ShardIdentity(
                            shardId, registry.poolName(shardId), rs.getString(1), rs.getString(2)));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Connectivity check failed on shard " + shardId, e);
        }
    }

    static void requireDistinctDatabases(List<ShardIdentity> identities) {
        Map<String, Integer> firstShardByDatabase = new HashMap<>();
        for (ShardIdentity identity : identities) {
            Integer earlier = firstShardByDatabase.putIfAbsent(identity.database(), identity.shardId());
            if (earlier != null) {
                throw new IllegalStateException("Shards " + earlier + " and " + identity.shardId()
                        + " both report database '" + identity.database()
                        + "': check the shard URLs (a shard may be wired to the wrong database)");
            }
        }
    }
}
