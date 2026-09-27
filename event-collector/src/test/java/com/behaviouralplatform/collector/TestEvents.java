package com.behaviouralplatform.collector;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class TestEvents {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** The Design.md example event, without platform metadata. */
    static final String VALID_JSON = """
            {
              "eventId": "01K5R4F8W8J5Z8XJH0N6F4P2C1",
              "eventType": "product_viewed",
              "schemaVersion": 2,
              "occurredAt": "2026-09-27T01:23:31Z",
              "source": "web",
              "userId": "user-123",
              "sessionId": "session-456",
              "payload": { "productId": "SKU-981", "category": "laptops" }
            }
            """;

    private TestEvents() {}

    static ObjectNode validNode() {
        return (ObjectNode) MAPPER.readTree(VALID_JSON);
    }

    static BehaviouralEvent valid() {
        return toEvent(validNode());
    }

    static BehaviouralEvent toEvent(ObjectNode node) {
        return MAPPER.treeToValue(node, BehaviouralEvent.class);
    }
}
