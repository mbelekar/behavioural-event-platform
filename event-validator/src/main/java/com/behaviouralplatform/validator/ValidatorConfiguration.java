package com.behaviouralplatform.validator;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ValidatorConfiguration {

    /** Short timeouts so a slow or paused registry fails fast and is retried, rather than blocking for 60s. */
    @Bean
    SchemaRegistryClient schemaRegistryClient(@Value("${validator.schema-registry.url}") String url) {
        return new CachedSchemaRegistryClient(List.of(url), 1000, List.of(new JsonSchemaProvider()), Map.of(
                SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS, 5000,
                SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS, 5000));
    }
}
