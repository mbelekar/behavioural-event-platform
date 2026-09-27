package com.behaviouralplatform.schemas;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import java.nio.file.Path;
import java.util.List;
import org.everit.json.schema.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SchemaExamplesTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    static String event(String type, int version, String payload) {
        return """
                {"eventId":"01K5R4F8W8J5Z8XJH0N6F4P2C1","eventType":"%s","schemaVersion":%d,
                 "occurredAt":"2026-09-27T01:23:31Z","source":"web","userId":null,"sessionId":"session-456",
                 "correlationId":"req-789","receivedAt":"2026-09-27T02:05:42.441768Z","payload":%s}
                """.formatted(type, version, payload);
    }

    static JsonSchema schema(String subject, int version) throws Exception {
        List<SchemaFile> all = SchemaFiles.load(Path.of(System.getProperty("schemas.dir")));
        SchemaFile file = all.stream()
                .filter(f -> f.subject().equals(subject) && f.version() == version)
                .findFirst()
                .orElseThrow();
        return SchemaFiles.toJsonSchema(file, all);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "page_viewed        | 1 | {\"pageUrl\":\"https://shop.example/home\"}",
                "product_viewed     | 1 | {\"productId\":\"SKU-981\",\"category\":\"laptops\"}",
                "product_viewed     | 2 | {\"productId\":\"SKU-981\",\"recommendationSource\":\"home\"}",
                "search_performed   | 1 | {\"query\":\"laptop\",\"resultCount\":42}",
                "button_clicked     | 1 | {\"buttonId\":\"add-to-cart\"}",
                "checkout_started   | 1 | {\"cartId\":\"cart-1\",\"itemCount\":2}",
                "purchase_completed | 1 | {\"orderId\":\"order-1\",\"amount\":1299.95,\"currency\":\"AUD\"}"
            })
    void acceptsCollectorShapedExample(String type, int version, String payload) throws Exception {
        JsonSchema schema = schema(type, version);

        assertThatCode(() -> schema.validate(MAPPER.readTree(event(type, version, payload))))
                .doesNotThrowAnyException();
    }

    @Test
    void v1RejectsFieldIntroducedInV2() throws Exception {
        JsonSchema v1 = schema("product_viewed", 1);
        String event = event("product_viewed", 1, "{\"productId\":\"SKU-981\",\"recommendationSource\":\"home\"}");

        assertThatThrownBy(() -> v1.validate(MAPPER.readTree(event))).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsMissingRequiredPayloadField() throws Exception {
        JsonSchema v1 = schema("product_viewed", 1);
        String event = event("product_viewed", 1, "{\"category\":\"laptops\"}");

        assertThatThrownBy(() -> v1.validate(MAPPER.readTree(event))).isInstanceOf(ValidationException.class);
    }

    @Test
    void envelopeRejectsUnknownTopLevelField() throws Exception {
        JsonSchema v1 = schema("product_viewed", 1);
        String event = event("product_viewed", 1, "{\"productId\":\"SKU-981\"}")
                .replace("\"source\"", "\"debug\":true,\"source\"");

        assertThatThrownBy(() -> v1.validate(MAPPER.readTree(event))).isInstanceOf(ValidationException.class);
    }
}
