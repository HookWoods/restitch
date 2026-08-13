package io.github.restaggregation.core;

import java.time.Duration;

public record AggregationLimits(
        int maxDepth,
        int maxRequests,
        int maxConcurrency,
        long maxResponseBytes,
        long maxObjectBytes,
        int maxBufferedItems,
        int maxSessionEntries,
        long maxSessionBytes,
        int maxPendingIds,
        int streamPrefetch,
        int maxBatchSize,
        Duration batchFlushWindow) {
    public AggregationLimits {
        requirePositive(maxDepth, "maxDepth");
        requirePositive(maxRequests, "maxRequests");
        requirePositive(maxConcurrency, "maxConcurrency");
        requirePositive(maxResponseBytes, "maxResponseBytes");
        requirePositive(maxObjectBytes, "maxObjectBytes");
        requirePositive(maxBufferedItems, "maxBufferedItems");
        requirePositive(maxSessionEntries, "maxSessionEntries");
        requirePositive(maxSessionBytes, "maxSessionBytes");
        requirePositive(maxPendingIds, "maxPendingIds");
        requirePositive(streamPrefetch, "streamPrefetch");
        requirePositive(maxBatchSize, "maxBatchSize");
        if (batchFlushWindow == null || batchFlushWindow.isNegative() || batchFlushWindow.isZero()) {
            throw new IllegalArgumentException("batchFlushWindow must be positive");
        }
    }

    public static AggregationLimits defaults() {
        return new AggregationLimits(
                8,
                256,
                16,
                10 * 1024 * 1024,
                1024 * 1024,
                256,
                1_024,
                16 * 1024 * 1024,
                10_000,
                32,
                100,
                Duration.ofMillis(10));
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
