package io.github.hookwoods.restitch.json;

import java.util.List;

/**
 * JSON abstraction used by the aggregation engine and supplied by platform integrations.
 *
 * <p>Implementations must treat documents as values and return documents compatible with the same adapter.
 */
public interface JsonAdapter {
    /**
     * Parses a JSON payload into an adapter-owned document.
     *
     * @param bytes UTF-8 JSON payload bytes
     * @return parsed document owned by this adapter
     */
    JsonDocument parse(byte[] bytes);

    /**
     * Selects the JSON value at a JSON Pointer.
     *
     * @param document source document owned by this adapter
     * @param pointer JSON Pointer to select
     * @return document at the requested pointer
     */
    JsonDocument at(JsonDocument document, String pointer);

    /**
     * Returns a copy of {@code document} with the value at {@code pointer} replaced.
     *
     * @param document source document owned by this adapter
     * @param pointer JSON Pointer to replace
     * @param replacement document to store at the pointer
     * @return updated document owned by this adapter
     */
    JsonDocument replace(JsonDocument document, String pointer, JsonDocument replacement);

    /**
     * Returns the scalar text representation of a JSON document.
     *
     * @param document document to convert to text
     * @return scalar text representation
     */
    String text(JsonDocument document);

    /**
     * Creates a JSON array from adapter-owned documents.
     *
     * @param documents documents to include in order
     * @return array document owned by this adapter
     */
    JsonDocument array(List<JsonDocument> documents);

    /**
     * Maps a JSON document to the requested Java type.
     *
     * @param document document to map
     * @param targetType target Java type
     * @param <T> requested Java type
     * @return mapped value
     */
    <T> T toValue(JsonDocument document, Class<T> targetType);
}
