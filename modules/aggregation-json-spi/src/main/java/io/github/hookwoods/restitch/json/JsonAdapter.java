package io.github.hookwoods.restitch.json;

import java.util.List;

/**
 * JSON abstraction used by the aggregation engine and supplied by platform integrations.
 *
 * <p>Implementations must treat documents as values and return documents compatible with the same adapter.
 */
public interface JsonAdapter {
    /** Parses a JSON payload into an adapter-owned document. */
    JsonDocument parse(byte[] bytes);

    /** Selects the JSON value at a JSON Pointer. */
    JsonDocument at(JsonDocument document, String pointer);

    /** Returns a copy of {@code document} with the value at {@code pointer} replaced. */
    JsonDocument replace(JsonDocument document, String pointer, JsonDocument replacement);

    /** Returns the scalar text representation of a JSON document. */
    String text(JsonDocument document);

    /** Creates a JSON array from adapter-owned documents. */
    JsonDocument array(List<JsonDocument> documents);

    /** Maps a JSON document to the requested Java type. */
    <T> T toValue(JsonDocument document, Class<T> targetType);
}
