# 0009: Test the pipeline with service jars

## Context

The HTTP-to-validated-topic flow crosses independently deployed services. Single-service tests cannot prove that their packaged configuration and event contracts work together.

## Decision

Keep cross-service tests in `integration-tests`. Run the collector and validator as separate packaged Spring Boot jars against Kafka and Schema Registry in Testcontainers. Exercise the collector over HTTP and inspect Kafka output. Keep narrower tests in each service module.

## Why

Separate JVMs use the same environment-variable configuration as deployed services and avoid classpath collisions between their `application.yml` files. Building service images would be slower for little additional confidence at this stage.

## Consequences

- Tests cover the real jars and their integration boundary.
- Builds and test runs take longer and require Docker.
- Failures may require inspecting captured service logs.
