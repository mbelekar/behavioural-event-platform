plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "behavioural-event-platform"

include("event-contracts", "event-collector", "schema-registration", "event-validator", "integration-tests")
