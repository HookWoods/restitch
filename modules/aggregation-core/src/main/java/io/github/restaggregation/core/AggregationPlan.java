package io.github.restaggregation.core;

import java.util.Map;

public record AggregationPlan(Class<?> rootType, Map<String, ResolverProfile> resolvers, AggregationLimits limits) {
    public AggregationPlan {
        resolvers = Map.copyOf(resolvers);
        limits = limits == null ? AggregationLimits.defaults() : limits;
    }
}
