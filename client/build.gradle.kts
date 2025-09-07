plugins {
    id("java")
    id("application")
}

val jacksonVersion: String by rootProject.extra
val slf4jVersion: String by rootProject.extra
val logbackVersion: String by rootProject.extra

dependencies {
    implementation(project(":common"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
}

application {
    mainClass.set("rt.client.app.ClientApp")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
