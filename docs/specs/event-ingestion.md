# Spec: Event ingestion

## Purpose

Accept behavioural events from web and mobile clients over HTTP and durably store them in Kafka (`behavioural.raw`), so they can be validated and processed asynchronously. Ingestion must not depend on Schema Registry or any downstream processing being available.

## Requirements

**Endpoint**

- **R1.** The collector accepts events with `POST /v1/events` and a JSON body in the common event envelope.
- **R2.** On success, it responds `202 Accepted` with `{"eventId": "<eventId>", "status": "accepted"}`.
- **R3.** `202` is returned only after Kafka has acknowledged the write to `behavioural.raw`. An accepted event is durably stored.

**Basic checks.** These are the only checks at ingestion; schema and payload validation happen later (see [event-validation](event-validation.md)).

- **R4.** The body must be well-formed JSON with no unknown top-level fields.
- **R5.** `occurredAt` must be a parseable ISO-8601 timestamp.
- **R6.** These fields are required, and a blank string counts as missing:
  - `eventId`, `eventType` and `source` (non-blank)
  - `schemaVersion` and `occurredAt` (present)
  - at least one of `userId` or `sessionId` (non-blank)
  - `payload`, which must be a JSON object

**Identifiers and platform metadata**

- **R7.** `eventId` is always supplied by the client. The collector never generates or changes it.
- **R8.** `correlationId` is kept if the client sent one in the body; otherwise it is taken from the `X-Correlation-Id` request header; otherwise a new UUID is generated.
- **R9.** `receivedAt` is always set by the collector to the time of receipt. Any client-supplied value is replaced.
- **R10.** Everything else the client sent, including `payload`, is stored unchanged.

**Keying**

- **R11.** Each record in `behavioural.raw` is keyed by `userId` when it is non-blank, otherwise by `sessionId`. Events for the same user therefore keep their relative order.

**Errors**

- **R12.** Every error response uses the RFC 9457 ProblemDetail format (`application/problem+json`, with `type`, `title`, `status`, `detail` and `instance`).

## Acceptance criteria

- **AC1.** Given a complete event, when it is posted, then the response is `202` with its `eventId` and `"status": "accepted"`, and exactly one record appears on `behavioural.raw`.
- **AC2.** Given an event with `userId` "user-123", when it is accepted, then its `behavioural.raw` record has key `user-123`.
- **AC3.** Given an event with a blank or missing `userId` and `sessionId` "session-456", when it is accepted, then its record has key `session-456`.
- **AC4.** Given the header `X-Correlation-Id: req-789` and no `correlationId` in the body, when the event is accepted, then the stored event has `correlationId` "req-789".
- **AC5.** Given a `correlationId` in the body *and* a different header value, when the event is accepted, then the body's value is stored.
- **AC6.** Given no `correlationId` in either place, when the event is accepted, then the stored event has a generated UUID `correlationId`.
- **AC7.** Given a client-supplied `receivedAt`, when the event is accepted, then the stored `receivedAt` is the server's receipt time.
- **AC8.** Given an event missing `eventType`, `userId` and `sessionId`, when it is posted, then the response is `400` with a `fields` array containing `eventType` and `userId|sessionId`, and nothing is written to Kafka.
- **AC9.** Given malformed JSON, an unknown top-level field (e.g. `eventTyp`) or an unparseable `occurredAt`, when it is posted, then the response is `400` ProblemDetail with detail "Failed to read request", and nothing is written to Kafka.
- **AC10.** Given Kafka is unavailable, when an event is posted, then the response is `503` ProblemDetail ("Event could not be accepted, retry later") within about 10 seconds, and the failure is logged with the `eventId`.

## Failure and edge-case behaviour

| Situation | Behaviour |
|---|---|
| Kafka unavailable when the collector has no topic metadata yet (e.g. Kafka down at startup, or the topic is missing) | `503` after about 5s |
| Kafka goes down while the collector is running | `503` after about 10s |
| Kafka recovers | Subsequent requests are accepted again, with no restart needed |
| Client retries after a `503` or a timeout | The client must reuse the same `eventId`. The collector does **not** deduplicate, so a retried event can be stored twice with the same `eventId`; consumers deduplicate downstream. |
| `payload` is `null`, a number or an array | `400`, with `payload` in `fields` |
| `payload` contents | Not checked at ingestion. Wrong or missing payload fields are accepted (`202`) and later marked invalid by the validator. |
| Unknown or malformed `eventType` or `schemaVersion` (e.g. `schemaVersion: 0`) | Accepted (`202`). The collector has no knowledge of registered schemas; the validator marks such events invalid. |
| Schema Registry unavailable | No effect on ingestion |
| `behavioural.raw` doesn't exist | Treated like Kafka being unavailable (`503`). The collector never creates topics. |

## Not yet implemented

- Authentication, rate limiting and the least-privilege Kafka credentials described in Design.md section 19.
- Metrics and tracing for ingestion (Design.md section 18).

## Discrepancies

- **Design.md section 6** says the collector "assigns missing platform metadata". The implementation never assigns `eventId`: it is required from the client (R7, ADR 0002). Only `correlationId` and `receivedAt` are assigned.
- **Phase 1 plan (Task 4)** says malformed JSON, unknown fields and bad timestamps return Spring's default error body. The implementation returns ProblemDetail for all errors (R12), following a later fix.
