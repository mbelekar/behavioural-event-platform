package com.behaviouralplatform.schemas;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Small Kafka helpers for tests. Records are matched by a substring (usually a unique eventId). */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static void create(String bootstrapServers, List<String> topics) {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            admin.createTopics(topics.stream()
                            .map(t -> new NewTopic(t, 6, (short) 1))
                            .toList())
                    .all()
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create topics " + topics, e);
        }
    }

    public static void send(String bootstrapServers, String topic, String key, String value) {
        send(bootstrapServers, topic, key, value.getBytes(StandardCharsets.UTF_8));
    }

    public static void send(String bootstrapServers, String topic, String key, byte[] value) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (var producer = new KafkaProducer<String, byte[]>(config)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Could not send to " + topic, e);
        }
    }

    public static ConsumerRecord<String, byte[]> awaitRecord(
            String bootstrapServers, String topic, String containing, Duration timeout) {
        List<ConsumerRecord<String, byte[]>> found = poll(bootstrapServers, topic, containing, timeout, true);
        if (found.isEmpty()) {
            throw new AssertionError("No record containing '" + containing + "' on " + topic + " within " + timeout);
        }
        return found.getFirst();
    }

    public static List<ConsumerRecord<String, byte[]>> recordsContaining(
            String bootstrapServers, String topic, String containing, Duration pollFor) {
        return poll(bootstrapServers, topic, containing, pollFor, false);
    }

    private static List<ConsumerRecord<String, byte[]>> poll(
            String bootstrapServers, String topic, String containing, Duration duration, boolean stopAtFirst) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG,
                "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class);
        List<ConsumerRecord<String, byte[]>> found = new ArrayList<>();
        try (var consumer = new KafkaConsumer<String, byte[]>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + duration.toNanos();
            while (System.nanoTime() < deadline && !(stopAtFirst && !found.isEmpty())) {
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    if (new String(record.value(), StandardCharsets.UTF_8).contains(containing)) {
                        found.add(record);
                    }
                }
            }
        }
        return found;
    }
}
