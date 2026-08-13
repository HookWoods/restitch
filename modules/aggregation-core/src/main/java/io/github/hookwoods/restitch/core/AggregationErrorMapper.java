package io.github.hookwoods.restitch.core;

import io.github.hookwoods.restitch.api.AggregationError;

/** Maps a resolver failure to the portable aggregation error contract. */
@FunctionalInterface
public interface AggregationErrorMapper {
    /** Returns the error representation for a failed resolver invocation. */
    AggregationError map(ResolverProfile resolverProfile, Throwable error);
}
