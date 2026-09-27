# 0005 — Use JSON Schema subjects per event type

## Decision

Event contracts are **JSON Schema (draft-07)** documents registered in Confluent Schema Registry:

- **One subject per event type,** named exactly as the `eventType` (e.g. `product_viewed`).
- **The envelope's `schemaVersion` equals the registry's version number** for that subject.
- **The common envelope is its own subject, `behavioural_envelope`.** Every event-type schema combines it, through `$ref`, with that type's `payload` definition.
- **The content model is closed** (`"additionalProperties": false`), for the envelope and every payload.
- **Every subject uses `BACKWARD` compatibility.**
- **`behavioural_envelope` is reserved** and is never a valid `eventType`.

## Context

The validator must decide whether an event conforms to a registered contract (Design.md sections 7.1 and 11), and schema evolution must be enforced by Schema Registry rather than by each service (section 10).

Events are JSON from the client, through `behavioural.raw`, to consumers. The trusted topic must mean "the whole event conforms to a registered schema", including the envelope, not only the payload.

## How it works

Schema files live in `event-contracts/schemas/<subject>/v<N>.json`:

```text
event-contracts/schemas/
├── behavioural_envelope/v1.json
├── product_viewed/v1.json
├── product_viewed/v2.json
└── ...
```

An event-type schema is the envelope plus its payload:

```json
{
  "title": "product_viewed",
  "allOf": [
    { "$ref": "behavioural_envelope/v1.json" },
    { "properties": { "eventType": { "const": "product_viewed" }, "payload": { "...": "..." } } }
  ]
}
```

The `$ref` name, `<subject>/v<N>.json`, says exactly which subject and version it refers to. Registration turns it into a Schema Registry reference (subject `behavioural_envelope`, version 1), so each schema is pinned to the envelope version it was written against.

The validator reads `eventType` and `schemaVersion` from an event, fetches that subject and version, and validates the whole event.

Schema Registry checks compatibility between versions of the same subject. With a closed content model (verified against Schema Registry 8.3.2):

| Change | Result |
|---|---|
| Add an optional field (`product_viewed` v2 adds `recommendationSource`) | Accepted |
| Add a new required field | Rejected |
| Change a field's type | Rejected |

## Alternatives considered

- **Avro or Protobuf.** Compact, with mature compatibility rules. Rejected: JSON events would need a conversion step, conversion errors are harder to report as field-level errors, and the trusted topic would carry binary data that is harder to inspect.
- **One subject per event type *and* version** (`product_viewed-v2`). Rejected: each subject would hold one schema, so Schema Registry would never check v2 against v1.
- **The registry's schema ID in the envelope instead of `schemaVersion`.** Rejected: client SDKs would depend on registry-internal IDs that differ between environments.
- **Payload-only schemas.** Rejected: the envelope would only be enforced by Java code, and the trusted topic couldn't claim full schema conformance.
- **The envelope copied into every type schema.** Rejected: an envelope change would mean editing every file for every type.
- **An open content model** (unknown fields allowed). Rejected: typos such as `productID` would pass silently. The collector already rejects unknown top-level fields for the same reason.

## Consequences and trade-offs

- Schemas must be registered in order, with no gaps, and never deleted, or `schemaVersion` would stop matching the registry's version (see ADR 0006).
- Optional fields must be **omitted** rather than sent as `null`. `null` is only allowed where a schema says so (for example `userId` and `sessionId` in the envelope).
- Changing the envelope means a new `behavioural_envelope` version, plus a new version of every event type that should use it.
- Unknown fields are always rejected. A producer that starts sending a new field before its schema version is registered will have those events marked invalid.
