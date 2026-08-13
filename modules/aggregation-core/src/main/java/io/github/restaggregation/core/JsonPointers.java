package io.github.restaggregation.core;

final class JsonPointers {
    private JsonPointers() {}

    static void requirePointer(String value, String field) {
        if (value != null && !value.isBlank() && !value.startsWith("/")) {
            throw new IllegalArgumentException(field + " must be a JSON Pointer");
        }
    }
}
