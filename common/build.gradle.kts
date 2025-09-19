plugins { java }

dependencies {
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
}

tasks.register<JavaExec>("runPreview") {
    group = "application"
    mainClass.set("rt.tools.MapAsciiPreview")
    classpath = sourceSets["main"].runtimeClasspath
    // cho phép truyền seed: ./gradlew :common:runPreview -Pseed=12345
    project.findProperty("seed")?.toString()?.let { args(it) }
}