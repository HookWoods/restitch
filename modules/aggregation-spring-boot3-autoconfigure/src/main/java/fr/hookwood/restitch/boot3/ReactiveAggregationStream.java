package fr.hookwood.restitch.boot3;

import fr.hookwood.restitch.api.PageMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public record ReactiveAggregationStream<T>(Flux<T> items, Mono<PageMetadata> metadata) {}
