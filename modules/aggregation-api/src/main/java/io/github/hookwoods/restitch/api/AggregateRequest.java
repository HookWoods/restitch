package io.github.hookwoods.restitch.api;

import java.util.Map;

/**
 * Describes an explicit aggregation request.
 *
 * @param rootProfile configured root profile to execute
 * @param variables immutable variables available to the root path template
 * @param targetType type into which the root response is mapped
 * @param errorMode error handling policy for the root request
 * @param <T> mapped root type
 */
public record AggregateRequest<T>(
        String rootProfile, Map<String, Object> variables, Class<T> targetType, ErrorMode errorMode) {
    /**
     * Creates a request with an immutable copy of its variables.
     *
     * @param rootProfile configured root profile to execute
     * @param variables variables available to the root path template
     * @param targetType type into which the root response is mapped
     * @param errorMode error handling policy for the root request
     */
    public AggregateRequest {
        variables = Map.copyOf(variables);
    }
}
