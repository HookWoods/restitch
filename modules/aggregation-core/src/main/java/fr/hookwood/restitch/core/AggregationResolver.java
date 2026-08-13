package fr.hookwood.restitch.core;

import fr.hookwood.restitch.json.JsonDocument;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface AggregationResolver {
    String name();

    CompletionStage<JsonDocument> resolve(ResolverProfile resolverProfile, Map<String, Object> variables);
}
