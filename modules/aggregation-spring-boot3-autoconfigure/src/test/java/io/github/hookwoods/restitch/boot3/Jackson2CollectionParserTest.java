package io.github.hookwoods.restitch.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hookwoods.restitch.json.JsonDocument;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Jackson2CollectionParserTest {
    @Test
    void limitsItemsReturnedPerParserBuffer() throws Exception {
        Jackson2JsonAdapter adapter = new Jackson2JsonAdapter(new ObjectMapper());
        List<JsonDocument> items = new ArrayList<>();
        try (Jackson2CollectionParser parser = new Jackson2CollectionParser(
                adapter.objectMapper(), adapter, "/data/items", 1024, 1)) {
            items.addAll(parser.feed(("{\"data\":{\"items\":[{\"id\":1},{\"id\":2},{\"id\":3}]}}")
                    .getBytes(StandardCharsets.UTF_8)));
            assertThat(items).hasSize(1);
            while (parser.hasPendingItems()) {
                List<JsonDocument> next = parser.end();
                assertThat(next).hasSizeLessThanOrEqualTo(1);
                items.addAll(next);
            }
        }
        assertThat(items).hasSize(3);
    }
}
