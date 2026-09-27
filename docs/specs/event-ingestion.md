# Spec: Event ingestion

## Purpose

Accept behavioural events over HTTP and store them durably in `behavioural.raw` for asynchronous validation. Ingestion does not depend on Schema Registry or the validator.

## Requirements

- **R1.** `POST /v1/events` accepts a JSON event envelope.
- **R2.** A request is rejected unless:
  - the body is valid JSON with no unknown top-level fields and a parseable ISO-8601 `occurredAt`;
  - `eventId`, `eventType` and `source` are non-blank, and `schemaVersion` and `occurredAt` are present;
  - `userId` or `sessionId` is non-blank;
  - `payload` is a JSON object.
- **R3.** `payload` contents, `eventType` and `schemaVersion` are not checked against registered schemas.
- **R4.** Platform metadata is added; all other client data is stored unchanged:
  - `eventId` is never generated or changed;
  - `correlationId` is the body value, else the `X-Correlation-Id` header, else a generated UUID;
  - `receivedAt` is always the server's receipt time.
- **R5.** The record key is `userId`, or `sessionId` when `userId` is blank.
- **R6.** `202 Accepted` with `{"eventId": …, "status": "accepted"}` is returned only after Kafka acknowledges the write.
- **R7.** Errors use ProblemDetail (`application/problem+json`).
- **R8.** An event that Kafka rejects as too large returns `413 Payload Too Large`, which tells the client not to retry it. Other publish failures return `503`.

## Acceptance criteria

- **AC1.** A complete event with `userId` "user-123" returns `202` with its `eventId`, and one record keyed `user-123` appears on `behavioural.raw`.
- **AC2.** An event without `userId` but with `sessionId` "session-456" is keyed `session-456`.
- **AC3.** The body's `correlationId` wins over the header; the header is used when the body has none; otherwise a UUID is generated.
- **AC4.** A client-supplied `receivedAt` is replaced with the receipt time.
- **AC5.** An event missing `eventType`, `userId` and `sessionId` returns `400` with `fields` `eventType` and `userId|sessionId`, and nothing is written.
- **AC6.** Malformed JSON, an unknown top-level field or an unparseable `occurredAt` returns `400` "Failed to read request", and nothing is written.
- **AC7.** With Kafka unavailable, a request returns `503` within about 10 seconds.
- **AC8.** An event larger than Kafka's 1 MB request limit returns `413`, and nothing is written.

## Failure and edge cases

| Situation | Behaviour |
| --- | --- |
| Kafka unavailable | `503`, after about 5s if the collector has no topic metadata yet, about 10s otherwise. Requests succeed again once Kafka recovers, with no restart. |
| `behavioural.raw` missing | `503`. The collector never creates topics. |
| Client retries after a `503` or timeout | The client reuses its `eventId`. The collector does not deduplicate. |
| Wrong payload fields, or unknown or malformed `eventType` / `schemaVersion` | `202`. The validator marks the event invalid. |
| Schema Registry unavailable | No effect on ingestion. |
| Event larger than Kafka's 1 MB request limit | `413`. The client must not retry it; the collector still reads the whole body before Kafka rejects it. |
