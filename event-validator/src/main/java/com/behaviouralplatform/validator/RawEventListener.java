package com.behaviouralplatform.validator;

import com.behaviouralplatform.contracts.Topics;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** behavioural.raw → validation → behavioural.valid | behavioural.invalid. Offsets commit after the publish is acknowledged. */
@Component
class RawEventListener {

    private static final Logger log = LoggerFactory.getLogger(RawEventListener.class);

    private final EventValidator validator;
    private final ValidatedEventPublisher publisher;

    RawEventListener(EventValidator validator, ValidatedEventPublisher publisher) {
        this.validator = validator;
        this.publisher = publisher;
    }

    @KafkaListener(topics = Topics.RAW, groupId = "event-validator")
    void onRawEvent(ConsumerRecord<String, byte[]> record) throws IOException {
        JsonNode event = Json.MAPPER.readTree(record.value());
        ValidationResult result = validator.validate(event);
        if (result.valid()) {
            publisher.publishValid(record.key(), record.value(), result.schemaId());
        } else {
            log.info("Event {} is invalid: {}", event.path("eventId").asText(), result.errors());
            publisher.publishInvalid(record.key(), event, result.errors());
        }
    }
}
