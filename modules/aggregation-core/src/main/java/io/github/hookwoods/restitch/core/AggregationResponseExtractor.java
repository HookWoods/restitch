package io.github.hookwoods.restitch.core;

import io.github.hookwoods.restitch.json.JsonDocument;

/** Extracts the aggregate value from a resolver response. */
@FunctionalInterface
public interface AggregationResponseExtractor {
    /**
     * Returns the JSON value to hydrate into the aggregate target.
     *
     * @param response complete downstream response document
     * @param resolverProfile resolver that produced the response
     * @return value selected for hydration
     */
    JsonDocument extract(JsonDocument response, ResolverProfile resolverProfile);
}
