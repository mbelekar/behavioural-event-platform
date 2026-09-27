package com.behaviouralplatform.collector;

/** Kafka rejected the event as larger than it accepts. Retrying cannot succeed. */
class EventTooLargeException extends RuntimeException {

    private final String eventId;

    EventTooLargeException(String eventId, Throwable cause) {
        super("Event " + eventId + " is too large to publish", cause);
        this.eventId = eventId;
    }

    String eventId() {
        return eventId;
    }
}
