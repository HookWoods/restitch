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

@ConfigurationProperties(prefix = "aggregation")
public class AggregationProperties {
    private final Map<String, Client> clients = new LinkedHashMap<>();
    private final Map<String, Resolver> resolvers = new LinkedHashMap<>();
    private final Map<String, Root> roots = new LinkedHashMap<>();
    private final Limits limits = new Limits();

    public Map<String, Client> clients() {
        return clients;
    }

    public Map<String, Client> getClients() {
        return clients;
    }

    public Map<String, Resolver> resolvers() {
        return resolvers;
    }

    public Map<String, Resolver> getResolvers() {
        return resolvers;
    }

    public Map<String, Root> roots() {
        return roots;
    }

    public Map<String, Root> getRoots() {
        return roots;
    }

    public Limits limits() {
        return limits;
    }

    public Limits getLimits() {
        return limits;
    }

    public Map<String, ClientProfile> clientProfiles() {
        Map<String, ClientProfile> profiles = new LinkedHashMap<>();
        clients.forEach((name, client) -> profiles.put(name, client.toProfile()));
        return profiles;
    }

    public Map<String, ResolverProfile> resolverProfiles() {
        Map<String, ResolverProfile> profiles = new LinkedHashMap<>();
        resolvers.forEach((name, resolver) -> profiles.put(name, resolver.toProfile(name)));
        return profiles;
    }

    public AggregationLimits aggregationLimits() {
        return limits.toLimits();
    }

    public static class Client {
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(5);
        private Set<String> propagateHeaders = new LinkedHashSet<>();

        public Client() {}

        public Client(String baseUrl, Duration timeout, Set<String> propagatedHeaders) {
            this.baseUrl = baseUrl;
            this.timeout = timeout;
            this.propagateHeaders = new LinkedHashSet<>(propagatedHeaders == null ? Set.of() : propagatedHeaders);
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Set<String> getPropagatedHeaders() {
            return propagateHeaders;
        }

        public void setPropagatedHeaders(Set<String> propagatedHeaders) {
            setPropagateHeaders(propagatedHeaders);
        }

        public Set<String> getPropagateHeaders() {
            return propagateHeaders;
        }

        public void setPropagateHeaders(Set<String> propagateHeaders) {
            this.propagateHeaders = new LinkedHashSet<>(propagateHeaders == null ? Set.of() : propagateHeaders);
        }

        private ClientProfile toProfile() {
            return new ClientProfile(URI.create(baseUrl), timeout, propagateHeaders);
        }
    }

    public static class Resolver {
        private String client;
        private String path;
        private String sourcePointer;
        private String responsePointer;
        private ErrorMode errorMode = ErrorMode.FAIL_FAST;
        private Batch batch;

        public Resolver() {}

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

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSourcePointer() {
            return sourcePointer;
        }

        public void setSourcePointer(String sourcePointer) {
            this.sourcePointer = sourcePointer;
        }

        public String getResponsePointer() {
            return responsePointer;
        }

        public void setResponsePointer(String responsePointer) {
            this.responsePointer = responsePointer;
        }

        public ErrorMode getErrorMode() {
            return errorMode;
        }

        public void setErrorMode(ErrorMode errorMode) {
            this.errorMode = errorMode;
        }

        public Batch getBatch() {
            return batch;
        }

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

    public static class Root {
        private String client;
        private String path;
        private String responsePointer;
        private String itemsPointer;

        public String getClient() {
            return client;
        }

        public void setClient(String client) {
            this.client = client;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getResponsePointer() {
            return responsePointer;
        }

        public void setResponsePointer(String responsePointer) {
            this.responsePointer = responsePointer;
        }

        public String getItemsPointer() {
            return itemsPointer;
        }

        public void setItemsPointer(String itemsPointer) {
            this.itemsPointer = itemsPointer;
        }
    }

    public static class Batch {
        private String path;
        private String queryParameter;
        private String itemsPointer;
        private String itemKeyPointer;
        private int maxSize = 100;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getQueryParameter() {
            return queryParameter;
        }

        public void setQueryParameter(String queryParameter) {
            this.queryParameter = queryParameter;
        }

        public String getItemsPointer() {
            return itemsPointer;
        }

        public void setItemsPointer(String itemsPointer) {
            this.itemsPointer = itemsPointer;
        }

        public String getItemKeyPointer() {
            return itemKeyPointer;
        }

        public void setItemKeyPointer(String itemKeyPointer) {
            this.itemKeyPointer = itemKeyPointer;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        private BatchProfile toProfile() {
            return new BatchProfile(path, queryParameter, itemsPointer, itemKeyPointer, maxSize);
        }
    }

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

        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int value) { maxDepth = value; }
        public int getMaxRequests() { return maxRequests; }
        public void setMaxRequests(int value) { maxRequests = value; }
        public int getMaxConcurrency() { return maxConcurrency; }
        public void setMaxConcurrency(int value) { maxConcurrency = value; }
        public long getMaxResponseBytes() { return maxResponseBytes; }
        public void setMaxResponseBytes(long value) { maxResponseBytes = value; }
        public long getMaxObjectBytes() { return maxObjectBytes; }
        public void setMaxObjectBytes(long value) { maxObjectBytes = value; }
        public int getMaxBufferedItems() { return maxBufferedItems; }
        public void setMaxBufferedItems(int value) { maxBufferedItems = value; }
        public int getMaxSessionEntries() { return maxSessionEntries; }
        public void setMaxSessionEntries(int value) { maxSessionEntries = value; }
        public long getMaxSessionBytes() { return maxSessionBytes; }
        public void setMaxSessionBytes(long value) { maxSessionBytes = value; }
        public int getMaxPendingIds() { return maxPendingIds; }
        public void setMaxPendingIds(int value) { maxPendingIds = value; }
        public int getStreamPrefetch() { return streamPrefetch; }
        public void setStreamPrefetch(int value) { streamPrefetch = value; }
        public int getMaxBatchSize() { return maxBatchSize; }
        public void setMaxBatchSize(int value) { maxBatchSize = value; }
        public Duration getBatchFlushWindow() { return batchFlushWindow; }
        public void setBatchFlushWindow(Duration value) { batchFlushWindow = value; }
    }
}
