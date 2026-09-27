package com.behaviouralplatform.schemas;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers event-contracts/schemas in Schema Registry, in order. Registering an existing schema is a no-op,
 * so this is safe to re-run. Fails if the registry assigns a version other than the file's.
 */
public final class SchemaRegistration {

    static final String COMPATIBILITY = "BACKWARD";

    private SchemaRegistration() {
    }

    public record Registered(String subject, int version, int id) {
    }

    public static void main(String[] args) throws Exception {
        String url = args[0];
        Path dir = Path.of(args[1]);
        for (Registered registered : register(SchemaRegistryClients.forUrl(url), dir)) {
            System.out.printf("%s v%d -> id %d%n", registered.subject(), registered.version(), registered.id());
        }
    }

    public static List<Registered> register(SchemaRegistryClient client, Path dir) throws IOException, RestClientException {
        List<SchemaFile> files = SchemaFiles.load(dir);
        List<Registered> registered = new ArrayList<>();
        for (SchemaFile file : files) {
            JsonSchema schema = SchemaFiles.toJsonSchema(file, files);
            client.updateCompatibility(file.subject(), COMPATIBILITY);
            int id = client.register(file.subject(), schema);
            int version = client.getVersion(file.subject(), schema);
            if (version != file.version()) {
                throw new IllegalStateException("%s/v%d.json was registered as version %d; schema files must be registered in order, without gaps"
                        .formatted(file.subject(), file.version(), version));
            }
            registered.add(new Registered(file.subject(), version, id));
        }
        return registered;
    }
}
