# 0008: Separate invalid events from infrastructure failures

## Context

An unavailable Schema Registry says nothing about event validity. Classifying dependency failures as bad data would send good events to `behavioural.invalid` without rechecking them.

## Decision

Publish confirmed contract violations and unknown event types or schema versions to `behavioural.invalid`. Retry Schema Registry failures, and Kafka publish failures that Kafka marks as retriable, without advancing past the affected record. Permanent publish failures, such as a record too large for Kafka, are not retried. Commit input offsets after a successful output publish. Consume each `behavioural.raw` partition on its own thread, so a retrying record holds up only its own partition.

The current implementation retries infrastructure failures without a limit. A later phase will add bounded retries and `validation.dlq`. Unexpected failures, including permanent publish failures, are currently logged and skipped; this interim data-loss risk also requires the DLQ work. ADR 0011 closes it: unexpected failures are dead-lettered, and infrastructure retries stay unbounded.

## Why

Retrying keeps dependency outages from being mistaken for producer errors. A short default retry window could exhaust before recovery.

## Consequences

- An outage blocks only the partitions with a waiting record, and increases their consumer lag; other partitions keep flowing.
- Listener concurrency must follow the `behavioural.raw` partition count (currently 6); with fewer threads than partitions, one waiting record also delays the other partitions on its thread.
- A permanently failing dependency can stall processing indefinitely.
- Unexpected processing failures, and records too large for Kafka, can be lost until DLQ handling is implemented.
