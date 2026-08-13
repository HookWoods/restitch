package io.github.hookwoods.restitch.boot4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.api.ErrorMode;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class MvcAggregatorTest {
    private MockWebServer server;
    private AggregationProperties properties;
    private MvcAggregator aggregator;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        properties = new AggregationProperties();
        properties.clients().put("identity", new AggregationProperties.Client(
                server.url("/").toString(), Duration.ofSeconds(1), Set.of()));
        properties.resolvers().put("owner", new AggregationProperties.Resolver(
                "identity", "/users/{id}", "/ownerId", "/data/user", ErrorMode.FAIL_FAST, null));
        aggregator = new MvcAggregator(properties, new Jackson3JsonAdapter(new ObjectMapper()), RestClient.builder());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void hydratesThroughRestClientAndResponsePointer() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}"));

        Order result = aggregator.hydrate(new Order("9"), Order.class);

        assertThat(result.owner).isEqualTo(new User("9", "Ada"));
    }

    @Test
    void usesConfiguredBatchEndpointForMvcResolution() throws InterruptedException {
        var batch = new AggregationProperties.Batch();
        batch.setPath("/users");
        batch.setQueryParameter("ids");
        batch.setItemsPointer("/data/users");
        batch.setItemKeyPointer("/id");
        properties.resolvers().put("owner", new AggregationProperties.Resolver(
                "identity", "/users/{id}", "/ownerId", "/data/user", ErrorMode.FAIL_FAST, batch));
        server.enqueue(new MockResponse().setResponseCode(200).setBody(
                "{\"data\":{\"users\":[{\"id\":\"9\",\"name\":\"Ada\"}]}}"));

        Order result = aggregator.hydrate(new Order("9"), Order.class);

        assertThat(result.owner).isEqualTo(new User("9", "Ada"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/users?ids=9");
    }

    @Test
    void appliesConfiguredTimeoutToMvcRequest() {
        properties.clients().get("identity").setTimeout(Duration.ofMillis(50));
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}")
                .setBodyDelay(500, TimeUnit.MILLISECONDS));

        assertThatThrownBy(() -> aggregator.hydrate(new Order("9"), Order.class))
                .hasMessageContaining("timed out");
    }

    @Test
    void rejectsOversizedBodyWhileReading() {
        properties.limits().setMaxResponseBytes(10);
        server.enqueue(new MockResponse().setResponseCode(200).setBody("12345678901234567890"));

        assertThatThrownBy(() -> aggregator.hydrate(new Order("9"), Order.class))
                .hasMessageContaining("maxResponseBytes");
    }

    @Test
    void rejectsAuthorityChangingResolverPath() {
        properties.resolvers().put("owner", new AggregationProperties.Resolver(
                "identity", "//attacker.example/users/{id}", "/ownerId", "/data/user", ErrorMode.FAIL_FAST, null));

        assertThatThrownBy(() -> aggregator.hydrate(new Order("9"), Order.class))
                .hasMessageContaining("relative");
        assertThat(server.getRequestCount()).isZero();
    }

    static final class Order {
        private final String ownerId;
        @AggregateRef("owner")
        private User owner;

        Order(String ownerId) {
            this.ownerId = ownerId;
        }
    }

    record User(String id, String name) {}
}
