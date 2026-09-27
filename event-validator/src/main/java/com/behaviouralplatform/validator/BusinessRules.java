package com.behaviouralplatform.validator;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Set;

/**
 * Rules the schema cannot express (ADR 0012). Only called for events that conform to their contract, so every field
 * the rules read is present and well-formed.
 */
final class BusinessRules {

    private static final Duration CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Duration MAX_AGE = Duration.ofDays(7);

    /** ISO 4217 codes that are not currencies: precious metals, bond and accounting units, testing, no currency. */
    private static final Set<String> NOT_CURRENCIES =
            Set.of("XAU", "XAG", "XPD", "XPT", "XBA", "XBB", "XBC", "XBD", "XDR", "XSU", "XUA", "XTS", "XXX");

    private BusinessRules() {}

    static List<ValidationError> check(JsonNode event) {
        List<ValidationError> errors = new ArrayList<>();
        Instant occurredAt = instant(event, "occurredAt");
        Instant receivedAt = instant(event, "receivedAt");
        if (occurredAt.isAfter(receivedAt.plus(CLOCK_SKEW))) {
            errors.add(new ValidationError(
                    "OCCURRED_IN_FUTURE", "occurredAt", "occurredAt is more than 5 minutes after receivedAt"));
        }
        if (occurredAt.isBefore(receivedAt.minus(MAX_AGE))) {
            errors.add(new ValidationError(
                    "EVENT_TOO_OLD", "occurredAt", "occurredAt is more than 7 days before receivedAt"));
        }
        if (event.get("eventType").asText().equals("purchase_completed")
                && !isCurrency(event.get("payload").get("currency").asText())) {
            errors.add(new ValidationError(
                    "UNKNOWN_CURRENCY", "payload.currency", "currency is not an ISO 4217 currency code"));
        }
        return errors;
    }

    private static boolean isCurrency(String code) {
        if (NOT_CURRENCIES.contains(code)) {
            return false;
        }
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Instant instant(JsonNode event, String field) {
        return OffsetDateTime.parse(event.get(field).asText()).toInstant();
    }
}
