package io.github.restaggregation.json;

import java.util.List;

public interface JsonAdapter {
    JsonDocument parse(byte[] bytes);

    JsonDocument at(JsonDocument document, String pointer);

    JsonDocument replace(JsonDocument document, String pointer, JsonDocument replacement);

    String text(JsonDocument document);

    JsonDocument array(List<JsonDocument> documents);

    <T> T toValue(JsonDocument document, Class<T> targetType);
}
