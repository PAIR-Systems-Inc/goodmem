plugins {
    id("java")
    id("application")
    id("com.google.protobuf") version "0.9.5"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.goodmem"
version = "0.1.0"

val javalinVersion = "6.6.0"
val jacksonVersion = "2.18.3"
val protobufVersion = "4.30.2"
val grpcVersion = "1.72.0"

dependencies {
    // Javalin
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:$javalinVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinVersion")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.30")
    annotationProcessor("io.javalin.community.openapi:openapi-annotation-processor:$javalinVersion")

    // MinIO official client.
    implementation("io.minio:minio:8.5.17")
    
    // gRPC
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-inprocess:$grpcVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")
    
    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    
    // Protocol Buffer utilities
    implementation("com.google.protobuf:protobuf-java-util:$protobufVersion")
    
    // Annotation for generated code
    implementation("jakarta.annotation:jakarta.annotation-api:3.0.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.tinylog:tinylog-api:2.7.0")
    implementation("org.tinylog:tinylog-impl:2.7.0")
    implementation("org.tinylog:slf4j-tinylog:2.7.0")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0") // Connection pooling
    implementation("com.google.guava:guava:33.4.8-jre")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.testcontainers:testcontainers:1.20.0")
    testImplementation("org.testcontainers:junit-jupiter:1.20.0")
    testImplementation("org.testcontainers:postgresql:1.20.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
    
    // gRPC testing
    testImplementation("io.grpc:grpc-testing:${grpcVersion}")
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
        artifact = "com.google.protobuf:protoc:3.25.7"
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

tasks.withType<Test> {
    useJUnitPlatform()
}

// Task to copy database schema files to the test resources directory
val copySchemaFiles = tasks.register<Copy>("copySchemaFiles") {
    description = "Copies database schema files to the test resources directory"
    group = "Build"
    
    from("${rootProject.projectDir}/database/initdb") {
        include("00-extensions.sql")
        include("01-schema.sql")
    }
    into("${layout.buildDirectory.get()}/resources/test/db")
}

// Make processTestResources depend on copySchemaFiles
tasks.named("processTestResources") {
    dependsOn(copySchemaFiles)
}

tasks.register<JavaExec>("debugRun") {
    group = "Debugging" // Assign it to the 'Debugging' group in './gradlew tasks' output
    description = "Runs the main server class with JVM debug agent enabled on port 5005, waiting for attachment."

    // Make sure the code is compiled first
    dependsOn(tasks.named("classes"))

    // Use the same classpath that the standard 'run' task would use
    classpath = sourceSets.main.get().runtimeClasspath

    // Configure the main class using the value from the 'application' plugin setting
    mainClass.set(application.mainClass) // Gets "com.goodmem.Main" from your application block

    // Add the JDWP agent arguments unconditionally for this task
    // suspend=y makes it wait for the debugger
    jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005")
}