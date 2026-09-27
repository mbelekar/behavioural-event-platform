package com.behaviouralplatform.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.behaviouralplatform.schemas.KafkaTopics;
import com.behaviouralplatform.schemas.SharedSchemaRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializerConfig;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class RawEventListenerIntegrationTest {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", SharedSchemaRegistry::bootstrapServers);
        registry.add("validator.schema-registry.url", SharedSchemaRegistry::schemaRegistryUrl);
    }

    @Test
    void validEventIsRepublishedInWireFormatWithSameKey() throws Exception {
        String eventId = ValidatorTestEvents.uniqueEventId();
        String raw = ValidatorTestEvents.json(eventId, "product_viewed", 2, "{\"productId\":\"SKU-981\",\"recommendationSource\":\"home\"}");

        KafkaTopics.send(SharedSchemaRegistry.bootstrapServers(), "behavioural.raw", "user-123", raw);

        ConsumerRecord<String, byte[]> record = KafkaTopics.awaitRecord(
                SharedSchemaRegistry.bootstrapServers(), ValidatedEventPublisher.VALID_TOPIC, eventId, TIMEOUT);
        assertThat(record.key()).isEqualTo("user-123");
        assertThat(ByteBuffer.wrap(record.value(), 1, 4).getInt())
                .isEqualTo(SharedSchemaRegistry.client().getSchemaMetadata("product_viewed", 2).getId());
        try (var deserializer = new KafkaJsonSchemaDeserializer<JsonNode>()) {
            deserializer.configure(Map.of(
                    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SharedSchemaRegistry.schemaRegistryUrl(),
                    KafkaJsonSchemaDeserializerConfig.JSON_VALUE_TYPE, JsonNode.class.getName()), false);
            JsonNode read = deserializer.deserialize(ValidatedEventPublisher.VALID_TOPIC, record.value());
            assertThat(read).isEqualTo(Json.MAPPER.readTree(raw));
        }
    }

    @Test
    void invalidEventGoesToInvalidTopicWithErrors() throws Exception {
        String eventId = ValidatorTestEvents.uniqueEventId();
        String raw = ValidatorTestEvents.json(eventId, "product_viewed", 2, "{\"category\":\"laptops\"}");

        KafkaTopics.send(SharedSchemaRegistry.bootstrapServers(), "behavioural.raw", "user-123", raw);

        ConsumerRecord<String, byte[]> record = KafkaTopics.awaitRecord(
                SharedSchemaRegistry.bootstrapServers(), ValidatedEventPublisher.INVALID_TOPIC, eventId, TIMEOUT);
        assertThat(record.key()).isEqualTo("user-123");
        JsonNode document = Json.MAPPER.readTree(record.value());
        assertThat(document.get("event")).isEqualTo(Json.MAPPER.readTree(raw));
        assertThat(document.get("validationErrors").get(0).get("code").asText()).isEqualTo("REQUIRED_FIELD_MISSING");
        assertThat(document.get("validationErrors").get(0).get("field").asText()).isEqualTo("payload.productId");
        assertThat(document.hasNonNull("validatedAt")).isTrue();
    }

    @Test
    void v1EventIsStillValidAfterV2IsRegistered() {
        String eventId = ValidatorTestEvents.uniqueEventId();
        String raw = ValidatorTestEvents.json(eventId, "product_viewed", 1, "{\"productId\":\"SKU-981\"}");

        KafkaTopics.send(SharedSchemaRegistry.bootstrapServers(), "behavioural.raw", "user-123", raw);

        KafkaTopics.awaitRecord(SharedSchemaRegistry.bootstrapServers(), ValidatedEventPublisher.VALID_TOPIC, eventId, TIMEOUT);
    }

    @Test
    void schemaVersionZeroIsInvalidNotRetried() throws Exception {
        String eventId = ValidatorTestEvents.uniqueEventId();
        String raw = ValidatorTestEvents.json(eventId, "product_viewed", 0, "{\"productId\":\"SKU-981\"}");

        KafkaTopics.send(SharedSchemaRegistry.bootstrapServers(), "behavioural.raw", "user-123", raw);

        ConsumerRecord<String, byte[]> record = KafkaTopics.awaitRecord(
                SharedSchemaRegistry.bootstrapServers(), ValidatedEventPublisher.INVALID_TOPIC, eventId, TIMEOUT);
        JsonNode document = Json.MAPPER.readTree(record.value());
        assertThat(document.get("validationErrors").get(0).get("code").asText()).isEqualTo("UNKNOWN_SCHEMA_VERSION");
    }
}
