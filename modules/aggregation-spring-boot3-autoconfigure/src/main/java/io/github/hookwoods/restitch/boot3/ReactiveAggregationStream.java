package io.github.hookwoods.restitch.boot3;

import io.github.hookwoods.restitch.api.PageMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive aggregate items accompanied by metadata for their source page.
 *
 * @param <T> aggregate item type
 * @param items hydrated aggregate items
 * @param metadata page metadata, when available
 */
public record ReactiveAggregationStream<T>(Flux<T> items, Mono<PageMetadata> metadata) {
    /**
     * Creates a reactive aggregate stream.
     *
     * @param items hydrated aggregate items
     * @param metadata page metadata, when available
     */
    public ReactiveAggregationStream {}
}
