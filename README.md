# Behavioural Event Platform

Java/Spring Boot platform for ingesting, validating and routing behavioural events with Kafka.
See [docs/Design.md](docs/Design.md) for the design and [docs/decisions](docs/decisions) for ADRs.

## Prerequisites

- Docker (Engine 25+)
- A JDK 17+ to run Gradle. The build downloads JDK 21 automatically via Gradle toolchains.

## Run locally

    docker compose up -d                       # Kafka Cluster A + topics
    ./gradlew :event-collector:bootRun         # http://localhost:8080

Send an event:

    curl -i -X POST localhost:8080/v1/events -H 'Content-Type: application/json' \
      -d '{"eventId":"01K5R4F8W8J5Z8XJH0N6F4P2C1","eventType":"product_viewed","schemaVersion":2,"occurredAt":"2026-09-27T01:23:31Z","source":"web","userId":"user-123","payload":{"productId":"SKU-981"}}'

Read it back from `behavioural.raw`:

    docker compose exec kafka-a /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
      --topic behavioural.raw --from-beginning --formatter-property print.key=true --max-messages 1

## Test

    ./gradlew build     # unit + Testcontainers integration tests (needs Docker)

## Modules

| Module | Responsibility |
|---|---|
| `event-contracts` | Shared event envelope |
| `event-collector` | `POST /v1/events` → `behavioural.raw` |
