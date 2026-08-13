package io.github.hookwoods.restitch.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hookwoods.restitch.api.AggregateRef;
import io.github.hookwoods.restitch.json.JsonAdapter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

class AggregationBoot3IntegrationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class, AggregationBoot3AutoConfiguration.class))
            .withPropertyValues(
                    "aggregation.clients.identity.base-url=http://localhost:8080",
                    "aggregation.clients.identity.propagate-headers=Authorization",
                    "aggregation.resolvers.order-owner.client=identity",
                    "aggregation.resolvers.order-owner.path=/users/{id}",
                    "aggregation.resolvers.order-owner.source-pointer=/ownerId",
                    "aggregation.resolvers.order-owner.response-pointer=/data/user");

    @Test
    void yamlCreatesJackson2AdapterAndReactiveFacade() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(JsonAdapter.class);
            assertThat(context).hasSingleBean(Jackson2JsonAdapter.class);
            assertThat(context).hasSingleBean(ReactiveAggregator.class);
        });
    }

    @Test
    void userMapperIsUsedByJacksonAdapter() {
        contextRunner.withUserConfiguration(UserMapperConfiguration.class).run(context ->
                assertThat(context.getBean(Jackson2JsonAdapter.class).objectMapper())
                        .isSameAs(context.getBean(ObjectMapper.class)));
    }

    static class Order {
        private String ownerId;

        @AggregateRef("order-owner")
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

        public void setOwner(User owner) {
            this.owner = owner;
        }
    }

    record User(String id, String name) {}

    @Configuration(proxyBeanMethods = false)
    static class UserMapperConfiguration {
        @Bean
        @Primary
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
