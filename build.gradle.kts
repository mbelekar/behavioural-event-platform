plugins {
    base
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

spotless {
    java {
        target("*/src/**/*.java")
        palantirJavaFormat(libs.versions.palantir.java.format.get())
    }
}
