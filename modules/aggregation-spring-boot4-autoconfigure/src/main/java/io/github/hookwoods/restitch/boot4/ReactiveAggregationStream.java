package io.github.hookwoods.restitch.boot4;

import io.github.hookwoods.restitch.api.PageMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive aggregate items accompanied by metadata for their source page.
 *
 * @param <T> aggregate item type
 */
public final class ReactiveAggregationStream<T> {
    private final Flux<T> items;
    private final Mono<PageMetadata> metadata;

    /**
     * Creates a reactive aggregate stream.
     *
     * @param items hydrated aggregate items
     * @param metadata page metadata, when available
     */
    public ReactiveAggregationStream(Flux<T> items, Mono<PageMetadata> metadata) {
        this.items = items;
        this.metadata = metadata;
    }

    /**
     * Returns the hydrated aggregate items.
     *
     * @return stream of hydrated aggregate items
     */
    public Flux<T> items() {
        return items;
    }

    /**
     * Returns metadata for the source page.
     *
     * @return publisher of source page metadata
     */
    public Mono<PageMetadata> metadata() {
        return metadata;
    }
}
