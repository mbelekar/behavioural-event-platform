# Spec: Validation resilience

## Purpose

Keep "the event is wrong" separate from "the platform couldn't check or publish it". When Schema Registry or Kafka is unavailable, the validator must neither lose events nor mark good events invalid. Events wait, and are processed once the dependency recovers.

## Requirements

**Classifying failures**

- **R1.** Only a definitive answer from Schema Registry counts as an invalid-event outcome: "no such event type" or "no such version". Every other lookup failure means *Schema Registry unavailable*: connection refused, timeout, or any other error response.
- **R2.** A single Schema Registry lookup gives up after about 5 seconds.
- **R3.** Failing to publish a valid or invalid record (Kafka not acknowledging it) counts as an infrastructure failure, not an outcome.

**Retrying**

- **R4.** On an infrastructure failure, the same record is retried every 5 seconds, **with no limit**, until it succeeds. Records behind it on the same partition wait, so per-key ordering is preserved.
- **R5.** No record is published to `behavioural.invalid` because of an infrastructure failure.
- **R6.** Each failed attempt is logged at WARN, in the form `Retrying <topic>-<partition>@<offset> key=<key> (attempt <n>): <cause>`.
- **R7.** Once a contract has been fetched for an event type and version, later events with that type and version are validated without contacting Schema Registry. They are unaffected by a registry outage.

**Delivery and ingestion**

- **R8.** The raw record's offset is committed only after its outcome has been acknowledged by Kafka. No event is skipped because of an outage.
- **R9.** The collector keeps accepting events while Schema Registry or the validator is down (see [event ingestion](event-ingestion.md)). Events accumulate on `behavioural.raw` and are validated after recovery.

**Unexpected failures**

- **R10.** Unexpected failures, such as a raw record that isn't JSON or a processing bug, are **not** retried. They are logged at ERROR and the record is skipped.

## Acceptance criteria

- **AC1.** Given Schema Registry is paused and a valid event of a type not yet fetched is on `behavioural.raw`, then:
  - nothing for that event appears on `behavioural.invalid`;
  - WARN retry lines naming its key and `SchemaRegistryUnavailableException` are logged about every 5–10 seconds;
  - after Schema Registry is unpaused, the event appears on `behavioural.valid`.
- **AC2.** Given Schema Registry is stopped (connections refused) and an event is sent through the collector, then:
  - the collector still returns `202`;
  - the validator logs WARN retries about every 5 seconds;
  - after Schema Registry restarts, the event appears on `behavioural.valid`.
- **AC3.** Given Schema Registry is unreachable, when an event type and version is looked up, then the lookup fails as "unavailable", never as `UNKNOWN_EVENT_TYPE` or `UNKNOWN_SCHEMA_VERSION`.
- **AC4.** Given Kafka is unreachable when the validator publishes an outcome, then the publish fails within the producer's time limit and is retried, rather than being skipped.

## Failure and edge-case behaviour

| Situation | Behaviour |
|---|---|
| Long Schema Registry outage | The affected partitions stay blocked and consumer lag grows until recovery. There is no upper bound. |
| Outage affects one event type only (e.g. only uncached types) | Events whose contract was already fetched keep flowing. Partitions blocked on an uncached type wait. |
| Validator restarts during an outage | Contracts must be fetched again, so every partition blocks until Schema Registry recovers. Nothing is lost. |
| Corrupt raw record, or a processing bug | Logged at ERROR and skipped: **the event is lost** from the valid and invalid streams |

## Not yet implemented

- Bounded retries with exponential back-off, and `validation.dlq` for events that still fail after the retries (Design.md sections 15 and 16, Phase 3).
- Metrics and alerting on retries and consumer lag (Design.md section 18, Phase 6).

## Discrepancies

- **Design.md section 15** specifies bounded retries (1s → 5s → 30s, then DLQ). The current implementation retries infrastructure failures every 5 seconds with no limit (R4). This is the documented interim policy until Phase 3 (ADR 0008).
- **Design.md section 20** says unexpected processing errors go to a DLQ, and section 26 says no event should silently disappear. Today, unexpected failures are logged at ERROR and skipped (R10), so such events can be lost until `validation.dlq` exists.
