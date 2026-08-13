package fr.hookwood.restitch.sample;

import fr.hookwood.restitch.api.AggregateRef;
import fr.hookwood.restitch.boot3.MvcAggregator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class OrderController {
    private final MvcAggregator aggregator;

    public OrderController(MvcAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @GetMapping("/orders/{orderId}")
    public Order getOrder(@PathVariable String orderId) {
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
