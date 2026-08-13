package io.github.hookwoods.restitch.core;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/**
 * Immutable configuration for a named downstream REST client.
 *
 * @param baseUri absolute base URI for downstream requests
 * @param timeout maximum duration of each downstream request
 * @param propagatedHeaders explicitly allowlisted inbound headers
 */
public record ClientProfile(URI baseUri, Duration timeout, Set<String> propagatedHeaders) {
    public ClientProfile {
        if (baseUri == null || !baseUri.isAbsolute()) {
            throw new IllegalArgumentException("baseUri must be absolute");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        propagatedHeaders = Set.copyOf(propagatedHeaders == null ? Set.of() : propagatedHeaders);
    }
}
