package fr.hookwood.restitch.core;

public interface AggregationObserver {
    default void resolutionStarted(ResolutionKey key) {}

    default void resolutionSucceeded(ResolutionKey key) {}

    default void resolutionFailed(ResolutionKey key, Throwable error) {}
}
