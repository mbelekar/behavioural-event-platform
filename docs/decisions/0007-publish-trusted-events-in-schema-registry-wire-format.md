# 0007 — Publish trusted events in Schema Registry wire format

## Context

Consumers of `behavioural.valid` need to know which registered schema accepted an event and use standard Schema Registry tooling.

## Decision

Prefix each validated JSON record with the Confluent wire-format magic byte and its matched schema ID. Preserve the validated JSON bytes and Kafka key. The validator writes the prefix because it already knows the schema ID. Invalid event records remain plain JSON.

## Why

Standard deserializers recognise the prefix. A schema ID in Kafka headers would not work with those tools. Re-serializing JSON could change the bytes after validation.

## Consequences

- Trusted-topic consumers need Schema Registry access or must handle the five-byte prefix.
- The validator owns wire-format code, verified with a standard deserializer in integration tests.
- `behavioural.invalid` has a separate, application-defined format.
