package io.github.restaggregation.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.restaggregation.core.AggregationResponseExtractor;
import io.github.restaggregation.json.JsonAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

class AggregationBoot4IntegrationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AggregationBoot4AutoConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void autoConfigurationUsesApplicationJackson3Mapper() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JsonAdapter.class);
            assertThat(context.getBean(JsonAdapter.class)).isInstanceOf(Jackson3JsonAdapter.class);
            assertThat(context).hasSingleBean(AggregateResponseInterceptor.class);
        });
    }

    @Test
    void userResponseExtractorReplacesDefault() {
        contextRunner.withBean(AggregationResponseExtractor.class, () -> (response, profile) -> response)
                .run(context -> assertThat(context).hasSingleBean(AggregationResponseExtractor.class));
    }
}
