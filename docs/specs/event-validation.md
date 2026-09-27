# Spec: Event validation

## Purpose

Turn the untrusted events on `behavioural.raw` into a trusted stream. Every event is validated against its registered contract. Events that conform are published to `behavioural.valid`, which consumers can rely on without re-validating. Events that don't conform are published to `behavioural.invalid`, with structured errors explaining why.

## Requirements

**Consuming**

- **R1.** The validator consumes every record on `behavioural.raw` as consumer group `event-validator`. When it starts for the first time, it begins from the earliest available record.

**Choosing the contract**

- **R2.** The contract is selected by the event's `eventType` and `schemaVersion` (see [event contracts](event-contracts.md)). The whole event is validated, envelope and payload.

**Outcomes**

- **R3.** **Valid events** are published to `behavioural.valid`:
  - with the same Kafka key as the raw record;
  - with the producer's original JSON, byte for byte;
  - preceded by the 5-byte Schema Registry prefix: a magic byte `0`, then the 4-byte big-endian ID of the schema the event conformed to. Standard Schema Registry deserializers can read these records.
- **R4.** **Invalid events** are published to `behavioural.invalid` with the same Kafka key, as a JSON document:

  ```json
  {
    "event": { "...the original event..." },
    "validationErrors": [ { "code": "...", "field": "...", "message": "..." } ],
    "validatedAt": "<ISO-8601 instant>"
  }
  ```

- **R5.** Every violation in an event is reported, not only the first.
- **R6.** Each error has a `code`, a dotted `field` path relative to the event root (e.g. `payload.productId`), and a non-empty human-readable `message`.

**Error codes**

- **R7.** The error codes are:

  | Code | Meaning | `field` |
  |---|---|---|
  | `REQUIRED_FIELD_MISSING` | A required field is absent, or `eventType`/`schemaVersion` is missing or null | Path of the missing field |
  | `INVALID_TYPE` | A value has the wrong JSON type, including `null` for a non-nullable field, or a non-string `eventType` or non-integer `schemaVersion` | Path of the value |
  | `INVALID_FORMAT` | A string doesn't match its required format (e.g. a date-time) | Path of the value |
  | `UNKNOWN_FIELD` | A field that the contract doesn't allow | Path of the unexpected field |
  | `UNKNOWN_EVENT_TYPE` | No contract is registered for `eventType`; `eventType` is `behavioural_envelope`; or `eventType` isn't a valid event type name (lower-case letter first, then lower-case letters, digits or `_`, at most 100 characters) | `eventType` |
  | `UNKNOWN_SCHEMA_VERSION` | The event type exists, but not that `schemaVersion`; or `schemaVersion` is less than 1 | `schemaVersion` |
  | `SCHEMA_VIOLATION` | Any other contract rule (e.g. a pattern, a minimum, or `eventType` not matching the contract) | Path of the value |

**Delivery**

- **R8.** An event counts as processed only once its valid or invalid record has been acknowledged by Kafka. Delivery is at-least-once.
- **R9.** Each invalid event is logged at INFO with its `eventId` and errors.

## Acceptance criteria

- **AC1.** Given a conforming `product_viewed` v2 event keyed `user-123` on `behavioural.raw`, then within seconds a record keyed `user-123` appears on `behavioural.valid`:
  - its bytes 1–4 are the registered ID of `product_viewed` v2;
  - deserializing it with Confluent's JSON Schema deserializer returns exactly the original event.
- **AC2.** Given a `product_viewed` v2 event without `productId`, then a record keyed like the input appears on `behavioural.invalid`:
  - `event` equals the original event;
  - the first error is `REQUIRED_FIELD_MISSING` at `payload.productId`;
  - `validatedAt` is present.
- **AC3.** Given a `product_viewed` v1 event after v2 has been registered, then it is published to `behavioural.valid`.
- **AC4.** Given a `purchase_completed` event with only `orderId`, then it is invalid, with exactly two errors: `REQUIRED_FIELD_MISSING` at `payload.amount` and at `payload.currency`.
- **AC5.** Given invalid input, the (code, field) outcome is:

  | Input | Code | Field |
  |---|---|---|
  | `productId` is `42` | `INVALID_TYPE` | `payload.productId` |
  | `"recommendationSource": null` | `INVALID_TYPE` | `payload.recommendationSource` |
  | Top-level `debug` field | `UNKNOWN_FIELD` | `debug` |
  | v1 event with `recommendationSource` | `UNKNOWN_FIELD` | `payload.recommendationSource` |
  | `eventType` `wishlist_added` | `UNKNOWN_EVENT_TYPE` | `eventType` |
  | `eventType` `behavioural_envelope` | `UNKNOWN_EVENT_TYPE` | `eventType` |
  | `schemaVersion` `9` | `UNKNOWN_SCHEMA_VERSION` | `schemaVersion` |
  | `schemaVersion` `0` or `-1` | `UNKNOWN_SCHEMA_VERSION` | `schemaVersion` |
  | `eventType` `:.:behavioural_envelope` | `UNKNOWN_EVENT_TYPE` | `eventType` |
  | `eventType` longer than 100 characters, or containing a control character or upper-case letter | `UNKNOWN_EVENT_TYPE` | `eventType` |
  | `eventType` missing | `REQUIRED_FIELD_MISSING` | `eventType` |
  | `schemaVersion` `"2"` (a string) | `INVALID_TYPE` | `schemaVersion` |

- **AC6.** Given any invalid event, then it produces no record on `behavioural.valid`. Given any valid event, then it produces no record on `behavioural.invalid`.

## Failure and edge-case behaviour

| Situation | Behaviour |
|---|---|
| Schema Registry or Kafka unavailable | Never produces an invalid event. See [validation resilience](validation-resilience.md). |
| A version is registered after events using it were marked invalid | Those events stay on `behavioural.invalid` and aren't re-validated. Later events with that version validate normally, since "unknown" results aren't remembered. |
| The same raw event is delivered twice (at-least-once) | It is validated and published twice, with the same `eventId`. Downstream consumers deduplicate on `eventId`. |
| The validator restarts | It resumes from the last committed offset. Events whose outcome was published but not yet committed are processed again (duplicates, as above). |

## Not yet implemented

- Business validation rules (Design.md section 7.2), such as rejecting an implausible future `occurredAt`. Only contract (schema) validation happens today.
- Metrics such as `events_validated_total`, `events_invalid_total` and `validation_latency` (Design.md section 18).

## Discrepancies

- Design.md section 9's example invalid document shows only `code` and `field` per error. The implementation always includes `message` too (R6).
- Design.md section 8's `ValidationResult` has only `valid` and `errors`. This doesn't affect external behaviour: the record formats in R3 and R4 are what consumers see.
