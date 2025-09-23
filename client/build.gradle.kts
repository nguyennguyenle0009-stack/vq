plugins { 
	java 
	id("application")
}

dependencies {
    implementation(project(":common"))
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("rt.client.app.ClientApp")
    
    // JVM flags khi chạy :client:run
    applicationDefaultJvmArgs = listOf(
        "-Xms1g", "-Xmx1g",
        "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=20"
    )
}