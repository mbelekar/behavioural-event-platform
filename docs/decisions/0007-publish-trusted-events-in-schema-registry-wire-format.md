# 0007 — Publish trusted events in Schema Registry wire format

## Decision

Records on `behavioural.valid` use the **Confluent Schema Registry wire format**:

```text
byte 0      magic byte, always 0
bytes 1–4   schema id (big-endian int) of the schema the event was validated against
bytes 5–    the event's JSON, exactly as consumed from behavioural.raw
```

The Event Validator writes this 5-byte prefix itself (`SchemaRegistryWireFormat`), rather than through Confluent's `KafkaJsonSchemaSerializer`.

Records on `behavioural.invalid` are plain JSON documents: `{"event": …, "validationErrors": […], "validatedAt": …}` (Design.md section 9).

## Context

ADR 0004 says that consumers of the trusted topic read schema-validated data through standard Schema Registry tooling. Design.md section 11 says they shouldn't need to repeat validation, or guess which schema an event conforms to.

The validator already knows the exact schema (type and version) each valid event matched, from ADR 0005's lookup.

## How it works

- `EventValidator` returns the matched schema's ID with a valid result.
- `ValidatedEventPublisher.publishValid` frames the original JSON bytes with that ID and publishes with the same key it consumed (ADR 0003).
- Consumers use Confluent's `KafkaJsonSchemaDeserializer`, or read the ID from bytes 1–4 and fetch the schema from the registry themselves. The validator's integration tests read the output with the Confluent deserializer to prove the framing is correct.
- The JSON bytes are **not re-serialized**. Field order and formatting are exactly what the collector wrote, so "raw events are immutable" holds all the way to the trusted topic.
- Invalid events carry no schema ID, since by definition no schema accepted them.

## Alternatives considered

- **`KafkaJsonSchemaSerializer`.** Rejected: it needs a schema-carrying object for every message and derives or looks up the schema itself, although the validator already knows the ID. It would also pull the Kafka broker jar (`kafka_2.13`) into the validator's runtime.
- **Plain JSON with the schema ID in a Kafka header.** Rejected: standard Schema Registry deserializers and tools don't read it.
- **Re-serializing the parsed event.** Rejected: it could reorder or reformat fields, so the trusted record would no longer be exactly what was validated.

## Consequences and trade-offs

- Consumers of `behavioural.valid` need Schema Registry access, or must skip the 5-byte prefix.
- The framing code is ours to keep correct. The deserializer test is its safety net.
- `behavioural.invalid` is not schema-governed. Its document shape is defined only by Design.md section 9 and the validator's code.
