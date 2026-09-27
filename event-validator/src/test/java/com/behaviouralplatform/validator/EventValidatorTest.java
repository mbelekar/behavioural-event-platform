package com.behaviouralplatform.validator;

import static com.behaviouralplatform.validator.ValidatorTestEvents.json;
import static com.behaviouralplatform.validator.ValidatorTestEvents.node;
import static com.behaviouralplatform.validator.ValidatorTestEvents.productViewedV2;
import static com.behaviouralplatform.validator.ValidatorTestEvents.uniqueEventId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.behaviouralplatform.schemas.SharedSchemaRegistry;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class EventValidatorTest {

    private final EventValidator validator = new EventValidator(new SchemaCatalog(SharedSchemaRegistry.client()));

    @Test
    void validEventPassesWithItsSchemaId() throws Exception {
        int expectedId = SharedSchemaRegistry.client().getSchemaMetadata("product_viewed", 2).getId();

        ValidationResult result = validator.validate(productViewedV2());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.schemaId()).isEqualTo(expectedId);
    }

    @Test
    void olderSchemaVersionStillValidates() {
        ObjectNode v1 = node(json(uniqueEventId(), "product_viewed", 1, "{\"productId\":\"SKU-981\",\"category\":\"laptops\"}"));

        assertThat(validator.validate(v1).valid()).isTrue();
    }

    @Test
    void v1EventWithV2FieldIsUnknownField() {
        ObjectNode v1 = node(json(uniqueEventId(), "product_viewed", 1, "{\"productId\":\"SKU-981\",\"recommendationSource\":\"home\"}"));

        assertErrors(validator.validate(v1), tuple("UNKNOWN_FIELD", "payload.recommendationSource"));
    }

    @Test
    void missingRequiredPayloadField() {
        ObjectNode event = productViewedV2();
        ((ObjectNode) event.get("payload")).remove("productId");

        assertErrors(validator.validate(event), tuple("REQUIRED_FIELD_MISSING", "payload.productId"));
    }

    @Test
    void wrongPayloadFieldType() {
        ObjectNode event = productViewedV2();
        ((ObjectNode) event.get("payload")).put("productId", 42);

        assertErrors(validator.validate(event), tuple("INVALID_TYPE", "payload.productId"));
    }

    @Test
    void nullOptionalFieldIsInvalidType() {
        ObjectNode event = productViewedV2();
        ((ObjectNode) event.get("payload")).putNull("recommendationSource");

        assertErrors(validator.validate(event), tuple("INVALID_TYPE", "payload.recommendationSource"));
    }

    @Test
    void unknownTopLevelField() {
        ObjectNode event = productViewedV2();
        event.put("debug", true);

        assertErrors(validator.validate(event), tuple("UNKNOWN_FIELD", "debug"));
    }

    @Test
    void reportsEveryViolation() {
        ObjectNode purchase = node(json(uniqueEventId(), "purchase_completed", 1, "{\"orderId\":\"order-1\"}"));

        assertErrors(validator.validate(purchase),
                tuple("REQUIRED_FIELD_MISSING", "payload.amount"),
                tuple("REQUIRED_FIELD_MISSING", "payload.currency"));
    }

    @Test
    void unknownEventType() {
        ObjectNode event = node(json(uniqueEventId(), "wishlist_added", 1, "{}"));

        assertErrors(validator.validate(event), tuple("UNKNOWN_EVENT_TYPE", "eventType"));
    }

    @Test
    void unknownSchemaVersion() {
        ObjectNode event = productViewedV2();
        event.put("schemaVersion", 9);

        assertErrors(validator.validate(event), tuple("UNKNOWN_SCHEMA_VERSION", "schemaVersion"));
    }

    @Test
    void missingEventType() {
        ObjectNode event = productViewedV2();
        event.remove("eventType");

        assertErrors(validator.validate(event), tuple("REQUIRED_FIELD_MISSING", "eventType"));
    }

    @Test
    void nonIntegerSchemaVersion() {
        ObjectNode event = productViewedV2();
        event.put("schemaVersion", "2");

        assertErrors(validator.validate(event), tuple("INVALID_TYPE", "schemaVersion"));
    }

    private static void assertErrors(ValidationResult result, Tuple... expected) {
        assertThat(result.valid()).isFalse();
        assertThat(result.schemaId()).isNull();
        assertThat(result.errors()).extracting(ValidationError::code, ValidationError::field).containsExactlyInAnyOrder(expected);
        assertThat(result.errors()).allSatisfy(e -> assertThat(e.message()).isNotBlank());
    }
}
