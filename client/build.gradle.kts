plugins { 
	java 
	id("application")
}

dependencies {
    implementation(project(":common"))
    implementation("org.slf4j:slf4j-api:2.0.13")

    // WebSocket client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // (tùy chọn) log HTTP nếu cần
    // implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON cho ObjectMapper ở client
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("rt.client.app.ClientApp")
}