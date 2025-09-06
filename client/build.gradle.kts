plugins {
    id("java")
    id("application")
}
repositories { mavenCentral() }
dependencies {
    implementation(project(":common"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}
application {
    mainClass.set("rt.client.app.ClientApp")
}
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
