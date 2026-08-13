package fr.hookwood.restitch.api;

import java.util.List;

public record AggregationResult<T>(T value, List<AggregationError> errors) {
    public AggregationResult {
        errors = List.copyOf(errors);
    }
}
