package com.behaviouralplatform.validator;

import io.confluent.kafka.schemaregistry.json.JsonSchema;

sealed interface SchemaLookup {

    record Found(int schemaId, JsonSchema schema) implements SchemaLookup {
    }

    record UnknownEventType() implements SchemaLookup {
    }

    record UnknownSchemaVersion() implements SchemaLookup {
    }
}
