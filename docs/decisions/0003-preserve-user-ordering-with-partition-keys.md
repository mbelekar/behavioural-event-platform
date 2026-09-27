# 0003 — Preserve user ordering with partition keys

## Decision

Kafka records carrying behavioural events are keyed by `userId`, or by `sessionId` when `userId` is missing or blank.
Every event must have at least one of the two; the Event Collector rejects a request with neither (`400`).

## Context

Behavioural events are often interpreted as sequences, such as product viewed, then checkout started, then purchase completed.
Kafka only guarantees ordering within a partition, so events that must stay in order have to share a key (see Design.md section 17).

## How it works

- `RawEventPublisher.partitionKey` in the collector chooses the key: `userId` if non-blank, otherwise `sessionId`.
- Kafka's default partitioner hashes the key, so all of a user's events land on the same partition of `behavioural.raw`.
- The validator (Phase 2) will republish with the same key it consumed, so ordering carries through `behavioural.valid`.

## Alternatives considered

- **Key by `eventId`.** Rejected: it spreads load evenly but loses per-user ordering.
- **No key (sticky partitioning).** Rejected for the same reason.
- **Key only by `sessionId`.** Rejected: ordering would not hold across a user's sessions or devices.

## Consequences and trade-offs

- Very active users can create hot partitions. Throughput per user is limited to what one partition can handle.
- An anonymous session that later logs in switches from the `sessionId` key to the `userId` key, so ordering is not guaranteed across that transition.
- Changing a topic's partition count changes the key-to-partition mapping, which briefly breaks ordering for keys that move.
