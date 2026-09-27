package com.behaviouralplatform.validator;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class ValidatorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ValidatorConfiguration.class);

    /**
     * Each lookup fails within ~5s when the registry is slow or down. The client's own retries are off (default 3,
     * with backoff, turning one lookup into ~25s); the Kafka error handler below does the retrying instead.
     */
    @Bean
    SchemaRegistryClient schemaRegistryClient(@Value("${validator.schema-registry.url}") String url) {
        return new CachedSchemaRegistryClient(List.of(url), 1000, List.of(new JsonSchemaProvider()), Map.of(
                SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS, 5000,
                SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS, 5000,
                SchemaRegistryClientConfig.MAX_RETRIES_CONFIG, 0));
    }

    /**
     * Phase 2 interim (ADR 0008): infrastructure failures are retried every 5s, forever. The event waits; nothing is lost.
     * Anything else is logged and skipped until Phase 3 adds bounded retries and validation.dlq.
     * Each failed attempt is logged at WARN so an outage is visible, not only as consumer lag.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(new FixedBackOff(5_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.defaultFalse();
        handler.addRetryableExceptions(SchemaRegistryUnavailableException.class, PublishFailedException.class);
        handler.setRetryListeners((record, ex, attempt) -> log.warn("Retrying {}-{}@{} key={} (attempt {}): {}",
                record.topic(), record.partition(), record.offset(), record.key(), attempt,
                String.valueOf(ex.getCause() == null ? ex : ex.getCause())));
        return handler;
    }
}
