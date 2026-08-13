package fr.hookwood.restitch.boot3;

import fr.hookwood.restitch.api.AggregateRef;
import fr.hookwood.restitch.api.AggregationError;
import fr.hookwood.restitch.api.AggregationResult;
import fr.hookwood.restitch.api.ErrorMode;
import fr.hookwood.restitch.core.AggregationErrorMapper;
import fr.hookwood.restitch.core.AggregationObserver;
import fr.hookwood.restitch.core.AggregationPlanCompiler;
import fr.hookwood.restitch.core.AggregationRequestCustomizer;
import fr.hookwood.restitch.core.AggregationResponseExtractor;
import fr.hookwood.restitch.core.AggregationSession;
import fr.hookwood.restitch.core.ClientProfile;
import fr.hookwood.restitch.core.ResolutionKey;
import fr.hookwood.restitch.core.ResolverProfile;
import fr.hookwood.restitch.json.JsonAdapter;
import fr.hookwood.restitch.json.JsonDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

public final class MvcAggregator {
    private static final ExecutorService REQUEST_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "restitch-mvc-request");
        thread.setDaemon(true);
        return thread;
    });

    private final AggregationProperties properties;
    private final Jackson2JsonAdapter adapter;
    private final RestClient restClient;
    private final RestClient.Builder restClientBuilder;
    private final AggregationResponseExtractor responseExtractor;
    private final AggregationRequestCustomizer requestCustomizer;
    private final AggregationErrorMapper errorMapper;
    private final AggregationObserver observer;

    public MvcAggregator(AggregationProperties properties, JsonAdapter adapter, RestClient restClient) {
        this(properties, adapter, restClient, (response, profile) -> response,
                (client, profile, uri, headers) -> headers,
                (profile, error) -> new AggregationError(profile.name(), "", "DOWNSTREAM", ""),
                new AggregationObserver() {});
    }

    public MvcAggregator(AggregationProperties properties, JsonAdapter adapter, RestClient.Builder restClientBuilder) {
        this(properties, adapter, restClientBuilder, (response, profile) -> response,
                (client, profile, uri, headers) -> headers,
                (profile, error) -> new AggregationError(profile.name(), "", "DOWNSTREAM", ""),
                new AggregationObserver() {});
    }

    public MvcAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            RestClient restClient,
            AggregationResponseExtractor responseExtractor,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        this.properties = properties;
        this.adapter = (Jackson2JsonAdapter) adapter;
        this.restClient = restClient;
        this.restClientBuilder = null;
        this.responseExtractor = responseExtractor;
        this.requestCustomizer = requestCustomizer;
        this.errorMapper = errorMapper;
        this.observer = observer;
    }

    public MvcAggregator(
            AggregationProperties properties,
            JsonAdapter adapter,
            RestClient.Builder restClientBuilder,
            AggregationResponseExtractor responseExtractor,
            AggregationRequestCustomizer requestCustomizer,
            AggregationErrorMapper errorMapper,
            AggregationObserver observer) {
        this.properties = properties;
        this.adapter = (Jackson2JsonAdapter) adapter;
        this.restClient = null;
        this.restClientBuilder = restClientBuilder;
        this.responseExtractor = responseExtractor;
        this.requestCustomizer = requestCustomizer;
        this.errorMapper = errorMapper;
        this.observer = observer;
    }

    public <T> T hydrate(T source, Class<T> type) {
        return hydrate(source, type, Map.of());
    }

    public <T> T hydrate(T source, Class<T> type, Map<String, String> inboundHeaders) {
        List<AggregationError> errors = new CopyOnWriteArrayList<>();
        try (AggregationSession session = new AggregationSession(properties.aggregationLimits())) {
            JsonDocument document = adapter.fromValue(source);
            new AggregationPlanCompiler(properties.aggregationLimits()).compile(type, properties.resolverProfiles());
            JsonDocument result = hydrateDocument(document, type, session, filteredHeaders(inboundHeaders), errors);
            return adapter.toValue(result, type);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot hydrate MVC response", error);
        }
    }

    public <T> AggregationResult<T> hydrateResult(T source, Class<T> type) {
        return hydrateResult(source, type, Map.of());
    }

    public <T> AggregationResult<T> hydrateResult(
            T source, Class<T> type, Map<String, String> inboundHeaders) {
        List<AggregationError> errors = new CopyOnWriteArrayList<>();
        try (AggregationSession session = new AggregationSession(properties.aggregationLimits())) {
            JsonDocument document = adapter.fromValue(source);
            new AggregationPlanCompiler(properties.aggregationLimits()).compile(type, properties.resolverProfiles());
            JsonDocument result = hydrateDocument(document, type, session, filteredHeaders(inboundHeaders), errors);
            return new AggregationResult<>(adapter.toValue(result, type), errors);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot hydrate MVC response", error);
        }
    }

    private JsonDocument hydrateDocument(
            JsonDocument document,
            Class<?> type,
            AggregationSession session,
            Map<String, String> headers,
            List<AggregationError> errors) {
        if (type == null || document == null) {
            return document;
        }
        JsonDocument current = document;
        List<Field> fields = aggregateFields(type);
        for (int index = 0; index < fields.size();) {
            Field field = fields.get(index);
            ResolverProfile resolver = resolver(field);
            if (resolver == null) {
                throw new IllegalArgumentException("missing resolver profile for field " + field.getName());
            }
            if (resolver.batch() != null) {
                List<Field> batchFields = new ArrayList<>();
                while (index < fields.size()) {
                    Field candidate = fields.get(index);
                    ResolverProfile candidateResolver = resolver(candidate);
                    if (candidateResolver == null || candidateResolver.batch() == null
                            || !compatibleBatch(resolver, candidateResolver)) {
                        break;
                    }
                    batchFields.add(candidate);
                    index++;
                }
                current = resolveBatchFields(current, batchFields, session, headers, errors);
                continue;
            }
            String id = adapter.text(adapter.at(current, resolver.sourcePointer()));
            if (id == null || id.isBlank()) {
                current = adapter.replace(current, "/" + field.getName(), jsonNull());
                index++;
                continue;
            }
            try {
                JsonDocument resolved = resolveField(current, field, resolver, id, session, headers);
                resolved = hydrateDocument(resolved, field.getType(), session, headers, errors);
                current = adapter.replace(current, "/" + field.getName(), resolved);
            } catch (RuntimeException error) {
                current = adapter.replace(current, "/" + field.getName(), handleError(resolver, id, field, error, errors));
            }
            index++;
        }
        return current;
    }

    private JsonDocument resolveBatchFields(
            JsonDocument document,
            List<Field> fields,
            AggregationSession session,
            Map<String, String> headers,
            List<AggregationError> errors) {
        if (fields.isEmpty()) {
            return document;
        }
        ResolverProfile requestResolver = resolver(fields.get(0));
        ClientProfile client = properties.clientProfiles().get(requestResolver.client());
        if (client == null) {
            throw new IllegalArgumentException("unknown client " + requestResolver.client());
        }
        Map<String, String> allowedHeaders = allowlistedHeaders(client, headers);
        Map<Field, String> idsByField = new LinkedHashMap<>();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        JsonDocument current = document;
        for (Field field : fields) {
            ResolverProfile resolver = resolver(field);
            String id = adapter.text(adapter.at(current, resolver.sourcePointer()));
            idsByField.put(field, id);
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
        for (Map.Entry<Field, String> entry : idsByField.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                current = adapter.replace(current, "/" + entry.getKey().getName(), jsonNull());
            }
        }
        if (ids.isEmpty()) {
            return current;
        }
        int batchSize = Math.min(requestResolver.batch().maxSize(),
                Math.min(properties.aggregationLimits().maxBatchSize(),
                        properties.aggregationLimits().maxPendingIds()));
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batch identifier limit exceeded");
        }
        Map<String, JsonDocument> values = new LinkedHashMap<>();
        List<String> allIds = new ArrayList<>(ids);
        try {
            for (int start = 0; start < allIds.size(); start += batchSize) {
                List<String> chunk = allIds.subList(start, Math.min(start + batchSize, allIds.size()));
                values.putAll(fetchBatchValues(client, requestResolver, chunk, allowedHeaders, session));
            }
        } catch (RuntimeException error) {
            for (Field field : fields) {
                ResolverProfile resolver = resolver(field);
                String id = idsByField.get(field);
                if (id != null && !id.isBlank()) {
                    current = adapter.replace(current, "/" + field.getName(), handleError(resolver, id, field, error, errors));
                }
            }
            return current;
        }
        for (Field field : fields) {
            ResolverProfile resolver = resolver(field);
            String id = idsByField.get(field);
            if (id == null || id.isBlank()) {
                continue;
            }
            try {
                JsonDocument resolved = values.get(id);
                if (resolved == null) {
                    throw new IllegalStateException("batch item was not returned");
                }
                resolved = hydrateDocument(resolved, field.getType(), session, headers, errors);
                current = adapter.replace(current, "/" + field.getName(), resolved);
            } catch (RuntimeException error) {
                current = adapter.replace(current, "/" + field.getName(), handleError(resolver, id, field, error, errors));
            }
        }
        return current;
    }

    private JsonDocument resolveField(
            JsonDocument document,
            Field field,
            ResolverProfile resolver,
            String id,
            AggregationSession session,
            Map<String, String> headers) {
        ClientProfile client = properties.clientProfiles().get(resolver.client());
        if (client == null) {
            throw new IllegalArgumentException("unknown client " + resolver.client());
        }
        String path = resolver.path().replace("{id}", UriUtils.encodePathSegment(id, StandardCharsets.UTF_8));
        URI uri = resolveUri(client, path);
        Map<String, String> allowedHeaders = allowlistedHeaders(client, headers);
        ResolutionKey key = ResolutionKey.of(client.baseUri().toString(), uri.getPath(), resolver.name(),
                field.getType(), allowedHeaders);
        return session.memoize(key, JsonDocument.class, () -> {
            observer.resolutionStarted(key);
            try {
                JsonDocument result = fetch(client, resolver, uri, allowedHeaders);
                observer.resolutionSucceeded(key);
                return result;
            } catch (RuntimeException error) {
                observer.resolutionFailed(key, error);
                throw error;
            }
        });
    }

    private JsonDocument fetch(
            ClientProfile client, ResolverProfile resolver, URI uri, Map<String, String> headers) {
        byte[] body = request(client, resolver, uri, headers);
        JsonDocument extracted = responseExtractor.extract(adapter.parse(body), resolver);
        return adapter.at(extracted, resolver.responsePointer());
    }

    private Map<String, JsonDocument> fetchBatchValues(
            ClientProfile client,
            ResolverProfile resolver,
            Collection<String> ids,
            Map<String, String> headers,
            AggregationSession session) {
        URI uri = resolveUri(client, resolver.batch().path());
        String query = String.join(",", ids);
        URI requestUri = URI.create(uri + "?" + resolver.batch().queryParameter() + "="
                + UriUtils.encodeQueryParam(query, StandardCharsets.UTF_8));
        ResolutionKey key = ResolutionKey.of(client.baseUri().toString(), requestUri.toString(),
                resolver.name(), Map.class, headers);
        return session.memoize(key, Map.class, () -> {
            observer.resolutionStarted(key);
            try {
                byte[] body = request(client, resolver, requestUri, headers);
                JsonDocument items = adapter.at(adapter.parse(body), resolver.batch().itemsPointer());
                Map<String, JsonDocument> values = new LinkedHashMap<>();
                for (JsonDocument item : adapter.elements(items)) {
                    String itemId = adapter.text(adapter.at(item, resolver.batch().itemKeyPointer()));
                    if (itemId != null) {
                        values.put(itemId, item);
                    }
                }
                observer.resolutionSucceeded(key);
                return values;
            } catch (RuntimeException error) {
                observer.resolutionFailed(key, error);
                throw error;
            }
        });
    }

    private boolean compatibleBatch(ResolverProfile first, ResolverProfile second) {
        return first.client().equals(second.client())
                && first.batch().equals(second.batch());
    }

    private byte[] request(
            ClientProfile client, ResolverProfile resolver, URI uri, Map<String, String> headers) {
        Map<String, String> customized = Map.copyOf(requestCustomizer.customize(client, resolver, uri, headers));
        RestClient requestClient = restClientBuilder == null
                ? restClient
                : restClientBuilder.clone().requestFactory(requestFactory(client.timeout())).build();
        byte[] body = withTimeout(client, () -> requestClient.get().uri(uri)
                .headers(request -> customized.forEach(request::set))
                .exchange((request, response) -> {
                    byte[] responseBody = readBody(response.getBody(), properties.aggregationLimits().maxResponseBytes());
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("downstream status " + response.getStatusCode().value());
                    }
                    return responseBody;
                }));
        return body;
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
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
                throw new IllegalStateException("aggregation response exceeds maxResponseBytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private JsonDocument handleError(
            ResolverProfile resolver,
            String id,
            Field field,
            RuntimeException error,
            List<AggregationError> errors) {
        if (resolver.errorMode() == ErrorMode.FAIL_FAST) {
            throw error;
        }
        errors.add(errorMapper.map(resolver, error));
        if (resolver.errorMode() == ErrorMode.KEEP_SOURCE_ID) {
            return adapter.fromValue(id);
        }
        return jsonNull();
    }

    private JsonDocument jsonNull() {
        return adapter.parse("null".getBytes(StandardCharsets.UTF_8));
    }

    private <T> T withTimeout(ClientProfile client, Callable<T> action) {
        Future<T> future = REQUEST_EXECUTOR.submit(action);
        try {
            return future.get(client.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new IllegalStateException("aggregation request timed out after " + client.timeout(), error);
        } catch (InterruptedException error) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("aggregation request interrupted", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("aggregation request failed", cause);
        }
    }

    private ResolverProfile resolver(Field field) {
        return properties.resolverProfiles().get(field.getAnnotation(AggregateRef.class).value());
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

    private static Map<String, String> filteredHeaders(Map<String, String> headers) {
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
}
