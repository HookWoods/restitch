package io.github.restaggregation.core;

import io.github.restaggregation.api.AggregationError;

@FunctionalInterface
public interface AggregationErrorMapper {
    AggregationError map(ResolverProfile resolverProfile, Throwable error);
}
