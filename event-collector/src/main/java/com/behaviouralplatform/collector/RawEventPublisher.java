package com.behaviouralplatform.collector;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Publishes events to behavioural.raw and waits for the broker acknowledgement. */
@Component
class RawEventPublisher {

    static final String TOPIC = "behavioural.raw";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    RawEventPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    void publish(BehaviouralEvent event) {
        String json = jsonMapper.writeValueAsString(event);
        try {
            kafkaTemplate.send(TOPIC, partitionKey(event), json).join();
        } catch (RuntimeException e) {
            throw new PublishFailedException(event.eventId(), e);
        }
    }

    /** userId preserves per-user ordering; sessionId is the fallback (Design.md section 17). */
    static String partitionKey(BehaviouralEvent event) {
        return EventRequestChecks.isBlank(event.userId()) ? event.sessionId() : event.userId();
    }
}
