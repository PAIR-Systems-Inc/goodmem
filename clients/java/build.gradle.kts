plugins {
    id("java")
    id("java-library")
    id("maven-publish")
}

group = "com.pairsystems.goodmem"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // For Java 8, we use source and target compatibility instead of release
    // as the --release flag is only available in Java 9+
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = "com.pairsystems.goodmem"
            artifactId = "goodmem-client-java"
            version = "0.1.0"
            
            pom {
                name.set("GoodMem Java Client")
                description.set("Java client library for GoodMem services")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
    
    // Configure GitHub Packages repository
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/PAIR-Systems-Inc/goodmem")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
