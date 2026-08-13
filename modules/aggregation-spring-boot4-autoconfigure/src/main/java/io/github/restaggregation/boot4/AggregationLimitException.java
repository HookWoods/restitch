package io.github.restaggregation.boot4;

final class AggregationLimitException extends IllegalStateException {
    AggregationLimitException(String message) {
        super(message);
    }
}
