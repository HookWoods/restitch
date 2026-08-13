package io.github.hookwoods.restitch.boot3;

import io.github.hookwoods.restitch.api.AggregationResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Adapts MVC aggregation operations to {@link CompletionStage} results. */
public final class FutureAggregator {
    private final MvcAggregator mvcAggregator;

    /**
     * Creates an adapter backed by the supplied MVC aggregator.
     *
     * @param mvcAggregator synchronous aggregation implementation to invoke
     */
    public FutureAggregator(MvcAggregator mvcAggregator) {
        this.mvcAggregator = mvcAggregator;
    }

    /**
     * Hydrates a source value asynchronously.
     *
     * @param source source value to hydrate
     * @param type declared source value type
     * @param <T> source value type
     * @return stage completing with the hydrated value
     */
    public <T> CompletionStage<T> hydrate(T source, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrate(source, type));
    }

    /**
     * Hydrates a source value asynchronously while retaining recoverable errors.
     *
     * @param source source value to hydrate
     * @param type declared source value type
     * @param <T> source value type
     * @return stage completing with the hydrated value and collected errors
     */
    public <T> CompletionStage<AggregationResult<T>> hydrateResult(T source, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrateResult(source, type));
    }
}
