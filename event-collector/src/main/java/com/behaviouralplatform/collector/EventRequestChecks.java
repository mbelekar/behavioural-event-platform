package com.behaviouralplatform.collector;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import java.util.ArrayList;
import java.util.List;

/** Minimal shape checks done at ingestion. Schema and business validation belong to the validator. */
final class EventRequestChecks {

    private EventRequestChecks() {
    }

    static List<String> missingFields(BehaviouralEvent event) {
        List<String> missing = new ArrayList<>();
        if (isBlank(event.eventId())) missing.add("eventId");
        if (isBlank(event.eventType())) missing.add("eventType");
        if (event.schemaVersion() == null) missing.add("schemaVersion");
        if (event.occurredAt() == null) missing.add("occurredAt");
        if (isBlank(event.source())) missing.add("source");
        if (isBlank(event.userId()) && isBlank(event.sessionId())) missing.add("userId|sessionId");
        if (event.payload() == null || !event.payload().isObject()) missing.add("payload");
        return missing;
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
