package com.behaviouralplatform.schemas;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers event-contracts/schemas in Schema Registry, in order. Re-running is a no-op for schemas already registered.
 * Before registering a file, checks that it will get its file's version; if not, fails without changing the registry.
 */
public final class SchemaRegistration {

    static final String COMPATIBILITY = "BACKWARD";

    private static final int SUBJECT_NOT_FOUND = 40401;
    private static final int SCHEMA_NOT_FOUND = 40403;

    private SchemaRegistration() {}

    public record Registered(String subject, int version, int id) {}

    public static void main(String[] args) throws Exception {
        String url = args[0];
        Path dir = Path.of(args[1]);
        for (Registered registered : register(SchemaRegistryClients.forUrl(url), dir)) {
            System.out.printf("%s v%d -> id %d%n", registered.subject(), registered.version(), registered.id());
        }
    }

    public static List<Registered> register(SchemaRegistryClient client, Path dir)
            throws IOException, RestClientException {
        List<SchemaFile> files = SchemaFiles.load(dir);
        List<Registered> registered = new ArrayList<>();
        for (SchemaFile file : files) {
            JsonSchema schema = SchemaFiles.toJsonSchema(file, files);
            client.updateCompatibility(file.subject(), COMPATIBILITY);
            Integer existing = registeredVersion(client, file.subject(), schema);
            int id;
            if (existing != null) {
                if (existing != file.version()) {
                    throw new IllegalStateException("%s/v%d.json is already registered as version %d"
                            .formatted(file.subject(), file.version(), existing));
                }
                id = client.getSchemaMetadata(file.subject(), existing).getId();
            } else {
                int latest = latestVersion(client, file.subject());
                if (latest != file.version() - 1) {
                    throw new IllegalStateException(
                            "%s/v%d.json cannot be registered: the registry's latest version of %s is %d, expected %d"
                                    .formatted(
                                            file.subject(),
                                            file.version(),
                                            file.subject(),
                                            latest,
                                            file.version() - 1));
                }
                id = client.register(file.subject(), schema);
            }
            registered.add(new Registered(file.subject(), file.version(), id));
        }
        return registered;
    }

    /** The version this exact schema is registered as, or null if it isn't registered under the subject. */
    private static Integer registeredVersion(SchemaRegistryClient client, String subject, JsonSchema schema)
            throws IOException, RestClientException {
        try {
            return client.getVersion(subject, schema);
        } catch (RestClientException e) {
            if (e.getErrorCode() == SUBJECT_NOT_FOUND || e.getErrorCode() == SCHEMA_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /** The subject's latest version, or 0 if the subject doesn't exist yet. */
    private static int latestVersion(SchemaRegistryClient client, String subject)
            throws IOException, RestClientException {
        try {
            return client.getLatestSchemaMetadata(subject).getVersion();
        } catch (RestClientException e) {
            if (e.getErrorCode() == SUBJECT_NOT_FOUND) {
                return 0;
            }
            throw e;
        }
    }
}
