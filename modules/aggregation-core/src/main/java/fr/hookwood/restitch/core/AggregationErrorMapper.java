package fr.hookwood.restitch.core;

import fr.hookwood.restitch.api.AggregationError;

@FunctionalInterface
public interface AggregationErrorMapper {
    AggregationError map(ResolverProfile resolverProfile, Throwable error);
}
