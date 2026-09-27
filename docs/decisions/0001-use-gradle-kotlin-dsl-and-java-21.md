# 0001: Use Gradle and Java 21

## Context

This multi-module Spring Boot project needs consistent dependencies and a common Java target. The project owner prefers Gradle.

## Decision

Use the Gradle wrapper with Kotlin DSL and a version catalog. Target Java 21 through a toolchain and use Spring Boot 4.1 for services.

## Why

The wrapper and toolchain make builds reproducible, while the catalog keeps dependency versions together. A convention plugin would add complexity before the build needs it. Maven did not match the project preference.

## Consequences

- The first build may download Gradle and JDK 21.
- Spring Boot 4 examples differ from earlier versions, including Jackson 3 package names.
