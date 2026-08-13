package io.github.hookwoods.restitch.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hookwoods.restitch.api.AggregateResponse;
import io.github.hookwoods.restitch.core.AggregationObserver;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AggregationResponseAdviceTest {
    private MockWebServer server;
    private AggregationResponseAdvice advice;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        AggregationProperties properties = new AggregationProperties();
        AggregationProperties.Client identity = new AggregationProperties.Client();
        identity.setBaseUrl(server.url("/").toString());
        properties.getClients().put("identity", identity);
        AggregationProperties.Resolver resolver = new AggregationProperties.Resolver();
        resolver.setClient("identity");
        resolver.setPath("/users/{id}");
        resolver.setSourcePointer("/ownerId");
        resolver.setResponsePointer("/data/user");
        properties.getResolvers().put("order-owner", resolver);

        Jackson2JsonAdapter adapter = new Jackson2JsonAdapter(new ObjectMapper());
        ReactiveAggregator reactive = new ReactiveAggregator(properties, adapter, WebClient.builder().build(),
                (response, profile) -> response, (client, profile, uri, headers) -> headers,
                (profile, error) -> null, new AggregationObserver() {});
        MvcAggregator mvc = new MvcAggregator(properties, adapter, RestClient.builder().build());
        advice = new AggregationResponseAdvice(reactive, mvc, new FutureAggregator(mvc));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void wrapsAnnotatedPlainReturnWithMvcFacade() throws Exception {
        server.enqueue(userResponse());
        Object result = advice.intercept(method("plain"), new AggregationBoot3IntegrationTest.Order("9"));
        assertThat(((AggregationBoot3IntegrationTest.Order) result).getOwner().name()).isEqualTo("Ada");
    }

    @Test
    void wrapsMonoAndFluxWithReactiveFacade() throws Exception {
        server.enqueue(userResponse());
        Object mono = advice.intercept(method("mono"), Mono.just(new AggregationBoot3IntegrationTest.Order("9")));
        assertThat(((AggregationBoot3IntegrationTest.Order) ((Mono<?>) mono).block()).getOwner().name())
                .isEqualTo("Ada");

        server.enqueue(userResponse());
        Object flux = advice.intercept(method("flux"), Flux.just(new AggregationBoot3IntegrationTest.Order("9")));
        assertThat(((AggregationBoot3IntegrationTest.Order) ((Flux<?>) flux).blockFirst()).getOwner().name())
                .isEqualTo("Ada");
    }

    @Test
    void wrapsCompletionStageWithFutureFacade() throws Exception {
        server.enqueue(userResponse());
        Object result = advice.intercept(method("future"),
                CompletableFuture.completedFuture(
                        new AggregationBoot3IntegrationTest.Order("9")));
        assertThat(((AggregationBoot3IntegrationTest.Order) ((CompletionStage<?>) result).toCompletableFuture().join())
                .getOwner().name()).isEqualTo("Ada");
    }

    private Method method(String name) throws NoSuchMethodException {
        return Controller.class.getDeclaredMethod(name, AggregationBoot3IntegrationTest.Order.class);
    }

    private MockResponse userResponse() {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}");
    }

    static class Controller {
        @AggregateResponse("orders")
        AggregationBoot3IntegrationTest.Order plain(AggregationBoot3IntegrationTest.Order order) { return order; }

        @AggregateResponse("orders")
        Mono<AggregationBoot3IntegrationTest.Order> mono(AggregationBoot3IntegrationTest.Order order) {
            return Mono.just(order);
        }

        @AggregateResponse("orders")
        Flux<AggregationBoot3IntegrationTest.Order> flux(AggregationBoot3IntegrationTest.Order order) {
            return Flux.just(order);
        }

        @AggregateResponse("orders")
        CompletionStage<AggregationBoot3IntegrationTest.Order> future(
                AggregationBoot3IntegrationTest.Order order) {
            return CompletableFuture.completedFuture(order);
        }
    }
}
