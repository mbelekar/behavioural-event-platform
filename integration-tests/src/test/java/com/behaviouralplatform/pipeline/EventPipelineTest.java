package com.behaviouralplatform.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.behaviouralplatform.schemas.KafkaTopics;
import com.behaviouralplatform.schemas.SharedSchemaRegistry;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Design.md "Testing": HTTP → behavioural.raw → validator → behavioural.valid / behavioural.invalid. */
class EventPipelineTest {

    static final Duration TIMEOUT = Duration.ofSeconds(60);
    static final HttpClient HTTP = HttpClient.newHttpClient();

    static ServiceProcess collector;
    static ServiceProcess validator;
    static int collectorPort;

    @BeforeAll
    static void startServices() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            collectorPort = socket.getLocalPort();
        }
        collector = ServiceProcess.start(
                "event-collector",
                Map.of(
                        "KAFKA_BOOTSTRAP_SERVERS", SharedSchemaRegistry.bootstrapServers(),
                        "SERVER_PORT", String.valueOf(collectorPort)));
        validator = ServiceProcess.start(
                "event-validator",
                Map.of(
                        "KAFKA_BOOTSTRAP_SERVERS", SharedSchemaRegistry.bootstrapServers(),
                        "SCHEMA_REGISTRY_URL", SharedSchemaRegistry.schemaRegistryUrl()));
        awaitCollector();
    }

    @AfterAll
    static void stopServices() {
        if (collector != null) collector.close();
        if (validator != null) validator.close();
    }

    @Test
    void validEventFlowsFromHttpToTheTrustedTopic() throws Exception {
        String eventId = "pipeline-" + UUID.randomUUID();

        assertThat(post(event(eventId, "{\"productId\":\"SKU-981\",\"recommendationSource\":\"home\"}")))
                .isEqualTo(202);

        ConsumerRecord<String, byte[]> record =
                KafkaTopics.awaitRecord(SharedSchemaRegistry.bootstrapServers(), "behavioural.valid", eventId, TIMEOUT);
        assertThat(record.key()).isEqualTo("user-123");
        assertThat(new String(record.value(), StandardCharsets.UTF_8))
                .contains("\"correlationId\"")
                .contains("\"receivedAt\"");
    }

    @Test
    void invalidEventFlowsFromHttpToTheInvalidTopic() throws Exception {
        String eventId = "pipeline-" + UUID.randomUUID();

        assertThat(post(event(eventId, "{\"category\":\"laptops\"}"))).isEqualTo(202);

        ConsumerRecord<String, byte[]> record = KafkaTopics.awaitRecord(
                SharedSchemaRegistry.bootstrapServers(), "behavioural.invalid", eventId, TIMEOUT);
        assertThat(new String(record.value(), StandardCharsets.UTF_8))
                .contains("\"code\":\"REQUIRED_FIELD_MISSING\"")
                .contains("\"field\":\"payload.productId\"");
    }

    /** occurredAt is now: the collector sets receivedAt to now, and the validator rejects events over 7 days old. */
    private static String event(String eventId, String payload) {
        return """
                {"eventId":"%s","eventType":"product_viewed","schemaVersion":2,"occurredAt":"%s",
                 "source":"web","userId":"user-123","payload":%s}
                """.formatted(eventId, Instant.now(), payload);
    }

    private static int post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + collectorPort + "/v1/events"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static void awaitCollector() throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                HTTP.send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + collectorPort + "/"))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
                return;
            } catch (ConnectException e) {
                Thread.sleep(500);
            }
        }
        throw new AssertionError("event-collector did not start; see build/service-logs/event-collector.log");
    }
}
