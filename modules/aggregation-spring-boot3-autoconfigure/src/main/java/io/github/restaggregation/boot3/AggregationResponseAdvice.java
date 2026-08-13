package io.github.restaggregation.boot3;

import io.github.restaggregation.api.AggregateResponse;
import io.github.restaggregation.api.AggregationResult;
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ControllerAdvice
public final class AggregationResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ReactiveAggregator reactiveAggregator;
    private final MvcAggregator mvcAggregator;
    private final FutureAggregator futureAggregator;

    public AggregationResponseAdvice(
            ReactiveAggregator reactiveAggregator, MvcAggregator mvcAggregator, FutureAggregator futureAggregator) {
        this.reactiveAggregator = reactiveAggregator;
        this.mvcAggregator = mvcAggregator;
        this.futureAggregator = futureAggregator;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Method method = returnType.getMethod();
        return method != null && method.isAnnotationPresent(AggregateResponse.class);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        Method method = returnType.getMethod();
        return method == null ? body : intercept(method, body);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object intercept(Method method, Object body) {
        if (!method.isAnnotationPresent(AggregateResponse.class) || body == null) {
            return body;
        }
        Class<?> elementType = elementType(method, body);
        boolean resultContainer = resultContainer(method);
        if (body instanceof Mono<?> mono) {
            return mono.flatMap(value -> resultContainer
                    ? reactiveAggregator.hydrateResult((Object) unwrapResult(value), (Class) elementType)
                    : reactiveAggregator.hydrate(value, (Class) elementType));
        }
        if (body instanceof Flux<?> flux) {
            return resultContainer
                    ? reactiveAggregator.hydrateResults(flux.cast(Object.class), (Class) elementType)
                    : reactiveAggregator.hydrateFlux(flux, elementType);
        }
        if (body instanceof CompletionStage<?> stage) {
            return stage.thenCompose(value -> resultContainer
                    ? futureAggregator.hydrateResult((Object) unwrapResult(value), (Class) elementType)
                    : futureAggregator.hydrate(value, (Class) elementType));
        }
        return resultContainer
                ? mvcAggregator.hydrateResult((Object) unwrapResult(body), (Class) elementType)
                : mvcAggregator.hydrate(body, (Class) elementType);
    }

    private static Class<?> elementType(Method method, Object body) {
        ResolvableType returnType = payloadType(method);
        if (AggregationResult.class.isAssignableFrom(returnType.resolve(Object.class))) {
            returnType = returnType.getGeneric(0);
        }
        Class<?> resolved = returnType.resolve();
        return resolved == null || resolved == Object.class ? bodyType(body) : resolved;
    }

    private static boolean resultContainer(Method method) {
        return AggregationResult.class.isAssignableFrom(payloadType(method).resolve(Object.class));
    }

    private static ResolvableType payloadType(Method method) {
        ResolvableType returnType = ResolvableType.forMethodReturnType(method);
        Class<?> raw = returnType.resolve(Object.class);
        if (Mono.class.isAssignableFrom(raw)
                || Flux.class.isAssignableFrom(raw)
                || CompletionStage.class.isAssignableFrom(raw)) {
            return returnType.getGeneric(0);
        }
        return returnType;
    }

    private static Object unwrapResult(Object value) {
        return value instanceof AggregationResult<?> result ? result.value() : value;
    }

    private static Class<?> bodyType(Object body) {
        if (body instanceof Mono<?> || body instanceof Flux<?> || body instanceof CompletionStage<?>) {
            return Object.class;
        }
        return body.getClass();
    }
}
