# 0010 — Cap event requests at 64 KB

## Context

The collector is an unauthenticated HTTP endpoint. Without a limit of its own, it parses a body of any size, and only Kafka's 1 MB record limit stops an event, after the work is done. Behavioural events are typically under 5 KB.

## Decision

Reject a request body larger than 64 KB (65,536 bytes) with `413` before parsing it. A declared `Content-Length` over the limit is rejected without reading the body; a body without one is read only up to the limit. The limit is fixed in the collector. Kafka's size rejection (ADR 0004) stays as a safety net for a topic or broker configured below 64 KB.

## Why

A cap bounds the memory and CPU one request can use, while allowing well over ten times a typical event, in line with common analytics APIs. It also keeps raw records, and the validator's invalid-event documents built from them, far below Kafka's limit. Relying on Kafka's limit parses up to 1 MB first. A proxy limit would need a layer that doesn't exist yet. A configurable limit has no current use, and changing the limit changes the client contract.

## Consequences

- Clients must keep each event within 64 KB and must not retry a `413`.
- Raising the limit later is backward compatible; lowering it can break producers.
- Authentication and rate limiting are not addressed; they are deferred until the platform is productionised.
