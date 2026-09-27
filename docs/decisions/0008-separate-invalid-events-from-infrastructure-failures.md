# 0008 — Separate invalid events from infrastructure failures

## Decision

The Event Validator treats "the event is wrong" and "the platform couldn't check or publish it" as different outcomes:

| Outcome | Examples | Handling |
|---|---|---|
| **Invalid event** | unknown event type or schema version, schema violations | Published to `behavioural.invalid` with structured errors, offset committed |
| **Infrastructure failure** | Schema Registry unreachable, slow or erroring; Kafka not acknowledging a publish | The same record is retried every 5 seconds, **with no limit** (Phase 2 interim) |
| **Unexpected failure** | a record that isn't JSON, a bug | Logged at ERROR and skipped (Phase 2 interim) |

Phase 3 replaces the two interim rows with bounded exponential retries and `validation.dlq` (Design.md sections 15 and 16).

## Context

Design.md section 26 says to "separate bad data from broken infrastructure" and that "no event should silently disappear". An event must never land in `behavioural.invalid` just because Schema Registry was down when it was checked. That would label a producer's good data as bad, and nothing would re-validate it.

Phase 2 ships `behavioural.invalid` but not yet the DLQ or bounded retries, so it needs an interim policy that doesn't lose events.

## How it works

- **Unknown vs unavailable.** `SchemaCatalog` treats only Schema Registry error codes `40401` (subject not found) and `40402` (version not found) as *unknown*. Any other error response, I/O error or timeout raises `SchemaRegistryUnavailableException`.
- **Bounded lookups.** The registry client uses 5-second connect and read timeouts, and **its own retries are turned off** (`max.retries=0`). The client's default of 3 internal retries with back-off made one lookup against a hung registry take about 24 seconds. Retrying is left to the Kafka error handler, so it happens in one place.
- **Retrying without loss.** A Spring Kafka `DefaultErrorHandler` with `FixedBackOff(5s, unlimited)` retries only `SchemaRegistryUnavailableException` and `PublishFailedException`. It seeks back to the failed record, so the partition doesn't advance past it. All other exceptions are classified as not retryable and are logged and skipped.
- **Visible retries.** A retry listener logs every failed attempt at WARN, one line per attempt: `Retrying behavioural.raw-2@0 key=user-123 (attempt 1): …SchemaRegistryUnavailableException: …`.
- **At-least-once.** The listener publishes synchronously, so offsets are committed only after the broker acknowledges the valid or invalid record.

`SchemaRegistryOutageTest` pauses the Schema Registry container while an event is waiting. It checks that nothing reaches `behavioural.invalid`, and that the event reaches `behavioural.valid` after the registry is unpaused. It also checks that the WARN retry line appears for the waiting record.

## Alternatives considered

- **Spring Kafka's default error handler** (about 10 quick retries, then log and skip). Rejected: a Schema Registry outage longer than a few seconds would silently drop events.
- **Treat any lookup failure as invalid.** Rejected: outages would fill `behavioural.invalid` with valid events.
- **Bring Phase 3's bounded retries and DLQ forward.** Deferred to keep Phase 2's scope as agreed.
- **Keep the client's internal retries.** Rejected: they multiply the per-lookup timeout and hide the retrying from the Kafka error handler.

## Consequences and trade-offs

- During an infrastructure outage, the affected partition stops: later events for those users wait behind the failed one, and consumer lag grows until recovery. Ordering is preserved and nothing is lost, but the wait is unbounded.
- An outage produces one WARN line per retry (every ~10 seconds per blocked partition) until it ends. Metrics and alerting on retries and consumer lag come with the observability work (Phase 6).
- A record that can never be processed (corrupt input or a bug) is dropped with an ERROR log until `validation.dlq` exists in Phase 3.
