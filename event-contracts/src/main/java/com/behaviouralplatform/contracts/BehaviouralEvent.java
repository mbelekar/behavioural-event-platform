package com.behaviouralplatform.contracts;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * Common envelope for all behavioural events. {@code payload} is specific to {@code eventType}.
 * {@code correlationId} and {@code receivedAt} are platform metadata added by the collector.
 */
public record BehaviouralEvent(
        String eventId,
        String eventType,
        Integer schemaVersion,
        Instant occurredAt,
        String source,
        String userId,
        String sessionId,
        String correlationId,
        Instant receivedAt,
        JsonNode payload) {}
