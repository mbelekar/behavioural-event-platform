# Spec: Event contracts

## Purpose

Define what a well-formed behavioural event is, for each supported event type and version, so that producers know what to send and consumers of the trusted stream know what they will receive. Contracts are JSON Schemas held in Schema Registry, and they can evolve without breaking existing producers.

## Requirements

**Supported event types**

- **R1.** The platform supports these event types:

  | `eventType` | Versions | Required payload fields | Optional payload fields |
  |---|---|---|---|
  | `page_viewed` | 1 | `pageUrl` (non-empty string) | `referrer` (string) |
  | `product_viewed` | 1, 2 | `productId` (non-empty string) | `category` (string); v2 only: `recommendationSource` (string) |
  | `search_performed` | 1 | `query` (non-empty string) | `resultCount` (integer ≥ 0) |
  | `button_clicked` | 1 | `buttonId` (non-empty string) | `pageUrl` (string) |
  | `checkout_started` | 1 | `cartId` (non-empty string) | `itemCount` (integer ≥ 1) |
  | `purchase_completed` | 1 | `orderId` (non-empty string), `amount` (number), `currency` (three upper-case letters, e.g. `AUD`) | — |

**The envelope** (the same for every type)

- **R2.** Every event has these fields:

  | Field | Rule |
  |---|---|
  | `eventId`, `eventType`, `source` | Non-empty string, required |
  | `correlationId` | Non-empty string, required |
  | `schemaVersion` | Integer ≥ 1, required |
  | `occurredAt`, `receivedAt` | ISO-8601 date-time string, required |
  | `userId`, `sessionId` | String or `null`, optional |
  | `payload` | Object, required |

- **R3.** An event's `eventType` must match the schema it is validated against.

**Content rules**

- **R4.** Unknown fields are not allowed, either at the top level or inside `payload`.
- **R5.** Optional fields must be **omitted** when they have no value. `null` is allowed only where a contract says so (`userId` and `sessionId`).

**Versioning**

- **R6.** Each event type is versioned independently. An event's `schemaVersion` identifies the contract version it claims to follow, and it matches that type's version number in Schema Registry.
- **R7.** Contract versions are append-only. A registered version is never changed or removed, and versions are numbered 1, 2, 3, … without gaps.
- **R8.** A new version must be **backward compatible** with the previous one:
  - **Allowed:** adding an optional field.
  - **Rejected:** adding a required field, or changing a field's type.
- **R9.** Older versions stay valid after a newer one is registered. Producers can migrate at their own pace.
- **R10.** `behavioural_envelope` is reserved for the shared envelope contract and is never a valid `eventType`.

## Acceptance criteria

- **AC1.** Given an event shaped like real collector output (for example with `"userId": null` and a `receivedAt` with microseconds), when it follows its type's contract, then it conforms, for every type and version in R1.
- **AC2.** Given a `product_viewed` v1 event containing `recommendationSource`, then it does not conform. The field exists only in v2.
- **AC3.** Given a `product_viewed` event without `productId`, then it does not conform.
- **AC4.** Given an event with an unknown top-level field, then it does not conform.
- **AC5.** Given `product_viewed` v2 is registered, when a v1 event arrives, then it still conforms to v1.
- **AC6.** Given a proposed new `product_viewed` version that changes `recommendationSource` to an integer, or adds `category` as required, then Schema Registry rejects it as incompatible.

## Failure and edge-case behaviour

| Situation | Behaviour |
|---|---|
| Optional payload field sent as `null` (e.g. `"category": null`) | Does not conform (`INVALID_TYPE`) |
| Producer sends a field that its version's contract doesn't define | Does not conform (`UNKNOWN_FIELD`), even if a later version defines it |
| Producer uses a `schemaVersion` before it's registered | The event is treated as `UNKNOWN_SCHEMA_VERSION` until registration runs (see [schema registration](schema-registration.md)) |
| Neither `userId` nor `sessionId` present | Allowed by the envelope contract. Ingestion already requires one of them ([event ingestion](event-ingestion.md) R6). |

## Discrepancies

- The payload fields for `page_viewed`, `search_performed`, `button_clicked` and `checkout_started`, plus `currency` for `purchase_completed`, are **not specified in Design.md**. Design.md names only `productId`, `category`, `recommendationSource`, `orderId` and `amount`. The fields in R1 are those implemented in `event-contracts/schemas/`, which is authoritative.
- Design.md section 7.2 lists "product_viewed must contain productId" and "purchase_completed must contain orderId and amount" as **business** rules. In the implementation they are enforced by the schemas, as required fields.
