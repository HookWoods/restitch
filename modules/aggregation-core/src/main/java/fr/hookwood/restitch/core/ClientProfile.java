package fr.hookwood.restitch.core;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

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
