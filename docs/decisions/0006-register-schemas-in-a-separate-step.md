# 0006: Register schemas separately from services

## Context

An event's `schemaVersion` maps to its registry version. Registration must preserve that mapping and reject incompatible changes before services use them.

## Decision

Run schema registration as a dedicated Gradle task before deploying new producers. Register the envelope before dependent schemas, enforce `BACKWARD` compatibility, and check before registration that each file will receive its declared version. A mismatch stops registration without adding a stray version. Services only read schemas.

## Why

Registration at service startup would require write access and could discover incompatibility during deployment. One path can be reused locally, in CI, and in tests.

## Consequences

- Register new schemas before clients send those versions.
- Schema files are append-only and must have no version gaps.
- The registration step needs registry write access; services do not.
