package io.github.hookwoods.restitch.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.api.AggregationResult;
import io.github.hookwoods.restitch.api.ErrorMode;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

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
                .expectNextMatches(order -> order.getOwner().name().equals("Ada"))
                .expectNextMatches(order -> order.getOwner().name().equals("Ada"))
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
                .expectNextMatches(order -> order.getOwner().name().equals("Ada"))
                .expectNextMatches(order -> order.getOwner().name().equals("Grace"))
                .verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void configuredPointersHydrateTheExpectedDto() {
        server.enqueue(response(200, "{\"payload\":{\"person\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        StepVerifier.create(aggregator(ErrorMode.FAIL_FAST, null).hydrate(new Order("9"), Order.class))
                .assertNext(order -> assertThat(order.getOwner()).isEqualTo(new User("9", "Ada")))
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
                .assertNext(result -> assertThat(result.getOwner()).isNull())
                .verifyComplete();
    }

    @Test
    void keepSourceIdModePreservesRawCompatibleSourceData() {
        server.enqueue(response(500, "{\"error\":\"missing\"}"));
        RawOrder order = new RawOrder("9");

        StepVerifier.create(aggregator(ErrorMode.KEEP_SOURCE_ID, null).hydrate(order, RawOrder.class))
                .assertNext(result -> assertThat(result.getOwner()).isEqualTo("9"))
                .verifyComplete();
    }

    @Test
    void resultModeReturnsHydratedValueAndMappedFieldError() {
        server.enqueue(response(500, "{\"error\":\"missing\"}"));
        ReactiveAggregator aggregator = aggregator(ErrorMode.RESULT, null);

        StepVerifier.create(aggregator.hydrateResult(new Order("9"), Order.class))
                .assertNext(result -> {
                    assertThat(result.value().getOwner()).isNull();
                    assertThat(result.errors()).hasSize(1);
                    assertThat(result.errors().get(0).resolver()).isEqualTo("owner");
                    assertThat(result.errors().get(0).category()).isEqualTo("DOWNSTREAM");
                })
                .verifyComplete();
    }

    @Test
    void batchesEveryAnnotatedField() {
        AggregationProperties.Batch ownerBatch = batch("/people", "/payload/people");
        ReactiveAggregator aggregator = aggregator(ErrorMode.FAIL_FAST, ownerBatch);
        AggregationProperties.Resolver reviewer = new AggregationProperties.Resolver();
        reviewer.setClient("identity");
        reviewer.setPath("/reviewers/{id}");
        reviewer.setSourcePointer("/reviewerId");
        reviewer.setResponsePointer("/payload/reviewer");
        reviewer.setBatch(batch("/reviewers", "/payload/reviewers"));
        aggregator.properties().getResolvers().put("reviewer", reviewer);
        server.enqueue(response(200, "{\"payload\":{\"people\":[{\"id\":\"9\",\"name\":\"Ada\"}]}}"));
        server.enqueue(response(200, "{\"payload\":{\"reviewers\":[{\"id\":\"10\",\"name\":\"Grace\"}]}}"));

        StepVerifier.create(aggregator.hydrate(Flux.just(new MultiOrder("9", "10")), MultiOrder.class))
                .assertNext(order -> {
                    assertThat(order.getOwner().name()).isEqualTo("Ada");
                    assertThat(order.getReviewer().name()).isEqualTo("Grace");
                })
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void resultModeMapsBatchHttpErrors() {
        AggregationProperties.Batch batch = batch("/people", "/payload/people");
        server.enqueue(response(500, "{\"error\":\"missing\"}"));
        ReactiveAggregator aggregator = aggregator(ErrorMode.RESULT, batch);

        StepVerifier.create(aggregator.hydrateResult(new Order("9"), Order.class))
                .assertNext(result -> assertThat(result.errors()).extracting(error -> error.category())
                        .containsExactly("DOWNSTREAM"))
                .verifyComplete();
    }

    @Test
    void configuredClientTimeoutStopsReactiveResolution() {
        ReactiveAggregator aggregator = aggregator(ErrorMode.FAIL_FAST, null);
        aggregator.properties().getClients().get("identity").setTimeout(Duration.ofMillis(50));
        server.enqueue(response(200, "{\"payload\":{\"person\":{\"id\":\"9\"}}}")
                .setBodyDelay(500, TimeUnit.MILLISECONDS));

        StepVerifier.create(aggregator.hydrate(new Order("9"), Order.class))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(TimeoutException.class))
                .verify();
    }

    private static AggregationProperties.Batch batch(String path, String itemsPointer) {
        AggregationProperties.Batch batch = new AggregationProperties.Batch();
        batch.setPath(path);
        batch.setQueryParameter("ids");
        batch.setItemsPointer(itemsPointer);
        batch.setItemKeyPointer("/id");
        batch.setMaxSize(10);
        return batch;
    }

    private ReactiveAggregator aggregator(ErrorMode errorMode, AggregationProperties.Batch batch) {
        AggregationProperties properties = new AggregationProperties();
        AggregationProperties.Client client = new AggregationProperties.Client();
        client.setBaseUrl(server.url("/").toString());
        client.setPropagateHeaders(List.of());
        properties.getClients().put("identity", client);

        AggregationProperties.Resolver resolver = new AggregationProperties.Resolver();
        resolver.setClient("identity");
        resolver.setPath("/people/{id}");
        resolver.setSourcePointer("/ownerId");
        resolver.setResponsePointer("/payload/person");
        resolver.setErrorMode(errorMode);
        resolver.setBatch(batch);
        properties.getResolvers().put("owner", resolver);
        return new ReactiveAggregator(properties, new Jackson2JsonAdapter(new ObjectMapper()));
    }

    private static MockResponse response(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    static final class Order {
        private String ownerId;
        @AggregateRef("owner")
        private User owner;

        Order() {}

        Order(String ownerId) {
            this.ownerId = ownerId;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public User getOwner() {
            return owner;
        }

        public void setOwnerId(String ownerId) {
            this.ownerId = ownerId;
        }

        public void setOwner(User owner) {
            this.owner = owner;
        }
    }

    static final class RawOrder {
        private String ownerId;
        @AggregateRef("owner")
        private String owner;

        RawOrder() {}

        RawOrder(String ownerId) {
            this.ownerId = ownerId;
            this.owner = ownerId;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwnerId(String ownerId) {
            this.ownerId = ownerId;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }
    }

    static final class MultiOrder {
        private String ownerId;
        private String reviewerId;
        @AggregateRef("owner")
        private User owner;
        @AggregateRef("reviewer")
        private User reviewer;

        MultiOrder() {}

        MultiOrder(String ownerId, String reviewerId) {
            this.ownerId = ownerId;
            this.reviewerId = reviewerId;
        }

        public String getOwnerId() { return ownerId; }
        public String getReviewerId() { return reviewerId; }
        public User getOwner() { return owner; }
        public User getReviewer() { return reviewer; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
        public void setOwner(User owner) { this.owner = owner; }
        public void setReviewer(User reviewer) { this.reviewer = reviewer; }
    }

    record User(String id, String name) {}
}
