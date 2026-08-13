package io.github.hookwoods.restitch.api;

/**
 * Describes one resolver failure captured during an aggregation.
 *
 * @param resolver configured resolver profile that failed
 * @param targetPointer JSON Pointer of the target field, when available
 * @param category stable error category
 * @param correlationId downstream correlation identifier, when available
 */
public record AggregationError(String resolver, String targetPointer, String category, String correlationId) {}
