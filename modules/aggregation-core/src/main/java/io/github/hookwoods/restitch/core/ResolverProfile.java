package io.github.hookwoods.restitch.core;

import io.github.hookwoods.restitch.api.ErrorMode;

/**
 * Immutable resolver behavior loaded from aggregation configuration.
 *
 * @param name resolver profile name referenced by {@code AggregateRef}
 * @param client named downstream client
 * @param path downstream path template
 * @param sourcePointer JSON Pointer to the source identifier
 * @param responsePointer JSON Pointer to the hydrated response value
 * @param errorMode resolver failure policy
 * @param batch optional batch-resolution behavior
 */
public record ResolverProfile(
        String name,
        String client,
        String path,
        String sourcePointer,
        String responsePointer,
        ErrorMode errorMode,
        BatchProfile batch) {
    /**
     * Creates validated resolver behavior loaded from aggregation configuration.
     *
     * @param name resolver profile name referenced by {@code AggregateRef}
     * @param client named downstream client
     * @param path downstream path template
     * @param sourcePointer JSON Pointer to the source identifier
     * @param responsePointer JSON Pointer to the hydrated response value
     * @param errorMode resolver failure policy
     * @param batch optional batch-resolution behavior
     */
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
