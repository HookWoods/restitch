package fr.hookwood.restitch.boot4;

import fr.hookwood.restitch.api.PageMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ReactiveAggregationStream<T> {
    private final Flux<T> items;
    private final Mono<PageMetadata> metadata;

    public ReactiveAggregationStream(Flux<T> items, Mono<PageMetadata> metadata) {
        this.items = items;
        this.metadata = metadata;
    }

    public Flux<T> items() {
        return items;
    }

    public Mono<PageMetadata> metadata() {
        return metadata;
    }
}
