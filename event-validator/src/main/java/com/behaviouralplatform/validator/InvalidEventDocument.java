package com.behaviouralplatform.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;

/** The behavioural.invalid record (Design.md section 9). */
final class InvalidEventDocument {

    private InvalidEventDocument() {
    }

    static ObjectNode of(JsonNode event, List<ValidationError> errors, Instant validatedAt) {
        ObjectNode document = Json.MAPPER.createObjectNode();
        document.set("event", event);
        document.set("validationErrors", Json.MAPPER.valueToTree(errors));
        document.put("validatedAt", validatedAt.toString());
        return document;
    }
}
