package io.github.restaggregation.core;

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
