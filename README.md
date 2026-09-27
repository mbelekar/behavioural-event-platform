# Behavioural Event Platform

A Java/Spring Boot platform that accepts behavioural events, validates them against contracts in Schema Registry, and publishes a trusted stream to Kafka.

The main idea is simple: producers can send events without being coupled to downstream validation, while consumers get a stream they can trust.

## Status

HTTP ingestion and asynchronous schema validation are implemented.

| Component | Status |
| --- | --- |
| Event Collector | ✅ Implemented |
| Schema Registry integration | ✅ Implemented |
| Event Validator | ✅ Implemented |
| Valid / invalid event streams | ✅ Implemented |
| Business validation and DLQ | 📋 Planned |
| Cross-cluster Event Router | 📋 Planned |
| Observability | 📋 Planned |

See [`docs/Design.md`](docs/Design.md) for the full design.

## Why this exists

Behavioural events arriving from web and mobile clients may be malformed, outdated or incompatible with the expected contract.

Making every downstream consumer defend against those problems duplicates validation logic and makes it difficult to know which data can be trusted.

This platform creates a clear boundary:

```text
behavioural.raw
      ↓
  validation
      ↓
behavioural.valid
```

Anything on `behavioural.valid` conforms to a registered schema.

Invalid producer data goes to `behavioural.invalid`. Infrastructure failures are retried rather than being mistaken for bad data.

## Architecture

```mermaid
flowchart LR
    CLIENT[Web / Mobile] -->|POST /v1/events| COLLECTOR[Event Collector]
    COLLECTOR --> RAW[(behavioural.raw)]

    RAW --> VALIDATOR[Event Validator]
    REGISTRY[Schema Registry] --> VALIDATOR

    VALIDATOR -->|valid| VALID[(behavioural.valid)]
    VALIDATOR -->|invalid| INVALID[(behavioural.invalid)]

    VALID -. planned .-> ROUTER[Event Router<br/>planned]
    ROUTER -. planned .-> TARGET[(Kafka Cluster B)]
```

The collector writes to Kafka before acknowledging the request. Validation then happens asynchronously.

Schema Registry holds versioned JSON Schema contracts for each event type and enforces compatibility as those contracts evolve.

## Run locally

Requirements:

- Docker
- JDK 17+

Build and run the tests:

```bash
./gradlew build
```

Start Kafka and Schema Registry:

```bash
docker compose up -d --wait
```

Register the event schemas:

```bash
./gradlew :schema-registration:registerSchemas
```

Run the collector and validator:

```bash
./gradlew :event-collector:bootRun
./gradlew :event-validator:bootRun
```

## Try it

Send a valid event:

```bash
curl -X POST localhost:8080/v1/events \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "demo-1",
    "eventType": "product_viewed",
    "schemaVersion": 2,
    "occurredAt": "2026-09-27T01:23:31Z",
    "source": "web",
    "userId": "user-123",
    "payload": {
      "productId": "SKU-981",
      "recommendationSource": "home"
    }
  }'
```

The request returns `202 Accepted`.

The event flows asynchronously through:

```text
HTTP
  → behavioural.raw
  → Event Validator
  → behavioural.valid
```

An event that does not conform to its registered schema is instead published to `behavioural.invalid` with structured validation errors.

## Testing

The project uses Testcontainers to test against real Kafka and Schema Registry instances rather than mocking the infrastructure.

```bash
./gradlew build
```

The tests cover ingestion, schema registration and evolution, valid and invalid events, and dependency failure behaviour.

## Project structure

```text
event-collector/       HTTP → behavioural.raw
event-validator/       raw → schema validation → valid / invalid
event-contracts/       event models and JSON Schemas
schema-registration/   Schema Registry registration
integration-tests/     pipeline tests running the service jars
docs/                  design, specs and architecture decisions
docker-compose.yml     local Kafka, Schema Registry and topics
```

## Documentation

- [`Design.md`](docs/Design.md) — architecture and system behaviour
- [`docs/decisions`](docs/decisions/) — significant architecture decisions