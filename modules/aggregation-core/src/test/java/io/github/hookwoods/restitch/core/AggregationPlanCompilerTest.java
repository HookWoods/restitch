package io.github.hookwoods.restitch.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.api.ErrorMode;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AggregationPlanCompilerTest {
    private final AggregationPlanCompiler compiler = new AggregationPlanCompiler();

    @Test
    void compilerRejectsMissingResolver() {
        assertThatThrownBy(() -> compiler.compile(Order.class, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order-owner");
    }

    @Test
    void compilerAcceptsConfiguredResolverProfile() {
        ResolverProfile resolver = new ResolverProfile(
                "order-owner",
                "identity",
                "/users/{id}",
                "/ownerId",
                "/data/user",
                ErrorMode.NULL_FIELD,
                null);

        AggregationPlan plan = compiler.compile(Order.class, Map.of("order-owner", resolver));

        assertThat(plan.resolvers()).containsEntry("order-owner", resolver);
    }

    @Test
    void clientProfileKeepsPropagatedHeadersImmutable() {
        ClientProfile profile =
                new ClientProfile(URI.create("https://identity.internal"), Duration.ofMillis(500), Set.of("Authorization"));

        assertThat(profile.propagatedHeaders()).containsExactly("Authorization");
        assertThatThrownBy(() -> profile.propagatedHeaders().add("Cookie")).isInstanceOf(UnsupportedOperationException.class);
    }

    static final class Order {
        private String ownerId;

        @AggregateRef("order-owner")
        private User owner;
    }

    static final class User {
        private String id;
    }
}
