package io.github.hookwoods.restitch.core;

import io.github.hookwoods.restitch.api.AggregationError;

/** Maps a resolver failure to the portable aggregation error contract. */
@FunctionalInterface
public interface AggregationErrorMapper {
    /**
     * Returns the error representation for a failed resolver invocation.
     *
     * @param resolverProfile resolver whose invocation failed
     * @param error underlying resolver failure
     * @return portable error reported to the aggregation caller
     */
    AggregationError map(ResolverProfile resolverProfile, Throwable error);
}
