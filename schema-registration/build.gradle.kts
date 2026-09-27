plugins {
    `java-library`
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

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("schemas.dir", rootDir.resolve("event-contracts/schemas").absolutePath)
}
