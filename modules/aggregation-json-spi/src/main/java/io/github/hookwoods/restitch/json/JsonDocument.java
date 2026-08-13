package io.github.hookwoods.restitch.json;

/**
 * Opaque JSON value owned by a {@link JsonAdapter} implementation.
 *
 * <p>Applications should pass documents back to the originating adapter instead of assuming a concrete tree model.
 */
public interface JsonDocument {}
