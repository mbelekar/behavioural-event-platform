# 0002: Use client-supplied event IDs

## Context

Clients can retry requests, and Kafka can redeliver records. Consumers need a stable identifier to recognise the same logical event.

## Decision

Require clients to supply `eventId`. Reject requests without it and preserve it across topics and clusters. Consumers that need deduplication use this ID.

## Why

Generating an ID in the collector would assign a new ID after a client timeout. Hashing event content could merge two separate but identical actions.

## Consequences

- Clients must generate unique IDs and reuse them on retry.
- The platform preserves IDs but does not itself deduplicate events.
- Reusing an ID for different events can cause a deduplicating consumer to collapse them.
