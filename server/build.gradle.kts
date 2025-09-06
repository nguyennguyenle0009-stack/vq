plugins {
    id("java")
    application
}
repositories { mavenCentral() }
dependencies { implementation(project(":lib")) }
application { mainClass.set("game.server.MainServer") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
