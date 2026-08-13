package io.github.hookwoods.restitch.core;

import java.time.Duration;

/**
 * Safety limits enforced while compiling and executing a single aggregation.
 *
 * @param maxDepth maximum nested DTO traversal depth
 * @param maxRequests maximum distinct downstream resolutions per session
 * @param maxConcurrency maximum concurrent downstream resolutions
 * @param maxResponseBytes maximum downstream response size
 * @param maxObjectBytes maximum retained aggregate object estimate
 * @param maxBufferedItems maximum buffered stream items
 * @param maxSessionEntries maximum request-local memoized entries
 * @param maxSessionBytes maximum estimated bytes retained by a session
 * @param maxPendingIds maximum IDs waiting for batch resolution
 * @param streamPrefetch Reactor stream prefetch limit
 * @param maxBatchSize maximum IDs in one batch request
 * @param batchFlushWindow maximum time a batch waits before dispatch
 */
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

    /** Returns the conservative defaults used when no limits are configured. */
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
