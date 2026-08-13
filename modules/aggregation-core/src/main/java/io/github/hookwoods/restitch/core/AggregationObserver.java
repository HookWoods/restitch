package io.github.hookwoods.restitch.core;

/** Receives best-effort lifecycle events for individual request-local resolutions. */
public interface AggregationObserver {
    /** Invoked immediately before a resolution begins. */
    default void resolutionStarted(ResolutionKey key) {}

    /** Invoked when a resolution completes successfully. */
    default void resolutionSucceeded(ResolutionKey key) {}

    /** Invoked when a resolution fails. */
    default void resolutionFailed(ResolutionKey key, Throwable error) {}
}
