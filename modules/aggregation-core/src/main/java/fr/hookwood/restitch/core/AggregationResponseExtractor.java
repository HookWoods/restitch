package fr.hookwood.restitch.core;

import fr.hookwood.restitch.json.JsonDocument;

@FunctionalInterface
public interface AggregationResponseExtractor {
    JsonDocument extract(JsonDocument response, ResolverProfile resolverProfile);
}
