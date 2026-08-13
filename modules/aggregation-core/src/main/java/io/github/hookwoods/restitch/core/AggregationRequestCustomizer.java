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
    /**
     * Returns headers to send for the resolved request URI.
     *
     * @param clientProfile downstream client configuration
     * @param resolverProfile resolver being invoked
     * @param requestUri resolved downstream request URI
     * @param headers headers collected before customization
     * @return headers to send with the downstream request
     */
    Map<String, String> customize(ClientProfile clientProfile, ResolverProfile resolverProfile, URI requestUri, Map<String, String> headers);
}
