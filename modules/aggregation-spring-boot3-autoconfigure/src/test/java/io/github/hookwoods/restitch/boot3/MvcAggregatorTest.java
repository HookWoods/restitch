package io.github.hookwoods.restitch.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.api.ErrorMode;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MvcAggregatorTest {
    private MockWebServer server;
    private AggregationProperties properties;
    private MvcAggregator aggregator;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new AggregationProperties();
        AggregationProperties.Client client = new AggregationProperties.Client();
        client.setBaseUrl(server.url("/").toString());
        client.setPropagateHeaders(List.of("Authorization"));
        properties.getClients().put("identity", client);
        AggregationProperties.Resolver owner = resolver("/users/{id}", "/data/user");
        properties.getResolvers().put("owner", owner);
        aggregator = new MvcAggregator(properties, new Jackson2JsonAdapter(new ObjectMapper()),
                RestClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void mvcResultModeReturnsMappedFieldError() {
        owner().setErrorMode(ErrorMode.RESULT);
        server.enqueue(response(500, "{\"error\":\"missing\"}"));

        var result = aggregator.hydrateResult(new GenerationMatrixTest.Order("9"), GenerationMatrixTest.Order.class);

        assertThat(result.value().getOwner()).isNull();
        assertThat(result.errors()).extracting(error -> error.resolver()).containsExactly("owner");
    }

    @Test
    void mvcHydratesNestedFieldsAndFiltersHeaders() throws InterruptedException {
        properties.getResolvers().put("manager", resolver("/managers/{id}", "/data/manager"));
        server.enqueue(response(200, "{\"data\":{\"user\":{\"id\":\"9\",\"managerId\":\"7\"}}}"));
        server.enqueue(response(200, "{\"data\":{\"manager\":{\"id\":\"7\",\"name\":\"Ada\"}}}"));

        NestedOrder result = aggregator.hydrate(new NestedOrder("9"), NestedOrder.class,
                Map.of("Authorization", "Bearer token", "Cookie", "private"));

        assertThat(result.owner.manager.name()).isEqualTo("Ada");
        RecordedRequest first = server.takeRequest();
        RecordedRequest second = server.takeRequest();
        assertThat(first.getHeader("Authorization")).isEqualTo("Bearer token");
        assertThat(first.getHeader("Cookie")).isNull();
        assertThat(second.getHeader("Authorization")).isEqualTo("Bearer token");
    }

    @Test
    void mvcHonorsConfiguredTimeout() {
        properties.getClients().get("identity").setTimeout(Duration.ofMillis(50));
        server.enqueue(response(200, "{\"data\":{\"user\":{\"id\":\"9\"}}}")
                .setBodyDelay(500, TimeUnit.MILLISECONDS));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> aggregator.hydrate(new GenerationMatrixTest.Order("9"), GenerationMatrixTest.Order.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void mvcRejectsOversizedResponseWhileReading() {
        properties.getLimits().setMaxResponseBytes(4);
        server.enqueue(response(200, "{\"data\":{\"user\":{\"id\":\"9\"}}}"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> aggregator.hydrate(new GenerationMatrixTest.Order("9"), GenerationMatrixTest.Order.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maxResponseBytes");
    }

    @Test
    void mvcBatchResolverCombinesCompatibleAnnotatedFields() throws InterruptedException {
        AggregationProperties.Batch batch = new AggregationProperties.Batch();
        batch.setPath("/users");
        batch.setQueryParameter("ids");
        batch.setItemsPointer("/data/users");
        batch.setItemKeyPointer("/id");
        batch.setMaxSize(10);
        owner().setBatch(batch);

        AggregationProperties.Resolver reviewer = resolver("/users/{id}", "/data/user");
        reviewer.setSourcePointer("/reviewerId");
        reviewer.setBatch(batch);
        properties.getResolvers().put("reviewer", reviewer);
        server.enqueue(response(200, "{\"data\":{\"users\":["
                + "{\"id\":\"9\",\"name\":\"Ada\"},"
                + "{\"id\":\"10\",\"name\":\"Grace\"}]}}"));

        BatchOrder result = aggregator.hydrate(new BatchOrder("9", "10"), BatchOrder.class);

        assertThat(result.getOwner().name()).isEqualTo("Ada");
        assertThat(result.getReviewer().name()).isEqualTo("Grace");
        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(server.takeRequest().getPath()).contains("/users?ids=9,10");
    }

    private AggregationProperties.Resolver owner() {
        return properties.getResolvers().get("owner");
    }

    private AggregationProperties.Resolver resolver(String path, String responsePointer) {
        AggregationProperties.Resolver resolver = new AggregationProperties.Resolver();
        resolver.setClient("identity");
        resolver.setPath(path);
        resolver.setSourcePointer(path.startsWith("/managers") ? "/managerId" : "/ownerId");
        resolver.setResponsePointer(responsePointer);
        return resolver;
    }

    private static MockResponse response(int status, String body) {
        return new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    static final class NestedOrder {
        private String ownerId;
        @AggregateRef("owner")
        private NestedUser owner;

        NestedOrder() {}

        NestedOrder(String ownerId) {
            this.ownerId = ownerId;
        }

        public String getOwnerId() { return ownerId; }
        public NestedUser getOwner() { return owner; }
        public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
        public void setOwner(NestedUser owner) { this.owner = owner; }
    }

    static final class NestedUser {
        private String id;
        private String managerId;
        @AggregateRef("manager")
        private Manager manager;

        public String getId() { return id; }
        public String getManagerId() { return managerId; }
        public Manager getManager() { return manager; }
        public void setId(String id) { this.id = id; }
        public void setManagerId(String managerId) { this.managerId = managerId; }
        public void setManager(Manager manager) { this.manager = manager; }
    }

    record Manager(String id, String name) {}

    static final class BatchOrder {
        private String ownerId;
        private String reviewerId;
        @AggregateRef("owner")
        private User owner;
        @AggregateRef("reviewer")
        private User reviewer;

        BatchOrder() {}

        BatchOrder(String ownerId, String reviewerId) {
            this.ownerId = ownerId;
            this.reviewerId = reviewerId;
        }

        public String getOwnerId() { return ownerId; }
        public String getReviewerId() { return reviewerId; }
        public User getOwner() { return owner; }
        public User getReviewer() { return reviewer; }
        public void setOwner(User owner) { this.owner = owner; }
        public void setReviewer(User reviewer) { this.reviewer = reviewer; }
    }

    record User(String id, String name) {}
}
