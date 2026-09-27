package com.behaviouralplatform.collector;

class PublishFailedException extends RuntimeException {

    private final String eventId;

    PublishFailedException(String eventId, Throwable cause) {
        super("Failed to publish event " + eventId, cause);
        this.eventId = eventId;
    }

    String eventId() {
        return eventId;
    }
}
