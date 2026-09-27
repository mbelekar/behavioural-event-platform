# 0009 — Test the pipeline with service jars

## Decision

Tests that cross service boundaries live in the `integration-tests` module. They run each service's packaged Spring Boot jar as its **own JVM process**, against Kafka and Schema Registry started with Testcontainers.

Each service's own tests (unit, slice and single-service Testcontainers tests) stay in that service's module.

## Context

Design.md section 21 asks for integration tests that exercise complete flows, for example HTTP → `behavioural.raw` → validator → `behavioural.valid`. That flow crosses two independently deployed services, each with its own configuration (`application.yml` at the root of its classpath) and its own settings through environment variables.

## How it works

- Gradle resolves the runnable jars of `event-collector` and `event-validator` through the Spring Boot plugin's `bootArchives` configuration, requested by name, and passes their paths to the test JVM.
- Before the tests, the shared test fixture starts Kafka and Schema Registry, creates the topics and registers the schemas.
- Each service is started with `java -jar` and configured only through the environment variables used outside tests (`KAFKA_BOOTSTRAP_SERVERS`, `SCHEMA_REGISTRY_URL`, `SERVER_PORT`).
- Tests talk to the collector over real HTTP and read results from Kafka. Service output is written to `integration-tests/build/service-logs/` for diagnosing failures.

## Alternatives considered

- **Several Spring application contexts in one test JVM.** Rejected: both services have `application.yml` at the root of the classpath, so only one of them would be loaded, and one service would silently run with the other's settings. Working around that would mean renaming configuration files, or adding test-only configuration that production doesn't use.
- **Service containers built with `bootBuildImage`.** Rejected for now: building images is much slower and needs image builds in CI, for little extra confidence at this stage.
- **Only single-service tests.** Rejected: nothing would check that the collector's real output is accepted by the validator's real contract. For example, the collector writes `"sessionId": null`, which the envelope schema must allow.

## Consequences and trade-offs

- The tests exercise the real packaged artifacts and their real configuration, including environment-variable overrides.
- Each run builds the service jars and starts two extra JVMs, which is slower than in-process tests. The pipeline tests take about 80 seconds.
- When a test fails, the cause may be in a service log rather than the test output.
- The pipeline tests need Docker, like the other Testcontainers tests.
