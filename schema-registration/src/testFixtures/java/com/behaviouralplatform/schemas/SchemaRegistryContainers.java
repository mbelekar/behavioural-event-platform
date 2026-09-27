package com.behaviouralplatform.schemas;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/** Container definitions shared by every module's tests. */
public final class SchemaRegistryContainers {

    public static final String KAFKA_IMAGE = "apache/kafka:4.2.1";
    public static final String SCHEMA_REGISTRY_IMAGE = "confluentinc/cp-schema-registry:8.3.2";

    private SchemaRegistryContainers() {
    }

    public static KafkaContainer kafka(Network network) {
        return new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("kafka")
                .withListener("kafka:19092");
    }

    public static GenericContainer<?> schemaRegistry(Network network) {
        return new GenericContainer<>(DockerImageName.parse(SCHEMA_REGISTRY_IMAGE))
                .withNetwork(network)
                .withExposedPorts(8081)
                .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
                .waitingFor(Wait.forHttp("/subjects").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(3));
    }

    public static String url(GenericContainer<?> schemaRegistry) {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
    }

    public static Path schemasDir() {
        return Path.of(System.getProperty("schemas.dir"));
    }

    public static SchemaRegistryClient newClient(String url) {
        return SchemaRegistryClients.forUrl(url);
    }
}
