package com.behaviouralplatform.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Kafka's record size limit is lowered below the collector's request cap, so its size rejection can be tested. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.kafka.producer.properties.max.request.size=32768")
@Import(TestcontainersConfiguration.class)
class EventIngestionIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    KafkaContainer kafka;

    @Test
    void acceptedEventIsOnRawTopicWithMetadataAndUserKey() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/events"))
                .header("Content-Type", "application/json")
                .header("X-Correlation-Id", "req-789")
                .POST(HttpRequest.BodyPublishers.ofString(TestEvents.VALID_JSON))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(202);
        ConsumerRecord<String, String> record = readSingleRecord(RawEventPublisher.TOPIC);
        assertThat(record.key()).isEqualTo("user-123");
        JsonNode published = TestEvents.MAPPER.readTree(record.value());
        assertThat(published.get("eventId").asString()).isEqualTo("01K5R4F8W8J5Z8XJH0N6F4P2C1");
        assertThat(published.get("correlationId").asString()).isEqualTo("req-789");
        assertThat(published.hasNonNull("receivedAt")).isTrue();
        assertThat(published.get("payload")).isEqualTo(TestEvents.validNode().get("payload"));
    }

    @Test
    void eventTooLargeForKafkaIsRejectedWith413() throws Exception {
        String eventId = "too-large-" + UUID.randomUUID();
        ObjectNode event = TestEvents.validNode();
        event.put("eventId", eventId);
        ((ObjectNode) event.get("payload")).put("category", "x".repeat(40_000));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/events"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event.toString()))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("application/problem+json"));
        assertThat(TestEvents.MAPPER.readTree(response.body()).get("detail").asString())
                .isEqualTo("Event exceeds the maximum size accepted by the platform");
        assertThat(countRecordsContaining(RawEventPublisher.TOPIC, eventId, Duration.ofSeconds(5)))
                .isZero();
    }

    @Test
    void oversizedChunkedBodyIsRejectedWith413() throws Exception {
        String eventId = "chunked-" + UUID.randomUUID();
        byte[] body = TestEvents.validOfSize(eventId, 70_000).getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/events"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body)))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("application/problem+json"));
        assertThat(TestEvents.MAPPER.readTree(response.body()).get("detail").asString())
                .isEqualTo("Request body exceeds the maximum event size of 64 KB");
        assertThat(countRecordsContaining(RawEventPublisher.TOPIC, eventId, Duration.ofSeconds(5)))
                .isZero();
    }

    private int countRecordsContaining(String topic, String text, Duration pollFor) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        int count = 0;
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + pollFor.toMillis();
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (record.value().contains(text)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private ConsumerRecord<String, String> readSingleRecord(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            consumer.subscribe(List.of(topic));
            ConsumerRecords<String, String> records = ConsumerRecords.empty();
            long deadline = System.currentTimeMillis() + 15_000;
            while (records.isEmpty() && System.currentTimeMillis() < deadline) {
                records = consumer.poll(Duration.ofMillis(500));
            }
            assertThat(records.count()).isEqualTo(1);
            return records.iterator().next();
        }
    }
}
