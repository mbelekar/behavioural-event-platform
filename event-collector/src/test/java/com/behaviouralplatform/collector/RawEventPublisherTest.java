package com.behaviouralplatform.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class RawEventPublisherTest {

    @Test
    void keysByUserId() {
        assertThat(RawEventPublisher.partitionKey(TestEvents.valid())).isEqualTo("user-123");
    }

    @Test
    void fallsBackToSessionIdWhenUserIdAbsent() {
        ObjectNode node = TestEvents.validNode();
        node.remove("userId");

        assertThat(RawEventPublisher.partitionKey(TestEvents.toEvent(node))).isEqualTo("session-456");
    }

    @Test
    void fallsBackToSessionIdWhenUserIdBlank() {
        ObjectNode node = TestEvents.validNode();
        node.put("userId", "");

        assertThat(RawEventPublisher.partitionKey(TestEvents.toEvent(node))).isEqualTo("session-456");
    }

    @Test
    void failsWithinBoundWhenBrokerUnreachable() {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000);
        var producerFactory = new DefaultKafkaProducerFactory<String, String>(config);
        var publisher = new RawEventPublisher(new KafkaTemplate<>(producerFactory), JsonMapper.builder().build());

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                    assertThatThrownBy(() -> publisher.publish(TestEvents.valid()))
                            .isInstanceOf(PublishFailedException.class)
                            .hasMessageContaining("01K5R4F8W8J5Z8XJH0N6F4P2C1"));
        } finally {
            producerFactory.destroy();
        }
    }
}
