plugins {
    id("java")
    id("application")
    id("com.google.protobuf") version "0.9.5"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.goodmem"
version = "0.1.0"

dependencies {
    // Javalin
    implementation("io.javalin:javalin:6.6.0")
    
    // gRPC
    implementation("io.grpc:grpc-netty-shaded:1.72.0")
    implementation("io.grpc:grpc-protobuf:1.72.0")
    implementation("io.grpc:grpc-stub:1.72.0")
    implementation("io.grpc:grpc-inprocess:1.72.0")
    
    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3")
    
    // Protocol Buffer utilities
    implementation("com.google.protobuf:protobuf-java-util:3.25.5")
    
    // Annotation for generated code
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    
    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

application {
    mainClass.set("com.goodmem.Main")
}

sourceSets {
    main {
        java {
            srcDirs("build/generated/source/proto/main/java")
            srcDirs("build/generated/source/proto/main/grpc")
        }
        proto {
            srcDir("../proto")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.72.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Main-Class" to application.mainClass.get()
            )
        )
    }
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveBaseName.set("goodmem-server")
    archiveClassifier.set("")
    archiveVersion.set("")
}
