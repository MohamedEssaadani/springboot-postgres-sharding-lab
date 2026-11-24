package com.shardinglab.shard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** No database needed. */
class ShardRouterTest {

    private static final ShardRouter TWO = new ShardRouter(List.of(0, 1));

    @Test
    void sameIdAlwaysMapsToSameShard() {
        int first = TWO.shardFor(123_456_789L);

        for (int i = 0; i < 1000; i++) {
            assertThat(TWO.shardFor(123_456_789L)).isEqualTo(first);
        }
    }

    /** Fixed expectations worked out by hand: Long.hashCode(n) == n for small non-negative n. */
    @Test
    void knownIdsMapToKnownShards() {
        assertThat(TWO.shardFor(4)).isEqualTo(0);
        assertThat(TWO.shardFor(5)).isEqualTo(1);
        assertThat(TWO.shardFor(1_000_001)).isEqualTo(1);
        // Long.hashCode(-1) == 0
        assertThat(TWO.shardFor(-1)).isEqualTo(0);

        ShardRouter three = new ShardRouter(List.of(0, 1, 2));
        assertThat(three.shardFor(10)).isEqualTo(1);
        assertThat(three.shardFor(11)).isEqualTo(2);
        assertThat(three.shardFor(12)).isEqualTo(0);
    }

    @Test
    void extremeIdsStayInsideTheConfiguredShards() {
        long[] ids = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1, 0, 1, Long.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE};
        ShardRouter three = new ShardRouter(List.of(0, 1, 2));

        for (long id : ids) {
            assertThat(TWO.shardFor(id)).isIn(0, 1);
            assertThat(three.shardFor(id)).isIn(0, 1, 2);
        }
    }

    @Test
    void worksWithNonContiguousShardIdsRegardlessOfInputOrder() {
        ShardRouter router = new ShardRouter(List.of(7, 3)); // unsorted on purpose

        assertThat(router.shardCount()).isEqualTo(2);
        assertThat(router.shardFor(8)).isEqualTo(3); // index 0 -> smallest id
        assertThat(router.shardFor(7)).isEqualTo(7); // index 1 -> largest id
    }

    @Test
    void singleShardOwnsEverything() {
        ShardRouter single = new ShardRouter(List.of(5));

        assertThat(single.shardFor(0)).isEqualTo(5);
        assertThat(single.shardFor(Long.MIN_VALUE)).isEqualTo(5);
        assertThat(single.shardFor(987_654_321L)).isEqualTo(5);
    }

    @Test
    void scatteredIdsSpreadRoughlyEvenly() {
        Random random = new Random(42); // fixed seed: deterministic test
        int total = 10_000;
        int onShardZero = 0;

        for (int i = 0; i < total; i++) {
            if (TWO.shardFor(random.nextLong()) == 0) {
                onShardZero++;
            }
        }

        assertThat(onShardZero).isBetween((int) (total * 0.45), (int) (total * 0.55));
    }

    @Test
    void usesEveryShardWhenManyIdsAreRouted() {
        ShardRouter four = new ShardRouter(List.of(0, 1, 2, 3));
        Random random = new Random(7);
        Set<Integer> seen = new java.util.HashSet<>();

        for (int i = 0; i < 1000; i++) {
            seen.add(four.shardFor(random.nextLong()));
        }

        assertThat(seen).containsExactlyInAnyOrder(0, 1, 2, 3);
    }

    @Test
    void rejectsMissingOrDuplicateShardIds() {
        assertThatThrownBy(() -> new ShardRouter(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShardRouter((java.util.Collection<Integer>) null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ShardRouter(List.of(1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }
}
