package io.github.restaggregation.boot3;

import io.github.restaggregation.api.PageMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public record ReactiveAggregationStream<T>(Flux<T> items, Mono<PageMetadata> metadata) {}
