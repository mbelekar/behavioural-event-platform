package com.behaviouralplatform.collector;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import com.behaviouralplatform.contracts.Topics;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Publishes events to behavioural.raw and waits for the broker acknowledgement. */
@Component
class RawEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    RawEventPublisher(KafkaTemplate<String, String> kafkaTemplate, JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    void publish(BehaviouralEvent event) {
        String json = jsonMapper.writeValueAsString(event);
        try {
            kafkaTemplate.send(Topics.RAW, partitionKey(event), json).join();
        } catch (RuntimeException e) {
            RecordTooLargeException tooLarge = recordTooLarge(e);
            if (tooLarge != null) {
                throw new EventTooLargeException(event.eventId(), tooLarge);
            }
            throw new PublishFailedException(event.eventId(), e);
        }
    }

    private static RecordTooLargeException recordTooLarge(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof RecordTooLargeException tooLarge) {
                return tooLarge;
            }
        }
        return null;
    }

    /** userId preserves per-user ordering; sessionId is the fallback (Design.md "Event model"). */
    static String partitionKey(BehaviouralEvent event) {
        return EventRequestChecks.isBlank(event.userId()) ? event.sessionId() : event.userId();
    }
}
