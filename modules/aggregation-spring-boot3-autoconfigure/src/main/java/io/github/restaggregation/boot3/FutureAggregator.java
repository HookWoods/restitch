package io.github.restaggregation.boot3;

import io.github.restaggregation.api.AggregationResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class FutureAggregator {
    private final MvcAggregator mvcAggregator;

    public FutureAggregator(MvcAggregator mvcAggregator) {
        this.mvcAggregator = mvcAggregator;
    }

    public <T> CompletionStage<T> hydrate(T source, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrate(source, type));
    }

    public <T> CompletionStage<AggregationResult<T>> hydrateResult(T source, Class<T> type) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrateResult(source, type));
    }
}
