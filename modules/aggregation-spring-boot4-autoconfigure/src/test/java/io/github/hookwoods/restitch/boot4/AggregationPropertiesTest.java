package io.github.hookwoods.restitch.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AggregationPropertiesTest {
    @Test
    void bindsDocumentedAndLegacyHeaderPropertyNamesToOneProfile() {
        AggregationProperties properties = new AggregationProperties();
        AggregationProperties.Client client = new AggregationProperties.Client();
        client.setBaseUrl("https://identity.example");
        client.setTimeout(Duration.ofSeconds(2));
        client.setPropagateHeaders(Set.of("Authorization"));
        properties.clients().put("identity", client);

        assertThat(properties.clientProfiles().get("identity").propagatedHeaders())
                .containsExactly("Authorization");

        client.setPropagatedHeaders(Set.of("X-Request-Id"));
        assertThat(properties.clientProfiles().get("identity").propagatedHeaders())
                .containsExactly("X-Request-Id");
    }
}
