package com.behaviouralplatform.collector;

import static org.assertj.core.api.Assertions.assertThat;

import com.behaviouralplatform.contracts.BehaviouralEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class PlatformMetadataTest {

    private static final Instant NOW = Instant.parse("2026-09-27T01:24:00Z");

    @Test
    void keepsCorrelationIdFromBodyOverHeader() {
        ObjectNode node = TestEvents.validNode();
        node.put("correlationId", "from-body");

        BehaviouralEvent enriched = PlatformMetadata.apply(TestEvents.toEvent(node), "from-header", NOW);

        assertThat(enriched.correlationId()).isEqualTo("from-body");
    }

    @Test
    void usesHeaderCorrelationIdWhenBodyHasNone() {
        BehaviouralEvent enriched = PlatformMetadata.apply(TestEvents.valid(), "from-header", NOW);

        assertThat(enriched.correlationId()).isEqualTo("from-header");
    }

    @Test
    void generatesCorrelationIdWhenBodyAndHeaderHaveNone() {
        BehaviouralEvent enriched = PlatformMetadata.apply(TestEvents.valid(), null, NOW);

        assertThat(UUID.fromString(enriched.correlationId())).isNotNull();
    }

    @Test
    void alwaysSetsReceivedAtEvenIfClientSentOne() {
        ObjectNode node = TestEvents.validNode();
        node.put("receivedAt", "2000-01-01T00:00:00Z");

        BehaviouralEvent enriched = PlatformMetadata.apply(TestEvents.toEvent(node), null, NOW);

        assertThat(enriched.receivedAt()).isEqualTo(NOW);
    }

    @Test
    void leavesClientFieldsUnchanged() {
        BehaviouralEvent original = TestEvents.valid();

        BehaviouralEvent enriched = PlatformMetadata.apply(original, "c", NOW);

        assertThat(enriched)
                .usingRecursiveComparison()
                .ignoringFields("correlationId", "receivedAt")
                .isEqualTo(original);
    }
}
