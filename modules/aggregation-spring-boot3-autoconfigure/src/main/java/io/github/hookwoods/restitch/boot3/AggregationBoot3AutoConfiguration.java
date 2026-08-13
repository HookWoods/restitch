package io.github.hookwoods.restitch.boot3;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hookwoods.restitch.api.AggregationError;
import io.github.hookwoods.restitch.core.AggregationErrorMapper;
import io.github.hookwoods.restitch.core.AggregationObserver;
import io.github.hookwoods.restitch.core.AggregationRequestCustomizer;
import io.github.hookwoods.restitch.core.AggregationResponseExtractor;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;

@AutoConfiguration
@EnableConfigurationProperties(AggregationProperties.class)
public class AggregationBoot3AutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    Jackson2JsonAdapter aggregationJsonAdapter(ObjectMapper objectMapper) {
        return new Jackson2JsonAdapter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AggregationResponseExtractor.class)
    AggregationResponseExtractor aggregationResponseExtractor() {
        return (response, profile) -> response;
    }

    @Bean
    @ConditionalOnMissingBean(AggregationRequestCustomizer.class)
    AggregationRequestCustomizer aggregationRequestCustomizer() {
        return (client, profile, uri, headers) -> Map.copyOf(headers);
    }

    @Bean
    @ConditionalOnMissingBean(AggregationErrorMapper.class)
    AggregationErrorMapper aggregationErrorMapper() {
        return (profile, error) -> new AggregationError(profile.name(), "", "DOWNSTREAM", "");
    }

    @Bean
    @ConditionalOnMissingBean(AggregationObserver.class)
    AggregationObserver aggregationObserver() {
        return new AggregationObserver() {};
    }

    @Bean
    @ConditionalOnMissingBean
    WebClient aggregationWebClient() {
        return WebClient.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    RestClient.Builder aggregationRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    ReactiveAggregator reactiveAggregator(
            AggregationProperties properties,
            Jackson2JsonAdapter adapter,
            WebClient webClient,
            AggregationResponseExtractor responseExtractor,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        return new ReactiveAggregator(properties, adapter, webClient, responseExtractor, requestCustomizer,
                errorMapper, observer);
    }

    @Bean
    MvcAggregator mvcAggregator(
            AggregationProperties properties,
            Jackson2JsonAdapter adapter,
            RestClient.Builder restClientBuilder,
            AggregationResponseExtractor responseExtractor,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        return new MvcAggregator(properties, adapter, restClientBuilder, responseExtractor, requestCustomizer,
                errorMapper, observer);
    }

    @Bean
    FutureAggregator futureAggregator(MvcAggregator mvcAggregator) {
        return new FutureAggregator(mvcAggregator);
    }

    @Bean
    AggregationResponseAdvice aggregationResponseAdvice(
            ReactiveAggregator reactiveAggregator, MvcAggregator mvcAggregator, FutureAggregator futureAggregator) {
        return new AggregationResponseAdvice(reactiveAggregator, mvcAggregator, futureAggregator);
    }

    @Bean
    @ConditionalOnBean(ResponseBodyResultHandler.class)
    AggregationWebFluxHandlerResultHandler aggregationWebFluxHandlerResultHandler(
            ResponseBodyResultHandler delegate, AggregationResponseAdvice advice) {
        return new AggregationWebFluxHandlerResultHandler(delegate, advice);
    }
}
