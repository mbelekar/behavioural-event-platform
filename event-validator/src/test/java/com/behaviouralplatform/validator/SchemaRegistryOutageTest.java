package com.behaviouralplatform.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.behaviouralplatform.contracts.Topics;
import com.behaviouralplatform.schemas.KafkaTopics;
import com.behaviouralplatform.schemas.SchemaRegistration;
import com.behaviouralplatform.schemas.SchemaRegistryContainers;
import com.behaviouralplatform.schemas.SharedSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SchemaRegistryOutageTest {

    static final Network NETWORK = Network.newNetwork();
    static final KafkaContainer KAFKA = SchemaRegistryContainers.kafka(NETWORK);
    static final GenericContainer<?> SCHEMA_REGISTRY = SchemaRegistryContainers.schemaRegistry(NETWORK);

    static {
        KAFKA.start();
        SCHEMA_REGISTRY.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("validator.schema-registry.url", () -> SchemaRegistryContainers.url(SCHEMA_REGISTRY));
    }

    @BeforeAll
    static void registerSchemasAndTopics() throws Exception {
        KafkaTopics.create(KAFKA.getBootstrapServers(), SharedSchemaRegistry.TOPICS);
        SchemaRegistration.register(
                SchemaRegistryContainers.newClient(SchemaRegistryContainers.url(SCHEMA_REGISTRY)),
                SchemaRegistryContainers.schemasDir());
    }

    @Test
    void holdsEventsWhileRegistryIsDownAndDeliversThemAfterRecovery(CapturedOutput output) {
        String eventId = ValidatorTestEvents.uniqueEventId();
        String raw = ValidatorTestEvents.json(eventId, "search_performed", 1, "{\"query\":\"laptop\"}");
        var docker = SCHEMA_REGISTRY.getDockerClient();

        docker.pauseContainerCmd(SCHEMA_REGISTRY.getContainerId()).exec();
        try {
            KafkaTopics.send(KAFKA.getBootstrapServers(), "behavioural.raw", "user-123", raw);

            assertThat(KafkaTopics.recordsContaining(
                            KAFKA.getBootstrapServers(), Topics.INVALID, eventId, Duration.ofSeconds(15)))
                    .as("an unavailable registry must never produce an invalid event")
                    .isEmpty();
            assertThat(KafkaTopics.recordsContaining(
                            KAFKA.getBootstrapServers(), Topics.DLQ, eventId, Duration.ofSeconds(5)))
                    .as("an infrastructure failure is never dead-lettered")
                    .isEmpty();
        } finally {
            docker.unpauseContainerCmd(SCHEMA_REGISTRY.getContainerId()).exec();
        }

        KafkaTopics.awaitRecord(KAFKA.getBootstrapServers(), Topics.VALID, eventId, Duration.ofSeconds(60));
        assertThat(output.getOut())
                .as("each failed attempt is visible at WARN, naming the record and the cause")
                .containsPattern(
                        "WARN .*Retrying behavioural\\.raw-\\d+@\\d+ key=user-123 \\(attempt \\d+\\): .*SchemaRegistryUnavailableException");
    }

    @Test
    void otherPartitionsKeepFlowingWhileOneRecordWaits(CapturedOutput output) throws Exception {
        String bootstrap = KAFKA.getBootstrapServers();
        String warmId = ValidatorTestEvents.uniqueEventId();
        KafkaTopics.send(
                bootstrap,
                "behavioural.raw",
                "warm-user",
                ValidatorTestEvents.json(warmId, "page_viewed", 1, "{\"pageUrl\":\"https://shop.example/home\"}"));
        KafkaTopics.awaitRecord(bootstrap, Topics.VALID, warmId, Duration.ofSeconds(30));

        String stuckKey = "stuck-user";
        String flowingKey = keyOnOtherPartitionThan(stuckKey);
        String stuckId = ValidatorTestEvents.uniqueEventId();
        String flowingId = ValidatorTestEvents.uniqueEventId();
        var docker = SCHEMA_REGISTRY.getDockerClient();

        docker.pauseContainerCmd(SCHEMA_REGISTRY.getContainerId()).exec();
        try {
            KafkaTopics.send(
                    bootstrap,
                    "behavioural.raw",
                    stuckKey,
                    ValidatorTestEvents.json(stuckId, "checkout_started", 1, "{\"cartId\":\"cart-1\"}"));
            awaitOutput(output, "key=" + stuckKey, Duration.ofSeconds(30));

            long sentAt = System.currentTimeMillis();
            KafkaTopics.send(
                    bootstrap,
                    "behavioural.raw",
                    flowingKey,
                    ValidatorTestEvents.json(
                            flowingId, "page_viewed", 1, "{\"pageUrl\":\"https://shop.example/cart\"}"));

            ConsumerRecord<String, byte[]> flowing =
                    KafkaTopics.awaitRecord(bootstrap, Topics.VALID, flowingId, Duration.ofSeconds(30));
            assertThat(flowing.timestamp() - sentAt)
                    .as("an event on another partition is validated without waiting behind the stuck record")
                    .isLessThan(4_000);
        } finally {
            docker.unpauseContainerCmd(SCHEMA_REGISTRY.getContainerId()).exec();
        }

        KafkaTopics.awaitRecord(bootstrap, Topics.VALID, stuckId, Duration.ofSeconds(60));
    }

    /** Kafka's default partitioner for a String key on the 6-partition raw topic. */
    private static int partition(String key) {
        return Utils.toPositive(Utils.murmur2(key.getBytes(StandardCharsets.UTF_8))) % 6;
    }

    private static String keyOnOtherPartitionThan(String key) {
        for (int i = 0; ; i++) {
            String candidate = "flowing-user-" + i;
            if (partition(candidate) != partition(key)) {
                return candidate;
            }
        }
    }

    private static void awaitOutput(CapturedOutput output, String text, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!output.getOut().contains(text)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("'" + text + "' not logged within " + timeout);
            }
            Thread.sleep(200);
        }
    }
}
