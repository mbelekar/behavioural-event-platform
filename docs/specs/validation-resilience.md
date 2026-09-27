# Spec: Validation resilience

## Purpose

Keep bad data separate from broken infrastructure. When Schema Registry or Kafka is unavailable, the validator must not lose events or mark good events invalid; events wait and are processed after recovery. An event that cannot be processed for any other reason is set aside in `validation.dlq`, not lost.

## Requirements

- **R1.** Only an `eventType` or `schemaVersion` that cannot identify a contract is an invalid outcome (see [event validation](event-validation.md) R6). Any other Schema Registry lookup failure means *unavailable*.
- **R2.** A Schema Registry lookup gives up after about 5 seconds.
- **R3.** A publish failure that may succeed on retry (broker unavailable, no acknowledgement in time) is an infrastructure failure. A permanent publish failure (e.g. a record over Kafka's size limit) is an unexpected failure (R8).
- **R4.** An infrastructure failure is retried on the same record every 5 seconds, with no limit. Later records on that partition wait; other partitions keep flowing.
- **R5.** Infrastructure failures never produce a record on `behavioural.invalid`.
- **R6.** Each failed attempt is logged at WARN: `Retrying <topic>-<partition>@<offset> key=<key> (attempt <n>): <cause>`.
- **R7.** A contract already fetched for a type and version is used without contacting Schema Registry.
- **R8.** Unexpected failures (a raw record that isn't JSON, a permanent publish failure, a bug) are retried twice, 1 second apart. If the record still fails, it is published to `validation.dlq` and logged at ERROR: `Dead-lettered <topic>-<partition>@<offset> key=<key> to validation.dlq: <cause>`.
- **R9.** A raw record's offset is committed only after its outcome, or its `validation.dlq` record, is acknowledged by Kafka.
- **R10.** A `validation.dlq` record has the raw record's key and value bytes, unchanged, on the same partition number, with headers giving the source topic, partition and offset, and the failure's exception class and message.
- **R11.** If publishing to `validation.dlq` fails, the raw record is not skipped; it is processed again from the first attempt.
- **R12.** Infrastructure failures (R4) are never published to `validation.dlq`.

## Acceptance criteria

- **AC1.** With Schema Registry paused, a valid event of an unfetched type produces no invalid record and WARN retries; after unpausing, it appears on `behavioural.valid`.
- **AC2.** With Schema Registry stopped, the collector still returns `202`, the validator retries about every 5 seconds, and the event appears on `behavioural.valid` after restart.
- **AC3.** An unreachable Schema Registry is reported as unavailable, never as `UNKNOWN_EVENT_TYPE` or `UNKNOWN_SCHEMA_VERSION`.
- **AC4.** An unreachable Kafka makes the publish fail within the producer's time limit, and it is retried.
- **AC5.** While one partition waits on an unfetched contract, an event with a fetched contract on another partition reaches `behavioural.valid` within a few seconds.
- **AC6.** An invalid event whose outcome record is larger than Kafka accepts is retried twice, then appears on `validation.dlq` with its original key and bytes; nothing reaches `behavioural.valid` or `behavioural.invalid`, and the partition moves on.
- **AC7.** An event with `schemaVersion` `0` goes to `behavioural.invalid` and is not retried.
- **AC8.** A raw record that isn't JSON (including bytes that aren't valid UTF-8) is logged at WARN for attempts 1 to 3, then appears on `validation.dlq` byte-for-byte with the same key, the same partition number, and headers naming `behavioural.raw`, its partition and offset, and the exception. It is logged at ERROR, and nothing reaches `behavioural.valid` or `behavioural.invalid`.
- **AC9.** While Schema Registry is paused for longer than the unexpected-failure retries take, a waiting event never appears on `validation.dlq`.

## Failure and edge cases

| Situation | Behaviour |
| --- | --- |
| Long Schema Registry outage | Affected partitions stay blocked and lag grows, with no upper bound. |
| Validator restarts during an outage | Contracts must be fetched again, so all partitions wait; nothing is lost. |
| Corrupt raw record or processing bug | Dead-lettered after two retries. |
| Outcome record too large for Kafka | Dead-lettered after two retries. The raw record fits, because it was already accepted by Kafka. |
| Kafka unavailable while dead-lettering | The record is processed again from the first attempt; its partition waits and nothing is lost. |
| A failure that clears on retry | Processed normally; nothing is dead-lettered. |
| Records in `validation.dlq` | Kept until an operator deletes them (no time-based retention). Nothing consumes them automatically. |
| A `validation.dlq` record is replayed to `behavioural.raw` | Validated again with its original `eventId`; if it now succeeds it is published once more, and consumers deduplicate. |
