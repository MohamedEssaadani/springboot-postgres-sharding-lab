package com.shardinglab.shard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.shardinglab.model.User;
import com.zaxxer.hikari.HikariDataSource;

/**
 * No database needed: fake JdbcTemplates record what each shard was asked to run.
 * With shards 0 and 1, Long.hashCode(n) == n, so id 4 belongs to shard 0 and id 5 to shard 1.
 */
class UserRepositoryTest {

    /** Records calls and returns canned results instead of talking to PostgreSQL. */
    static class FakeJdbc extends JdbcTemplate {
        final List<String> sqls = new ArrayList<>();
        final List<List<Object>> args = new ArrayList<>();
        List<?> rows = List.of();
        int updateCount = 0;
        RuntimeException failure;

        private void record(String sql, Object[] a) {
            sqls.add(sql);
            args.add(List.of(a));
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... a) {
            record(sql, a);
            return (T) rows.get(0);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... a) {
            record(sql, a);
            return (List<T>) rows;
        }

        @Override
        public int update(String sql, Object... a) {
            record(sql, a);
            return updateCount;
        }

        int calls() {
            return sqls.size();
        }
    }

    /** Hands out the fake templates; no pools are ever opened. */
    static class FakeRegistry extends ShardRegistry {
        private final Map<Integer, JdbcTemplate> templates;

        FakeRegistry(Map<Integer, JdbcTemplate> templates) {
            super(Map.of(0, new HikariDataSource(), 1, new HikariDataSource()));
            this.templates = templates;
        }

        @Override
        public JdbcTemplate forShard(int shardId) {
            return templates.get(shardId);
        }
    }

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private FakeJdbc shard0;
    private FakeJdbc shard1;
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        shard0 = new FakeJdbc();
        shard1 = new FakeJdbc();
        repository = new UserRepository(
                new FakeRegistry(Map.of(0, shard0, 1, shard1)),
                new ShardRouter(List.of(0, 1)));
    }

    private static User stored(long id) {
        return new User(id, "Ada", "ada@example.com", NOW);
    }

    @Test
    void insertRunsOnlyOnTheOwningShardWithParameters() {
        shard0.rows = List.of(stored(4));

        ShardedUser result = repository.insert(User.newUser(4, "Ada", "ada@example.com"));

        assertThat(result.shardId()).isEqualTo(0);
        assertThat(result.user().createdAt()).isEqualTo(NOW);
        assertThat(shard0.calls()).isEqualTo(1);
        assertThat(shard1.calls()).isZero();
        assertThat(shard0.sqls.get(0)).startsWith("INSERT INTO users").doesNotContain("Ada");
        assertThat(shard0.args.get(0)).containsExactly(4L, "Ada", "ada@example.com");
    }

    @Test
    void findByIdUsesTheShardTheRouterChooses() {
        shard1.rows = List.of(stored(5));

        ShardedUser result = repository.findById(5).orElseThrow();

        assertThat(result.shardId()).isEqualTo(1);
        assertThat(result.user().id()).isEqualTo(5);
        assertThat(shard1.calls()).isEqualTo(1);
        assertThat(shard0.calls()).isZero();
        assertThat(shard1.args.get(0)).containsExactly(5L);
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        shard0.rows = List.of();

        assertThat(repository.findById(4)).isEmpty();
        assertThat(shard1.calls()).isZero();
    }

    @Test
    void updateReturnsTheUpdatedRowFromTheOwningShard() {
        shard1.rows = List.of(new User(5, "Grace", "grace@example.com", NOW));

        ShardedUser result = repository.update(5, "Grace", "grace@example.com").orElseThrow();

        assertThat(result.shardId()).isEqualTo(1);
        assertThat(result.user().name()).isEqualTo("Grace");
        assertThat(shard1.sqls.get(0)).startsWith("UPDATE users");
        assertThat(shard1.args.get(0)).containsExactly("Grace", "grace@example.com", 5L);
        assertThat(shard0.calls()).isZero();
    }

    @Test
    void updateOfUnknownIdIsEmpty() {
        shard0.rows = List.of();

        assertThat(repository.update(4, "Ada", "ada@example.com")).isEmpty();
    }

    @Test
    void updateRejectsInvalidInputBeforeTouchingAnyShard() {
        assertThatThrownBy(() -> repository.update(4, " ", "ada@example.com"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(shard0.calls()).isZero();
        assertThat(shard1.calls()).isZero();
    }

    @Test
    void deleteReportsWhetherARowWasRemoved() {
        shard1.updateCount = 1;
        assertThat(repository.delete(5)).isTrue();
        assertThat(shard1.sqls.get(0)).startsWith("DELETE FROM users");
        assertThat(shard0.calls()).isZero();

        shard0.updateCount = 0;
        assertThat(repository.delete(4)).isFalse();
    }

    @Test
    void duplicateIdIsSurfacedNotSwallowed() {
        shard0.failure = new DuplicateKeyException("duplicate key value violates unique constraint");

        assertThatThrownBy(() -> repository.insert(User.newUser(4, "Ada", "ada@example.com")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void connectionFailureOnTheOwningShardNamesThatShardAndNeverFallsBack() {
        shard1.failure = new CannotGetJdbcConnectionException("shard 1 is down");
        shard0.rows = List.of(stored(5)); // would "succeed" if the repository wrongly fell back

        assertThatThrownBy(() -> repository.findById(5))
                .isInstanceOf(ShardUnavailableException.class)
                .hasCauseInstanceOf(CannotGetJdbcConnectionException.class)
                .satisfies(e -> assertThat(((ShardUnavailableException) e).shardId()).isEqualTo(1));

        assertThat(shard0.calls()).isZero();
    }

    @Test
    void connectionFailureIsReportedForEveryOperation() {
        shard0.failure = new CannotGetJdbcConnectionException("shard 0 is down");

        assertThatThrownBy(() -> repository.insert(User.newUser(4, "Ada", "ada@example.com")))
                .isInstanceOf(ShardUnavailableException.class);
        assertThatThrownBy(() -> repository.update(4, "Ada", "ada@example.com"))
                .isInstanceOf(ShardUnavailableException.class);
        assertThatThrownBy(() -> repository.delete(4))
                .isInstanceOf(ShardUnavailableException.class);
    }
}
