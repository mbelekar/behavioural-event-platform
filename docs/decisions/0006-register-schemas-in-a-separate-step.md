# 0006 — Register schemas in a separate step

## Decision

Schemas in `event-contracts/schemas/` are registered in Schema Registry by a dedicated step:

```bash
./gradlew :schema-registration:registerSchemas                       # default http://localhost:8081
./gradlew :schema-registration:registerSchemas -PschemaRegistryUrl=…  # other environments
```

The same code runs in CI and in the test fixtures. **Services never register schemas.** The validator only reads from the registry.

For each schema file, registration:

- sets the subject's compatibility to `BACKWARD`,
- registers the files in order,
- **checks, before registering, that the file will get its file-name version** (`v<N>.json`), and fails without changing the registry if it won't.

## Context

ADR 0005 maps an event's `schemaVersion` directly to the registry's version number for that subject. That only holds if versions are registered in order, without gaps. Schema Registry assigns version numbers itself and can't be told which number to use, so the mapping has to be checked at registration time.

Design.md section 19 gives each service the least privilege it needs. The validator needs to *read* schemas, not write them.

## How it works

- `SchemaFiles.load` reads `event-contracts/schemas/`, envelope first (other subjects reference it), then subjects alphabetically, versions ascending.
- A reference is derived from each `$ref` name (`behavioural_envelope/v1.json` → subject `behavioural_envelope`, version 1).
- `SchemaRegistration.register` sets compatibility, then checks each file against the registry **before** registering it:
  - if the registry already holds that exact schema, it must be at the file's version;
  - otherwise the subject's latest version must be exactly one lower than the file's (or the subject must not exist yet, for `v1.json`).

  Only then is the schema registered. The check comes first because Schema Registry assigns a version as soon as a schema is registered, and never takes it back: checking afterwards would leave a stray version behind on every mismatch.
- **Re-running is safe.** A schema that is already registered at its file's version is reported with its existing ID, and nothing new is registered.
- **An incompatible change stops the run.** Schema Registry rejects it with `409`, before any service is deployed with it.
- **A version mismatch stops the run and leaves the registry unchanged** for that file. The message names the file and the registry's latest version. This covers a skipped file, an edited already-registered file, and a schema registered by hand.

The code lives in its own `schema-registration` module rather than in `event-contracts`. The collector depends on `event-contracts`, and the Schema Registry client (with Avro, Guava and more) has no place in the collector's jar.

## Alternatives considered

- **Services register schemas at startup.** Rejected: the validator would need write access to the registry, an incompatible schema would only be discovered while deploying a service, and several instances would race to register.
- **A Docker Compose init container using `curl`.** Rejected: it only covers local development. Tests and CI would need a second mechanism that could drift from it.
- **Registration code inside `event-contracts`.** Rejected: see above, it would add the registry client to the collector.

## Consequences and trade-offs

- Registration must run before any producer sends a new schema version. Until it does, the validator marks those events `UNKNOWN_SCHEMA_VERSION` and publishes them to `behavioural.invalid`.
- The registration job needs Schema Registry write credentials, which are kept away from services.
- Schema files are append-only: never edit a registered version, delete one, or leave a gap.
