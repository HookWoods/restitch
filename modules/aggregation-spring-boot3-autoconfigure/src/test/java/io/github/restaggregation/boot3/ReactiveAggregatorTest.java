package io.github.restaggregation.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.restaggregation.api.AggregateRef;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactiveAggregatorTest {
    private MockWebServer server;
    private ReactiveAggregator aggregator;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        AggregationProperties properties = new AggregationProperties();
        AggregationProperties.Client identity = new AggregationProperties.Client();
        identity.setBaseUrl(server.url("/").toString());
        identity.setPropagateHeaders(List.of("Authorization"));
        properties.getClients().put("identity", identity);

        AggregationProperties.Resolver resolver = new AggregationProperties.Resolver();
        resolver.setClient("identity");
        resolver.setPath("/users/{id}");
        resolver.setSourcePointer("/ownerId");
        resolver.setResponsePointer("/data/user");
        properties.getResolvers().put("order-owner", resolver);

        AggregationProperties.Root orders = new AggregationProperties.Root();
        orders.setClient("identity");
        orders.setPath("/orders");
        orders.setItemsPointer("/data/orders");
        properties.getRoots().put("orders", orders);

        aggregator = new ReactiveAggregator(
                properties, new Jackson2JsonAdapter(new ObjectMapper()));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void reactiveFacadeHydratesNestedUser() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        StepVerifier.create(aggregator.hydrate(new AggregationBoot3IntegrationTest.Order("9"),
                        AggregationBoot3IntegrationTest.Order.class))
                .assertNext(order -> assertThat(order.getOwner().name()).isEqualTo("Ada"))
                .verifyComplete();
    }

    @Test
    void onlyAllowlistedHeadersReachDownstream() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        aggregator.hydrate(new AggregationBoot3IntegrationTest.Order("9"),
                        AggregationBoot3IntegrationTest.Order.class,
                        Map.of("Authorization", "Bearer token", "Cookie", "session=private"))
                .block();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(request.getHeader("Cookie")).isNull();
    }

    @Test
    void repeatedIdsUseOneBatchRequest() throws InterruptedException {
        AggregationProperties.Batch batch = new AggregationProperties.Batch();
        batch.setPath("/users");
        batch.setQueryParameter("ids");
        batch.setItemsPointer("/data/users");
        batch.setItemKeyPointer("/id");
        batch.setMaxSize(10);
        properties().getResolvers().get("order-owner").setBatch(batch);

        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"users\":[{\"id\":\"9\",\"name\":\"Ada\"}]}}"));

        StepVerifier.create(aggregator.hydrate(Flux.just(
                                new AggregationBoot3IntegrationTest.Order("9"),
                                new AggregationBoot3IntegrationTest.Order("9"),
                                new AggregationBoot3IntegrationTest.Order("9")),
                        AggregationBoot3IntegrationTest.Order.class))
                .expectNextCount(3)
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void rootCollectionStreamsAndCancellationStopsFurtherHydration() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"orders\":["
                        + "{\"ownerId\":\"9\"},"
                        + "{\"ownerId\":\"10\"},"
                        + "{\"ownerId\":\"11\"}]}}"));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        StepVerifier.create(aggregator.stream("orders", AggregationBoot3IntegrationTest.Order.class)
                        .items().take(1))
                .assertNext(order -> assertThat(order.getOwner().name()).isEqualTo("Ada"))
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void rootRequestCountsAgainstAggregationSessionLimit() {
        aggregator.properties().getLimits().setMaxRequests(1);
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"orders\":[{\"ownerId\":\"9\"}]}}"));

        StepVerifier.create(aggregator.stream("orders", AggregationBoot3IntegrationTest.Order.class).items())
                .expectErrorSatisfies(error -> assertThat(error)
                        .hasMessageContaining("aggregation session exceeds maxRequests"))
                .verify();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void proactiveBatchRequestsCountAgainstAggregationSessionLimit() {
        AggregationProperties.Batch batch = new AggregationProperties.Batch();
        batch.setPath("/users");
        batch.setQueryParameter("ids");
        batch.setItemsPointer("/data/users");
        batch.setItemKeyPointer("/id");
        batch.setMaxSize(1);
        properties().getResolvers().get("order-owner").setBatch(batch);
        properties().getLimits().setMaxRequests(1);
        server.enqueue(response(200, "{\"data\":{\"users\":[{\"id\":\"9\",\"name\":\"Ada\"}]}}"));

        StepVerifier.create(aggregator.hydrate(Flux.just(
                                new AggregationBoot3IntegrationTest.Order("9"),
                                new AggregationBoot3IntegrationTest.Order("10")),
                        AggregationBoot3IntegrationTest.Order.class))
                .expectNextCount(1)
                .expectErrorSatisfies(error -> assertThat(error)
                        .hasMessageContaining("aggregation session exceeds maxRequests"))
                .verify();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void reactiveBatchFlushesWhenWindowExpiresBeforeMaxBatchSize() {
        AggregationProperties.Batch batch = new AggregationProperties.Batch();
        batch.setPath("/users");
        batch.setQueryParameter("ids");
        batch.setItemsPointer("/data/users");
        batch.setItemKeyPointer("/id");
        batch.setMaxSize(10);
        properties().getResolvers().get("order-owner").setBatch(batch);
        properties().getLimits().setBatchFlushWindow(Duration.ofMillis(50));
        server.enqueue(response(200, "{\"data\":{\"users\":[{\"id\":\"9\",\"name\":\"Ada\"}]}}"));
        server.enqueue(response(200, "{\"data\":{\"users\":[{\"id\":\"10\",\"name\":\"Grace\"}]}}"));

        StepVerifier.create(aggregator.hydrate(Flux.concat(
                                Flux.just(new AggregationBoot3IntegrationTest.Order("9")),
                                Mono.delay(Duration.ofMillis(150))
                                        .thenReturn(new AggregationBoot3IntegrationTest.Order("10"))),
                        AggregationBoot3IntegrationTest.Order.class))
                .expectNextMatches(order -> order.getOwner().name().equals("Ada"))
                .expectNextMatches(order -> order.getOwner().name().equals("Grace"))
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    private AggregationProperties properties() {
        return aggregator.properties();
    }

    private static MockResponse response(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
