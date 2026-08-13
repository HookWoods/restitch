package io.github.hookwoods.restitch.api;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a DTO field that Restitch hydrates through the named resolver profile.
 *
 * <p>The value identifies configuration only; route, batching, and error behavior remain in YAML.
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface AggregateRef {
    /**
     * Returns the resolver profile name declared in aggregation configuration.
     *
     * @return the configured resolver profile name
     */
    String value();
}
