package com.behaviouralplatform.schemas;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import java.util.List;
import java.util.Map;

public final class SchemaRegistryClients {

    private SchemaRegistryClients() {}

    public static SchemaRegistryClient forUrl(String url) {
        return new CachedSchemaRegistryClient(List.of(url), 100, List.of(new JsonSchemaProvider()), Map.of());
    }
}
