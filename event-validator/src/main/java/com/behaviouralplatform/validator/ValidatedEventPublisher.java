package com.behaviouralplatform.validator;

import com.behaviouralplatform.contracts.Topics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes validation outcomes with the key the event was consumed with (ADR 0003), waiting for the broker ack. */
@Component
class ValidatedEventPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    ValidatedEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Republishes the producer's exact JSON, framed with the id of the schema it conformed to. */
    void publishValid(String key, byte[] rawJson, int schemaId) {
        send(Topics.VALID, key, SchemaRegistryWireFormat.frame(schemaId, rawJson));
    }

    void publishInvalid(String key, JsonNode event, List<ValidationError> errors) {
        try {
            send(
                    Topics.INVALID,
                    key,
                    Json.MAPPER.writeValueAsBytes(InvalidEventDocument.of(event, errors, Instant.now())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize invalid event document", e);
        }
    }

    /**
     * Failures Kafka marks as retriable (broker unavailable, timeouts) become PublishFailedException and are retried.
     * Permanent ones (e.g. record too large) are rethrown as-is, so they are logged and skipped rather than retried forever.
     */
    private void send(String topic, String key, byte[] value) {
        try {
            kafkaTemplate.send(topic, key, value).join();
        } catch (RuntimeException e) {
            if (isRetriable(e)) {
                throw new PublishFailedException(topic, e);
            }
            throw e;
        }
    }

    private static boolean isRetriable(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RetriableException) {
                return true;
            }
        }
        return false;
    }
}
