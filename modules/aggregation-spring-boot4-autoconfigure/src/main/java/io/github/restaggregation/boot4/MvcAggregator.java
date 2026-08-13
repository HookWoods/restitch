package io.github.restaggregation.boot4;

import io.github.restaggregation.api.AggregateRef;
import io.github.restaggregation.api.AggregationError;
import io.github.restaggregation.api.AggregationResult;
import io.github.restaggregation.api.ErrorMode;
import io.github.restaggregation.core.AggregationErrorMapper;
import io.github.restaggregation.core.AggregationLimits;
import io.github.restaggregation.core.AggregationObserver;
import io.github.restaggregation.core.AggregationPlanCompiler;
import io.github.restaggregation.core.AggregationRequestCustomizer;
import io.github.restaggregation.core.AggregationResponseExtractor;
import io.github.restaggregation.core.AggregationSession;
import io.github.restaggregation.core.BatchProfile;
import io.github.restaggregation.core.ClientProfile;
import io.github.restaggregation.core.ResolutionKey;
import io.github.restaggregation.core.ResolverProfile;
import io.github.restaggregation.json.JsonAdapter;
import io.github.restaggregation.json.JsonDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

public final class MvcAggregator {
    private final AggregationProperties properties;
    private final JsonAdapter adapter;
    private final AggregationResponseExtractor responseExtractor;
    private final AggregationRequestCustomizer requestCustomizer;
    private final AggregationErrorMapper errorMapper;
    private final AggregationObserver observer;
    private final RestClient.Builder restClientBuilder;

    public MvcAggregator(AggregationProperties properties, JsonAdapter adapter, RestClient.Builder restClientBuilder) {
        this(properties, adapter, (response, resolver) -> response, restClientBuilder,
                (client, profile, uri, headers) -> headers,
                (profile, error) -> new AggregationError(profile.name(), "", "DOWNSTREAM", ""),
                new AggregationObserver() {});
    }

    public MvcAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            AggregationResponseExtractor responseExtractor,
            RestClient.Builder restClientBuilder) {
        this(properties, adapter, responseExtractor, restClientBuilder,
                (client, profile, uri, headers) -> headers,
                (profile, error) -> new AggregationError(profile.name(), "", "DOWNSTREAM", ""),
                new AggregationObserver() {});
    }

    public MvcAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            AggregationResponseExtractor responseExtractor,
            RestClient.Builder restClientBuilder,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        this.properties = properties;
        this.adapter = adapter;
        this.responseExtractor = responseExtractor;
        this.restClientBuilder = restClientBuilder;
        this.requestCustomizer = requestCustomizer;
        this.errorMapper = errorMapper;
        this.observer = observer;
    }

    public AggregationProperties properties() {
        return properties;
    }

    public <T> T hydrate(T root, Class<T> rootType) {
        return hydrate(root, rootType, Map.of());
    }

    public <T> T hydrate(T root, Class<T> rootType, Map<String, String> inboundHeaders) {
        RequestState state = new RequestState(properties.aggregationLimits());
        try {
            new AggregationPlanCompiler(properties.aggregationLimits()).compile(rootType, properties.resolverProfiles());
            hydrateObject(root, rootType, state, filteredHeaders(inboundHeaders), 0);
            return root;
        } finally {
            state.close();
        }
    }

    public <T> AggregationResult<T> hydrateResult(T root, Class<T> rootType) {
        return hydrateResult(root, rootType, Map.of());
    }

    public <T> AggregationResult<T> hydrateResult(
            T root, Class<T> rootType, Map<String, String> inboundHeaders) {
        RequestState state = new RequestState(properties.aggregationLimits());
        try {
            new AggregationPlanCompiler(properties.aggregationLimits()).compile(rootType, properties.resolverProfiles());
            hydrateObject(root, rootType, state, filteredHeaders(inboundHeaders), 0);
            return new AggregationResult<>(root, state.errors());
        } finally {
            state.close();
        }
    }

    public <T> T aggregate(T root, Class<T> rootType) {
        return hydrate(root, rootType);
    }

    private void hydrateObject(
            Object root, Class<?> rootType, RequestState state, Map<String, String> inboundHeaders, int depth) {
        if (root == null) {
            return;
        }
        if (depth > properties.aggregationLimits().maxDepth()) {
            throw new AggregationLimitException("aggregation depth exceeds configured limit");
        }
        for (Field field : rootType.getDeclaredFields()) {
            if (field.isAnnotationPresent(AggregateRef.class)) {
                hydrateField(root, field, state, inboundHeaders, depth);
            }
        }
    }

    private void hydrateField(
            Object root, Field field, RequestState state, Map<String, String> inboundHeaders, int depth) {
        String resolverName = field.getAnnotation(AggregateRef.class).value();
        ResolverProfile resolver = properties.resolverProfiles().get(resolverName);
        if (resolver == null) {
            throw new IllegalArgumentException("missing resolver profile " + resolverName);
        }
        try {
            Object sourceId = sourceValue(root, resolver.sourcePointer());
            if (sourceId == null) {
                return;
            }
            ClientProfile client = properties.clientProfiles().get(resolver.client());
            if (client == null) {
                throw new IllegalArgumentException("missing client profile " + resolver.client());
            }
            String path = resolver.path().replace("{id}", UriUtils.encodePathSegment(String.valueOf(sourceId), StandardCharsets.UTF_8));
            URI uri = resolveUri(client, path);
            Map<String, String> headers = allowlistedHeaders(client, inboundHeaders);
            String extractor = resolver.responsePointer() == null || resolver.responsePointer().isBlank()
                    ? "response" : resolver.responsePointer();
            ResolutionKey key = ResolutionKey.of(resolver.client(), path, extractor, field.getType(), headers);
            Object value = state.session.memoize(key, Object.class,
                    () -> fetch(client, uri, resolver, String.valueOf(sourceId), field.getType(), headers, state));
            setField(field, root, value);
            hydrateObject(value, field.getType(), state, inboundHeaders, depth + 1);
        } catch (Throwable error) {
            if (error instanceof AggregationLimitException) {
                throw error instanceof RuntimeException runtime ? runtime : new IllegalStateException(error);
            }
            if (resolver.errorMode() == ErrorMode.NULL_FIELD) {
                setField(field, root, null);
            } else if (resolver.errorMode() == ErrorMode.KEEP_SOURCE_ID) {
                return;
            } else if (resolver.errorMode() == ErrorMode.RESULT) {
                state.addError(errorMapper.map(resolver, error));
            } else {
                throw error instanceof RuntimeException runtime ? runtime : new IllegalStateException(error);
            }
        }
    }

    private Object fetch(
            ClientProfile client,
            URI requestUri,
            ResolverProfile resolver,
            String sourceId,
            Class<?> targetType,
            Map<String, String> headers,
            RequestState state) {
        state.requestSlot();
        ResolutionKey key = ResolutionKey.of(resolver.client(), requestUri.getPath(), resolver.name(), targetType, headers);
        observer.resolutionStarted(key);
        try {
            URI resolvedRequestUri = resolver.batch() == null
                    ? requestUri
                    : batchUri(client, resolver.batch(), sourceId);
            byte[] body = request(client, resolver, resolvedRequestUri, headers);
            Object value = resolver.batch() == null
                    ? singleValue(body, resolver, targetType)
                    : batchValue(body, resolver.batch(), sourceId, targetType);
            observer.resolutionSucceeded(key);
            return value;
        } catch (RuntimeException error) {
            observer.resolutionFailed(key, error);
            throw error;
        }
    }

    private Object singleValue(byte[] body, ResolverProfile resolver, Class<?> targetType) {
        var extracted = responseExtractor.extract(adapter.parse(body), resolver);
        if (resolver.responsePointer() != null && !resolver.responsePointer().isBlank()) {
            extracted = adapter.at(extracted, resolver.responsePointer());
        }
        return adapter.toValue(extracted, targetType);
    }

    private Object batchValue(byte[] body, BatchProfile batch,
            String sourceId, Class<?> targetType) {
        JsonDocument items = adapter.at(adapter.parse(body), batch.itemsPointer());
        List<?> rawItems = adapter.toValue(items, List.class);
        Jackson3JsonAdapter jackson3Adapter = (Jackson3JsonAdapter) adapter;
        for (Object rawItem : rawItems) {
            JsonDocument item = jackson3Adapter.toDocument(rawItem);
            if (sourceId.equals(adapter.text(adapter.at(item, batch.itemKeyPointer())))) {
                return adapter.toValue(item, targetType);
            }
        }
        throw new IllegalStateException("batch item was not returned");
    }

    private byte[] request(
            ClientProfile client, ResolverProfile resolver, URI requestUri, Map<String, String> headers) {
        Map<String, String> customized = customizeHeaders(client, resolver, requestUri, headers);
        return restClientBuilder.clone()
                .requestFactory(requestFactory(client.timeout()))
                .build()
                .get()
                .uri(requestUri)
                .headers(outbound -> customized.forEach(outbound::set))
                .exchange((request, response) -> {
                    byte[] responseBody = readBody(response.getBody(), properties.aggregationLimits().maxResponseBytes());
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("downstream status " + response.getStatusCode().value());
                    }
                    return responseBody;
                });
    }

    private static URI batchUri(
            ClientProfile client, BatchProfile batch, String sourceId) {
        URI base = resolveUri(client, batch.path());
        return URI.create(base + "?" + batch.queryParameter() + "="
                + UriUtils.encodeQueryParam(sourceId, StandardCharsets.UTF_8));
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    private Map<String, String> customizeHeaders(
            ClientProfile client, ResolverProfile resolver, URI uri, Map<String, String> headers) {
        Map<String, String> customized = requestCustomizer.customize(client, resolver, uri, headers);
        return Map.copyOf(customized == null ? Map.of() : customized);
    }

    private static byte[] readBody(InputStream input, long maxBytes) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new AggregationLimitException("response exceeds maxResponseBytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void setField(Field field, Object root, Object value) {
        try {
            field.setAccessible(true);
            field.set(root, value);
        } catch (IllegalAccessException error) {
            throw new IllegalStateException("cannot set aggregate field " + field.getName(), error);
        }
    }

    private static Object sourceValue(Object root, String pointer) throws IllegalAccessException {
        if (pointer == null || pointer.isBlank() || "/".equals(pointer)) {
            return root;
        }
        Object current = root;
        for (String token : pointer.substring(1).split("/")) {
            String name = token.replace("~1", "/").replace("~0", "~");
            if (current == null) {
                return null;
            }
            Field field = findField(current.getClass(), name);
            field.setAccessible(true);
            current = field.get(current);
        }
        return current;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalArgumentException("missing source field " + name);
    }

    private static Map<String, String> allowlistedHeaders(ClientProfile client, Map<String, String> inboundHeaders) {
        return inboundHeaders.entrySet().stream()
                .filter(entry -> client.propagatedHeaders().stream().anyMatch(name -> name.equalsIgnoreCase(entry.getKey())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<String, String> filteredHeaders(Map<String, String> headers) {
        return headers == null ? Map.of() : Map.copyOf(headers);
    }

    private static URI resolveUri(ClientProfile client, String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            throw new IllegalArgumentException("aggregation path must be relative to the configured client");
        }
        URI resolved = client.baseUri().resolve(path);
        if (!Objects.equals(client.baseUri().getScheme(), resolved.getScheme())
                || !Objects.equals(client.baseUri().getHost(), resolved.getHost())
                || client.baseUri().getPort() != resolved.getPort()) {
            throw new IllegalArgumentException("aggregation path must use the configured client host");
        }
        return resolved;
    }

    private static final class RequestState implements AutoCloseable {
        private final AggregationSession session;
        private final AggregationLimits limits;
        private final AtomicInteger requests = new AtomicInteger();
        private final List<AggregationError> errors = new ArrayList<>();

        private RequestState(AggregationLimits limits) {
            this.limits = limits;
            this.session = new AggregationSession(limits);
        }

        private void requestSlot() {
            if (requests.incrementAndGet() > limits.maxRequests()) {
                throw new AggregationLimitException("aggregation request limit exceeded");
            }
        }

        private void addError(AggregationError error) {
            errors.add(error);
        }

        private List<AggregationError> errors() {
            return List.copyOf(errors);
        }

        @Override
        public void close() {
            session.close();
        }
    }
}
