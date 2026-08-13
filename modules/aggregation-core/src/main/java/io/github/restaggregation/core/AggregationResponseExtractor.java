package io.github.restaggregation.core;

import io.github.restaggregation.json.JsonDocument;

@FunctionalInterface
public interface AggregationResponseExtractor {
    JsonDocument extract(JsonDocument response, ResolverProfile resolverProfile);
}
