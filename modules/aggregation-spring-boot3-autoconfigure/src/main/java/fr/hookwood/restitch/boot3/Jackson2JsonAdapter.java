package fr.hookwood.restitch.boot3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.hookwood.restitch.json.JsonAdapter;
import fr.hookwood.restitch.json.JsonDocument;
import java.util.ArrayList;
import java.util.List;

public final class Jackson2JsonAdapter implements JsonAdapter {
    private final ObjectMapper objectMapper;

    public Jackson2JsonAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    @Override
    public JsonDocument parse(byte[] bytes) {
        try {
            return new Document(objectMapper.readTree(bytes));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid JSON response", error);
        }
    }

    public JsonDocument fromValue(Object value) {
        return new Document(objectMapper.valueToTree(value));
    }

    JsonDocument fromNode(JsonNode value) {
        return new Document(value);
    }

    public List<JsonDocument> elements(JsonDocument document) {
        JsonNode node = node(document);
        if (!node.isArray()) {
            throw new IllegalArgumentException("JSON value is not an array");
        }
        List<JsonDocument> result = new ArrayList<>(node.size());
        node.forEach(element -> result.add(new Document(element)));
        return List.copyOf(result);
    }

    @Override
    public JsonDocument at(JsonDocument document, String pointer) {
        return new Document(node(document).at(pointer == null ? "" : pointer));
    }

    @Override
    public JsonDocument replace(JsonDocument document, String pointer, JsonDocument replacement) {
        if (pointer == null || pointer.isEmpty()) {
            return replacement;
        }
        JsonNode root = node(document).deepCopy();
        String[] tokens = pointer.substring(1).split("/", -1);
        JsonNode current = root;
        for (int index = 0; index < tokens.length - 1; index++) {
            current = child(current, decode(tokens[index]));
        }
        String token = decode(tokens[tokens.length - 1]);
        if (current instanceof ObjectNode objectNode) {
            objectNode.set(token, node(replacement));
        } else if (current instanceof ArrayNode arrayNode) {
            arrayNode.set(Integer.parseInt(token), node(replacement));
        } else {
            throw new IllegalArgumentException("Cannot replace JSON Pointer " + pointer);
        }
        return new Document(root);
    }

    @Override
    public String text(JsonDocument document) {
        JsonNode value = node(document);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    @Override
    public JsonDocument array(List<JsonDocument> documents) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        documents.forEach(document -> array.add(node(document)));
        return new Document(array);
    }

    @Override
    public <T> T toValue(JsonDocument document, Class<T> targetType) {
        try {
            return objectMapper.treeToValue(node(document), targetType);
        } catch (Exception error) {
            throw new IllegalArgumentException("Cannot bind JSON value", error);
        }
    }

    private static JsonNode child(JsonNode current, String token) {
        if (current.isObject()) {
            return current.get(token);
        }
        if (current.isArray()) {
            return current.get(Integer.parseInt(token));
        }
        throw new IllegalArgumentException("Invalid JSON Pointer segment " + token);
    }

    private static String decode(String token) {
        return token.replace("~1", "/").replace("~0", "~");
    }

    private static JsonNode node(JsonDocument document) {
        return ((Document) document).node();
    }

    private record Document(JsonNode node) implements JsonDocument {}
}
