# 0011: Dead-letter unexpected validation failures

## Context

ADR 0008 left an interim gap: unexpected failures (a raw record that isn't JSON, an outcome record too large for Kafka, a bug) are logged and skipped, so the event is lost. Infrastructure failures are retried without limit.

## Decision

Retry an unexpected failure twice, one second apart, then publish the raw record to `validation.dlq`: the same key and value bytes, on the same partition number, with headers naming the source topic, partition and offset and the exception. Infrastructure failures keep retrying without limit and are never dead-lettered. If publishing to `validation.dlq` fails, the record is not skipped; it is tried again from the start. `validation.dlq` has no time-based retention. Replay is manual, with the Kafka CLI tools.

## Why

Bounding infrastructure retries would dead-letter good events during an outage, and during a Kafka outage the dead-letter topic is unreachable anyway. Unexpected failures don't recover by waiting, so a few quick retries catch an intermittent fault without blocking the partition. The raw bytes, not a JSON wrapper, keep a record that isn't JSON intact, can't exceed Kafka's limit when the raw record fit, and replay by copying. A replay tool has no requirements yet.

## Consequences

- No event is skipped; one that cannot be processed waits in `validation.dlq` until someone replays or deletes it.
- A replayed event is validated again with its original `eventId`; consumers deduplicate as usual (ADR 0002).
- `validation.dlq` must have at least as many partitions as `behavioural.raw`.
- A long outage still blocks affected partitions, as in ADR 0008.
