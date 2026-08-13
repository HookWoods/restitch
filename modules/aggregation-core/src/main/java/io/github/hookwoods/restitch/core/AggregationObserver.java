package io.github.hookwoods.restitch.core;

/** Receives best-effort lifecycle events for individual request-local resolutions. */
public interface AggregationObserver {
    /**
     * Invoked immediately before a resolution begins.
     *
     * @param key identity of the resolution starting
     */
    default void resolutionStarted(ResolutionKey key) {}

    /**
     * Invoked when a resolution completes successfully.
     *
     * @param key identity of the completed resolution
     */
    default void resolutionSucceeded(ResolutionKey key) {}

    /**
     * Invoked when a resolution fails.
     *
     * @param key identity of the failed resolution
     * @param error failure raised while resolving the value
     */
    default void resolutionFailed(ResolutionKey key, Throwable error) {}
}
