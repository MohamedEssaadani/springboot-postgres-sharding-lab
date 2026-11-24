package com.shardinglab.shard;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.shardinglab.model.User;

/**
 * Stores users on the shard that owns them. Every operation asks the {@link ShardRouter} for the
 * owning shard and runs exactly one statement on that shard, so each call is atomic and no call ever
 * tries another shard.
 *
 * <p>Outcomes are kept distinct for the caller:
 * <ul>
 *   <li>insert of an existing id: {@link org.springframework.dao.DuplicateKeyException} propagates;</li>
 *   <li>unknown id on read/update/delete: empty {@link Optional} or {@code false};</li>
 *   <li>shard unreachable: {@link ShardUnavailableException} naming the shard. It is never turned into
 *       "not found" and never retried on a different shard.</li>
 * </ul>
 * All SQL uses {@code ?} parameters; values are never concatenated into the statement.
 */
@Repository
public class UserRepository {

    private static final String COLUMNS = "id, name, email, created_at";
    private static final String INSERT_SQL =
            "INSERT INTO users (id, name, email) VALUES (?, ?, ?) RETURNING " + COLUMNS;
    private static final String SELECT_SQL = "SELECT " + COLUMNS + " FROM users WHERE id = ?";
    private static final String UPDATE_SQL =
            "UPDATE users SET name = ?, email = ? WHERE id = ? RETURNING " + COLUMNS;
    private static final String DELETE_SQL = "DELETE FROM users WHERE id = ?";

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final ShardRegistry registry;
    private final ShardRouter router;

    public UserRepository(ShardRegistry registry, ShardRouter router) {
        this.registry = registry;
        this.router = router;
    }

    /** Inserts the user on its owning shard and returns the stored row (including created_at). */
    public ShardedUser insert(User user) {
        int shardId = router.shardFor(user.id());
        User stored = onShard(shardId, jdbc -> jdbc.queryForObject(
                INSERT_SQL, USER_MAPPER, user.id(), user.name(), user.email()));
        return new ShardedUser(shardId, stored);
    }

    public Optional<ShardedUser> findById(long id) {
        int shardId = router.shardFor(id);
        List<User> rows = onShard(shardId, jdbc -> jdbc.query(SELECT_SQL, USER_MAPPER, id));
        return rows.stream().findFirst().map(user -> new ShardedUser(shardId, user));
    }

    /** Updates name and email on the owning shard; empty if the id does not exist. */
    public Optional<ShardedUser> update(long id, String name, String email) {
        User.newUser(id, name, email); // validate input before touching any shard
        int shardId = router.shardFor(id);
        List<User> rows = onShard(shardId, jdbc -> jdbc.query(UPDATE_SQL, USER_MAPPER, name, email, id));
        return rows.stream().findFirst().map(user -> new ShardedUser(shardId, user));
    }

    /** Deletes the user from its owning shard; false if the id does not exist. */
    public boolean delete(long id) {
        int shardId = router.shardFor(id);
        return onShard(shardId, jdbc -> jdbc.update(DELETE_SQL, id)) > 0;
    }

    /** Runs the operation on one shard; connection-level failures are reported as that shard being unavailable. */
    private <T> T onShard(int shardId, Function<JdbcTemplate, T> operation) {
        try {
            return operation.apply(registry.forShard(shardId));
        } catch (DataAccessResourceFailureException e) {
            throw new ShardUnavailableException(shardId, e);
        }
    }
}
