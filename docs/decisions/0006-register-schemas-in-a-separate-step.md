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
- **fails if the registry assigns a version different from the file name** (`v<N>.json`).

## Context

ADR 0005 maps an event's `schemaVersion` directly to the registry's version number for that subject. That only holds if versions are registered in order, without gaps. Schema Registry assigns version numbers itself and can't be told which number to use, so the mapping has to be checked at registration time.

Design.md section 19 gives each service the least privilege it needs. The validator needs to *read* schemas, not write them.

## How it works

- `SchemaFiles.load` reads `event-contracts/schemas/`, envelope first (other subjects reference it), then subjects alphabetically, versions ascending.
- A reference is derived from each `$ref` name (`behavioural_envelope/v1.json` → subject `behavioural_envelope`, version 1).
- `SchemaRegistration.register` sets compatibility, registers each schema, then asks the registry which version it holds and compares that with the file name.
- **Re-running is safe.** Registering a schema identical to an existing version returns the existing ID and creates no new version.
- **An incompatible change stops the run.** Schema Registry rejects it with `409`, before any service is deployed with it.
- **A version mismatch stops the run** with a message naming the file and the version the registry assigned, for example after a file was skipped or a schema was registered by hand.

The code lives in its own `schema-registration` module rather than in `event-contracts`. The collector depends on `event-contracts`, and the Schema Registry client (with Avro, Guava and more) has no place in the collector's jar.

## Alternatives considered

- **Services register schemas at startup.** Rejected: the validator would need write access to the registry, an incompatible schema would only be discovered while deploying a service, and several instances would race to register.
- **A Docker Compose init container using `curl`.** Rejected: it only covers local development. Tests and CI would need a second mechanism that could drift from it.
- **Registration code inside `event-contracts`.** Rejected: see above, it would add the registry client to the collector.

## Consequences and trade-offs

- Registration must run before any producer sends a new schema version. Until it does, the validator marks those events `UNKNOWN_SCHEMA_VERSION` and publishes them to `behavioural.invalid`.
- The registration job needs Schema Registry write credentials, which are kept away from services.
- Schema files are append-only: never edit a registered version, delete one, or leave a gap.
