import org.gradle.api.plugins.JavaPluginExtension

// Không cần plugins{} ở root

allprojects {
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")

    // Thay cho `java { toolchain { ... } }`
    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Bỏ libs.*, dùng toạ độ chuỗi rõ ràng
    dependencies {
        add("implementation", "org.slf4j:slf4j-api:2.0.13")
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")
    }
}
