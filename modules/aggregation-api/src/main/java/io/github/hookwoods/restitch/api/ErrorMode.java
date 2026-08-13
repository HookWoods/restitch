package io.github.hookwoods.restitch.api;

/** Defines how a resolver failure affects the aggregate currently being hydrated. */
public enum ErrorMode {
    /** Stop aggregation and propagate the failure. */
    FAIL_FAST,
    /** Replace the aggregate target with {@code null}. */
    NULL_FIELD,
    /** Preserve the original source identifier in the aggregate target. */
    KEEP_SOURCE_ID,
    /** Retain the failure in an {@link AggregationResult} instead of throwing it. */
    RESULT
}
