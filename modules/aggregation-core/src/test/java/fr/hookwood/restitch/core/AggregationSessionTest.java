package fr.hookwood.restitch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class AggregationSessionTest {
    private final AggregationSession session = new AggregationSession(AggregationLimits.defaults());

    @Test
    void sessionUsesOneInFlightValueForEquivalentKey() {
        AtomicInteger created = new AtomicInteger();
        ResolutionKey key = ResolutionKey.of("identity", "/users/9", "user", Map.of("X-Tenant", "acme"));

        Object first = session.memoize(key, Object.class, created::incrementAndGet);
        Object second = session.memoize(key, Object.class, created::incrementAndGet);

        assertThat(second).isSameAs(first);
        assertThat(created).hasValue(1);
    }

    @Test
    void closeClearsSessionEntries() {
        ResolutionKey key = ResolutionKey.of("identity", "/users/9", "user", Map.of());

        session.memoize(key, Object.class, Object::new);
        session.close();

        AtomicInteger created = new AtomicInteger();
        session.memoize(key, Object.class, created::incrementAndGet);
        assertThat(created).hasValue(1);
    }

    @Test
    void inFlightReactiveValueIsRetainedUntilItsCompletionSignal() {
        AggregationLimits limits = limits(10, 1, 1_000);
        AggregationSession session = new AggregationSession(limits);
        ResolutionKey inFlightKey = ResolutionKey.of("identity", "/users/9", "user", Map.of());
        ResolutionKey completedKey = ResolutionKey.of("identity", "/users/10", "user", Map.of());
        ReactiveLikeValue inFlight = new ReactiveLikeValue();

        ReactiveLikeValue first = session.memoize(inFlightKey, ReactiveLikeValue.class, () -> inFlight);
        session.memoize(completedKey, Object.class, Object::new);

        assertThat(session.size()).isEqualTo(1);
        assertThat(session.memoize(inFlightKey, ReactiveLikeValue.class, ReactiveLikeValue::new)).isSameAs(first);

        inFlight.complete();
        ResolutionKey nextKey = ResolutionKey.of("identity", "/users/11", "user", Map.of());
        session.memoize(nextKey, Object.class, Object::new);

        assertThat(session.size()).isEqualTo(1);
        assertThat(session.memoize(inFlightKey, ReactiveLikeValue.class, ReactiveLikeValue::new)).isNotSameAs(first);
    }

    @Test
    void completedEntriesAreEvictedWhenEntryLimitIsReached() {
        AggregationSession session = new AggregationSession(limits(10, 1, 1_000));
        ResolutionKey firstKey = ResolutionKey.of("identity", "/users/9", "user", Map.of());
        ResolutionKey secondKey = ResolutionKey.of("identity", "/users/10", "user", Map.of());

        session.memoize(firstKey, Object.class, Object::new);
        session.memoize(secondKey, Object.class, Object::new);

        AtomicInteger recreated = new AtomicInteger();
        session.memoize(firstKey, Object.class, () -> {
            recreated.incrementAndGet();
            return new Object();
        });

        assertThat(recreated).hasValue(1);
        assertThat(session.size()).isEqualTo(1);
    }

    @Test
    void completedEntriesAreEvictedWhenByteLimitIsReached() {
        AggregationSession session = new AggregationSession(limits(10, 10, 4));
        ResolutionKey firstKey = ResolutionKey.of("identity", "/users/9", "user", Map.of());
        ResolutionKey secondKey = ResolutionKey.of("identity", "/users/10", "user", Map.of());

        session.memoize(firstKey, byte[].class, () -> new byte[3]);
        session.memoize(secondKey, byte[].class, () -> new byte[3]);

        AtomicInteger recreated = new AtomicInteger();
        session.memoize(firstKey, byte[].class, () -> {
            recreated.incrementAndGet();
            return new byte[3];
        });

        assertThat(recreated).hasValue(1);
        assertThat(session.size()).isEqualTo(1);
    }

    @Test
    void opaqueCompletedValuesAreEvictedWhenByteLimitIsVeryLow() {
        AggregationSession session = new AggregationSession(limits(10, 10, 1));
        ResolutionKey key = ResolutionKey.of("identity", "/users/9", "user", Map.of());

        session.memoize(key, Object.class, Object::new);

        AtomicInteger recreated = new AtomicInteger();
        session.memoize(key, Object.class, () -> {
            recreated.incrementAndGet();
            return new Object();
        });

        assertThat(recreated).hasValue(1);
    }

    @Test
    void cancelledCompletionStageRemainsInFlight() {
        AggregationSession session = new AggregationSession(limits(10, 1, 1));
        ResolutionKey inFlightKey = ResolutionKey.of("identity", "/users/9", "user", Map.of());
        ResolutionKey completedKey = ResolutionKey.of("identity", "/users/10", "user", Map.of());
        CompletionStageValue inFlight = new CompletionStageValue();

        CompletionStageValue first = session.memoize(inFlightKey, CompletionStageValue.class, () -> inFlight);
        inFlight.cancel(false);
        session.memoize(completedKey, Object.class, Object::new);

        assertThat(session.memoize(inFlightKey, CompletionStageValue.class, CompletionStageValue::new))
                .isSameAs(first);
        assertThat(session.size()).isEqualTo(1);
    }

    @Test
    void reactiveCancellationSignalDoesNotCompleteEntry() {
        AggregationLimits limits = limits(10, 1, 1);
        AggregationSession session = new AggregationSession(limits);
        ResolutionKey inFlightKey = ResolutionKey.of("identity", "/users/9", "user", Map.of());
        ResolutionKey completedKey = ResolutionKey.of("identity", "/users/10", "user", Map.of());
        ReactiveLikeValue inFlight = new ReactiveLikeValue();

        ReactiveLikeValue first = session.memoize(inFlightKey, ReactiveLikeValue.class, () -> inFlight);
        inFlight.cancel();
        session.memoize(completedKey, Object.class, Object::new);

        assertThat(session.memoize(inFlightKey, ReactiveLikeValue.class, ReactiveLikeValue::new)).isSameAs(first);
        assertThat(session.size()).isEqualTo(1);
    }

    @Test
    void maxRequestsCountsNewMemoizedKeys() {
        AggregationSession session = new AggregationSession(limits(1, 10, 1_000));
        ResolutionKey firstKey = ResolutionKey.of("identity", "/users/9", "user", Map.of());
        ResolutionKey secondKey = ResolutionKey.of("identity", "/users/10", "user", Map.of());

        session.memoize(firstKey, Object.class, Object::new);

        assertThatThrownBy(() -> session.memoize(secondKey, Object.class, Object::new))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("aggregation session exceeds maxRequests");
    }

    private static AggregationLimits limits(int maxRequests, int maxSessionEntries, long maxSessionBytes) {
        AggregationLimits defaults = AggregationLimits.defaults();
        return new AggregationLimits(
                defaults.maxDepth(),
                maxRequests,
                defaults.maxConcurrency(),
                defaults.maxResponseBytes(),
                defaults.maxObjectBytes(),
                defaults.maxBufferedItems(),
                maxSessionEntries,
                maxSessionBytes,
                defaults.maxPendingIds(),
                defaults.streamPrefetch(),
                defaults.maxBatchSize(),
                defaults.batchFlushWindow());
    }

    static final class ReactiveLikeValue {
        private Consumer<Object> completion;

        public ReactiveLikeValue doFinally(Consumer<Object> completion) {
            this.completion = completion;
            return this;
        }

        void complete() {
            completion.accept(CompletionSignal.COMPLETE);
        }

        void cancel() {
            completion.accept(CompletionSignal.CANCEL);
        }
    }

    private enum CompletionSignal {
        COMPLETE,
        CANCEL
    }

    static final class CompletionStageValue extends CompletableFuture<Object> {
    }
}
