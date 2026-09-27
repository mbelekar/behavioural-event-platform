plugins {
    java
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

val serviceJars = configurations.create("serviceJars") {
    isCanBeConsumed = false
}

dependencies {
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(testFixtures(project(":schema-registration")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    serviceJars(project(":event-collector", "bootArchives"))
    serviceJars(project(":event-validator", "bootArchives"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("schemas.dir", rootDir.resolve("event-contracts/schemas").absolutePath)
    val jars: FileCollection = serviceJars
    inputs.files(jars)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dservice.jars=" + jars.files.joinToString(File.pathSeparator) { it.absolutePath })
    })
}
