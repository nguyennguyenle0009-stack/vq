plugins {
    id("java-library")
}

repositories { 
	mavenCentral() 
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}


java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}