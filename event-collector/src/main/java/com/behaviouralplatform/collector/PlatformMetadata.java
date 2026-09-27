package com.behaviouralplatform.collector;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import java.time.Instant;
import java.util.UUID;

/** Adds platform-owned metadata. Client-supplied values other than receivedAt are never changed. */
final class PlatformMetadata {

    private PlatformMetadata() {
    }

    static BehaviouralEvent apply(BehaviouralEvent event, String headerCorrelationId, Instant receivedAt) {
        String correlationId = event.correlationId();
        if (EventRequestChecks.isBlank(correlationId)) {
            correlationId = EventRequestChecks.isBlank(headerCorrelationId)
                    ? UUID.randomUUID().toString()
                    : headerCorrelationId;
        }
        return new BehaviouralEvent(
                event.eventId(),
                event.eventType(),
                event.schemaVersion(),
                event.occurredAt(),
                event.source(),
                event.userId(),
                event.sessionId(),
                correlationId,
                receivedAt,
                event.payload());
    }
}
