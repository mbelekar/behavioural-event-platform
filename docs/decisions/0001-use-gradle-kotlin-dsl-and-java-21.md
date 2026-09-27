# 0001 — Use Gradle (Kotlin DSL) and Java 21

## Decision

Build with the Gradle wrapper (9.7.1), using the Kotlin DSL and a version catalog.
Target Java 21 through a Gradle toolchain, provisioned automatically by the foojay resolver.
Use Spring Boot 4.1 for services.

## Context

The original design (Design.md section 23) showed a Maven `pom.xml`. The project owner prefers Gradle.

The platform is a multi-module build (contracts plus one module per service), so dependency versions need a single source of truth.

## How it works

- `settings.gradle.kts` lists the modules and applies the foojay toolchain resolver.
- `gradle/libs.versions.toml` holds every plugin and library coordinate. Library entries without a version take it from the Spring Boot BOM.
- Each module declares `languageVersion = JavaLanguageVersion.of(21)`. Gradle downloads a JDK 21 if none is installed.
- Service modules apply the Spring Boot and dependency-management plugins. Library modules (for example `event-contracts`) import the Boot BOM as a Gradle `platform`, so they use the same versions as the services.

## Alternatives considered

- **Maven**, as in the original design. Rejected on the owner's preference.
- **Groovy DSL.** Rejected: weaker IDE support and no type-safe accessors for the version catalog.
- **A `buildSrc` convention plugin** for the shared toolchain and repositories setup. Deferred: with two modules the duplication is a few lines. Revisit when a third service module arrives.
- **Java 17.** Rejected: 21 is the current LTS and adds virtual threads at no extra cost.

## Consequences and trade-offs

- Contributors need only a JDK (17 or later) to launch Gradle. The build supplies JDK 21 itself.
- Spring Boot 4 brings Jackson 3 (`tools.jackson.*` packages) and moves test-slice annotations into per-technology modules, so Spring Boot 3 examples don't copy across directly.
- The first build is slower because it downloads the Gradle distribution and a JDK.
