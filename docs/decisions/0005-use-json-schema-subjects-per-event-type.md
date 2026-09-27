# 0005: Use JSON Schema subjects per event type

## Context

Events arrive as JSON. The validator needs a versioned contract for the whole event, including its envelope and event-specific payload.

## Decision

Register draft-07 JSON Schemas in Confluent Schema Registry. Each event type has one subject named after its `eventType`; `schemaVersion` maps to that subject's registry version. Event schemas reference a versioned `behavioural_envelope` subject. Subjects use `BACKWARD` compatibility and reject unknown fields. The envelope subject name is reserved.

## Why

Separate subjects let event types evolve independently while the registry checks successive versions. A shared envelope avoids duplication. Closed schemas expose misspelled fields.

One subject per version would bypass compatibility checks. Payload-only schemas would leave the envelope outside the contract. Avro and Protobuf would require converting incoming JSON.

## Consequences

- Register schemas in version order and keep them append-only.
- Register a compatible version before producers send new fields.
- Envelope changes require new versions of event schemas that reference it.
- Optional fields may be omitted; `null` is accepted only where permitted.
