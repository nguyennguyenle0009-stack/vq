plugins {
    id("java")
    application
}
repositories { mavenCentral() }
dependencies { implementation(project(":lib")) }
application { mainClass.set("rt.client.MainClient") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }