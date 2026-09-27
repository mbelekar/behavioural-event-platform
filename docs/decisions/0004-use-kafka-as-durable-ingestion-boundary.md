# 0004 — Use Kafka as the ingestion boundary

## Context

Client ingestion should continue when validation or downstream systems are unavailable. An HTTP acknowledgement should mean the event reached a durable boundary.

## Decision

The Event Collector checks the request envelope, adds platform metadata, and publishes to `behavioural.raw`. It returns `202 Accepted` after Kafka acknowledges the write with `acks=all`; a failed publish returns `503`. Contract validation happens asynchronously.

## Why

Kafka retains accepted events while the validator recovers. Synchronous validation would couple ingestion to Schema Registry. A fire-and-forget `202` could acknowledge an event that was never stored.

## Consequences

- Request latency includes the Kafka acknowledgement.
- Clients should retry a `503` using the same `eventId`.
- `behavioural.raw` contains unvalidated client data plus platform metadata. Only `behavioural.valid` is trusted.
