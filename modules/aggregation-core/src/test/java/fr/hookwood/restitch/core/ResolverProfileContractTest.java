package fr.hookwood.restitch.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.hookwood.restitch.api.ErrorMode;
import org.junit.jupiter.api.Test;

class ResolverProfileContractTest {
    @Test
    void profileRejectsNonPointerSourceAndResponsePaths() {
        assertThatThrownBy(() -> new ResolverProfile(
                        "order-owner", "identity", "/users/{id}", "ownerId", "data/user", ErrorMode.FAIL_FAST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON Pointer");
    }
}
