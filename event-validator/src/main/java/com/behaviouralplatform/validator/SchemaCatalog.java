package com.behaviouralplatform.validator;

import com.behaviouralplatform.schemas.SchemaFiles;
import io.confluent.kafka.schemaregistry.client.SchemaMetadata;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Maps eventType + schemaVersion to a registered schema (ADR 0005). */
@Component
class SchemaCatalog {

    private static final int SUBJECT_NOT_FOUND = 40401;
    private static final int VERSION_NOT_FOUND = 40402;

    private final SchemaRegistryClient client;
    /** Registered versions never change, so found schemas are cached. Unknown results are not: they may be registered later. */
    private final Map<String, SchemaLookup.Found> found = new ConcurrentHashMap<>();

    SchemaCatalog(SchemaRegistryClient client) {
        this.client = client;
    }

    SchemaLookup find(String eventType, int version) {
        if (eventType.equals(SchemaFiles.ENVELOPE_SUBJECT)) {
            return new SchemaLookup.UnknownEventType();
        }
        String key = eventType + "/v" + version;
        SchemaLookup.Found cached = found.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            SchemaMetadata metadata = client.getSchemaMetadata(eventType, version);
            JsonSchema schema = (JsonSchema) client.getSchemaById(metadata.getId());
            SchemaLookup.Found result = new SchemaLookup.Found(metadata.getId(), schema);
            found.put(key, result);
            return result;
        } catch (RestClientException e) {
            if (e.getErrorCode() == SUBJECT_NOT_FOUND) {
                return new SchemaLookup.UnknownEventType();
            }
            if (e.getErrorCode() == VERSION_NOT_FOUND) {
                return new SchemaLookup.UnknownSchemaVersion();
            }
            throw new SchemaRegistryUnavailableException(key, e);
        } catch (IOException e) {
            throw new SchemaRegistryUnavailableException(key, e);
        }
    }
}
