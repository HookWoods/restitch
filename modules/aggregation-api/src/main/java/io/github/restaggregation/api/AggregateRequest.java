package io.github.restaggregation.api;

import java.util.Map;

public record AggregateRequest<T>(
        String rootProfile, Map<String, Object> variables, Class<T> targetType, ErrorMode errorMode) {
    public AggregateRequest {
        variables = Map.copyOf(variables);
    }
}
