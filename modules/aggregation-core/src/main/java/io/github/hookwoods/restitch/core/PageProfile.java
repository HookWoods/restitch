package io.github.hookwoods.restitch.core;

import java.util.Map;

/**
 * JSON Pointer configuration for a paginated root response.
 *
 * @param itemsPointer JSON Pointer to page items
 * @param metadataPointers JSON Pointers keyed by pagination metadata name
 */
public record PageProfile(String itemsPointer, Map<String, String> metadataPointers) {
    /**
     * Creates validated JSON Pointer configuration for a paginated response.
     *
     * @param itemsPointer JSON Pointer to page items
     * @param metadataPointers JSON Pointers keyed by pagination metadata name
     */
    public PageProfile {
        JsonPointers.requirePointer(itemsPointer, "itemsPointer");
        metadataPointers = Map.copyOf(metadataPointers == null ? Map.of() : metadataPointers);
        metadataPointers.forEach((name, pointer) -> JsonPointers.requirePointer(pointer, "metadata pointer " + name));
    }
}
