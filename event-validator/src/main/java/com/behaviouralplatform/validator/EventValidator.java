package com.behaviouralplatform.validator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.regex.Pattern;
import org.everit.json.schema.ValidationException;
import org.springframework.stereotype.Component;

/** Validates a raw event against the schema selected by its eventType and schemaVersion. */
@Component
class EventValidator {

    /** What a Schema Registry subject for an event type can look like; anything else can never name a contract. */
    private static final Pattern EVENT_TYPE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,99}");

    private final SchemaCatalog catalog;

    EventValidator(SchemaCatalog catalog) {
        this.catalog = catalog;
    }

    ValidationResult validate(JsonNode event) {
        JsonNode eventType = event.get("eventType");
        JsonNode schemaVersion = event.get("schemaVersion");
        if (eventType == null || eventType.isNull()) {
            return failed("REQUIRED_FIELD_MISSING", "eventType", "eventType is required to select a schema");
        }
        if (!eventType.isTextual()) {
            return failed("INVALID_TYPE", "eventType", "eventType must be a string");
        }
        if (schemaVersion == null || schemaVersion.isNull()) {
            return failed("REQUIRED_FIELD_MISSING", "schemaVersion", "schemaVersion is required to select a schema");
        }
        if (!schemaVersion.isInt()) {
            return failed("INVALID_TYPE", "schemaVersion", "schemaVersion must be an integer");
        }
        String type = eventType.asText();
        int version = schemaVersion.intValue();
        if (!EVENT_TYPE_NAME.matcher(type).matches()) {
            return failed("UNKNOWN_EVENT_TYPE", "eventType", "eventType is not a valid event type name");
        }
        if (version < 1) {
            return failed("UNKNOWN_SCHEMA_VERSION", "schemaVersion", "schemaVersion must be 1 or greater");
        }
        return switch (catalog.find(type, version)) {
            case SchemaLookup.UnknownEventType unknown ->
                failed("UNKNOWN_EVENT_TYPE", "eventType", "No schema is registered for event type " + type);
            case SchemaLookup.UnknownSchemaVersion unknown ->
                failed(
                        "UNKNOWN_SCHEMA_VERSION",
                        "schemaVersion",
                        "No version " + version + " is registered for " + type);
            case SchemaLookup.Found found -> validateAgainst(found, event);
        };
    }

    private static ValidationResult validateAgainst(SchemaLookup.Found found, JsonNode event) {
        try {
            found.schema().validate(event);
            List<ValidationError> ruleErrors = BusinessRules.check(event);
            return ruleErrors.isEmpty()
                    ? ValidationResult.passed(found.schemaId())
                    : ValidationResult.failed(ruleErrors);
        } catch (ValidationException e) {
            return ValidationResult.failed(SchemaViolations.toErrors(e));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JsonNode could not be processed", e);
        }
    }

    private static ValidationResult failed(String code, String field, String message) {
        return ValidationResult.failed(List.of(new ValidationError(code, field, message)));
    }
}
