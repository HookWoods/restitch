package io.github.restaggregation.boot4;

import io.github.restaggregation.json.JsonAdapter;
import io.github.restaggregation.json.JsonDocument;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class Jackson3JsonAdapter implements JsonAdapter {
    private final ObjectMapper mapper;

    public Jackson3JsonAdapter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public JsonDocument parse(byte[] bytes) {
        try {
            return new Jackson3Document(mapper.readTree(bytes));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid JSON document", error);
        }
    }

    @Override
    public JsonDocument at(JsonDocument document, String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return document;
        }
        return new Jackson3Document(node(document).at(pointer));
    }

    @Override
    public JsonDocument replace(JsonDocument document, String pointer, JsonDocument replacement) {
        JsonNode root = node(document).deepCopy();
        JsonNode value = node(replacement).deepCopy();
        if (pointer == null || pointer.isEmpty()) {
            return new Jackson3Document(value);
        }
        int separator = pointer.lastIndexOf('/');
        String parentPointer = separator == 0 ? "" : pointer.substring(0, separator);
        String property = decode(pointer.substring(separator + 1));
        JsonNode parent = root.at(parentPointer);
        if (parent.isObject()) {
            ((ObjectNode) parent).set(property, value);
        } else if (parent.isArray()) {
            ((ArrayNode) parent).set(Integer.parseInt(property), value);
        } else {
            throw new IllegalArgumentException("Cannot replace JSON Pointer " + pointer);
        }
        return new Jackson3Document(root);
    }

    @Override
    public String text(JsonDocument document) {
        JsonNode value = node(document);
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    @Override
    public JsonDocument array(List<JsonDocument> documents) {
        ArrayNode array = mapper.createArrayNode();
        for (JsonDocument document : documents) {
            array.add(node(document).deepCopy());
        }
        return new Jackson3Document(array);
    }

    @Override
    public <T> T toValue(JsonDocument document, Class<T> targetType) {
        try {
            return mapper.treeToValue(node(document), targetType);
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot bind JSON document", error);
        }
    }

    public JsonDocument toDocument(Object value) {
        try {
            return new Jackson3Document(mapper.valueToTree(value));
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot serialize value", error);
        }
    }

    ObjectMapper objectMapper() {
        return mapper;
    }

    JsonDocument fromNode(JsonNode value) {
        return new Jackson3Document(value);
    }

    private static JsonNode node(JsonDocument document) {
        if (!(document instanceof Jackson3Document jackson3Document)) {
            throw new IllegalArgumentException("Document was not created by Jackson3JsonAdapter");
        }
        return jackson3Document.node();
    }

    private static String decode(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private record Jackson3Document(JsonNode node) implements JsonDocument {}
}
