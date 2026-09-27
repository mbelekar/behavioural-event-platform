# 0004 — Use Kafka as the durable ingestion boundary

## Decision

The Event Collector's only job is to durably write accepted events to the Kafka topic `behavioural.raw`.
It returns `202 Accepted` only after the broker has acknowledged the write (`acks=all`, idempotent producer), and `503 Service Unavailable` if that can't happen within about 5 seconds.
All further processing (schema validation, business validation, routing) happens asynchronously, downstream of that topic.

## Context

The collector is the only internet-facing service. Clients such as web and mobile SDKs should not have to resend events because Schema Registry, the validator or the downstream cluster is temporarily unavailable (see Design.md sections 5, 6 and 26).
A durable topic between ingestion and processing absorbs those outages: events wait in `behavioural.raw` until the consumers recover.

## How it works

`POST /v1/events` does four things, in order:

1. **Parse strictly.** Malformed JSON, an unparseable timestamp or an unknown top-level field gets `400`. Unknown fields are rejected rather than silently dropped, so a typo such as `eventTyp` is visible to the client.
2. **Basic shape checks.** These are the required envelope fields, with at least one of `userId`/`sessionId` and `payload` as a JSON object. Failures get `400` with a `fields` list.
3. **Platform metadata.** `correlationId` is taken from the body, else the `X-Correlation-Id` header, else generated. `receivedAt` is always set by the server.
4. **Synchronous publish** to `behavioural.raw`, keyed as described in ADR 0003. Producer limits (`max.block.ms=5000`, `delivery.timeout.ms=10000`) keep a Kafka outage from hanging requests.

"Raw events are immutable" is interpreted as: **client-supplied data is never altered; platform fields are added to the envelope.** `eventId` is never generated or changed (ADR 0002).

Topics are created by infrastructure (for example the Docker Compose init container), not by services. Services have write access only to the topics they produce to (Design.md section 19).

### How contracts are enforced

The collector checks only the envelope's shape. Contract enforcement is Schema Registry's job, at the **Event Validator** (Phase 2):

- The schema for each event type's payload, per version, lives in `event-contracts/schemas/` and is registered in Schema Registry.
- The validator maps `eventType` + `schemaVersion` to a registered schema and validates `payload` against it. Failures go to `behavioural.invalid` with structured errors.
- Schema Registry's compatibility rules block breaking schema changes at registration time.
- `behavioural.valid` is written with a Schema Registry–aware serializer, so consumers of the trusted topic read schema-validated data.

The `BehaviouralEvent` record in `event-contracts` describes only the common envelope. Its `payload` is generic JSON because its shape depends on the event type.

## Alternatives considered

- **Validate synchronously in the collector.** Rejected: ingestion would then depend on Schema Registry being available, and clients would need to resend events during its outages.
- **Keep the body byte-for-byte and carry metadata in Kafka headers.** Rejected: every consumer would have to merge headers and body. Trusted consumers expect platform metadata inside the envelope (Design.md section 11).
- **Fire-and-forget publish** (return `202` before the broker acknowledges). Rejected: a `202` would not guarantee the event was stored, so events could be lost silently.
- **Enforce Schema Registry at the collector** (serialize to `behavioural.raw` with the Schema Registry serializer). Rejected for two reasons. Every request would depend on Schema Registry being up. And bad events would get a `400` instead of being quarantined in `behavioural.invalid`, which loses visibility of which producers send bad data.

## Consequences and trade-offs

- Every request's latency includes a Kafka round-trip with `acks=all`.
- Clients must retry on `503`, reusing the same `eventId` so duplicates can be removed downstream.
- `behavioural.raw` contains unvalidated data. Only `behavioural.valid` is a trusted boundary.
- The raw topic is not byte-for-byte what the client sent: it holds the client's data plus platform fields.
