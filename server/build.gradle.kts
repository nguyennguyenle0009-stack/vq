plugins { application }

dependencies {
    implementation(project(":common"))
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("io.netty:netty-all:4.1.110.Final")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("rt.server.main.MainServer") // sửa FQN nếu khác
}
