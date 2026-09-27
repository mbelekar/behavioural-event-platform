# Spec: Schema registration

## Purpose

Publish the event contracts (see [event contracts](event-contracts.md)) to Schema Registry in a controlled, repeatable step. This keeps each registry version aligned with its `schemaVersion`, and stops breaking changes before any producer or service depends on them. Services never register schemas themselves.

## Requirements

- **R1.** Registration is run as a single command:
  - `./gradlew :schema-registration:registerSchemas` targets `http://localhost:8081`.
  - `-PschemaRegistryUrl=<url>` targets another registry.
- **R2.** The envelope contract is registered first, then each event type alphabetically, each type's versions in ascending order.
- **R3.** Every subject's compatibility level is set to `BACKWARD` before its schemas are registered.
- **R4.** Each file's version must line up with the registry **before** anything is registered for it:
  - a schema the registry already holds must be registered at exactly its file's version;
  - a new schema is registered only if the subject's latest version is exactly one lower than the file's (or the subject doesn't exist yet and the file is `v1.json`).

  Otherwise registration fails, and the registry is left unchanged for that file.
- **R5.** Registration is idempotent. Re-running it with unchanged schemas creates no new versions and reports the same schema IDs.
- **R6.** An incompatible schema change is rejected by Schema Registry, and registration fails.
- **R7.** On success, one line is printed per schema: `<subject> v<N> -> id <schemaId>`.
- **R8.** Registration stops at the first failure. Schemas registered before that failure stay registered.

## Acceptance criteria

- **AC1.** Given an empty Schema Registry, when registration runs, then 8 schemas are registered, in order:

  | Subject | Version |
  |---|---|
  | `behavioural_envelope` | 1 |
  | `button_clicked` | 1 |
  | `checkout_started` | 1 |
  | `page_viewed` | 1 |
  | `product_viewed` | 1 |
  | `product_viewed` | 2 |
  | `purchase_completed` | 1 |
  | `search_performed` | 1 |

- **AC2.** Given registration has already run, when it runs again, then it prints the same subjects, versions and IDs, and `product_viewed` still has exactly versions `[1, 2]`.
- **AC3.** Given registration has run, then every subject's compatibility level is `BACKWARD`.
- **AC4.** Given a schema file that is incompatible with the latest registered version (for example, a field changed from string to integer), when registration runs, then it fails with an HTTP `409` from Schema Registry.
- **AC5.** Given the registry already holds a different version 1 of a subject, when registration runs with that subject's `v1.json`, then it fails with a message naming the file and the registry's latest version, and the subject still has only version 1.
- **AC6.** Given Schema Registry is unreachable, when registration runs, then it fails with a connection error and registers nothing.

## Failure and edge-case behaviour

| Situation | Behaviour |
|---|---|
| A version file is skipped (e.g. `v1.json`, then `v3.json`) | Registration fails at `v3.json` and registers nothing for it (R4) |
| An already-registered file is edited | Its content is now a new schema that doesn't match its version, so registration fails and registers nothing for it |
| Someone registered a schema by hand | Detected as a version mismatch at the next run; the registry isn't changed further |
| Registration hasn't run for a newly added version | Events using that version are marked `UNKNOWN_SCHEMA_VERSION` by the validator until it does |
| Failure part-way through | Earlier schemas stay registered. Fix the cause and re-run; already-registered schemas are unaffected (R5). |
