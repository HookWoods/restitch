package io.github.restaggregation.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsonAdapterContractTest {
    @Test
    void adapterContractKeepsDocumentsOpaque() {
        assertThat(JsonDocument.class).isInterface();
        assertThat(JsonAdapter.class.getDeclaredMethods())
                .extracting("name")
                .contains("parse", "at", "replace", "text", "array", "toValue");
    }

    @Test
    void adapterMethodsUsePortableTypes() throws Exception {
        assertThat(JsonAdapter.class.getMethod("parse", byte[].class).getReturnType()).isEqualTo(JsonDocument.class);
        assertThat(JsonAdapter.class.getMethod("array", List.class).getReturnType()).isEqualTo(JsonDocument.class);
    }
}
