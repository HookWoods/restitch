package io.github.hookwoods.restitch.boot3;

import io.github.hookwoods.restitch.api.PageMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public record ReactiveAggregationStream<T>(Flux<T> items, Mono<PageMetadata> metadata) {}
