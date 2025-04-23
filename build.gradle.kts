plugins {
    id("java")
    id("com.github.ben-manes.versions") version "0.51.0"
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    // Apply the following configurations ONLY to subprojects that have the 'java' plugin applied
    pluginManager.withPlugin("java") {
        tasks.withType<JavaCompile>().configureEach {
            // Setting options.release is generally preferred over toolchain for compatibility
            // It configures both compilation target and runtime compatibility.
            options.release.set(21)
        }
    }

    // You can add other configurations here that apply to ALL subprojects,
    // regardless of plugins.
}
