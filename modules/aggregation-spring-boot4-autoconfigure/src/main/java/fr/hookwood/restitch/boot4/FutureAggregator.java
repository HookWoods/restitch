package fr.hookwood.restitch.boot4;

import fr.hookwood.restitch.api.AggregationResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class FutureAggregator {
    private final MvcAggregator mvcAggregator;

    public FutureAggregator(MvcAggregator mvcAggregator) {
        this.mvcAggregator = mvcAggregator;
    }

    public <T> CompletionStage<T> hydrate(T root, Class<T> rootType) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrate(root, rootType));
    }

    public <T> CompletionStage<T> hydrate(T root, Class<T> rootType, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrate(root, rootType, headers));
    }

    public <T> CompletionStage<AggregationResult<T>> hydrateResult(T root, Class<T> rootType) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrateResult(root, rootType));
    }

    public <T> CompletionStage<AggregationResult<T>> hydrateResult(
            T root, Class<T> rootType, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> mvcAggregator.hydrateResult(root, rootType, headers));
    }
}
