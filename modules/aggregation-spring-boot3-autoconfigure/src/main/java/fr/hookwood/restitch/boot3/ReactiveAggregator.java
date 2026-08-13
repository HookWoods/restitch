package fr.hookwood.restitch.boot3;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.hookwood.restitch.api.AggregateRef;
import fr.hookwood.restitch.api.AggregationError;
import fr.hookwood.restitch.api.AggregationResult;
import fr.hookwood.restitch.api.ErrorMode;
import fr.hookwood.restitch.core.AggregationErrorMapper;
import fr.hookwood.restitch.core.AggregationLimits;
import fr.hookwood.restitch.core.AggregationObserver;
import fr.hookwood.restitch.core.AggregationPlanCompiler;
import fr.hookwood.restitch.core.AggregationRequestCustomizer;
import fr.hookwood.restitch.core.AggregationResponseExtractor;
import fr.hookwood.restitch.core.AggregationSession;
import fr.hookwood.restitch.core.ClientProfile;
import fr.hookwood.restitch.core.ResolverProfile;
import fr.hookwood.restitch.core.ResolutionKey;
import fr.hookwood.restitch.json.JsonAdapter;
import fr.hookwood.restitch.json.JsonDocument;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

public final class ReactiveAggregator {
    public static final String INBOUND_HEADERS_CONTEXT_KEY = ReactiveAggregator.class.getName() + ".headers";

    private final AggregationProperties properties;
    private final JsonAdapter adapter;
    private final WebClient webClient;
    private final AggregationRequestCustomizer requestCustomizer;
    private final AggregationResponseExtractorSupport responseExtractor;
    private final AggregationErrorMapper errorMapper;
    private final AggregationObserver observer;
    private final ObjectMapper objectMapper;

    public ReactiveAggregator(AggregationProperties properties, JsonAdapter adapter) {
        this(properties, adapter, WebClient.builder().build(), (response, profile) -> response,
                (client, profile, uri, headers) -> headers, (profile, error) ->
                        new AggregationError(profile.name(), "", "DOWNSTREAM", ""),
                new AggregationObserver() {});
    }

    public ReactiveAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            WebClient webClient,
            AggregationResponseExtractor responseExtractor,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        this.properties = properties;
        this.adapter = adapter;
        this.webClient = webClient;
        this.requestCustomizer = requestCustomizer;
        this.responseExtractor = responseExtractor::extract;
        this.errorMapper = errorMapper;
        this.observer = observer;
        this.objectMapper = ((Jackson2JsonAdapter) adapter).objectMapper();
    }

    public AggregationProperties properties() {
        return properties;
    }

    public <T> Mono<T> hydrate(T source, Class<T> targetType) {
        return Mono.deferContextual(context -> hydrate(source, targetType, contextHeaders(context)));
    }

    public <T> Mono<T> hydrate(T source, Class<T> targetType, Map<String, String> inboundHeaders) {
        return Mono.using(
                () -> new AggregationSession(properties.aggregationLimits()),
                session -> hydrateOne(source, targetType, session, filteredHeaders(inboundHeaders),
                        new CopyOnWriteArrayList<>()),
                AggregationSession::close);
    }

    public <T> Mono<AggregationResult<T>> hydrateResult(T source, Class<T> targetType) {
        return hydrateResult(source, targetType, Map.of());
    }

    public <T> Mono<AggregationResult<T>> hydrateResult(
            T source, Class<T> targetType, Map<String, String> inboundHeaders) {
        return Mono.using(
                CopyOnWriteArrayList<AggregationError>::new,
                errors -> Mono.using(
                        () -> new AggregationSession(properties.aggregationLimits()),
                        session -> hydrateOne(source, targetType, session, filteredHeaders(inboundHeaders), errors)
                                .map(value -> new AggregationResult<>(value, errors)),
                        AggregationSession::close),
                ignored -> {});
    }

    public <T> Flux<AggregationResult<T>> hydrateResults(Flux<T> sources, Class<T> targetType) {
        return hydrateResults(sources, targetType, Map.of());
    }

    public <T> Flux<AggregationResult<T>> hydrateResults(
            Flux<T> sources, Class<T> targetType, Map<String, String> inboundHeaders) {
        return sources.concatMap(source -> hydrateResult(source, targetType, inboundHeaders));
    }

    public <T> Flux<T> hydrate(Flux<T> sources, Class<T> targetType) {
        return Flux.deferContextual(context -> hydrateSources(sources, targetType, contextHeaders(context)));
    }

    @SuppressWarnings("unchecked")
    Flux<?> hydrateFlux(Flux<?> sources, Class<?> targetType) {
        return hydrateSources((Flux<Object>) sources, (Class<Object>) targetType, Map.of());
    }

    public <T> Flux<T> hydrate(Flux<T> sources, Class<T> targetType, Map<String, String> inboundHeaders) {
        return hydrateSources(sources, targetType, filteredHeaders(inboundHeaders));
    }

    private <T> Flux<T> hydrateSources(Flux<T> sources, Class<T> targetType, Map<String, String> inboundHeaders) {
        AggregationLimits limits = properties.aggregationLimits();
        return Flux.using(
                () -> new AggregationSession(limits),
                session -> sources.bufferTimeout(batchBufferSize(targetType, limits), limits.batchFlushWindow())
                    .concatMap(batch -> hydrateBatch(batch, targetType, session, inboundHeaders),
                                limits.streamPrefetch()),
                AggregationSession::close);
    }

    public <T> ReactiveAggregationStream<T> stream(Flux<T> sources, Class<T> targetType) {
        return new ReactiveAggregationStream<>(hydrate(sources, targetType), Mono.empty());
    }

    public <T> ReactiveAggregationStream<T> stream(String rootProfile, Class<T> targetType) {
        return new ReactiveAggregationStream<>(
                Flux.deferContextual(context -> streamRoot(rootProfile, targetType, contextHeaders(context))), Mono.empty());
    }

    public <T> ReactiveAggregationStream<T> stream(
            String rootProfile, Class<T> targetType, Map<String, String> inboundHeaders) {
        return new ReactiveAggregationStream<>(streamRoot(rootProfile, targetType, filteredHeaders(inboundHeaders)),
                Mono.empty());
    }

    private <T> Flux<T> streamRoot(String rootProfile, Class<T> targetType, Map<String, String> inboundHeaders) {
        AggregationProperties.Root root = properties.getRoots().get(rootProfile);
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
        if (itemsPointer == null) {
            itemsPointer = "";
        }
        String configuredItemsPointer = itemsPointer;
        AggregationLimits limits = properties.aggregationLimits();
        Map<String, String> allowedHeaders = allowlistedHeaders(client, inboundHeaders);
        return Flux.using(
                () -> new AggregationSession(limits),
                session -> {
                    new AggregationPlanCompiler(limits).compile(targetType, properties.resolverProfiles());
                    String rootExtractor = configuredItemsPointer.isBlank() ? rootProfile : configuredItemsPointer;
                    ResolutionKey rootKey = ResolutionKey.of(client.baseUri().toString(), uri.getPath(),
                            "root:" + rootExtractor, Object.class, allowedHeaders);
                    Flux<JsonDocument> documents = session.memoize(rootKey, Flux.class,
                            () -> streamDocuments(uri, client, configuredItemsPointer, limits));
                    return documents.concatMap(document -> hydrateDocument(
                                    document, targetType, session, allowedHeaders, Map.of(), Map.of(), 0, null)
                            .map(value -> adapter.toValue(value, targetType)), 1);
                },
                AggregationSession::close);
    }

    private Flux<JsonDocument> streamDocuments(
            URI uri, ClientProfile client, String itemsPointer, AggregationLimits limits) {
        return webClient.get().uri(uri).exchangeToFlux(response -> {
            if (response.statusCode().isError()) {
                return response.releaseBody().thenMany(Flux.error(
                        new IllegalStateException("downstream status " + response.statusCode().value())));
            }
            Jackson2CollectionParser parser = new Jackson2CollectionParser(
                    objectMapper, (Jackson2JsonAdapter) adapter, itemsPointer, limits.maxObjectBytes(),
                    limits.maxBufferedItems());
            AtomicLong responseBytes = new AtomicLong();
            Flux<JsonDocument> values = response.bodyToFlux(DataBuffer.class)
                    .concatMap(buffer -> {
                        int readable = buffer.readableByteCount();
                        long total = responseBytes.addAndGet(readable);
                        try {
                            if (total > limits.maxResponseBytes()) {
                                return Flux.error(new IllegalArgumentException("response exceeds maxResponseBytes"));
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
                    .doFinally(signal -> {
                        try {
                            parser.close();
                        } catch (Exception ignored) {
                        }
                    });
            return values;
        }).timeout(client.timeout());
    }

    private <T> Mono<T> hydrateOne(
            T source,
            Class<T> type,
            AggregationSession session,
            Map<String, String> headers,
            List<AggregationError> errors) {
        JsonDocument document = adapter.parse(write(source));
        new AggregationPlanCompiler(properties.aggregationLimits()).compile(type, properties.resolverProfiles());
        return hydrateDocument(document, type, session, headers, Map.of(), Map.of(), 0, errors)
                .map(result -> adapter.toValue(result, type));
    }

    private <T> Flux<T> hydrateBatch(
            List<T> sources, Class<T> type, AggregationSession session, Map<String, String> headers) {
        if (sources.isEmpty()) {
            return Flux.empty();
        }
        List<Field> fields = aggregateFields(type);
        return prepareBatchState(sources, fields, headers, session)
                .flatMapMany(batchState -> Flux.fromIterable(sources)
                        .flatMapSequential(source -> hydrateDocument(
                                        adapter.parse(write(source)), type, session, headers,
                                        batchState.values(), batchState.errors(), 0, null)
                                .map(result -> adapter.toValue(result, type)),
                                properties.aggregationLimits().maxConcurrency(),
                                properties.aggregationLimits().streamPrefetch()));
    }

    private Mono<BatchState> prepareBatchState(
            List<?> sources, List<Field> fields, Map<String, String> headers, AggregationSession session) {
        BatchState state = new BatchState();
        return Flux.fromIterable(fields)
                .concatMap(field -> {
                    ResolverProfile resolver = resolver(field);
                    if (resolver == null || resolver.batch() == null) {
                        return Mono.empty();
                    }
                    Map<String, List<Object>> byId = new LinkedHashMap<>();
                    for (Object source : sources) {
                        JsonDocument document = adapter.parse(write(source));
                        String id = adapter.text(adapter.at(document, resolver.sourcePointer()));
                        if (id != null && !id.isBlank()) {
                            byId.computeIfAbsent(id, ignored -> new ArrayList<>()).add(source);
                        }
                    }
                    if (byId.isEmpty()) {
                        return Mono.empty();
                    }
                    ClientProfile client = properties.clientProfiles().get(resolver.client());
                    if (client == null) {
                        return Mono.error(new IllegalArgumentException("unknown client " + resolver.client()));
                    }
                    URI batchUri = resolveUri(client, resolver.batch().path());
                    String query = String.join(",", byId.keySet());
                    String batchPath = batchUri.getPath() + "?" + resolver.batch().queryParameter() + "="
                            + UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8);
                    ResolutionKey batchKey = ResolutionKey.of(
                            client.baseUri().toString(), batchPath, resolver.name(), Map.class,
                            allowlistedHeaders(client, headers));
                    Mono<Map<String, JsonDocument>> request = fetchBatch(resolver, byId.keySet(), headers)
                            .doOnSubscribe(ignored -> observer.resolutionStarted(batchKey))
                            .doOnSuccess(ignored -> observer.resolutionSucceeded(batchKey))
                            .doOnError(error -> observer.resolutionFailed(batchKey, error))
                            .doOnNext(values -> state.values().put(resolver.name(), values));
                    Mono<Map<String, JsonDocument>> memoized = session.memoize(batchKey, Mono.class, () -> request);
                    return memoized
                            .onErrorResume(error -> {
                                if (resolver.errorMode() == ErrorMode.FAIL_FAST) {
                                    return Mono.error(error);
                                }
                                state.errors().put(resolver.name(), error);
                                return Mono.empty();
                            })
                            .then();
                })
                .then(Mono.just(state));
    }

    private Mono<JsonDocument> hydrateDocument(
            JsonDocument document,
            Class<?> type,
            AggregationSession session,
            Map<String, String> headers,
            Map<String, Map<String, JsonDocument>> batchValues,
            Map<String, Throwable> batchErrors,
            int depth,
            List<AggregationError> errors) {
        if (depth > properties.aggregationLimits().maxDepth()) {
            return Mono.error(new IllegalArgumentException("aggregation depth exceeds configured limit"));
        }
        Mono<JsonDocument> result = Mono.just(document);
        for (Field field : aggregateFields(type)) {
            ResolverProfile resolver = resolver(field);
            result = result.flatMap(current -> resolveField(
                            current, field, resolver, session, headers, batchValues, batchErrors, errors)
                    .flatMap(value -> hydrateDocument(
                            value, field.getType(), session, headers, batchValues, batchErrors, depth + 1, errors))
                    .map(value -> adapter.replace(current, "/" + field.getName(), value)));
        }
        return result;
    }

    private Mono<JsonDocument> resolveField(
            JsonDocument document,
            Field field,
            ResolverProfile resolver,
            AggregationSession session,
            Map<String, String> headers,
            Map<String, Map<String, JsonDocument>> batchValues,
            Map<String, Throwable> batchErrors,
            List<AggregationError> errors) {
        if (resolver == null) {
            return Mono.error(new IllegalArgumentException("missing resolver profile for field " + field.getName()));
        }
        String id = adapter.text(adapter.at(document, resolver.sourcePointer()));
        if (id == null || id.isBlank()) {
            return Mono.just(adapter.parse("null".getBytes(StandardCharsets.UTF_8)));
        }
        if (batchErrors.containsKey(resolver.name())) {
            return handleError(resolver, batchErrors.get(resolver.name()), id, field, errors);
        }
        Map<String, JsonDocument> batch = batchValues.get(resolver.name());
        if (batch != null) {
            JsonDocument value = batch.get(id);
            return value == null
                    ? handleError(resolver, new IllegalStateException("batch item was not returned"), id, field, errors)
                    : Mono.just(value);
        }
        ClientProfile client = properties.clientProfiles().get(resolver.client());
        if (client == null) {
            return handleError(resolver, new IllegalArgumentException("unknown client " + resolver.client()), null,
                    field, errors);
        }
        String path = resolver.path().replace("{id}", UriUtils.encodePathSegment(id, StandardCharsets.UTF_8));
        URI uri = resolveUri(client, path);
        ResolutionKey key = ResolutionKey.of(client.baseUri().toString(), uri.getPath(), resolver.name(),
                field.getType(), headers);
        Mono<JsonDocument> request = Mono.defer(() -> resolver.batch() == null
                        ? request(client, resolver, uri, headers)
                        : fetchBatch(resolver, List.of(id), headers)
                                .flatMap(values -> {
                                    JsonDocument value = values.get(id);
                                    return value == null
                                            ? Mono.error(new IllegalStateException("batch item was not returned"))
                                            : Mono.just(value);
                                }))
                .doOnSubscribe(ignored -> observer.resolutionStarted(key))
                .doOnSuccess(ignored -> observer.resolutionSucceeded(key))
                .doOnError(error -> observer.resolutionFailed(key, error))
                .onErrorResume(error -> handleError(resolver, error, id, field, errors))
                .cache();
        return session.memoize(key, Mono.class, () -> request);
    }

    private Mono<JsonDocument> request(
            ClientProfile client, ResolverProfile resolver, URI uri, Map<String, String> inboundHeaders) {
        Map<String, String> allowed = allowlistedHeaders(client, inboundHeaders);
        Map<String, String> customized = Map.copyOf(requestCustomizer.customize(client, resolver, uri, allowed));
        return webClient.get().uri(uri).headers(headers -> customized.forEach(headers::set))
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    Mono<byte[]> body = readBody(response.bodyToFlux(DataBuffer.class), properties.aggregationLimits().maxResponseBytes());
                    if (status.isError()) {
                        return body.flatMap(bytes -> Mono.error(new IllegalStateException("downstream status " + status.value())));
                    }
                    return body.map(adapter::parse)
                            .map(value -> responseExtractor.extract(value, resolver))
                            .map(value -> adapter.at(value, resolver.responsePointer()));
                })
                .timeout(client.timeout());
    }

    private Mono<Map<String, JsonDocument>> fetchBatch(
            ResolverProfile resolver, Collection<String> ids, Map<String, String> headers) {
        ClientProfile client = properties.clientProfiles().get(resolver.client());
        if (ids.isEmpty() || ids.size() > properties.aggregationLimits().maxPendingIds()
                || ids.size() > resolver.batch().maxSize()) {
            return Mono.error(new IllegalArgumentException("batch identifier limit exceeded"));
        }
        if (client == null) {
            return Mono.error(new IllegalArgumentException("unknown client " + resolver.client()));
        }
        URI uri = resolveUri(client, resolver.batch().path());
        Map<String, String> allowed = allowlistedHeaders(client, headers);
        Map<String, String> customized = Map.copyOf(requestCustomizer.customize(client, resolver, uri, allowed));
        return webClient.get().uri(builder -> builder.scheme(uri.getScheme()).host(uri.getHost()).port(uri.getPort())
                        .path(uri.getPath()).queryParam(resolver.batch().queryParameter(), String.join(",", ids)).build())
                .headers(request -> customized.forEach(request::set))
                .exchangeToMono(response -> {
                    Mono<byte[]> body = readBody(
                            response.bodyToFlux(DataBuffer.class), properties.aggregationLimits().maxResponseBytes());
                    if (response.statusCode().isError()) {
                        return body.flatMap(bytes -> Mono.error(
                                new IllegalStateException("downstream status " + response.statusCode().value())));
                    }
                    return body.map(adapter::parse);
                })
                .timeout(client.timeout())
                .map(document -> {
                    JsonDocument items = adapter.at(document, resolver.batch().itemsPointer());
                    Map<String, JsonDocument> result = new HashMap<>();
                    for (JsonDocument item : ((Jackson2JsonAdapter) adapter).elements(items)) {
                        String id = adapter.text(adapter.at(item, resolver.batch().itemKeyPointer()));
                        result.put(id, item);
                    }
                    return result;
                });
    }

    private Mono<JsonDocument> handleError(
            ResolverProfile resolver,
            Throwable error,
            String id,
            Field field,
            List<AggregationError> errors) {
        if (errors != null && (resolver.errorMode() == ErrorMode.RESULT
                || resolver.errorMode() == ErrorMode.NULL_FIELD
                || resolver.errorMode() == ErrorMode.KEEP_SOURCE_ID)) {
            errors.add(errorMapper.map(resolver, error));
        }
        if (resolver.errorMode() == ErrorMode.NULL_FIELD) {
            return Mono.just(adapter.parse("null".getBytes(StandardCharsets.UTF_8)));
        }
        if (resolver.errorMode() == ErrorMode.KEEP_SOURCE_ID && id != null) {
            return Mono.just(((Jackson2JsonAdapter) adapter).fromValue(id));
        }
        if (resolver.errorMode() == ErrorMode.RESULT) {
            return Mono.just(adapter.parse("null".getBytes(StandardCharsets.UTF_8)));
        }
        return Mono.error(error);
    }

    private byte[] write(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot serialize aggregation source", error);
        }
    }

    private Map<String, String> filteredHeaders(Map<String, String> headers) {
        return headers == null ? Map.of() : Map.copyOf(headers);
    }

    private static Map<String, String> allowlistedHeaders(ClientProfile client, Map<String, String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (client.propagatedHeaders().stream().anyMatch(name -> name.equalsIgnoreCase(header.getKey()))) {
                result.put(header.getKey(), header.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private static List<Field> aggregateFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(AggregateRef.class)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private int batchBufferSize(Class<?> type, AggregationLimits limits) {
        int configuredBatchSize = aggregateFields(type).stream()
                .map(this::resolver)
                .filter(Objects::nonNull)
                .map(ResolverProfile::batch)
                .filter(Objects::nonNull)
                .mapToInt(batch -> batch.maxSize())
                .min()
                .orElse(limits.maxBatchSize());
        return Math.min(limits.maxBufferedItems(), Math.min(limits.maxBatchSize(), configuredBatchSize));
    }

    private ResolverProfile resolver(Field field) {
        return properties.resolverProfiles().get(field.getAnnotation(AggregateRef.class).value());
    }

    private record BatchState(
            Map<String, Map<String, JsonDocument>> values,
            Map<String, Throwable> errors) {
        private BatchState() {
            this(new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }

    private static URI resolveUri(ClientProfile client, String path) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("://")) {
            throw new IllegalArgumentException("resolver path must be relative to the configured client");
        }
        URI resolved = client.baseUri().resolve(path);
        if (!Objects.equals(client.baseUri().getScheme(), resolved.getScheme())
                || !Objects.equals(client.baseUri().getHost(), resolved.getHost())
                || client.baseUri().getPort() != resolved.getPort()) {
            throw new IllegalArgumentException("resolver path must use the configured client host");
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
                            throw new IllegalArgumentException("response exceeds maxResponseBytes");
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

    private static Map<String, String> contextHeaders(ContextView context) {
        if (!context.hasKey(INBOUND_HEADERS_CONTEXT_KEY)) {
            return Map.of();
        }
        return context.get(INBOUND_HEADERS_CONTEXT_KEY);
    }

    @FunctionalInterface
    private interface AggregationResponseExtractorSupport {
        JsonDocument extract(JsonDocument response, ResolverProfile resolverProfile);
    }
}
