plugins {
    id("java")
    application
}
repositories { mavenCentral() }
dependencies { 
}
application { mainClass.set("rt.client.MainClient") }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }