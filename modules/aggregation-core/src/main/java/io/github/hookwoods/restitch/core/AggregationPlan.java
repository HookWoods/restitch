package io.github.hookwoods.restitch.core;

import java.util.Map;

/**
 * Immutable aggregation plan validated against a root DTO and resolver profiles.
 *
 * @param rootType DTO type containing aggregate references
 * @param resolvers immutable profiles addressed by {@code AggregateRef}
 * @param limits safety limits used to compile and execute the plan
 */
public record AggregationPlan(Class<?> rootType, Map<String, ResolverProfile> resolvers, AggregationLimits limits) {
    /**
     * Creates an immutable aggregation plan.
     *
     * @param rootType DTO type containing aggregate references
     * @param resolvers profiles addressed by {@code AggregateRef}
     * @param limits safety limits used to compile and execute the plan
     */
    public AggregationPlan {
        resolvers = Map.copyOf(resolvers);
        limits = limits == null ? AggregationLimits.defaults() : limits;
    }
}
