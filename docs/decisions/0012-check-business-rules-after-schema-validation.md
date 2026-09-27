# 0012 — Check business rules after schema validation

## Context

Some rules cannot be expressed cleanly in JSON Schema: comparing two timestamps in the same event, or checking a value against a list that changes independently of the contract, such as ISO 4217 currencies. `Design.md` places these rules after schema validation, with failures on `behavioural.invalid`.

## Decision

The validator checks business rules in code, only for events that conform to their contract, and reports every failing rule with its own error code. Time rules compare `occurredAt` with the collector's `receivedAt`: at most 5 minutes later and at most 7 days earlier. The limits are fixed. A `purchase_completed` currency must be an ISO 4217 code known to the JDK, excluding codes that are not currencies (precious metals, bond and accounting units, `XTS` and `XXX`).

## Why

Rules on a conforming event can read every field without guarding against missing or mistyped values. `receivedAt` is fixed when the event is accepted, so the outcome doesn't depend on when validation runs: a backlog or a replay from `validation.dlq` gives the same result. Per-rule codes let consumers count and route failures without parsing messages. A schema enum of currencies would need a new schema version whenever ISO 4217 changes; an own allow-list would need maintenance. Configurable limits have no current use and would change the client contract.

## Consequences

- An event with contract errors reports only those; its rule failures appear once the contract errors are fixed.
- Clients must send `occurredAt` within the limits; events queued offline for more than 7 days are invalid.
- The accepted currencies can change slightly when the JDK is upgraded; withdrawn codes such as `DEM` are accepted.
- Events already on `behavioural.valid` are not rechecked.
