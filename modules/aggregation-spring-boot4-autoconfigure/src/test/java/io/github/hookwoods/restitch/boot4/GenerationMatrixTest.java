package io.github.hookwoods.restitch.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.api.ErrorMode;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

class GenerationMatrixTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void repeatedIdsAreDeduplicatedWithinOneHydrationSession() throws InterruptedException {
        server.enqueue(response(200, "{\"payload\":{\"person\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));
        ReactiveAggregator aggregator = aggregator(ErrorMode.FAIL_FAST, null);

        StepVerifier.create(aggregator.hydrate(Flux.just(new Order("9"), new Order("9")), Order.class))
                .expectNextMatches(order -> order.owner().name().equals("Ada"))
                .expectNextMatches(order -> order.owner().name().equals("Ada"))
                .verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void configuredBatchHydratesDistinctIdsWithOneRequest() throws InterruptedException {
        AggregationProperties.Batch batch = new AggregationProperties.Batch();
        batch.setPath("/people");
        batch.setQueryParameter("ids");
        batch.setItemsPointer("/payload/people");
        batch.setItemKeyPointer("/id");
        batch.setMaxSize(10);
        server.enqueue(response(200, "{\"payload\":{\"people\":["
                + "{\"id\":\"9\",\"name\":\"Ada\"},"
                + "{\"id\":\"10\",\"name\":\"Grace\"}]}}"));
        ReactiveAggregator aggregator = aggregator(ErrorMode.FAIL_FAST, batch);

        StepVerifier.create(aggregator.hydrate(Flux.just(new Order("9"), new Order("10")), Order.class))
                .expectNextMatches(order -> order.owner().name().equals("Ada"))
                .expectNextMatches(order -> order.owner().name().equals("Grace"))
                .verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void configuredPointersHydrateTheExpectedDto() {
        server.enqueue(response(200, "{\"payload\":{\"person\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        StepVerifier.create(aggregator(ErrorMode.FAIL_FAST, null).hydrate(new Order("9"), Order.class))
                .assertNext(order -> assertThat(order.owner()).isEqualTo(new User("9", "Ada")))
                .verifyComplete();
    }

    @Test
    void failFastPropagatesDownstreamFailure() {
        server.enqueue(response(500, "{\"error\":\"missing\"}"));

        StepVerifier.create(aggregator(ErrorMode.FAIL_FAST, null).hydrate(new Order("9"), Order.class))
                .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("500"))
                .verify();
    }

    @Test
    void nullFieldModeClearsTheTargetField() {
        server.enqueue(response(500, "{\"error\":\"missing\"}"));
        Order order = new Order("9");

        StepVerifier.create(aggregator(ErrorMode.NULL_FIELD, null).hydrate(order, Order.class))
                .assertNext(result -> assertThat(result.owner()).isNull())
                .verifyComplete();
    }

    @Test
    void keepSourceIdModePreservesRawCompatibleSourceData() {
        server.enqueue(response(500, "{\"error\":\"missing\"}"));
        RawOrder order = new RawOrder("9");

        StepVerifier.create(aggregator(ErrorMode.KEEP_SOURCE_ID, null).hydrate(order, RawOrder.class))
                .assertNext(result -> assertThat(result.owner()).isEqualTo("9"))
                .verifyComplete();
    }

    @Test
    void resultModeReturnsCollectedErrorsThroughResultFacade() {
        server.enqueue(response(500, "{\"error\":\"missing\"}"));
        AggregationProperties properties = properties(ErrorMode.RESULT, null);
        ReactiveAggregator aggregator = new ReactiveAggregator(
                properties,
                new Jackson3JsonAdapter(new ObjectMapper()),
                WebClient.builder());

        assertThat(properties.resolverProfiles().get("owner").errorMode())
                .isEqualTo(ErrorMode.RESULT);
        StepVerifier.create(aggregator.hydrateResult(new Order("9"), Order.class))
                .assertNext(result -> {
                    assertThat(result.value().owner()).isNull();
                    assertThat(result.errors()).hasSize(1);
                })
                .verifyComplete();
    }

    private ReactiveAggregator aggregator(ErrorMode errorMode, AggregationProperties.Batch batch) {
        AggregationProperties properties = properties(errorMode, batch);
        return new ReactiveAggregator(
                properties,
                new Jackson3JsonAdapter(new ObjectMapper()),
                WebClient.builder());
    }

    private AggregationProperties properties(ErrorMode errorMode, AggregationProperties.Batch batch) {
        AggregationProperties properties = new AggregationProperties();
        properties.clients().put("identity", new AggregationProperties.Client(
                server.url("/").toString(), Duration.ofSeconds(2), Set.of()));
        properties.resolvers().put("owner", new AggregationProperties.Resolver(
                "identity", "/people/{id}", "/ownerId", "/payload/person", errorMode, batch));
        return properties;
    }

    private static MockResponse response(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    static final class Order {
        private final String ownerId;
        @AggregateRef("owner")
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

    static final class RawOrder {
        private final String ownerId;
        @AggregateRef("owner")
        private String owner;

        RawOrder(String ownerId) {
            this.ownerId = ownerId;
            this.owner = ownerId;
        }

        String ownerId() {
            return ownerId;
        }

        String owner() {
            return owner;
        }
    }

    record User(String id, String name) {}
}
