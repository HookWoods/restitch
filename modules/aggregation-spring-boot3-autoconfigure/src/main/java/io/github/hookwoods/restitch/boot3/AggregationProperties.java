package io.github.hookwoods.restitch.boot3;

import io.github.hookwoods.restitch.api.ErrorMode;
import io.github.hookwoods.restitch.core.AggregationLimits;
import io.github.hookwoods.restitch.core.BatchProfile;
import io.github.hookwoods.restitch.core.ClientProfile;
import io.github.hookwoods.restitch.core.ResolverProfile;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** External configuration for Restitch's Spring Boot 3 integration. */
@ConfigurationProperties(prefix = "aggregation")
public class AggregationProperties {
    private Map<String, Client> clients = new LinkedHashMap<>();
    private Map<String, Resolver> resolvers = new LinkedHashMap<>();
    private Map<String, Root> roots = new LinkedHashMap<>();
    private Limits limits = new Limits();

    /**
     * Returns downstream clients keyed by their configured names.
     *
     * @return mutable client configuration map
     */
    public Map<String, Client> getClients() {
        return clients;
    }

    /**
     * Replaces the configured downstream clients.
     *
     * @param clients client configuration keyed by client name
     */
    public void setClients(Map<String, Client> clients) {
        this.clients = clients;
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
     * Replaces the configured resolver profiles.
     *
     * @param resolvers resolver configuration keyed by profile name
     */
    public void setResolvers(Map<String, Resolver> resolvers) {
        this.resolvers = resolvers;
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
     * Replaces the configured root-response profiles.
     *
     * @param roots root configuration keyed by root profile name
     */
    public void setRoots(Map<String, Root> roots) {
        this.roots = roots;
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
     * Replaces the aggregation safety limits.
     *
     * @param limits safety-limit configuration
     */
    public void setLimits(Limits limits) {
        this.limits = limits;
    }

    Map<String, ClientProfile> clientProfiles() {
        Map<String, ClientProfile> result = new LinkedHashMap<>();
        clients.forEach((name, client) -> result.put(name, client.toProfile()));
        return Map.copyOf(result);
    }

    Map<String, ResolverProfile> resolverProfiles() {
        Map<String, ResolverProfile> result = new LinkedHashMap<>();
        resolvers.forEach((name, resolver) -> result.put(name, resolver.toProfile(name)));
        return Map.copyOf(result);
    }

    AggregationLimits aggregationLimits() {
        return limits.toLimits();
    }

    /** Configuration for one named downstream REST client. */
    public static class Client {
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(5);
        private List<String> propagateHeaders = List.of();

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
        public List<String> getPropagateHeaders() {
            return propagateHeaders;
        }

        /**
         * Sets inbound header names allowed for propagation.
         *
         * @param propagateHeaders allowlisted inbound header names
         */
        public void setPropagateHeaders(List<String> propagateHeaders) {
            this.propagateHeaders = propagateHeaders;
        }

        ClientProfile toProfile() {
            return new ClientProfile(URI.create(baseUrl), timeout, Set.copyOf(propagateHeaders));
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

        ResolverProfile toProfile(String name) {
            return new ResolverProfile(name, client, path, sourcePointer, responsePointer, errorMode,
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

        BatchProfile toProfile() {
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
        private int maxSessionEntries = 1_024;
        private long maxSessionBytes = 16 * 1024 * 1024;
        private int maxPendingIds = 10_000;
        private int streamPrefetch = 32;
        private int maxBatchSize = 100;
        private Duration batchFlushWindow = Duration.ofMillis(10);

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

        AggregationLimits toLimits() {
            return new AggregationLimits(maxDepth, maxRequests, maxConcurrency, maxResponseBytes, maxObjectBytes,
                    maxBufferedItems, maxSessionEntries, maxSessionBytes, maxPendingIds, streamPrefetch,
                    maxBatchSize, batchFlushWindow);
        }
    }
}
