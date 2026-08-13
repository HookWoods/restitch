package fr.hookwood.restitch.boot4;

import fr.hookwood.restitch.api.AggregateRef;
import fr.hookwood.restitch.api.AggregationError;
import fr.hookwood.restitch.api.AggregationResult;
import fr.hookwood.restitch.api.ErrorMode;
import fr.hookwood.restitch.api.PageMetadata;
import fr.hookwood.restitch.core.AggregationErrorMapper;
import fr.hookwood.restitch.core.AggregationLimits;
import fr.hookwood.restitch.core.AggregationObserver;
import fr.hookwood.restitch.core.AggregationPlanCompiler;
import fr.hookwood.restitch.core.AggregationRequestCustomizer;
import fr.hookwood.restitch.core.AggregationResponseExtractor;
import fr.hookwood.restitch.core.AggregationSession;
import fr.hookwood.restitch.core.BatchProfile;
import fr.hookwood.restitch.core.ClientProfile;
import fr.hookwood.restitch.core.ResolutionKey;
import fr.hookwood.restitch.core.ResolverProfile;
import fr.hookwood.restitch.json.JsonAdapter;
import fr.hookwood.restitch.json.JsonDocument;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Objects;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import tools.jackson.databind.ObjectMapper;

public final class ReactiveAggregator {
    public static final String INBOUND_HEADERS_CONTEXT_KEY = ReactiveAggregator.class.getName() + ".headers";

    private final AggregationProperties properties;
    private final JsonAdapter adapter;
    private final WebClient.Builder webClientBuilder;
    private final AggregationResponseExtractor responseExtractor;
    private final AggregationRequestCustomizer requestCustomizer;
    private final AggregationErrorMapper errorMapper;
    private final AggregationObserver observer;

    public ReactiveAggregator(AggregationProperties properties, JsonAdapter adapter, WebClient.Builder webClientBuilder) {
        this(properties, adapter, webClientBuilder, (response, profile) -> response,
                (client, profile, uri, headers) -> headers,
                (profile, error) -> new AggregationError(profile.name(), "", "DOWNSTREAM", ""),
                new AggregationObserver() {});
    }

    public ReactiveAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            AggregationResponseExtractor responseExtractor,
            WebClient.Builder webClientBuilder) {
        this(properties, adapter, webClientBuilder, responseExtractor,
                (client, profile, uri, headers) -> headers,
                (profile, error) -> new AggregationError(profile.name(), "", "DOWNSTREAM", ""),
                new AggregationObserver() {});
    }

    public ReactiveAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            WebClient.Builder webClientBuilder,
            AggregationResponseExtractor responseExtractor,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        this.properties = properties;
        this.adapter = adapter;
        this.webClientBuilder = webClientBuilder;
        this.responseExtractor = responseExtractor;
        this.requestCustomizer = requestCustomizer;
        this.errorMapper = errorMapper;
        this.observer = observer;
    }

    public AggregationProperties properties() {
        return properties;
    }

    public <T> Mono<T> hydrate(T root, Class<T> rootType) {
        return Mono.deferContextual(context -> hydrate(root, rootType, contextHeaders(context)));
    }

    public <T> Mono<T> hydrate(T root, Class<T> rootType, Map<String, String> inboundHeaders) {
        return Mono.using(
                () -> new RequestState(properties.aggregationLimits()),
                state -> {
                    new AggregationPlanCompiler(properties.aggregationLimits()).compile(rootType, properties.resolverProfiles());
                    return hydrateObject(root, rootType, state, filteredHeaders(inboundHeaders), 0).thenReturn(root);
                },
                RequestState::close);
    }

    public <T> Mono<AggregationResult<T>> hydrateResult(T root, Class<T> rootType) {
        return Mono.deferContextual(context -> hydrateResult(root, rootType, contextHeaders(context)));
    }

    public <T> Mono<AggregationResult<T>> hydrateResult(
            T root, Class<T> rootType, Map<String, String> inboundHeaders) {
        return Mono.using(
                () -> new RequestState(properties.aggregationLimits()),
                state -> {
                    new AggregationPlanCompiler(properties.aggregationLimits()).compile(rootType, properties.resolverProfiles());
                    return hydrateObject(root, rootType, state, filteredHeaders(inboundHeaders), 0)
                            .then(Mono.fromSupplier(() -> new AggregationResult<>(root, state.errors())));
                },
                RequestState::close);
    }

    public <T> Mono<T> aggregate(T root, Class<T> rootType) {
        return hydrate(root, rootType);
    }

    public <T> Flux<T> hydrate(Flux<T> roots, Class<T> rootType) {
        return stream(roots, rootType).items();
    }

    public <T> Flux<T> hydrate(Flux<T> roots, Class<T> rootType, Map<String, String> inboundHeaders) {
        return stream(roots, rootType, inboundHeaders, Mono.empty()).items();
    }

    public <T> ReactiveAggregationStream<T> stream(Flux<T> roots, Class<T> rootType) {
        return stream(roots, rootType, Mono.empty());
    }

    public <T> ReactiveAggregationStream<T> stream(
            Flux<T> roots, Class<T> rootType, Mono<PageMetadata> metadata) {
        return stream(roots, rootType, Map.of(), metadata);
    }

    public <T> ReactiveAggregationStream<T> stream(
            Flux<T> roots,
            Class<T> rootType,
            Map<String, String> inboundHeaders,
            Mono<PageMetadata> metadata) {
        AggregationLimits limits = properties.aggregationLimits();
        int bufferSize = Math.max(1, Math.min(limits.maxBufferedItems(),
                Math.min(limits.maxBatchSize(), configuredBatchSize())));
        Map<String, String> headers = filteredHeaders(inboundHeaders);
        Flux<T> hydrated = Flux.using(
                () -> new RequestState(limits),
                state -> roots.bufferTimeout(bufferSize, limits.batchFlushWindow())
                        .concatMap(batch -> hydrateBatch(batch, rootType, state, headers), limits.streamPrefetch()),
                RequestState::close);
        return new ReactiveAggregationStream<>(hydrated, metadata == null ? Mono.empty() : metadata);
    }

    public <T> ReactiveAggregationStream<T> stream(String rootProfile, Class<T> rootType) {
        return stream(rootProfile, rootType, Map.of());
    }

    public <T> ReactiveAggregationStream<T> stream(
            String rootProfile, Class<T> rootType, Map<String, String> inboundHeaders) {
        return new ReactiveAggregationStream<>(
                Flux.deferContextual(context -> streamRoot(rootProfile, rootType, contextHeadersOr(inboundHeaders, context))),
                Mono.empty());
    }

    private <T> Flux<T> streamRoot(String rootProfile, Class<T> rootType, Map<String, String> inboundHeaders) {
        AggregationProperties.Root root = properties.roots().get(rootProfile);
        if (root == null) {
            return Flux.error(new IllegalArgumentException("unknown root profile " + rootProfile));
        }
        ClientProfile client = properties.clientProfiles().get(root.getClient());
        if (client == null) {
            return Flux.error(new IllegalArgumentException("unknown client " + root.getClient()));
        }
        URI uri = resolveUri(client, root.getPath());
        String itemsPointer = root.getItemsPointer();
        if (itemsPointer == null || itemsPointer.isBlank()) {
            itemsPointer = root.getResponsePointer();
        }
        String configuredItemsPointer = itemsPointer == null ? "" : itemsPointer;
        AggregationLimits limits = properties.aggregationLimits();
        Map<String, String> headers = filteredHeaders(inboundHeaders);
        return Flux.using(
                () -> new RequestState(limits),
                state -> {
                    new AggregationPlanCompiler(limits).compile(rootType, properties.resolverProfiles());
                    state.requestSlot();
                    return streamDocuments(client, uri, configuredItemsPointer, limits, headers)
                            .concatMap(document -> {
                                T value = adapter.toValue(document, rootType);
                                return hydrateObject(value, rootType, state, headers, 0).thenReturn(value);
                            }, 1);
                },
                RequestState::close);
    }

    private Flux<JsonDocument> streamDocuments(
            ClientProfile client, URI uri, String itemsPointer, AggregationLimits limits, Map<String, String> headers) {
        String extractor = itemsPointer.isBlank() ? "root" : itemsPointer;
        ResolutionKey key = ResolutionKey.of(client.baseUri().toString(), uri.getPath(), extractor, Object.class, headers);
        Map<String, String> customized = allowlistedHeaders(client, headers);
        return webClientBuilder.clone().build().get().uri(uri).headers(outbound -> customized.forEach(outbound::set))
                .exchangeToFlux(response -> {
                    if (response.statusCode().isError()) {
                        return response.releaseBody().thenMany(Flux.error(
                                new IllegalStateException("downstream status " + response.statusCode().value())));
                    }
                    Jackson3CollectionParser parser = new Jackson3CollectionParser(
                            jacksonMapper(),
                            (Jackson3JsonAdapter) adapter,
                            itemsPointer,
                            limits.maxObjectBytes(),
                            limits.maxBufferedItems());
                    AtomicLong responseBytes = new AtomicLong();
                    observer.resolutionStarted(key);
                    return response.bodyToFlux(DataBuffer.class)
                            .concatMap(buffer -> {
                                int readable = buffer.readableByteCount();
                                long total = responseBytes.addAndGet(readable);
                                try {
                                    if (total > limits.maxResponseBytes()) {
                                        return Flux.error(new AggregationLimitException("response exceeds maxResponseBytes"));
                                    }
                                    byte[] bytes = new byte[readable];
                                    buffer.read(bytes);
                                    return Flux.fromIterable(parser.feed(bytes));
                                } finally {
                                    DataBufferUtils.release(buffer);
                                }
                            }, limits.streamPrefetch())
                            .concatWith(Flux.defer(() -> Flux.fromIterable(parser.end()))
                                    .repeat(parser::hasPendingItems))
                            .doOnComplete(() -> observer.resolutionSucceeded(key))
                            .doOnError(error -> observer.resolutionFailed(key, error))
                            .doFinally(signal -> {
                                try {
                                    parser.close();
                                } catch (Exception ignored) {
                                }
                            });
                })
                .timeout(client.timeout());
    }

    private <T> Flux<T> hydrateBatch(
            List<T> roots, Class<T> rootType, RequestState state, Map<String, String> headers) {
        return Flux.fromArray(rootType.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(AggregateRef.class))
                .concatMap(field -> hydrateBatchField(roots, field, state, headers))
                .thenMany(Flux.fromIterable(roots));
    }

    private <T> Mono<Void> hydrateBatchField(
            List<T> roots, Field field, RequestState state, Map<String, String> headers) {
        String resolverName = field.getAnnotation(AggregateRef.class).value();
        ResolverProfile resolver = properties.resolverProfiles().get(resolverName);
        if (resolver == null) {
            return Mono.error(new IllegalArgumentException("missing resolver profile " + resolverName));
        }
        BatchProfile batch = resolver.batch();
        if (batch == null) {
            return Flux.fromIterable(roots)
                    .concatMap(root -> hydrateField(root, field, state, headers, 0))
                    .then();
        }
        return fetchBatchValues(roots, field, resolver, batch, state, headers)
                .flatMapMany(values -> Flux.fromIterable(roots)
                        .concatMap(root -> applyBatchValue(root, field, resolver, values, state, headers)))
                .onErrorResume(error -> Flux.fromIterable(roots)
                        .concatMap(root -> fieldError(root, field, resolver, error, state)))
                .then();
    }

    private <T> Mono<Map<String, Object>> fetchBatchValues(
            List<T> roots,
            Field field,
            ResolverProfile resolver,
            BatchProfile batch,
            RequestState state,
            Map<String, String> inboundHeaders) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        try {
            for (Object root : roots) {
                Object sourceId = sourceValue(root, resolver.sourcePointer());
                if (sourceId != null) {
                    ids.add(String.valueOf(sourceId));
                }
            }
        } catch (Exception error) {
            return Mono.error(error);
        }
        if (ids.isEmpty()) {
            return Mono.just(Map.of());
        }
        if (ids.size() > properties.aggregationLimits().maxPendingIds()) {
            return Mono.error(new AggregationLimitException("batch identifier limit exceeded"));
        }
        ClientProfile client = properties.clientProfiles().get(resolver.client());
        if (client == null) {
            return Mono.error(new IllegalArgumentException("missing client profile " + resolver.client()));
        }
        Map<String, String> headers = allowlistedHeaders(client, inboundHeaders);
        String query = URLEncoder.encode(String.join(",", ids), StandardCharsets.UTF_8);
        String path = batch.path() + "?" + batch.queryParameter() + "=" + query;
        URI uri = resolveUri(client, path);
        ResolutionKey key = ResolutionKey.of(resolver.client(), path,
                batch.itemsPointer() + "|" + batch.itemKeyPointer(), Map.class, headers);
        CompletionStage<Map<String, Object>> future = state.session.memoize(key, CompletionStage.class,
                () -> fetchBatch(client, uri, resolver, batch, field.getType(), headers, state).toFuture());
        return Mono.fromCompletionStage(future);
    }

    private Mono<Map<String, Object>> fetchBatch(
            ClientProfile client,
            URI uri,
            ResolverProfile resolver,
            BatchProfile batch,
            Class<?> targetType,
            Map<String, String> headers,
            RequestState state) {
        state.requestSlot();
        Mono<Map<String, Object>> result = request(client, resolver, uri, headers, state)
                .map(body -> {
                    JsonDocument items = adapter.at(adapter.parse(body), batch.itemsPointer());
                    List<?> rawItems = adapter.toValue(items, List.class);
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (Object rawItem : rawItems) {
                        JsonDocument itemDocument = ((Jackson3JsonAdapter) adapter).toDocument(rawItem);
                        String itemKey = adapter.text(adapter.at(itemDocument, batch.itemKeyPointer()));
                        if (itemKey != null) {
                            values.put(itemKey, adapter.toValue(itemDocument, targetType));
                        }
                    }
                    return values;
                });
        return result;
    }

    private Mono<Void> applyBatchValue(
            Object root,
            Field field,
            ResolverProfile resolver,
            Map<String, Object> values,
            RequestState state,
            Map<String, String> inboundHeaders) {
        try {
            Object sourceId = sourceValue(root, resolver.sourcePointer());
            Object value = values.get(String.valueOf(sourceId));
            if (value == null) {
                return fieldError(root, field, resolver,
                        new IllegalStateException("batch item was not returned"), state);
            }
            return setField(root, field, value).then(hydrateObject(value, field.getType(), state, inboundHeaders, 1));
        } catch (Exception error) {
            return fieldError(root, field, resolver, error, state);
        }
    }

    private Mono<Void> hydrateObject(
            Object root, Class<?> rootType, RequestState state, Map<String, String> inboundHeaders, int depth) {
        if (root == null) {
            return Mono.empty();
        }
        if (depth > properties.aggregationLimits().maxDepth()) {
            return Mono.error(new AggregationLimitException("aggregation depth exceeds configured limit"));
        }
        return Flux.fromArray(rootType.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(AggregateRef.class))
                .concatMap(field -> hydrateField(root, field, state, inboundHeaders, depth))
                .then();
    }

    private Mono<Void> hydrateField(
            Object root, Field field, RequestState state, Map<String, String> inboundHeaders, int depth) {
        String resolverName = field.getAnnotation(AggregateRef.class).value();
        ResolverProfile resolver = properties.resolverProfiles().get(resolverName);
        if (resolver == null) {
            return Mono.error(new IllegalArgumentException("missing resolver profile " + resolverName));
        }
        Object sourceId;
        try {
            sourceId = sourceValue(root, resolver.sourcePointer());
        } catch (Exception error) {
            return fieldError(root, field, resolver, error, state);
        }
        if (sourceId == null) {
            return Mono.empty();
        }
        ClientProfile client = properties.clientProfiles().get(resolver.client());
        if (client == null) {
            return fieldError(root, field, resolver,
                    new IllegalArgumentException("missing client profile " + resolver.client()), state);
        }
        String path = resolver.path().replace("{id}", UriUtils.encodePathSegment(String.valueOf(sourceId), StandardCharsets.UTF_8));
        URI uri = resolveUri(client, path);
        Map<String, String> headers = allowlistedHeaders(client, inboundHeaders);
        String extractor = resolver.responsePointer() == null || resolver.responsePointer().isBlank()
                ? "response" : resolver.responsePointer();
        ResolutionKey key = ResolutionKey.of(resolver.client(), path, extractor, field.getType(), headers);
        CompletionStage<Object> future = state.session.memoize(key, CompletionStage.class,
                () -> fetch(client, uri, resolver, field.getType(), headers, state).toFuture());
        return Mono.fromCompletionStage(future)
                .flatMap(value -> setField(root, field, value)
                        .then(hydrateObject(value, field.getType(), state, inboundHeaders, depth + 1)))
                .onErrorResume(error -> fieldError(root, field, resolver, error, state));
    }

    private Mono<Object> fetch(
            ClientProfile client,
            URI uri,
            ResolverProfile resolver,
            Class<?> targetType,
            Map<String, String> headers,
            RequestState state) {
        state.requestSlot();
        ResolutionKey key = ResolutionKey.of(resolver.client(), uri.getPath(), resolver.name(), targetType, headers);
        observer.resolutionStarted(key);
        Mono<Object> result = request(client, resolver, uri, headers, state)
                .map(body -> {
                    JsonDocument document = adapter.parse(body);
                    JsonDocument extracted = responseExtractor.extract(document, resolver);
                    if (resolver.responsePointer() != null && !resolver.responsePointer().isBlank()) {
                        extracted = adapter.at(extracted, resolver.responsePointer());
                    }
                    return adapter.toValue(extracted, targetType);
                });
        return result.doOnSuccess(value -> observer.resolutionSucceeded(key))
                .doOnError(error -> observer.resolutionFailed(key, error));
    }

    private Mono<byte[]> request(
            ClientProfile client,
            ResolverProfile resolver,
            URI requestUri,
            Map<String, String> inboundHeaders,
            RequestState state) {
        Map<String, String> customized = customizeHeaders(client, resolver, requestUri, inboundHeaders);
        return webClientBuilder.clone().build().get().uri(requestUri)
                .headers(outbound -> customized.forEach(outbound::set))
                .exchangeToMono(response -> {
                    Mono<byte[]> body = readBody(response.bodyToFlux(DataBuffer.class),
                            properties.aggregationLimits().maxResponseBytes());
                    if (response.statusCode().isError()) {
                        return body.flatMap(bytes -> Mono.error(
                                new IllegalStateException("downstream status " + response.statusCode().value())));
                    }
                    return body;
                })
                .timeout(client.timeout());
    }

    private Mono<Void> fieldError(
            Object root, Field field, ResolverProfile resolver, Throwable error, RequestState state) {
        if (isLimitViolation(error)) {
            return Mono.error(error);
        }
        if (resolver.errorMode() == ErrorMode.NULL_FIELD) {
            return setField(root, field, null);
        }
        if (resolver.errorMode() == ErrorMode.KEEP_SOURCE_ID) {
            return Mono.empty();
        }
        if (resolver.errorMode() == ErrorMode.RESULT) {
            state.addError(errorMapper.map(resolver, error));
            return Mono.empty();
        }
        return Mono.error(error);
    }

    private Map<String, String> customizeHeaders(
            ClientProfile client, ResolverProfile resolver, URI uri, Map<String, String> headers) {
        Map<String, String> allowed = allowlistedHeaders(client, headers);
        Map<String, String> customized = requestCustomizer.customize(client, resolver, uri, allowed);
        return Map.copyOf(customized == null ? Map.of() : customized);
    }

    private static Mono<Void> setField(Object root, Field field, Object value) {
        return Mono.fromRunnable(() -> {
            try {
                field.setAccessible(true);
                field.set(root, value);
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("cannot set aggregate field " + field.getName(), error);
            }
        });
    }

    private static Object sourceValue(Object root, String pointer) throws IllegalAccessException {
        if (pointer == null || pointer.isBlank() || "/".equals(pointer)) {
            return root;
        }
        Object current = root;
        for (String token : pointer.substring(1).split("/")) {
            String fieldName = token.replace("~1", "/").replace("~0", "~");
            if (current == null) {
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(fieldName);
                continue;
            }
            Field field = findField(current.getClass(), fieldName);
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
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> header : inboundHeaders.entrySet()) {
            if (client.propagatedHeaders().stream().anyMatch(name -> name.equalsIgnoreCase(header.getKey()))) {
                result.put(header.getKey(), header.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private int configuredBatchSize() {
        return properties.resolvers().values().stream()
                .map(AggregationProperties.Resolver::getBatch)
                .filter(Objects::nonNull)
                .mapToInt(AggregationProperties.Batch::getMaxSize)
                .min()
                .orElse(properties.aggregationLimits().maxBatchSize());
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

    private static <T> Mono<byte[]> readBody(Flux<DataBuffer> body, long maxBytes) {
        AtomicLong size = new AtomicLong();
        return body.reduceWith(ByteArrayOutputStream::new, (output, buffer) -> {
                    int readable = buffer.readableByteCount();
                    long total = size.addAndGet(readable);
                    try {
                        if (total > maxBytes) {
                            throw new AggregationLimitException("response exceeds maxResponseBytes");
                        }
                        byte[] bytes = new byte[readable];
                        buffer.read(bytes);
                        output.writeBytes(bytes);
                        return output;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .map(ByteArrayOutputStream::toByteArray);
    }

    private ObjectMapper jacksonMapper() {
        if (!(adapter instanceof Jackson3JsonAdapter jackson3Adapter)) {
            throw new IllegalStateException("Boot 4 aggregation requires the Jackson 3 adapter");
        }
        return jackson3Adapter.objectMapper();
    }

    private static Map<String, String> filteredHeaders(Map<String, String> headers) {
        return headers == null ? Map.of() : Map.copyOf(headers);
    }

    private static Map<String, String> contextHeadersOr(
            Map<String, String> headers, ContextView context) {
        if (context.hasKey(INBOUND_HEADERS_CONTEXT_KEY)) {
            return context.get(INBOUND_HEADERS_CONTEXT_KEY);
        }
        return headers;
    }

    private static Map<String, String> contextHeaders(ContextView context) {
        return context.hasKey(INBOUND_HEADERS_CONTEXT_KEY) ? context.get(INBOUND_HEADERS_CONTEXT_KEY) : Map.of();
    }

    private static boolean isLimitViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AggregationLimitException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class RequestState implements AutoCloseable {
        private final AggregationSession session;
        private final AggregationLimits limits;
        private final AtomicInteger requests = new AtomicInteger();
        private final List<AggregationError> errors = new CopyOnWriteArrayList<>();

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
