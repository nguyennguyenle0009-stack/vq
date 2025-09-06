plugins {
    id("java")
    application
}
repositories { mavenCentral() }
dependencies {
    implementation("io.netty:netty-all:4.1.112.Final")
}
application { mainClass.set("game.server.MainServer") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
