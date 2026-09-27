package com.behaviouralplatform.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BehaviouralEventJsonTest {

    private static final String DESIGN_EXAMPLE = """
            {
              "eventId": "01K5R4F8W8J5Z8XJH0N6F4P2C1",
              "eventType": "product_viewed",
              "schemaVersion": 2,
              "occurredAt": "2026-09-27T01:23:31Z",
              "source": "web",
              "userId": "user-123",
              "sessionId": "session-456",
              "correlationId": "req-789",
              "payload": { "productId": "SKU-981", "category": "laptops" }
            }
            """;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void readsTheDesignDocumentExample() {
        BehaviouralEvent event = mapper.readValue(DESIGN_EXAMPLE, BehaviouralEvent.class);

        assertThat(event.eventId()).isEqualTo("01K5R4F8W8J5Z8XJH0N6F4P2C1");
        assertThat(event.schemaVersion()).isEqualTo(2);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-09-27T01:23:31Z"));
        assertThat(event.receivedAt()).isNull();
        assertThat(event.payload().get("productId").asString()).isEqualTo("SKU-981");
    }

    @Test
    void writesTimestampsAsIsoStrings() {
        BehaviouralEvent event = mapper.readValue(DESIGN_EXAMPLE, BehaviouralEvent.class);

        String json = mapper.writeValueAsString(event);

        assertThat(json).contains("\"occurredAt\":\"2026-09-27T01:23:31Z\"");
        assertThat(mapper.readValue(json, BehaviouralEvent.class)).isEqualTo(event);
    }
}
