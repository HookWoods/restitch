package fr.hookwood.restitch.api;

public record AggregationError(String resolver, String targetPointer, String category, String correlationId) {}
