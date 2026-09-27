package com.behaviouralplatform.schemas;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.util.List;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Kafka + Schema Registry started once per test JVM, with the pipeline topics created and every schema registered.
 * Tests that stop or pause containers must start their own instead.
 */
public final class SharedSchemaRegistry {

    public static final List<String> TOPICS = List.of("behavioural.raw", "behavioural.valid", "behavioural.invalid");

    private static final Network NETWORK = Network.newNetwork();
    private static final KafkaContainer KAFKA = SchemaRegistryContainers.kafka(NETWORK);
    private static final GenericContainer<?> SCHEMA_REGISTRY = SchemaRegistryContainers.schemaRegistry(NETWORK);
    private static boolean started;
    /** A failed start is not retried: later callers get the original error instead of half-started containers. */
    private static IllegalStateException startFailure;

    private SharedSchemaRegistry() {
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        if (startFailure != null) {
            throw startFailure;
        }
        try {
            KAFKA.start();
            SCHEMA_REGISTRY.start();
            KafkaTopics.create(KAFKA.getBootstrapServers(), TOPICS);
            SchemaRegistration.register(
                    SchemaRegistryContainers.newClient(SchemaRegistryContainers.url(SCHEMA_REGISTRY)),
                    SchemaRegistryContainers.schemasDir());
        } catch (Exception e) {
            startFailure = new IllegalStateException("Shared Kafka + Schema Registry failed to start", e);
            throw startFailure;
        }
        started = true;
    }

    public static String bootstrapServers() {
        start();
        return KAFKA.getBootstrapServers();
    }

    public static String schemaRegistryUrl() {
        start();
        return SchemaRegistryContainers.url(SCHEMA_REGISTRY);
    }

    public static SchemaRegistryClient client() {
        return SchemaRegistryContainers.newClient(schemaRegistryUrl());
    }
}
