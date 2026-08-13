package io.github.hookwoods.restitch.boot3;

import io.github.hookwoods.restitch.api.AggregateResponse;
import java.lang.reflect.Method;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.reactive.result.method.annotation.ResponseBodyResultHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Order(Ordered.HIGHEST_PRECEDENCE)
public final class AggregationWebFluxHandlerResultHandler implements HandlerResultHandler {
    private final ResponseBodyResultHandler delegate;
    private final AggregationResponseAdvice advice;

    public AggregationWebFluxHandlerResultHandler(
            ResponseBodyResultHandler delegate, AggregationResponseAdvice advice) {
        this.delegate = delegate;
        this.advice = advice;
    }

    @Override
    public boolean supports(HandlerResult result) {
        Method method = result.getReturnTypeSource().getMethod();
        return method != null && method.isAnnotationPresent(AggregateResponse.class)
                && delegate.supports(result);
    }

    @Override
    public Mono<Void> handleResult(ServerWebExchange exchange, HandlerResult result) {
        Method method = result.getReturnTypeSource().getMethod();
        Object intercepted = advice.intercept(method, result.getReturnValue());
        HandlerResult wrapped = new HandlerResult(
                result.getHandler(), intercepted, result.getReturnTypeSource(), result.getBindingContext());
        return delegate.handleResult(exchange, wrapped);
    }
}
