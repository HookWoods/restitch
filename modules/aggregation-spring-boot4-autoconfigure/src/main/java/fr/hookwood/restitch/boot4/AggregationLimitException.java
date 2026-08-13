package fr.hookwood.restitch.boot4;

final class AggregationLimitException extends IllegalStateException {
    AggregationLimitException(String message) {
        super(message);
    }
}
