package io.github.hookwoods.restitch.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.api.AggregateResponse;
import io.github.hookwoods.restitch.api.ErrorMode;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class AggregateResponseInterceptorTest {
    private MockWebServer server;
    private AggregateResponseInterceptor interceptor;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        var properties = new AggregationProperties();
        properties.clients().put("identity", new AggregationProperties.Client(
                server.url("/").toString(), Duration.ofSeconds(2), Set.of()));
        properties.resolvers().put("order-owner", new AggregationProperties.Resolver(
                "identity", "/users/{id}", "/ownerId", "/data/user", ErrorMode.FAIL_FAST, null));
        var adapter = new Jackson3JsonAdapter(new ObjectMapper());
        var reactive = new ReactiveAggregator(properties, adapter, WebClient.builder());
        var mvc = new MvcAggregator(properties, adapter, RestClient.builder());
        interceptor = new AggregateResponseInterceptor(reactive, mvc, new FutureAggregator(mvc));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void wrapsPlainMonoFluxAndCompletionStageResults() throws Throwable {
        server.enqueue(response());
        var plain = interceptor.invoke(new Controller(), Controller.class.getDeclaredMethod("plain"));
        assertThat(((Order) plain).owner().name()).isEqualTo("Ada");

        server.enqueue(response());
        StepVerifier.create((Mono<Order>) interceptor.invoke(new Controller(), Controller.class.getDeclaredMethod("mono")))
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Ada"))
                .verifyComplete();

        server.enqueue(response());
        StepVerifier.create((Flux<Order>) interceptor.invoke(new Controller(), Controller.class.getDeclaredMethod("flux")))
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Ada"))
                .verifyComplete();

        server.enqueue(response());
        Order future = ((CompletionStage<Order>) interceptor.invoke(
                        new Controller(), Controller.class.getDeclaredMethod("future")))
                .toCompletableFuture()
                .join();
        assertThat(future.owner().name()).isEqualTo("Ada");
    }

    @Test
    void beanPostProcessorWrapsAnnotatedController() throws Throwable {
        server.enqueue(response());
        Controller controller = (Controller) new AggregateResponseBeanPostProcessor(interceptor)
                .postProcessAfterInitialization(new Controller(), "controller");

        assertThat(controller.plain().owner().name()).isEqualTo("Ada");
    }

    private static MockResponse response() {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}");
    }

    static class Controller {
        @AggregateResponse("order-root")
        Order plain() {
            return new Order("9");
        }

        @AggregateResponse("order-root")
        Mono<Order> mono() {
            return Mono.just(new Order("9"));
        }

        @AggregateResponse("order-root")
        Flux<Order> flux() {
            return Flux.just(new Order("9"));
        }

        @AggregateResponse("order-root")
        CompletionStage<Order> future() {
            return CompletableFuture.completedFuture(new Order("9"));
        }
    }

    static final class Order {
        private final String ownerId;
        @AggregateRef("order-owner")
        private User owner;

        Order(String ownerId) {
            this.ownerId = ownerId;
        }

        User owner() {
            return owner;
        }
    }

    record User(String id, String name) {}
}
