# Spec: Event contracts

## Purpose

Define what a well-formed event is for each event type and version, so producers know what to send and consumers of `behavioural.valid` know what they receive. Contracts are JSON Schemas in Schema Registry and can evolve without breaking existing producers.

## Requirements

- **R1.** Supported event types and their payloads:

  | `eventType` | Versions | Required payload fields | Optional payload fields |
  | --- | --- | --- | --- |
  | `page_viewed` | 1 | `pageUrl` (non-empty string) | `referrer` (string) |
  | `product_viewed` | 1, 2 | `productId` (non-empty string) | `category` (string); v2 adds `recommendationSource` (string) |
  | `search_performed` | 1 | `query` (non-empty string) | `resultCount` (integer ≥ 0) |
  | `button_clicked` | 1 | `buttonId` (non-empty string) | `pageUrl` (string) |
  | `checkout_started` | 1 | `cartId` (non-empty string) | `itemCount` (integer ≥ 1) |
  | `purchase_completed` | 1 | `orderId` (non-empty string), `amount` (number), `currency` (three upper-case letters) | — |

- **R2.** Every event shares an envelope:
  - required non-empty strings: `eventId`, `eventType`, `source`, `correlationId`;
  - required `schemaVersion`, an integer ≥ 1;
  - required `occurredAt` and `receivedAt`, ISO-8601 date-times;
  - optional `userId` and `sessionId`, a string or `null`;
  - required `payload`, an object.
- **R3.** An event's `eventType` must match its contract.
- **R4.** Unknown fields are rejected, at the top level and in `payload`. Optional fields are omitted when empty; `null` is allowed only for `userId` and `sessionId`.
- **R5.** Each event type is versioned independently. `schemaVersion` equals the type's Schema Registry version.
- **R6.** Versions are append-only and numbered without gaps.
- **R7.** A new version must be backward compatible: adding an optional field is allowed; adding a required field or changing a field's type is rejected.
- **R8.** Older versions remain valid after a newer one is registered.
- **R9.** `behavioural_envelope` is reserved and is never a valid `eventType`.

## Acceptance criteria

- **AC1.** Collector-shaped events (e.g. `"userId": null`, `receivedAt` with microseconds) conform, for every type and version in R1.
- **AC2.** A `product_viewed` v1 event containing `recommendationSource` does not conform.
- **AC3.** A `product_viewed` event without `productId` does not conform.
- **AC4.** An event with an unknown top-level field does not conform.
- **AC5.** A `product_viewed` v1 event still conforms after v2 is registered.
- **AC6.** A new `product_viewed` version that changes `recommendationSource` to an integer, or makes `category` required, is rejected as incompatible.

## Failure and edge cases

| Situation | Behaviour |
| --- | --- |
| Optional payload field sent as `null` | Does not conform (`INVALID_TYPE`). |
| Field from a later version sent with an earlier `schemaVersion` | Does not conform (`UNKNOWN_FIELD`). |
| `schemaVersion` used before it is registered | `UNKNOWN_SCHEMA_VERSION` until registration runs. |
| Neither `userId` nor `sessionId` | Allowed by the contract; ingestion already requires one of them. |

## Discrepancies

- `Design.md` shows payload fields only for `product_viewed`. The other types' payloads in R1 come from `event-contracts/schemas/`, which is authoritative.
