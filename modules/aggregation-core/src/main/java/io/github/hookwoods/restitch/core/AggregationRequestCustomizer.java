package io.github.hookwoods.restitch.core;

import java.net.URI;
import java.util.Map;

/**
 * Customizes the outbound request headers for one resolver invocation.
 *
 * <p>Implementations should forward only headers explicitly allowed by the configured client profile.
 */
@FunctionalInterface
public interface AggregationRequestCustomizer {
    /** Returns headers to send for the resolved request URI. */
    Map<String, String> customize(ClientProfile clientProfile, ResolverProfile resolverProfile, URI requestUri, Map<String, String> headers);
}
