package com.behaviouralplatform.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.behaviouralplatform.schemas.KafkaTopics;
import com.behaviouralplatform.schemas.SchemaRegistration;
import com.behaviouralplatform.schemas.SchemaRegistryContainers;
import com.behaviouralplatform.schemas.SharedSchemaRegistry;
import java.time.Duration;
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
                    KAFKA.getBootstrapServers(), ValidatedEventPublisher.INVALID_TOPIC, eventId, Duration.ofSeconds(15)))
                    .as("an unavailable registry must never produce an invalid event").isEmpty();
        } finally {
            docker.unpauseContainerCmd(SCHEMA_REGISTRY.getContainerId()).exec();
        }

        KafkaTopics.awaitRecord(KAFKA.getBootstrapServers(), ValidatedEventPublisher.VALID_TOPIC, eventId, Duration.ofSeconds(60));
        assertThat(output.getOut())
                .as("each failed attempt is visible at WARN, naming the record and the cause")
                .containsPattern("WARN .*Retrying behavioural\\.raw-\\d+@\\d+ key=user-123 \\(attempt \\d+\\): .*SchemaRegistryUnavailableException");
    }
}
