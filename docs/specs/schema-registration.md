# Spec: Schema registration

## Purpose

Publish the event contracts to Schema Registry in a repeatable step that keeps each registry version aligned with its `schemaVersion` and rejects breaking changes before producers or services depend on them.

## Requirements

- **R1.** `./gradlew :schema-registration:registerSchemas` registers against `http://localhost:8081`; `-PschemaRegistryUrl=<url>` targets another registry.
- **R2.** The envelope is registered first, then event types alphabetically, each type's versions in ascending order.
- **R3.** Each subject's compatibility is set to `BACKWARD` before registration.
- **R4.** Before registering a file, its version must line up with the registry:
  - a schema already held must be at the file's version;
  - a new schema is registered only if the subject's latest version is one lower (or the subject doesn't exist and the file is `v1.json`).

  Otherwise registration fails and leaves the registry unchanged for that file.
- **R5.** Re-running with unchanged schemas creates no new versions and reports the same IDs.
- **R6.** An incompatible change is rejected by Schema Registry and fails registration.
- **R7.** Each registered schema is printed as `<subject> v<N> -> id <schemaId>`.
- **R8.** Registration stops at the first failure; earlier schemas stay registered.

## Acceptance criteria

- **AC1.** On an empty registry, 8 schemas are registered in this order: `behavioural_envelope` v1, `button_clicked` v1, `checkout_started` v1, `page_viewed` v1, `product_viewed` v1 and v2, `purchase_completed` v1, `search_performed` v1.
- **AC2.** A second run prints the same subjects, versions and IDs, and `product_viewed` still has versions `[1, 2]`.
- **AC3.** Every subject's compatibility is `BACKWARD`.
- **AC4.** An incompatible schema file (e.g. a string field changed to integer) fails with HTTP `409`.
- **AC5.** If the registry holds a different version 1 of a subject, registering that subject's `v1.json` fails with a message naming the file and the registry's latest version, and the subject still has only version 1.
- **AC6.** With Schema Registry unreachable, registration fails with a connection error and registers nothing.

## Failure and edge cases

| Situation | Behaviour |
| --- | --- |
| A version file is skipped (`v1.json`, then `v3.json`) | Fails at `v3.json`; nothing is registered for it. |
| An already-registered file is edited | Fails for that file; nothing is registered for it. |
| A schema was registered by hand | Detected as a mismatch at the next run; the registry isn't changed further. |
| A new version isn't registered yet | The validator marks events using it `UNKNOWN_SCHEMA_VERSION`. |
| Failure part-way through | Earlier schemas stay registered. Fix the cause and re-run. |
