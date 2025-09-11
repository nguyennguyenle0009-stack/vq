plugins { 
	application 
	id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {

    implementation(platform("io.netty:netty-bom:4.1.112.Final"))

    implementation("io.netty:netty-handler")
    implementation("io.netty:netty-codec-http")
    implementation("io.netty:netty-transport")
    implementation("io.netty:netty-buffer")
    
    implementation(project(":common"))
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("rt.server.main.MainServer")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    mergeServiceFiles()
}