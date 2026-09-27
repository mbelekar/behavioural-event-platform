plugins {
    `java-library`
    jacoco
    `java-test-fixtures`
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
    api(platform(libs.spring.boot.dependencies))
    api(libs.schema.registry.client)
    api(libs.json.schema.provider)

    testFixturesApi(platform(libs.spring.boot.dependencies))
    testFixturesApi(libs.testcontainers.kafka)
    testFixturesApi(libs.kafka.clients)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    systemProperty("schemas.dir", rootDir.resolve("event-contracts/schemas").absolutePath)
}

tasks.register<JavaExec>("registerSchemas") {
    group = "schema registry"
    description = "Registers event-contracts/schemas in Schema Registry (-PschemaRegistryUrl=..., default http://localhost:8081)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.behaviouralplatform.schemas.SchemaRegistration"
    args(
        providers.gradleProperty("schemaRegistryUrl").getOrElse("http://localhost:8081"),
        rootDir.resolve("event-contracts/schemas").absolutePath)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}
