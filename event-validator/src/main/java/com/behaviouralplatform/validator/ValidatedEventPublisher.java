package com.behaviouralplatform.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes validation outcomes with the key the event was consumed with (ADR 0003), waiting for the broker ack. */
@Component
class ValidatedEventPublisher {

    static final String VALID_TOPIC = "behavioural.valid";
    static final String INVALID_TOPIC = "behavioural.invalid";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    ValidatedEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** Republishes the producer's exact JSON, framed with the id of the schema it conformed to. */
    void publishValid(String key, String rawJson, int schemaId) {
        send(VALID_TOPIC, key, SchemaRegistryWireFormat.frame(schemaId, rawJson.getBytes(StandardCharsets.UTF_8)));
    }

    void publishInvalid(String key, JsonNode event, List<ValidationError> errors) {
        try {
            send(INVALID_TOPIC, key, Json.MAPPER.writeValueAsBytes(InvalidEventDocument.of(event, errors, Instant.now())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize invalid event document", e);
        }
    }

    private void send(String topic, String key, byte[] value) {
        try {
            kafkaTemplate.send(topic, key, value).join();
        } catch (RuntimeException e) {
            throw new PublishFailedException(topic, e);
        }
    }
}
