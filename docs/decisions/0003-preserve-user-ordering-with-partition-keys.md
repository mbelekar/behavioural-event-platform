# 0003 — Key events by user or session

## Context

Behavioural events can form a sequence, but Kafka preserves order only within a partition.

## Decision

Require at least one of `userId` or `sessionId`. Key records by `userId` when present, otherwise by `sessionId`. Preserve the key when republishing validated events.

## Why

Events for the same known user then share a partition. A session key serves anonymous events. An `eventId` key or no key would spread a user's events across partitions.

## Consequences

- Ordering is scoped to each topic's partition; the key alone does not guarantee end-to-end order.
- A session that later gains a user ID can change partitions.
- Active users may create hot partitions. Increasing partition count can change key placement.
