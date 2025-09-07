import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile

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
    
    // QUAN TRỌNG: ép mã hóa UTF-8
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    // Bỏ libs.*, dùng toạ độ chuỗi rõ ràng
    dependencies {
        add("implementation", "org.slf4j:slf4j-api:2.0.13")
        add("testImplementation", "org.junit.jupiter:junit-jupiter:5.10.2")
    }
}
