package io.github.restaggregation.sample;

import io.github.restaggregation.api.AggregateRef;
import io.github.restaggregation.boot4.ReactiveAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public final class OrderHandler {
    private final ReactiveAggregator aggregator;

    public OrderHandler(ReactiveAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/orders/{orderId}")
    public Mono<Order> getOrder(@PathVariable String orderId) {
        return aggregator.hydrate(new Order(orderId), Order.class);
    }

    public static final class Order {
        private String ownerId;
        @AggregateRef("order-owner")
        private User owner;

        public Order() {}

        public Order(String ownerId) {
            this.ownerId = ownerId;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(String ownerId) {
            this.ownerId = ownerId;
        }

        public User getOwner() {
            return owner;
        }

        public void setOwner(User owner) {
            this.owner = owner;
        }
    }

    public record User(String id, String name) {}
}
