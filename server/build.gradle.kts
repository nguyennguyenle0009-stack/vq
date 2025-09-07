plugins {
    id("java")
    application
}

val jacksonVersion: String by rootProject.extra
val nettyVersion: String by rootProject.extra
val slf4jVersion: String by rootProject.extra
val logbackVersion: String by rootProject.extra

dependencies {
    implementation(project(":common"))
    implementation("io.netty:netty-all:$nettyVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    runtimeOnly("ch.qos.logback:logback-classic:$logbackVersion")
}

application {
    mainClass.set("rt.server.main.MainServer")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
