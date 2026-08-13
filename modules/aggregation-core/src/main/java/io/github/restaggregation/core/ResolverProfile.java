package io.github.restaggregation.core;

import io.github.restaggregation.api.ErrorMode;

public record ResolverProfile(
        String name,
        String client,
        String path,
        String sourcePointer,
        String responsePointer,
        ErrorMode errorMode,
        BatchProfile batch) {
    public ResolverProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (client == null || client.isBlank()) {
            throw new IllegalArgumentException("client is required");
        }
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("path must start with slash");
        }
        JsonPointers.requirePointer(sourcePointer, "sourcePointer");
        JsonPointers.requirePointer(responsePointer, "responsePointer");
        errorMode = errorMode == null ? ErrorMode.FAIL_FAST : errorMode;
    }
}
