package io.github.hookwoods.restitch.core;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Normalized identity for one request-local downstream resolution.
 *
 * @param client named downstream client
 * @param path normalized downstream request path
 * @param extractor response extractor identity
 * @param targetType target Java type
 * @param identityHeaders normalized headers that affect the response identity
 */
public record ResolutionKey(
        String client, String path, String extractor, Class<?> targetType, Map<String, String> identityHeaders) {
    /**
     * Creates a normalized downstream resolution identity.
     *
     * @param client named downstream client
     * @param path normalized downstream request path
     * @param extractor response extractor identity
     * @param targetType target Java type
     * @param identityHeaders headers that affect response identity
     */
    public ResolutionKey {
        if (client == null || client.isBlank()) {
            throw new IllegalArgumentException("client is required");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        if (extractor == null || extractor.isBlank()) {
            throw new IllegalArgumentException("extractor is required");
        }
        targetType = targetType == null ? Object.class : targetType;
        identityHeaders = normalizeHeaders(identityHeaders);
    }

    /**
     * Creates a key with {@link Object} as the target type.
     *
     * @param client named downstream client
     * @param path downstream request path
     * @param extractor response extractor identity
     * @param identityHeaders headers that affect response identity
     * @return normalized resolution identity
     */
    public static ResolutionKey of(String client, String path, String extractor, Map<String, String> identityHeaders) {
        return of(client, path, extractor, Object.class, identityHeaders);
    }

    /**
     * Creates a normalized resolution key.
     *
     * @param client named downstream client
     * @param path downstream request path
     * @param extractor response extractor identity
     * @param targetType target Java type
     * @param identityHeaders headers that affect response identity
     * @return normalized resolution identity
     */
    public static ResolutionKey of(
            String client, String path, String extractor, Class<?> targetType, Map<String, String> identityHeaders) {
        return new ResolutionKey(client, normalizePath(path), extractor, targetType, identityHeaders);
    }

    private static String normalizePath(String path) {
        return path.replaceAll("/{2,}", "/");
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        TreeMap<String, String> normalized = new TreeMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> normalized.put(name.toLowerCase(Locale.ROOT), value));
        }
        return Map.copyOf(normalized);
    }
}
