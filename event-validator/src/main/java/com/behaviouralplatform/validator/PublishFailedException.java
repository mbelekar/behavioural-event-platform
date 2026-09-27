package com.behaviouralplatform.validator;

/** Kafka did not acknowledge a publish. Retried; never treated as an invalid event. */
class PublishFailedException extends RuntimeException {

    PublishFailedException(String topic, Throwable cause) {
        super("Failed to publish to " + topic, cause);
    }
}
