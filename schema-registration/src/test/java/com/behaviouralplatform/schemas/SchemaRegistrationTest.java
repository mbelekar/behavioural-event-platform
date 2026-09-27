package com.behaviouralplatform.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaRegistrationTest {

    @Test
    void registersEverySchemaInOrderAndIsIdempotent() throws Exception {
        SchemaRegistryClient client = SharedSchemaRegistry.client(); // start() already registered once

        List<SchemaRegistration.Registered> again =
                SchemaRegistration.register(client, SchemaRegistryContainers.schemasDir());

        assertThat(again).extracting(SchemaRegistration.Registered::subject, SchemaRegistration.Registered::version)
                .containsExactly(
                        tuple("behavioural_envelope", 1), tuple("button_clicked", 1), tuple("checkout_started", 1),
                        tuple("page_viewed", 1), tuple("product_viewed", 1), tuple("product_viewed", 2),
                        tuple("purchase_completed", 1), tuple("search_performed", 1));
        assertThat(client.getAllVersions("product_viewed")).containsExactly(1, 2);
        assertThat(client.getCompatibility("product_viewed")).isEqualTo("BACKWARD");
    }

    @Test
    void rejectsIncompatibleChanges() throws Exception {
        SchemaRegistryClient client = SharedSchemaRegistry.client();
        List<SchemaFile> all = SchemaFiles.load(SchemaRegistryContainers.schemasDir());
        SchemaFile v2 = all.stream().filter(f -> f.subject().equals("product_viewed") && f.version() == 2).findFirst().orElseThrow();
        String changedType = v2.content().replace(
                "\"recommendationSource\": { \"type\": \"string\" }", "\"recommendationSource\": { \"type\": \"integer\" }");
        String newRequired = v2.content().replace("\"required\": [\"productId\"]", "\"required\": [\"productId\", \"category\"]");
        assertThat(changedType).isNotEqualTo(v2.content());
        assertThat(newRequired).isNotEqualTo(v2.content());

        JsonSchema typeChange = SchemaFiles.toJsonSchema(new SchemaFile("product_viewed", 3, changedType, v2.references()), all);
        JsonSchema requiredAdded = SchemaFiles.toJsonSchema(new SchemaFile("product_viewed", 3, newRequired, v2.references()), all);

        assertThat(client.testCompatibility("product_viewed", typeChange)).isFalse();
        assertThat(client.testCompatibility("product_viewed", requiredAdded)).isFalse();
        assertThatThrownBy(() -> client.register("product_viewed", typeChange))
                .isInstanceOfSatisfying(RestClientException.class, e -> assertThat(e.getStatus()).isEqualTo(409));
    }

    @Test
    void failsWhenRegistryVersionDoesNotMatchFileName(@TempDir Path dir) throws Exception {
        SchemaRegistryClient client = SharedSchemaRegistry.client();
        // Closed content model, so v1.json is a compatible change that the registry accepts as version 2.
        String subject = "gap_test_" + UUID.randomUUID().toString().replace("-", "");
        client.register(subject, new JsonSchema("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}},\"additionalProperties\":false}"));
        Files.createDirectories(dir.resolve(subject));
        Files.writeString(dir.resolve(subject).resolve("v1.json"),
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"},\"b\":{\"type\":\"string\"}},\"additionalProperties\":false}");

        assertThatThrownBy(() -> SchemaRegistration.register(client, dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(subject + "/v1.json was registered as version 2");
    }
}
