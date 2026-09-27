# Spec: Event validation

## Purpose

Turn untrusted events on `behavioural.raw` into a trusted stream. Events that conform to their registered contract go to `behavioural.valid`; events that don't go to `behavioural.invalid` with structured errors.

## Requirements

- **R1.** The validator consumes `behavioural.raw` as consumer group `event-validator`, starting from the earliest record on first start.
- **R2.** The contract is selected by `eventType` and `schemaVersion`, and the whole event (envelope and payload) is validated against it.
- **R3.** Valid events go to `behavioural.valid` with the raw record's key and the original JSON bytes, prefixed with the Schema Registry wire format (magic byte `0` and the 4-byte ID of the matched schema).
- **R4.** Invalid events go to `behavioural.invalid` with the raw record's key, as `{"event": <original>, "validationErrors": [{code, field, message}], "validatedAt": <ISO-8601>}`.
- **R5.** Every violation is reported, each with a code, a dotted field path (e.g. `payload.productId`) and a non-empty message.
- **R6.** Error codes:

  | Code | When | `field` |
  | --- | --- | --- |
  | `REQUIRED_FIELD_MISSING` | A required field is absent, or `eventType` / `schemaVersion` is missing or null | The missing field |
  | `INVALID_TYPE` | Wrong JSON type, including `null` where not allowed, a non-string `eventType` or a non-integer `schemaVersion` | The value |
  | `INVALID_FORMAT` | A string doesn't match its format (e.g. date-time) | The value |
  | `UNKNOWN_FIELD` | A field the contract doesn't allow | The field |
  | `UNKNOWN_EVENT_TYPE` | No contract for `eventType`; `behavioural_envelope`; or not a valid type name (lower-case letter, then lower-case letters, digits or `_`, at most 100 characters) | `eventType` |
  | `UNKNOWN_SCHEMA_VERSION` | No such version for the type, or `schemaVersion` < 1 | `schemaVersion` |
  | `SCHEMA_VIOLATION` | Any other contract rule (e.g. pattern, minimum) | The value |

- **R7.** An event is processed once its outcome record is acknowledged by Kafka. Delivery is at-least-once.
- **R8.** Invalid events are logged at INFO with their `eventId` and errors.

## Acceptance criteria

- **AC1.** A conforming `product_viewed` v2 event keyed `user-123` appears on `behavioural.valid` keyed `user-123`, with the v2 schema ID in bytes 1–4, and Confluent's JSON Schema deserializer returns the original event.
- **AC2.** A `product_viewed` v2 event without `productId` appears on `behavioural.invalid` with the same key, the original `event`, `REQUIRED_FIELD_MISSING` at `payload.productId`, and `validatedAt`.
- **AC3.** A `product_viewed` v1 event is valid after v2 is registered.
- **AC4.** A `purchase_completed` event with only `orderId` has exactly two errors: `REQUIRED_FIELD_MISSING` at `payload.amount` and `payload.currency`.
- **AC5.** These inputs produce these errors:

  | Input | Code | Field |
  | --- | --- | --- |
  | `productId` is `42`, or `recommendationSource` is `null` | `INVALID_TYPE` | that payload field |
  | Top-level `debug`, or a v2 field in a v1 event | `UNKNOWN_FIELD` | that field |
  | `eventType` `wishlist_added`, `behavioural_envelope`, `:.:behavioural_envelope`, over 100 characters, or with a control character or upper-case letter | `UNKNOWN_EVENT_TYPE` | `eventType` |
  | `schemaVersion` `9`, `0` or `-1` | `UNKNOWN_SCHEMA_VERSION` | `schemaVersion` |
  | `eventType` missing | `REQUIRED_FIELD_MISSING` | `eventType` |
  | `eventType` `42` | `INVALID_TYPE` | `eventType` |
  | `schemaVersion` missing or `null` | `REQUIRED_FIELD_MISSING` | `schemaVersion` |
  | `schemaVersion` `"2"` | `INVALID_TYPE` | `schemaVersion` |
  | `occurredAt` `"yesterday"` | `INVALID_FORMAT` | `occurredAt` |
  | `purchase_completed` `currency` `"usd"` | `SCHEMA_VIOLATION` | `payload.currency` |

- **AC6.** An event produces a record on exactly one of `behavioural.valid` and `behavioural.invalid`.

## Failure and edge cases

| Situation | Behaviour |
| --- | --- |
| Schema Registry or Kafka unavailable | Never produces an invalid event; see [validation resilience](validation-resilience.md). |
| A version is registered after events using it were marked invalid | Those events stay invalid; later events validate normally. |
| The same raw event is delivered twice | Validated and published twice with the same `eventId`; consumers deduplicate. |
| Validator restarts | Resumes from the last committed offset; published but uncommitted events are processed again. |

## Discrepancies

- `Design.md` includes business validation after schema validation. It is not implemented; only contract validation runs.
