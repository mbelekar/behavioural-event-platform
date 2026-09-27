package com.behaviouralplatform.schemas;

import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import java.util.List;

/** One file under event-contracts/schemas: {@code <subject>/v<version>.json}. */
public record SchemaFile(String subject, int version, String content, List<SchemaReference> references) {}
