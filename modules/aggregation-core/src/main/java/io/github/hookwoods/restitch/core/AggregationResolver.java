package io.github.hookwoods.restitch.core;

import io.github.hookwoods.restitch.json.JsonDocument;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/** Resolves a configured profile to a JSON value. */
public interface AggregationResolver {
    /** Returns the resolver implementation name used for selection and diagnostics. */
    String name();

    /** Resolves a profile using the variables collected for the current aggregation. */
    CompletionStage<JsonDocument> resolve(ResolverProfile resolverProfile, Map<String, Object> variables);
}
