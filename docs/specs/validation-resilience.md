# Spec: Validation resilience

## Purpose

Keep bad data separate from broken infrastructure. When Schema Registry or Kafka is unavailable, the validator must not lose events or mark good events invalid; events wait and are processed after recovery.

## Requirements

- **R1.** Only an `eventType` or `schemaVersion` that cannot identify a contract is an invalid outcome (see [event validation](event-validation.md) R6). Any other Schema Registry lookup failure means *unavailable*.
- **R2.** A Schema Registry lookup gives up after about 5 seconds.
- **R3.** A publish failure that may succeed on retry (broker unavailable, no acknowledgement in time) is an infrastructure failure. A permanent publish failure (e.g. a record over Kafka's size limit) is an unexpected failure (R8).
- **R4.** An infrastructure failure is retried on the same record every 5 seconds, with no limit. Later records on that partition wait; other partitions keep flowing.
- **R5.** Infrastructure failures never produce a record on `behavioural.invalid`.
- **R6.** Each failed attempt is logged at WARN: `Retrying <topic>-<partition>@<offset> key=<key> (attempt <n>): <cause>`.
- **R7.** A contract already fetched for a type and version is used without contacting Schema Registry.
- **R8.** Unexpected failures (a raw record that isn't JSON, a permanent publish failure, a bug) are not retried; they are logged at ERROR and the record is skipped.
- **R9.** A raw record's offset is committed only after its outcome is acknowledged by Kafka.

## Acceptance criteria

- **AC1.** With Schema Registry paused, a valid event of an unfetched type produces no invalid record and WARN retries; after unpausing, it appears on `behavioural.valid`.
- **AC2.** With Schema Registry stopped, the collector still returns `202`, the validator retries about every 5 seconds, and the event appears on `behavioural.valid` after restart.
- **AC3.** An unreachable Schema Registry is reported as unavailable, never as `UNKNOWN_EVENT_TYPE` or `UNKNOWN_SCHEMA_VERSION`.
- **AC4.** An unreachable Kafka makes the publish fail within the producer's time limit, and it is retried.
- **AC5.** While one partition waits on an unfetched contract, an event with a fetched contract on another partition reaches `behavioural.valid` within a few seconds.
- **AC6.** An outcome record larger than Kafka accepts is not retried; it is logged at ERROR and the partition moves on.
- **AC7.** An event with `schemaVersion` `0` goes to `behavioural.invalid` and is not retried.

## Failure and edge cases

| Situation | Behaviour |
| --- | --- |
| Long Schema Registry outage | Affected partitions stay blocked and lag grows, with no upper bound. |
| Validator restarts during an outage | Contracts must be fetched again, so all partitions wait; nothing is lost. |
| Corrupt raw record or processing bug | Logged at ERROR and skipped. **Open issue:** the event is lost until a DLQ exists. |
| Outcome record too large for Kafka | Logged at ERROR and skipped. **Open issue:** the event is lost until a DLQ exists. |

## Discrepancies

- `Design.md` ("Failure handling") sends events whose processing repeatedly fails to `validation.dlq`. Today infrastructure failures are retried without limit (R4) and unexpected failures are skipped (R8); there is no DLQ.
