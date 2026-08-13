package io.github.restaggregation.boot4;

import io.github.restaggregation.json.JsonDocument;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.util.TokenBuffer;

final class Jackson3CollectionParser implements AutoCloseable {
    private final ObjectMapper objectMapper;
    private final Jackson3JsonAdapter adapter;
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

    Jackson3CollectionParser(
            ObjectMapper objectMapper,
            Jackson3JsonAdapter adapter,
            String itemsPointer,
            long maxObjectBytes,
            int maxBufferedItems) {
        this.objectMapper = objectMapper;
        this.adapter = adapter;
        this.itemsPointer = itemsPointer == null || itemsPointer.isBlank() ? "" : itemsPointer;
        this.maxObjectBytes = maxObjectBytes;
        this.maxBufferedItems = maxBufferedItems;
        try {
            parser = objectMapper.createNonBlockingByteArrayParser();
            feeder = (ByteArrayFeeder) parser.nonBlockingInputFeeder();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot create streaming JSON parser", error);
        }
    }

    List<JsonDocument> feed(byte[] bytes) {
        try {
            feeder.feedInput(bytes, 0, bytes.length);
            return drain(false);
        } catch (AggregationLimitException error) {
            throw error;
        } catch (Exception error) {
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
        } catch (AggregationLimitException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid streaming JSON response", error);
        }
    }

    boolean hasPendingItems() {
        return !complete;
    }

    @Override
    public void close() {
        parser.close();
    }

    private List<JsonDocument> drain(boolean endOfInput) throws Exception {
        List<JsonDocument> result = new ArrayList<>();
        while (result.size() < maxBufferedItems) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.NOT_AVAILABLE || token == null) {
                break;
            }
            if (!inItems) {
                if (token == JsonToken.START_ARRAY
                        && itemsPointer.equals(parser.streamReadContext().pathAsPointer().toString())) {
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

    private void startItem(JsonParser currentParser, JsonToken token) throws Exception {
        itemBuffer = TokenBuffer.forBuffering(currentParser, ObjectReadContext.empty());
        itemDepth = token.isStructStart() ? 1 : 0;
        itemBytes = 0;
        append(currentParser, token);
    }

    private void append(JsonParser currentParser, JsonToken token) throws Exception {
        itemBytes += estimateBytes(currentParser, token);
        if (itemBytes > maxObjectBytes) {
            throw new AggregationLimitException("root collection item exceeds maxObjectBytes");
        }
        itemBuffer.copyCurrentEvent(currentParser);
    }

    private JsonDocument finishItem() throws Exception {
        try (JsonParser itemParser = itemBuffer.asParser()) {
            JsonNode item = objectMapper.readTree(itemParser);
            return adapter.fromNode(item);
        } finally {
            itemBuffer.close();
            itemBuffer = null;
            itemBytes = 0;
        }
    }

    private static long estimateBytes(JsonParser currentParser, JsonToken token) throws Exception {
        return switch (token) {
            case PROPERTY_NAME -> currentParser.currentName() == null ? 8 : currentParser.currentName().length() + 4L;
            case VALUE_STRING -> currentParser.getTextLength() + 2L;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> currentParser.getTextLength();
            case VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> 5L;
            default -> 2L;
        };
    }
}
