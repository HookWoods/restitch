package fr.hookwood.restitch.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;

class AggregateRefTest {
    @Test
    void aggregateRefIsRuntimeVisibleOnFields() throws Exception {
        Field owner = Order.class.getDeclaredField("owner");
        assertThat(owner.getAnnotation(AggregateRef.class).value()).isEqualTo("order-owner");
    }

    @Test
    void aggregateResponseIsRuntimeVisibleOnMethods() throws Exception {
        assertThat(Controller.class.getDeclaredMethod("order").getAnnotation(AggregateResponse.class).value())
                .isEqualTo("order-root");
    }

    @Test
    void aggregationResultRetainsFieldErrors() {
        AggregationError error = new AggregationError("order-owner", "/owner", "DOWNSTREAM_404", "trace-1");
        assertThat(new AggregationResult<>("order", List.of(error)).errors()).containsExactly(error);
    }

    static final class Order {
        @AggregateRef("order-owner")
        private String owner;
    }

    static final class Controller {
        @AggregateResponse("order-root")
        String order() {
            return "order";
        }
    }
}
