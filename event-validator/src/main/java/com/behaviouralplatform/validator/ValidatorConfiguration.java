package com.behaviouralplatform.validator;

import com.behaviouralplatform.contracts.Topics;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
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
        return new CachedSchemaRegistryClient(
                List.of(url),
                1000,
                List.of(new JsonSchemaProvider()),
                Map.of(
                        SchemaRegistryClientConfig.HTTP_CONNECT_TIMEOUT_MS, 5000,
                        SchemaRegistryClientConfig.HTTP_READ_TIMEOUT_MS, 5000,
                        SchemaRegistryClientConfig.MAX_RETRIES_CONFIG, 0));
    }

    /**
     * Infrastructure failures are retried every 5s, forever: the event waits and is never dead-lettered (ADR 0008).
     * Anything else is retried twice, 1s apart, then the raw record goes to validation.dlq (ADR 0011). A failed
     * dead-letter publish is retried from the first attempt, so nothing is skipped.
     * Each failed attempt is logged at WARN so an outage is visible, not only as consumer lag.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, byte[]> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetter = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new TopicPartition(Topics.DLQ, record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> {
                    deadLetter.accept(record, ex);
                    log.error(
                            "Dead-lettered {}-{}@{} key={} to {}: {}",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            record.key(),
                            Topics.DLQ,
                            String.valueOf(ex.getCause() == null ? ex : ex.getCause()));
                },
                new FixedBackOff(1_000L, 2));
        handler.setBackOffFunction(
                (record, ex) -> ex instanceof SchemaRegistryUnavailableException || ex instanceof PublishFailedException
                        ? new FixedBackOff(5_000L, FixedBackOff.UNLIMITED_ATTEMPTS)
                        : null);
        handler.setRetryListeners((record, ex, attempt) -> log.warn(
                "Retrying {}-{}@{} key={} (attempt {}): {}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key(),
                attempt,
                String.valueOf(ex.getCause() == null ? ex : ex.getCause())));
        return handler;
    }
}
