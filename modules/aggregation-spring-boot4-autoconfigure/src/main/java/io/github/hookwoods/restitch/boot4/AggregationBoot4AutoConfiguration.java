package io.github.hookwoods.restitch.boot4;

import io.github.hookwoods.restitch.api.AggregationError;
import io.github.hookwoods.restitch.core.AggregationErrorMapper;
import io.github.hookwoods.restitch.core.AggregationObserver;
import io.github.hookwoods.restitch.core.AggregationRequestCustomizer;
import io.github.hookwoods.restitch.core.AggregationResponseExtractor;
import io.github.hookwoods.restitch.json.JsonAdapter;
import java.util.Map;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@ConditionalOnClass({ObjectMapper.class, WebClient.class, RestClient.class})
@EnableConfigurationProperties(AggregationProperties.class)
public class AggregationBoot4AutoConfiguration {
    @ConditionalOnMissingBean(JsonAdapter.class)
    @Bean
    Jackson3JsonAdapter aggregationJsonAdapter(ObjectMapper mapper) {
        return new Jackson3JsonAdapter(mapper);
    }

    @ConditionalOnMissingBean(AggregationResponseExtractor.class)
    @Bean
    AggregationResponseExtractor aggregationResponseExtractor() {
        return (response, resolverProfile) -> response;
    }

    @ConditionalOnMissingBean(AggregationRequestCustomizer.class)
    @Bean
    AggregationRequestCustomizer aggregationRequestCustomizer() {
        return (client, resolver, uri, headers) -> Map.copyOf(headers);
    }

    @ConditionalOnMissingBean(AggregationErrorMapper.class)
    @Bean
    AggregationErrorMapper aggregationErrorMapper() {
        return (resolver, error) -> new AggregationError(resolver.name(), "", "DOWNSTREAM", "");
    }

    @ConditionalOnMissingBean(AggregationObserver.class)
    @Bean
    AggregationObserver aggregationObserver() {
        return new AggregationObserver() {};
    }

    @ConditionalOnMissingBean(WebClient.Builder.class)
    @Bean
    WebClient.Builder aggregationWebClientBuilder() {
        return WebClient.builder();
    }

    @ConditionalOnMissingBean(RestClient.Builder.class)
    @Bean
    RestClient.Builder aggregationRestClientBuilder() {
        return RestClient.builder();
    }

    @ConditionalOnMissingBean(ReactiveAggregator.class)
    @Bean
    ReactiveAggregator reactiveAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            AggregationResponseExtractor responseExtractor,
            WebClient.Builder webClientBuilder,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        return new ReactiveAggregator(properties, adapter, webClientBuilder, responseExtractor,
                requestCustomizer, errorMapper, observer);
    }

    @ConditionalOnMissingBean(MvcAggregator.class)
    @Bean
    MvcAggregator mvcAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            AggregationResponseExtractor responseExtractor,
            RestClient.Builder restClientBuilder,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        return new MvcAggregator(properties, adapter, responseExtractor, restClientBuilder,
                requestCustomizer, errorMapper, observer);
    }

    @ConditionalOnMissingBean(FutureAggregator.class)
    @Bean
    FutureAggregator futureAggregator(MvcAggregator mvcAggregator) {
        return new FutureAggregator(mvcAggregator);
    }

    @ConditionalOnMissingBean(AggregateResponseInterceptor.class)
    @Bean
    AggregateResponseInterceptor aggregateResponseInterceptor(
            ReactiveAggregator reactiveAggregator,
            MvcAggregator mvcAggregator,
            FutureAggregator futureAggregator) {
        return new AggregateResponseInterceptor(reactiveAggregator, mvcAggregator, futureAggregator);
    }

    @ConditionalOnMissingBean(AggregateResponseBeanPostProcessor.class)
    @Bean
    AggregateResponseBeanPostProcessor aggregateResponseBeanPostProcessor(
            AggregateResponseInterceptor aggregateResponseInterceptor) {
        return new AggregateResponseBeanPostProcessor(aggregateResponseInterceptor);
    }
}
