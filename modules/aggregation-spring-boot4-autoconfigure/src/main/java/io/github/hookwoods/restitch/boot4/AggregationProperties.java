package io.github.hookwoods.restitch.boot4;

import io.github.hookwoods.restitch.api.ErrorMode;
import io.github.hookwoods.restitch.core.AggregationLimits;
import io.github.hookwoods.restitch.core.BatchProfile;
import io.github.hookwoods.restitch.core.ClientProfile;
import io.github.hookwoods.restitch.core.ResolverProfile;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration for Restitch's Spring Boot 4 integration. */
@ConfigurationProperties(prefix = "aggregation")
public class AggregationProperties {
    private final Map<String, Client> clients = new LinkedHashMap<>();
    private final Map<String, Resolver> resolvers = new LinkedHashMap<>();
    private final Map<String, Root> roots = new LinkedHashMap<>();
    private final Limits limits = new Limits();

    /**
     * Returns downstream clients keyed by their configured names.
     *
     * @return mutable client configuration map
     */
    public Map<String, Client> clients() {
        return clients;
    }

    /**
     * Returns downstream clients keyed by their configured names.
     *
     * @return mutable client configuration map
     */
    public Map<String, Client> getClients() {
        return clients;
    }

    /**
     * Returns resolver profiles keyed by their configured names.
     *
     * @return mutable resolver configuration map
     */
    public Map<String, Resolver> resolvers() {
        return resolvers;
    }

    /**
     * Returns resolver profiles keyed by their configured names.
     *
     * @return mutable resolver configuration map
     */
    public Map<String, Resolver> getResolvers() {
        return resolvers;
    }

    /**
     * Returns root-response configuration keyed by root profile name.
     *
     * @return mutable root configuration map
     */
    public Map<String, Root> roots() {
        return roots;
    }

    /**
     * Returns root-response configuration keyed by root profile name.
     *
     * @return mutable root configuration map
     */
    public Map<String, Root> getRoots() {
        return roots;
    }

    /**
     * Returns the aggregation safety limits.
     *
     * @return mutable safety-limit configuration
     */
    public Limits limits() {
        return limits;
    }

    /**
     * Returns the aggregation safety limits.
     *
     * @return mutable safety-limit configuration
     */
    public Limits getLimits() {
        return limits;
    }

    /**
     * Converts configured clients to immutable core client profiles.
     *
     * @return client profiles keyed by client name
     */
    public Map<String, ClientProfile> clientProfiles() {
        Map<String, ClientProfile> profiles = new LinkedHashMap<>();
        clients.forEach((name, client) -> profiles.put(name, client.toProfile()));
        return profiles;
    }

    /**
     * Converts configured resolvers to immutable core resolver profiles.
     *
     * @return resolver profiles keyed by resolver name
     */
    public Map<String, ResolverProfile> resolverProfiles() {
        Map<String, ResolverProfile> profiles = new LinkedHashMap<>();
        resolvers.forEach((name, resolver) -> profiles.put(name, resolver.toProfile(name)));
        return profiles;
    }

    /**
     * Converts configured limits to immutable core aggregation limits.
     *
     * @return configured aggregation limits
     */
    public AggregationLimits aggregationLimits() {
        return limits.toLimits();
    }

    /** Configuration for one named downstream REST client. */
    public static class Client {
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(5);
        private Set<String> propagateHeaders = new LinkedHashSet<>();

        /** Creates an empty client configuration for property binding. */
        public Client() {}

        /**
         * Creates a client configuration.
         *
         * @param baseUrl absolute downstream base URL
         * @param timeout downstream request timeout
         * @param propagatedHeaders inbound header names allowed for propagation
         */
        public Client(String baseUrl, Duration timeout, Set<String> propagatedHeaders) {
            this.baseUrl = baseUrl;
            this.timeout = timeout;
            this.propagateHeaders = new LinkedHashSet<>(propagatedHeaders == null ? Set.of() : propagatedHeaders);
        }

        /**
         * Returns the downstream base URL.
         *
         * @return downstream base URL
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * Sets the downstream base URL.
         *
         * @param baseUrl absolute downstream base URL
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Returns the maximum duration of a downstream request.
         *
         * @return downstream request timeout
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * Sets the maximum duration of a downstream request.
         *
         * @param timeout downstream request timeout
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        /**
         * Returns inbound header names allowed for propagation.
         *
         * @return allowlisted inbound header names
         */
        public Set<String> getPropagatedHeaders() {
            return propagateHeaders;
        }

        /**
         * Sets inbound header names allowed for propagation.
         *
         * @param propagatedHeaders allowlisted inbound header names
         */
        public void setPropagatedHeaders(Set<String> propagatedHeaders) {
            setPropagateHeaders(propagatedHeaders);
        }

        /**
         * Returns inbound header names allowed for propagation.
         *
         * @return allowlisted inbound header names
         */
        public Set<String> getPropagateHeaders() {
            return propagateHeaders;
        }

        /**
         * Sets inbound header names allowed for propagation.
         *
         * @param propagateHeaders allowlisted inbound header names
         */
        public void setPropagateHeaders(Set<String> propagateHeaders) {
            this.propagateHeaders = new LinkedHashSet<>(propagateHeaders == null ? Set.of() : propagateHeaders);
        }

        private ClientProfile toProfile() {
            return new ClientProfile(URI.create(baseUrl), timeout, propagateHeaders);
        }
    }

    /** Configuration for one resolver profile. */
    public static class Resolver {
        private String client;
        private String path;
        private String sourcePointer;
        private String responsePointer;
        private ErrorMode errorMode = ErrorMode.FAIL_FAST;
        private Batch batch;

        /** Creates an empty resolver configuration for property binding. */
        public Resolver() {}

        /**
         * Creates a resolver configuration.
         *
         * @param client configured downstream client name
         * @param path downstream path template
         * @param sourcePointer JSON Pointer selecting the source identifier
         * @param responsePointer JSON Pointer selecting the response value
         * @param errorMode resolver failure policy
         * @param batch optional batch-resolution configuration
         */
        public Resolver(
                String client,
                String path,
                String sourcePointer,
                String responsePointer,
                ErrorMode errorMode,
                Batch batch) {
            this.client = client;
            this.path = path;
            this.sourcePointer = sourcePointer;
            this.responsePointer = responsePointer;
            this.errorMode = errorMode;
            this.batch = batch;
        }

        /**
         * Returns the downstream client name.
         *
         * @return configured client name
         */
        public String getClient() {
            return client;
        }

        /**
         * Sets the downstream client name.
         *
         * @param client configured client name
         */
        public void setClient(String client) {
            this.client = client;
        }

        /**
         * Returns the downstream path template.
         *
         * @return downstream path template
         */
        public String getPath() {
            return path;
        }

        /**
         * Sets the downstream path template.
         *
         * @param path downstream path template
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Returns the JSON Pointer selecting the source identifier.
         *
         * @return source identifier JSON Pointer
         */
        public String getSourcePointer() {
            return sourcePointer;
        }

        /**
         * Sets the JSON Pointer selecting the source identifier.
         *
         * @param sourcePointer source identifier JSON Pointer
         */
        public void setSourcePointer(String sourcePointer) {
            this.sourcePointer = sourcePointer;
        }

        /**
         * Returns the JSON Pointer selecting the response value.
         *
         * @return response value JSON Pointer
         */
        public String getResponsePointer() {
            return responsePointer;
        }

        /**
         * Sets the JSON Pointer selecting the response value.
         *
         * @param responsePointer response value JSON Pointer
         */
        public void setResponsePointer(String responsePointer) {
            this.responsePointer = responsePointer;
        }

        /**
         * Returns the resolver failure policy.
         *
         * @return resolver failure policy
         */
        public ErrorMode getErrorMode() {
            return errorMode;
        }

        /**
         * Sets the resolver failure policy.
         *
         * @param errorMode resolver failure policy
         */
        public void setErrorMode(ErrorMode errorMode) {
            this.errorMode = errorMode;
        }

        /**
         * Returns optional batch-resolution configuration.
         *
         * @return batch-resolution configuration, or {@code null}
         */
        public Batch getBatch() {
            return batch;
        }

        /**
         * Sets optional batch-resolution configuration.
         *
         * @param batch batch-resolution configuration
         */
        public void setBatch(Batch batch) {
            this.batch = batch;
        }

        private ResolverProfile toProfile(String name) {
            return new ResolverProfile(
                    name,
                    client,
                    path,
                    sourcePointer,
                    responsePointer,
                    errorMode,
                    batch == null ? null : batch.toProfile());
        }
    }

    /** Configuration for one root downstream response. */
    public static class Root {
        private String client;
        private String path;
        private String responsePointer;
        private String itemsPointer;

        /**
         * Returns the downstream client name.
         *
         * @return configured client name
         */
        public String getClient() {
            return client;
        }

        /**
         * Sets the downstream client name.
         *
         * @param client configured client name
         */
        public void setClient(String client) {
            this.client = client;
        }

        /**
         * Returns the root-response path.
         *
         * @return root-response path
         */
        public String getPath() {
            return path;
        }

        /**
         * Sets the root-response path.
         *
         * @param path root-response path
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Returns the JSON Pointer selecting the root response.
         *
         * @return root response JSON Pointer
         */
        public String getResponsePointer() {
            return responsePointer;
        }

        /**
         * Sets the JSON Pointer selecting the root response.
         *
         * @param responsePointer root response JSON Pointer
         */
        public void setResponsePointer(String responsePointer) {
            this.responsePointer = responsePointer;
        }

        /**
         * Returns the JSON Pointer selecting root response items.
         *
         * @return root item JSON Pointer
         */
        public String getItemsPointer() {
            return itemsPointer;
        }

        /**
         * Sets the JSON Pointer selecting root response items.
         *
         * @param itemsPointer root item JSON Pointer
         */
        public void setItemsPointer(String itemsPointer) {
            this.itemsPointer = itemsPointer;
        }
    }

    /** Configuration for one batch resolver endpoint. */
    public static class Batch {
        private String path;
        private String queryParameter;
        private String itemsPointer;
        private String itemKeyPointer;
        private int maxSize = 100;

        /**
         * Returns the batch endpoint path.
         *
         * @return batch endpoint path
         */
        public String getPath() {
            return path;
        }

        /**
         * Sets the batch endpoint path.
         *
         * @param path batch endpoint path
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * Returns the query parameter that carries requested identifiers.
         *
         * @return identifier query parameter name
         */
        public String getQueryParameter() {
            return queryParameter;
        }

        /**
         * Sets the query parameter that carries requested identifiers.
         *
         * @param queryParameter identifier query parameter name
         */
        public void setQueryParameter(String queryParameter) {
            this.queryParameter = queryParameter;
        }

        /**
         * Returns the JSON Pointer selecting returned items.
         *
         * @return returned items JSON Pointer
         */
        public String getItemsPointer() {
            return itemsPointer;
        }

        /**
         * Sets the JSON Pointer selecting returned items.
         *
         * @param itemsPointer returned items JSON Pointer
         */
        public void setItemsPointer(String itemsPointer) {
            this.itemsPointer = itemsPointer;
        }

        /**
         * Returns the JSON Pointer selecting each item's matching identifier.
         *
         * @return item identifier JSON Pointer
         */
        public String getItemKeyPointer() {
            return itemKeyPointer;
        }

        /**
         * Sets the JSON Pointer selecting each item's matching identifier.
         *
         * @param itemKeyPointer item identifier JSON Pointer
         */
        public void setItemKeyPointer(String itemKeyPointer) {
            this.itemKeyPointer = itemKeyPointer;
        }

        /**
         * Returns the maximum identifiers sent in one batch request.
         *
         * @return maximum batch size
         */
        public int getMaxSize() {
            return maxSize;
        }

        /**
         * Sets the maximum identifiers sent in one batch request.
         *
         * @param maxSize maximum batch size
         */
        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        private BatchProfile toProfile() {
            return new BatchProfile(path, queryParameter, itemsPointer, itemKeyPointer, maxSize);
        }
    }

    /** Configuration for aggregation safety limits. */
    public static class Limits {
        private int maxDepth = 8;
        private int maxRequests = 256;
        private int maxConcurrency = 16;
        private long maxResponseBytes = 10 * 1024 * 1024;
        private long maxObjectBytes = 1024 * 1024;
        private int maxBufferedItems = 256;
        private int maxSessionEntries = 1024;
        private long maxSessionBytes = 16 * 1024 * 1024;
        private int maxPendingIds = 10_000;
        private int streamPrefetch = 32;
        private int maxBatchSize = 100;
        private Duration batchFlushWindow = Duration.ofMillis(10);

        /**
         * Converts this mutable configuration to immutable core limits.
         *
         * @return configured aggregation safety limits
         */
        public AggregationLimits toLimits() {
            return new AggregationLimits(
                    maxDepth,
                    maxRequests,
                    maxConcurrency,
                    maxResponseBytes,
                    maxObjectBytes,
                    maxBufferedItems,
                    maxSessionEntries,
                    maxSessionBytes,
                    maxPendingIds,
                    streamPrefetch,
                    maxBatchSize,
                    batchFlushWindow);
        }

        /**
         * Returns the maximum nested DTO traversal depth.
         *
         * @return maximum aggregation depth
         */
        public int getMaxDepth() { return maxDepth; }

        /**
         * Sets the maximum nested DTO traversal depth.
         *
         * @param value maximum aggregation depth
         */
        public void setMaxDepth(int value) { maxDepth = value; }

        /**
         * Returns the maximum distinct downstream resolutions per session.
         *
         * @return maximum downstream resolution count
         */
        public int getMaxRequests() { return maxRequests; }

        /**
         * Sets the maximum distinct downstream resolutions per session.
         *
         * @param value maximum downstream resolution count
         */
        public void setMaxRequests(int value) { maxRequests = value; }

        /**
         * Returns the maximum concurrent downstream resolutions.
         *
         * @return maximum downstream concurrency
         */
        public int getMaxConcurrency() { return maxConcurrency; }

        /**
         * Sets the maximum concurrent downstream resolutions.
         *
         * @param value maximum downstream concurrency
         */
        public void setMaxConcurrency(int value) { maxConcurrency = value; }

        /**
         * Returns the maximum downstream response size in bytes.
         *
         * @return maximum response size in bytes
         */
        public long getMaxResponseBytes() { return maxResponseBytes; }

        /**
         * Sets the maximum downstream response size in bytes.
         *
         * @param value maximum response size in bytes
         */
        public void setMaxResponseBytes(long value) { maxResponseBytes = value; }

        /**
         * Returns the maximum retained aggregate object estimate in bytes.
         *
         * @return maximum aggregate object size in bytes
         */
        public long getMaxObjectBytes() { return maxObjectBytes; }

        /**
         * Sets the maximum retained aggregate object estimate in bytes.
         *
         * @param value maximum aggregate object size in bytes
         */
        public void setMaxObjectBytes(long value) { maxObjectBytes = value; }

        /**
         * Returns the maximum buffered stream items.
         *
         * @return maximum buffered item count
         */
        public int getMaxBufferedItems() { return maxBufferedItems; }

        /**
         * Sets the maximum buffered stream items.
         *
         * @param value maximum buffered item count
         */
        public void setMaxBufferedItems(int value) { maxBufferedItems = value; }

        /**
         * Returns the maximum request-local memoized entries.
         *
         * @return maximum session entry count
         */
        public int getMaxSessionEntries() { return maxSessionEntries; }

        /**
         * Sets the maximum request-local memoized entries.
         *
         * @param value maximum session entry count
         */
        public void setMaxSessionEntries(int value) { maxSessionEntries = value; }

        /**
         * Returns the maximum estimated bytes retained by a session.
         *
         * @return maximum session bytes
         */
        public long getMaxSessionBytes() { return maxSessionBytes; }

        /**
         * Sets the maximum estimated bytes retained by a session.
         *
         * @param value maximum session bytes
         */
        public void setMaxSessionBytes(long value) { maxSessionBytes = value; }

        /**
         * Returns the maximum identifiers waiting for batch resolution.
         *
         * @return maximum pending identifier count
         */
        public int getMaxPendingIds() { return maxPendingIds; }

        /**
         * Sets the maximum identifiers waiting for batch resolution.
         *
         * @param value maximum pending identifier count
         */
        public void setMaxPendingIds(int value) { maxPendingIds = value; }

        /**
         * Returns the Reactor stream prefetch limit.
         *
         * @return stream prefetch limit
         */
        public int getStreamPrefetch() { return streamPrefetch; }

        /**
         * Sets the Reactor stream prefetch limit.
         *
         * @param value stream prefetch limit
         */
        public void setStreamPrefetch(int value) { streamPrefetch = value; }

        /**
         * Returns the maximum identifiers sent in one batch request.
         *
         * @return maximum batch size
         */
        public int getMaxBatchSize() { return maxBatchSize; }

        /**
         * Sets the maximum identifiers sent in one batch request.
         *
         * @param value maximum batch size
         */
        public void setMaxBatchSize(int value) { maxBatchSize = value; }

        /**
         * Returns the maximum time a batch waits before dispatch.
         *
         * @return batch flush window
         */
        public Duration getBatchFlushWindow() { return batchFlushWindow; }

        /**
         * Sets the maximum time a batch waits before dispatch.
         *
         * @param value batch flush window
         */
        public void setBatchFlushWindow(Duration value) { batchFlushWindow = value; }
    }
}
