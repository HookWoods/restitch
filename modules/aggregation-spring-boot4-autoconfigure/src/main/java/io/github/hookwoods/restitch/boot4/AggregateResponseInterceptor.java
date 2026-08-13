package io.github.hookwoods.restitch.boot4;

import io.github.hookwoods.restitch.api.AggregateResponse;
import io.github.hookwoods.restitch.api.AggregationResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.concurrent.CompletionStage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class AggregateResponseInterceptor {
    private final ReactiveAggregator reactiveAggregator;
    private final MvcAggregator mvcAggregator;
    private final FutureAggregator futureAggregator;

    public AggregateResponseInterceptor(
            ReactiveAggregator reactiveAggregator,
            MvcAggregator mvcAggregator,
            FutureAggregator futureAggregator) {
        this.reactiveAggregator = reactiveAggregator;
        this.mvcAggregator = mvcAggregator;
        this.futureAggregator = futureAggregator;
    }

    public Object invoke(Object target, Method method, Object... arguments) throws Throwable {
        Object result = invokeTarget(target, method, arguments);
        if (method.getAnnotation(AggregateResponse.class) == null || result == null) {
            return result;
        }
        Class<?> elementType = elementType(method.getGenericReturnType(), method.getReturnType());
        if (result instanceof Mono<?> mono) {
            if (containsResult(method.getGenericReturnType())) {
                return hydrateResultMono(mono, elementType);
            }
            return hydrateMono(mono, elementType);
        }
        if (result instanceof Flux<?> flux) {
            if (containsResult(method.getGenericReturnType())) {
                return hydrateResultFlux(flux, elementType);
            }
            return hydrateFlux(flux, elementType);
        }
        if (result instanceof CompletionStage<?> stage) {
            if (containsResult(method.getGenericReturnType())) {
                return stage.thenCompose(value -> futureAggregator.<Object>hydrateResult(value, objectType(elementType)));
            }
            return stage.thenCompose(value -> hydrateFuture(value, elementType));
        }
        if (containsResult(method.getGenericReturnType()) && result instanceof AggregationResult<?> existing) {
            return mvcAggregator.<Object>hydrateResult(existing.value(), objectType(elementType));
        }
        return hydrateMvc(result, elementType);
    }

    public Object intercept(Object target, Method method, Object... arguments) throws Throwable {
        return invoke(target, method, arguments);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Mono<?> hydrateMono(Mono<?> mono, Class<?> elementType) {
        return mono.flatMap(value -> reactiveAggregator.hydrate(value, (Class) elementType));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Flux<?> hydrateFlux(Flux<?> flux, Class<?> elementType) {
        return reactiveAggregator.stream((Flux) flux, (Class) elementType).items();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CompletionStage<?> hydrateFuture(Object value, Class<?> elementType) {
        return futureAggregator.hydrate(value, (Class) elementType);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object hydrateMvc(Object value, Class<?> elementType) {
        return mvcAggregator.hydrate(value, (Class) elementType);
    }

    private static Object invokeTarget(Object target, Method method, Object[] arguments) throws Throwable {
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private static Class<?> elementType(Type returnType, Class<?> fallback) {
        if (returnType instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1) {
                Type argument = arguments[0];
                if (argument instanceof Class<?> type) {
                    return type;
                }
                if (argument instanceof ParameterizedType nested && nested.getRawType() instanceof Class<?> type) {
                    Type[] nestedArguments = nested.getActualTypeArguments();
                    if (type == AggregationResult.class && nestedArguments.length == 1
                            && nestedArguments[0] instanceof Class<?> nestedType) {
                        return nestedType;
                    }
                    return type;
                }
            }
        }
        return fallback;
    }

    private static boolean containsResult(Type returnType) {
        if (!(returnType instanceof ParameterizedType parameterized)) {
            return returnType == AggregationResult.class;
        }
        if (parameterized.getRawType() == AggregationResult.class) {
            return true;
        }
        for (Type argument : parameterized.getActualTypeArguments()) {
            if (containsResult(argument)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Mono<?> hydrateResultMono(Mono<?> values, Class<?> elementType) {
        return values.flatMap(value -> reactiveAggregator.<Object>hydrateResult(value, objectType(elementType)));
    }

    @SuppressWarnings("unchecked")
    private Flux<?> hydrateResultFlux(Flux<?> values, Class<?> elementType) {
        return values.flatMap(value -> reactiveAggregator.<Object>hydrateResult(value, objectType(elementType)));
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> objectType(Class<?> type) {
        return (Class<Object>) type;
    }
}
