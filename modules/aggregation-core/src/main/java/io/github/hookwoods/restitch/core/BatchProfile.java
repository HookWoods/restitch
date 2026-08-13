package io.github.hookwoods.restitch.core;

/**
 * Immutable batch-resolution configuration for a resolver.
 *
 * @param path batch endpoint path
 * @param queryParameter query parameter carrying requested identifiers
 * @param itemsPointer JSON Pointer to returned items
 * @param itemKeyPointer JSON Pointer to each item's matching identifier
 * @param maxSize maximum identifiers sent in one batch request
 */
public record BatchProfile(String path, String queryParameter, String itemsPointer, String itemKeyPointer, int maxSize) {
    public BatchProfile {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with slash");
        }
        if (queryParameter == null || queryParameter.isBlank()) {
            throw new IllegalArgumentException("queryParameter is required");
        }
        JsonPointers.requirePointer(itemsPointer, "itemsPointer");
        JsonPointers.requirePointer(itemKeyPointer, "itemKeyPointer");
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
    }
}
