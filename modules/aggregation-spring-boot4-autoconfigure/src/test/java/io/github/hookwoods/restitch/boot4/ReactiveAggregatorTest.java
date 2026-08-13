package io.github.hookwoods.restitch.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.api.ErrorMode;
import io.github.hookwoods.restitch.api.PageMetadata;
import io.github.hookwoods.restitch.core.ClientProfile;
import io.github.hookwoods.restitch.core.ResolverProfile;
import io.github.hookwoods.restitch.json.JsonAdapter;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class ReactiveAggregatorTest {
    private MockWebServer server;
    private AggregationProperties properties;
    private ReactiveAggregator aggregator;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        properties = new AggregationProperties();
        properties.clients().put("identity", new AggregationProperties.Client(
                server.url("/").toString(), Duration.ofSeconds(2), Set.of()));
        properties.resolvers().put("order-owner", new AggregationProperties.Resolver(
                "identity", "/users/{id}", "/ownerId", "/data/user", ErrorMode.NULL_FIELD, null));
        AggregationProperties.Root orders = new AggregationProperties.Root();
        orders.setClient("identity");
        orders.setPath("/orders");
        orders.setItemsPointer("/data/orders");
        properties.roots().put("orders", orders);
        aggregator = new ReactiveAggregator(
                properties,
                new Jackson3JsonAdapter(new ObjectMapper()),
                WebClient.builder());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void responsePointerHydratesBoot4Dto() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        StepVerifier.create(aggregator.hydrate(new Order("9"), Order.class))
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Ada"))
                .verifyComplete();
    }

    @Test
    void nullFieldModeReturnsRootOnNested404() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"missing\"}"));

        StepVerifier.create(aggregator.hydrate(new Order("9"), Order.class))
                .assertNext(order -> assertThat(order.owner()).isNull())
                .verifyComplete();
    }

    @Test
    void repeatedIdsProduceOneBatchRequest() throws Exception {
        var batch = new AggregationProperties.Batch();
        batch.setPath("/users");
        batch.setQueryParameter("ids");
        batch.setItemsPointer("/data/users");
        batch.setItemKeyPointer("/id");
        batch.setMaxSize(100);
        properties.clients().put("identity", new AggregationProperties.Client(
                server.url("/").toString(), Duration.ofSeconds(2), Set.of("Authorization")));
        properties.resolvers().put("order-owner", new AggregationProperties.Resolver(
                "identity", "/users/{id}", "/ownerId", "/data/user", ErrorMode.NULL_FIELD, batch));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"users\":[{\"id\":\"9\",\"name\":\"Ada\"}]}}"));

        StepVerifier.create(aggregator.hydrate(
                        Flux.just(new Order("9"), new Order("9")),
                        Order.class,
                        Map.of("Authorization", "Bearer token", "Cookie", "secret")))
                .expectNextCount(2)
                .verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(1);
        var request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(request.getHeader("Cookie")).isNull();
    }

    @Test
    void streamRetainsOptionalPageMetadata() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        ReactiveAggregationStream<Order> stream = aggregator.stream(
                Flux.just(new Order("9")), Order.class, Mono.just(new PageMetadata(1, 1, 1, 1)));

        StepVerifier.create(stream.items())
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Ada"))
                .verifyComplete();
        StepVerifier.create(stream.metadata())
                .expectNext(new PageMetadata(1, 1, 1, 1))
                .verifyComplete();
    }

    @Test
    void configuredRootStreamsJsonPointerCollectionIncrementally() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"orders\":[{\"ownerId\":\"9\"},{\"ownerId\":\"10\"}]}}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        StepVerifier.create(aggregator.stream("orders", StreamOrder.class).items().take(1))
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Ada"))
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void configuredRootHonorsMaxBufferedItemsWhileContinuingTheCollection() {
        properties.limits().setMaxBufferedItems(1);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"orders\":[{\"ownerId\":\"9\"},{\"ownerId\":\"10\"}]}}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"10\",\"name\":\"Grace\"}}}"));

        StepVerifier.create(aggregator.stream("orders", StreamOrder.class).items())
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Ada"))
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Grace"))
                .verifyComplete();
    }

    @Test
    void reactiveBatchFlushesAfterConfiguredWindowBeforeCountIsReached() {
        properties.limits().setMaxBatchSize(2);
        properties.limits().setBatchFlushWindow(Duration.ofMillis(50));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        StepVerifier.create(aggregator.hydrate(
                        Flux.concat(Flux.just(new Order("9")), Flux.never()), Order.class).take(1))
                .assertNext(order -> assertThat(order.owner().name()).isEqualTo("Ada"))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void resolverRejectsAuthorityChangingPath() {
        properties.resolvers().put("order-owner", new AggregationProperties.Resolver(
                "identity", "//attacker.example/users/{id}", "/ownerId", "/data/user", ErrorMode.FAIL_FAST, null));

        StepVerifier.create(aggregator.hydrate(new Order("9"), Order.class))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("relative"))
                .verify();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void enforcesMaxRequests() {
        properties.limits().setMaxRequests(1);
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"data\":{\"orders\":[{\"ownerId\":\"9\"}]}}"));
        StepVerifier.create(aggregator.stream("orders", StreamOrder.class).items())
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("request limit"))
                .verify();
    }

    static final class Order {
        private final String ownerId;
        @AggregateRef("order-owner")
        private User owner;

        Order(String ownerId) {
            this.ownerId = ownerId;
        }

        String ownerId() {
            return ownerId;
        }

        User owner() {
            return owner;
        }
    }

    record User(String id, String name) {}

    static final class StreamOrder {
        private String ownerId;
        @AggregateRef("order-owner")
        private User owner;

        public String getOwnerId() {
            return ownerId;
        }

        public void setOwnerId(String ownerId) {
            this.ownerId = ownerId;
        }

        User owner() {
            return owner;
        }
    }
}
