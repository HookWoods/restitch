package io.github.hookwoods.restitch.boot4;

import io.github.hookwoods.restitch.api.AggregationResult;
import java.util.Map;
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
     * @param root source value to hydrate
     * @param rootType declared source value type
     * @param <T> source value type
     * @return stage completing with the hydrated value
     */
    public <T> CompletionStage<T> hydrate(T root, Class<T> rootType) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrate(root, rootType));
    }

    /**
     * Hydrates a source value asynchronously with inbound headers.
     *
     * @param root source value to hydrate
     * @param rootType declared source value type
     * @param headers inbound headers available for propagation
     * @param <T> source value type
     * @return stage completing with the hydrated value
     */
    public <T> CompletionStage<T> hydrate(T root, Class<T> rootType, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrate(root, rootType, headers));
    }

    /**
     * Hydrates a source value asynchronously while retaining recoverable errors.
     *
     * @param root source value to hydrate
     * @param rootType declared source value type
     * @param <T> source value type
     * @return stage completing with the hydrated value and collected errors
     */
    public <T> CompletionStage<AggregationResult<T>> hydrateResult(T root, Class<T> rootType) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrateResult(root, rootType));
    }

    /**
     * Hydrates a source value with inbound headers while retaining recoverable errors.
     *
     * @param root source value to hydrate
     * @param rootType declared source value type
     * @param headers inbound headers available for propagation
     * @param <T> source value type
     * @return stage completing with the hydrated value and collected errors
     */
    public <T> CompletionStage<AggregationResult<T>> hydrateResult(
            T root, Class<T> rootType, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrateResult(root, rootType, headers));
    }
}
