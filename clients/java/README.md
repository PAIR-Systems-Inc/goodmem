# GoodMem - Java Client

A Java client library for interacting with GoodMem services.

## Features

* Compatible with Java 8 and higher
* Built with Gradle
* Integrates with the main GoodMem project

## Installation

Add the following dependency to your Gradle project:

```kotlin
dependencies {
    implementation("com.pairsystems.goodmem:goodmem-client-java:0.1.0")
}
```

Or for Maven:

```xml
<dependency>
    <groupId>com.pairsystems.goodmem</groupId>
    <artifactId>goodmem-client-java</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage

```java
import com.pairsystems.goodmem.client.Client;

// Initialize client with server endpoint
Client client = new Client("http://localhost:8080");
```

## Building

The client can be built using Docker, which ensures a reproducible build environment regardless of your local setup:

```bash
./build.sh
```

This script will:
1. Create a Docker container with Java 8 JDK
2. Build the client library inside the container
3. Run all tests
4. Extract the build artifacts to the `dist/clients/java` directory

If you prefer to build without Docker and have Java 8+ installed, you can use Gradle directly:

```bash
./gradlew :clients:java:build
```

## Running Tests

Tests are automatically run as part of the build process. If you want to run just the tests:

```bash
./gradlew :clients:java:test
```
