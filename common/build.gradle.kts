plugins {
    id("java-library")
}

val jacksonVersion: String by rootProject.extra

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
