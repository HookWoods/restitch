package io.github.hookwoods.restitch.api;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks an MVC or WebFlux handler method whose response is hydrated through the named root profile.
 *
 * <p>Resolver behavior is configured in YAML rather than on the handler method.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface AggregateResponse {
    /** Returns the root profile name declared in aggregation configuration. */
    String value();
}
