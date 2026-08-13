package io.github.hookwoods.restitch.boot3;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.async.ByteArrayFeeder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.github.hookwoods.restitch.json.JsonDocument;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class Jackson2CollectionParser implements AutoCloseable {
    private final ObjectMapper objectMapper;
    private final Jackson2JsonAdapter adapter;
    private final String itemsPointer;
    private final long maxObjectBytes;
    private final int maxBufferedItems;
    private final JsonParser parser;
    private final ByteArrayFeeder feeder;
    private TokenBuffer itemBuffer;
    private int itemDepth;
    private boolean inItems;
    private boolean foundItems;
    private long itemBytes;
    private boolean endOfInput;
    private boolean complete;

    Jackson2CollectionParser(
            ObjectMapper objectMapper,
            Jackson2JsonAdapter adapter,
            String itemsPointer,
            long maxObjectBytes,
            int maxBufferedItems) {
        this.objectMapper = objectMapper;
        this.adapter = adapter;
        this.itemsPointer = itemsPointer == null || itemsPointer.isBlank() ? "" : itemsPointer;
        this.maxObjectBytes = maxObjectBytes;
        this.maxBufferedItems = maxBufferedItems;
        try {
            parser = objectMapper.getFactory().createNonBlockingByteArrayParser();
            feeder = (ByteArrayFeeder) parser.getNonBlockingInputFeeder();
        } catch (IOException error) {
            throw new IllegalStateException("Cannot create streaming JSON parser", error);
        }
    }

    List<JsonDocument> feed(byte[] bytes) {
        try {
            feeder.feedInput(bytes, 0, bytes.length);
            return drain(false);
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid streaming JSON response", error);
        }
    }

    List<JsonDocument> end() {
        try {
            if (!endOfInput) {
                feeder.endOfInput();
                endOfInput = true;
            }
            List<JsonDocument> result = drain(true);
            if (result.size() < maxBufferedItems && (itemDepth != 0 || inItems)) {
                throw new IllegalArgumentException("Truncated root collection item");
            }
            if (result.size() < maxBufferedItems) {
                complete = true;
            }
            return result;
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid streaming JSON response", error);
        }
    }

    boolean hasPendingItems() {
        return !complete;
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }

    private List<JsonDocument> drain(boolean endOfInput) throws IOException {
        List<JsonDocument> result = new ArrayList<>();
        while (result.size() < maxBufferedItems) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.NOT_AVAILABLE) {
                break;
            }
            if (token == null) {
                break;
            }
            if (!inItems) {
                if (token == JsonToken.START_ARRAY
                        && itemsPointer.equals(parser.getParsingContext().pathAsPointer().toString())) {
                    inItems = true;
                    foundItems = true;
                }
                continue;
            }
            if (itemDepth == 0 && token == JsonToken.END_ARRAY) {
                inItems = false;
                continue;
            }
            if (itemDepth == 0) {
                startItem(parser, token);
                if (!token.isStructStart()) {
                    result.add(finishItem());
                }
                continue;
            }
            append(parser, token);
            if (token.isStructStart()) {
                itemDepth++;
            } else if (token.isStructEnd()) {
                itemDepth--;
                if (itemDepth == 0) {
                    result.add(finishItem());
                }
            }
        }
        if (endOfInput && !foundItems) {
            throw new IllegalArgumentException("Configured root collection pointer was not found");
        }
        return result;
    }

    private void startItem(JsonParser currentParser, JsonToken token) throws IOException {
        itemBuffer = new TokenBuffer(currentParser);
        itemDepth = token.isStructStart() ? 1 : 0;
        itemBytes = 0;
        append(currentParser, token);
    }

    private void append(JsonParser currentParser, JsonToken token) throws IOException {
        itemBytes += estimateBytes(currentParser, token);
        if (itemBytes > maxObjectBytes) {
            throw new IllegalArgumentException("root collection item exceeds maxObjectBytes");
        }
        itemBuffer.copyCurrentEvent(currentParser);
    }

    private JsonDocument finishItem() throws IOException {
        try (JsonParser itemParser = itemBuffer.asParser()) {
            JsonNode item = objectMapper.readTree(itemParser);
            return adapter.fromNode(item);
        } finally {
            itemBuffer.close();
            itemBuffer = null;
            itemBytes = 0;
        }
    }

    private static long estimateBytes(JsonParser currentParser, JsonToken token) throws IOException {
        return switch (token) {
            case FIELD_NAME -> currentParser.getCurrentName() == null ? 8 : currentParser.getCurrentName().length() + 4L;
            case VALUE_STRING -> currentParser.getTextLength() + 2L;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> currentParser.getTextLength();
            case VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> 5L;
            default -> 2L;
        };
    }
}
