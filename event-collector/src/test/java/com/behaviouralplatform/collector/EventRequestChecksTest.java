package com.behaviouralplatform.collector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

class EventRequestChecksTest {

    @Test
    void acceptsTheValidEvent() {
        assertThat(EventRequestChecks.missingFields(TestEvents.valid())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "eventType", "schemaVersion", "occurredAt", "source", "payload"})
    void reportsAbsentRequiredField(String field) {
        ObjectNode node = TestEvents.validNode();
        node.remove(field);

        assertThat(EventRequestChecks.missingFields(TestEvents.toEvent(node))).containsExactly(field);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventId", "eventType", "source"})
    void treatsBlankStringAsMissing(String field) {
        ObjectNode node = TestEvents.validNode();
        node.put(field, "  ");

        assertThat(EventRequestChecks.missingFields(TestEvents.toEvent(node))).containsExactly(field);
    }

    @Test
    void acceptsSessionIdWithoutUserId() {
        ObjectNode node = TestEvents.validNode();
        node.put("userId", "");

        assertThat(EventRequestChecks.missingFields(TestEvents.toEvent(node))).isEmpty();
    }

    @Test
    void requiresUserIdOrSessionId() {
        ObjectNode node = TestEvents.validNode();
        node.remove("userId");
        node.remove("sessionId");

        assertThat(EventRequestChecks.missingFields(TestEvents.toEvent(node))).containsExactly("userId|sessionId");
    }

    @Test
    void rejectsNullPayload() {
        ObjectNode node = TestEvents.validNode();
        node.putNull("payload");

        assertThat(EventRequestChecks.missingFields(TestEvents.toEvent(node))).containsExactly("payload");
    }

    @Test
    void rejectsNonObjectPayload() {
        ObjectNode node = TestEvents.validNode();
        node.put("payload", 5);

        assertThat(EventRequestChecks.missingFields(TestEvents.toEvent(node))).containsExactly("payload");
    }
}
