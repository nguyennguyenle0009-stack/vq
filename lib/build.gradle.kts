plugins { `java-library` }
repositories { mavenCentral() }
dependencies {
    api("com.google.code.gson:gson:2.11.0") // ví dụ: DTO/JSON
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }