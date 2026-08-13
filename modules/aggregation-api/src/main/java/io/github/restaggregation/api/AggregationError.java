package io.github.restaggregation.api;

public record AggregationError(String resolver, String targetPointer, String category, String correlationId) {}
