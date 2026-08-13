package io.github.hookwoods.restitch.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class Jackson3JsonAdapterTest {
    private final Jackson3JsonAdapter adapter = new Jackson3JsonAdapter(new ObjectMapper());

    @Test
    void navigatesAndReplacesJsonPointer() {
        var document = adapter.parse("{\"data\":{\"user\":{\"id\":\"9\",\"name\":\"Ada\"}}}".getBytes());

        var user = adapter.at(document, "/data/user");
        assertThat(adapter.toValue(user, User.class).name()).isEqualTo("Ada");

        var replaced = adapter.replace(document, "/data/user/name", adapter.parse("\"Grace\"".getBytes()));
        assertThat(adapter.toValue(adapter.at(replaced, "/data/user"), User.class).name()).isEqualTo("Grace");
    }

    record User(String id, String name) {}
}
