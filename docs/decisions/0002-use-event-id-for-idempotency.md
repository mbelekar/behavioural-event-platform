# 0002 — Use the client-supplied event ID for idempotency

## Decision

Every event must carry an `eventId` supplied by the client. The Event Collector rejects a request without one (`400`).
The platform never generates or overwrites `eventId`; it is preserved unchanged through every topic and cluster.

## Context

The platform uses at-least-once delivery (see Design.md section 14), so duplicates are expected from two sources:

- Kafka redelivery, for example after a consumer rebalance or a producer retry.
- Clients retrying a request after a timeout or a `503` from the collector.

Downstream consumers can only deduplicate if the same logical event always has the same identifier, including across client retries.

## How it works

- `EventRequestChecks` in the collector treats a missing or blank `eventId` as a missing field, so the request gets a `400`.
- `PlatformMetadata` copies `eventId` unchanged. Only `correlationId` (when missing) and `receivedAt` are added.
- The validator and router republish the envelope with the same `eventId`. Consumers that need idempotency deduplicate on it.

## Alternatives considered

- **Generate `eventId` server-side when it is missing.** Rejected: a client that retries after a timeout would get a new ID for the same event, creating a duplicate that can't be detected.
- **Always generate `eventId` server-side.** Rejected for the same reason, applied to every retry.
- **Deduplicate on a hash of the event content.** Rejected: two genuinely separate identical actions (for example, two clicks on the same button) would be collapsed.

## Consequences and trade-offs

- Client SDKs must generate globally unique IDs (ULID or UUID) and reuse them on retry.
- A client that reuses an ID for different events will have those events collapsed by deduplicating consumers. The platform can't detect this.
- Clients that don't send an ID are rejected rather than silently accepted, which is stricter for SDK authors but keeps deduplication reliable.
