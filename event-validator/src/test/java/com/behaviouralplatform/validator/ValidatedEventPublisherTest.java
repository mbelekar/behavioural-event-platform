package com.behaviouralplatform.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.behaviouralplatform.schemas.SharedSchemaRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class ValidatedEventPublisherTest {

    @Test
    void failsWithinBoundWhenBrokerUnreachable() {
        var producerFactory = new DefaultKafkaProducerFactory<String, byte[]>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG,
                1000));
        var publisher = new ValidatedEventPublisher(new KafkaTemplate<>(producerFactory));

        try {
            assertTimeoutPreemptively(
                    Duration.ofSeconds(10),
                    () -> assertThatThrownBy(
                                    () -> publisher.publishValid("user-123", "{}".getBytes(StandardCharsets.UTF_8), 1))
                            .isInstanceOf(PublishFailedException.class)
                            .hasMessageContaining(ValidatedEventPublisher.VALID_TOPIC));
        } finally {
            producerFactory.destroy();
        }
    }

    @Test
    void permanentFailureIsNotRetryable() {
        var producerFactory = new DefaultKafkaProducerFactory<String, byte[]>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                SharedSchemaRegistry.bootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class,
                ProducerConfig.MAX_REQUEST_SIZE_CONFIG,
                1024));
        var publisher = new ValidatedEventPublisher(new KafkaTemplate<>(producerFactory));

        try {
            assertThatThrownBy(() -> publisher.publishValid("user-123", new byte[2048], 1))
                    .isNotInstanceOf(PublishFailedException.class)
                    .satisfies(e -> assertThat(e).hasRootCauseInstanceOf(RecordTooLargeException.class));
        } finally {
            producerFactory.destroy();
        }
    }
}
