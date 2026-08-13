package io.github.hookwoods.restitch.api;

import java.util.List;

/**
 * Aggregation output that preserves a hydrated value together with non-fatal resolver errors.
 *
 * @param value hydrated root value
 * @param errors immutable resolver errors collected during processing
 * @param <T> hydrated root type
 */
public record AggregationResult<T>(T value, List<AggregationError> errors) {
    public AggregationResult {
        errors = List.copyOf(errors);
    }
}
