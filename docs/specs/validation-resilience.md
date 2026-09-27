# Spec: Validation resilience

## Purpose

Keep "the event is wrong" separate from "the platform couldn't check or publish it". When Schema Registry or Kafka is unavailable, the validator must neither lose events nor mark good events invalid. Events wait, and are processed once the dependency recovers.

## Requirements

**Classifying failures**

- **R1.** An event whose `eventType` or `schemaVersion` can't identify a contract is an invalid-event outcome. That covers "no such event type" and "no such version" from Schema Registry, and values that can never name a contract (see [event validation](event-validation.md) R7). Every other lookup failure means *Schema Registry unavailable*: connection refused, timeout, or an error response from the registry.
- **R2.** A single Schema Registry lookup gives up after about 5 seconds.
- **R3.** A publish that fails in a way that may succeed on retry (the broker is unavailable, or doesn't acknowledge in time) is an infrastructure failure. A publish that fails permanently (for example, the record exceeds Kafka's size limit) is an unexpected failure (R10) and is not retried.

**Retrying**

- **R4.** On an infrastructure failure, the same record is retried every 5 seconds, **with no limit**, until it succeeds. Records behind it on the same partition wait, so per-key ordering is preserved. Other partitions continue to be processed independently.
- **R5.** No record is published to `behavioural.invalid` because of an infrastructure failure.
- **R6.** Each failed attempt is logged at WARN, in the form `Retrying <topic>-<partition>@<offset> key=<key> (attempt <n>): <cause>`.
- **R7.** Once a contract has been fetched for an event type and version, later events with that type and version are validated without contacting Schema Registry. They are unaffected by a registry outage.

**Delivery and ingestion**

- **R8.** The raw record's offset is committed only after its outcome has been acknowledged by Kafka. No event is skipped because of an outage.
- **R9.** The collector keeps accepting events while Schema Registry or the validator is down (see [event ingestion](event-ingestion.md)). Events accumulate on `behavioural.raw` and are validated after recovery.

**Unexpected failures**

- **R10.** Unexpected failures, such as a raw record that isn't JSON, a permanent publish failure or a processing bug, are **not** retried. They are logged at ERROR and the record is skipped.

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
- **AC5.** Given Schema Registry is paused and one partition is waiting on an event whose contract hasn't been fetched, when an event whose contract *has* been fetched arrives on a different partition, then it reaches `behavioural.valid` within a few seconds, without waiting for the registry to recover.
- **AC6.** Given an outcome record larger than Kafka accepts, when the validator publishes it, then it is not retried: it is logged at ERROR and skipped, and the partition moves on.
- **AC7.** Given an event with `schemaVersion` `0`, then it is published to `behavioural.invalid` with `UNKNOWN_SCHEMA_VERSION` and is not retried.

## Failure and edge-case behaviour

| Situation | Behaviour |
|---|---|
| Long Schema Registry outage | The affected partitions stay blocked and consumer lag grows until recovery. There is no upper bound. |
| Outage affects one event type only (e.g. only uncached types) | Events whose contract was already fetched keep flowing. Partitions blocked on an uncached type wait. |
| Validator restarts during an outage | Contracts must be fetched again, so every partition blocks until Schema Registry recovers. Nothing is lost. |
| Corrupt raw record, or a processing bug | Logged at ERROR and skipped: **the event is lost** from the valid and invalid streams |
| Outcome record too large for Kafka (e.g. a near-1 MB event, or an invalid event with thousands of errors) | Logged at ERROR and skipped: **the event is lost** until Phase 3's DLQ exists |

## Not yet implemented

- Bounded retries with exponential back-off, and `validation.dlq` for events that still fail after the retries (Design.md sections 15 and 16, Phase 3).
- Metrics and alerting on retries and consumer lag (Design.md section 18, Phase 6).

## Discrepancies

- **Design.md section 15** specifies bounded retries (1s → 5s → 30s, then DLQ). The current implementation retries infrastructure failures every 5 seconds with no limit (R4). This is the documented interim policy until Phase 3 (ADR 0008).
- **Design.md section 20** says unexpected processing errors go to a DLQ, and section 26 says no event should silently disappear. Today, unexpected failures are logged at ERROR and skipped (R10), so such events can be lost until `validation.dlq` exists.
