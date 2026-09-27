package com.behaviouralplatform.validator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvalidEventDocumentTest {

    @Test
    void wrapsOriginalEventWithErrorsAndTimestamp() {
        ObjectNode event = ValidatorTestEvents.productViewedV2();
        List<ValidationError> errors = List.of(new ValidationError(
                "REQUIRED_FIELD_MISSING", "payload.productId", "required key [productId] not found"));

        JsonNode document = InvalidEventDocument.of(event, errors, Instant.parse("2026-09-27T01:24:02Z"));

        assertThat(document.get("event")).isEqualTo(event);
        assertThat(document.get("validationErrors").get(0).get("code").asText()).isEqualTo("REQUIRED_FIELD_MISSING");
        assertThat(document.get("validationErrors").get(0).get("field").asText())
                .isEqualTo("payload.productId");
        assertThat(document.get("validationErrors").get(0).get("message").asText())
                .isNotBlank();
        assertThat(document.get("validatedAt").asText()).isEqualTo("2026-09-27T01:24:02Z");
    }
}
