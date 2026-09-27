plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
}

dependencies {
    implementation(project(":schema-registration"))
    implementation(libs.spring.boot.starter.kafka)

    testImplementation(testFixtures(project(":schema-registration")))
    testImplementation(libs.spring.boot.starter.kafka.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.json.schema.serializer)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    systemProperty("schemas.dir", rootDir.resolve("event-contracts/schemas").absolutePath)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}
