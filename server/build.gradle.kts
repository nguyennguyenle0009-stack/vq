plugins {
    id("java")
    application
}
repositories { 
	mavenCentral() 
}
dependencies {
    implementation(project(":common"))
    implementation("io.netty:netty-all:4.1.112.Final")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}
application {
    mainClass.set("rt.server.main.MainServer")
}
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
