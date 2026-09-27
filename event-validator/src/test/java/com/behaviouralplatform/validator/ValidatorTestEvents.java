package com.behaviouralplatform.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Events shaped exactly like the collector writes them to behavioural.raw. */
final class ValidatorTestEvents {

    private ValidatorTestEvents() {
    }

    static String uniqueEventId() {
        return "evt-" + UUID.randomUUID();
    }

    static String json(String eventId, String eventType, int version, String payload) {
        return """
                {"eventId":"%s","eventType":"%s","schemaVersion":%d,"occurredAt":"2026-09-27T01:23:31Z",
                 "source":"web","userId":"user-123","sessionId":null,"correlationId":"req-789",
                 "receivedAt":"2026-09-27T02:05:42.441768Z","payload":%s}
                """.formatted(eventId, eventType, version, payload);
    }

    static ObjectNode productViewedV2() {
        return node(json(uniqueEventId(), "product_viewed", 2, "{\"productId\":\"SKU-981\",\"recommendationSource\":\"home\"}"));
    }

    static ObjectNode node(String json) {
        try {
            return (ObjectNode) Json.MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
