# GoodMem Project Guide

## Project Overview
- **Name**: GoodMem
- **License**: Apache-2.0
- **Purpose**: Memory APIs with CLI and UI interfaces

## Architecture
- **Source of Truth**: `proto/goodmem/v1/space.proto`
- **Components**:
  - Java 21 / Javalin+gRPC server (REST mirrors every RPC)
  - Go CLI client
  - React 19 + Vite UI for browsing memory contents
- **Service Design**:
  - Dual protocol API (gRPC and REST)
  - In-process service bridging between protocols
  - REST endpoints automatically mapped to gRPC service methods

## Tech Stack
- **Server**:
  - Java 21
  - Javalin 6.6.0 (REST API framework)
  - gRPC 1.72.0 (RPC framework)
  - Protocol Buffers 3.25.5
  - Gradle 8.13 with Shadow plugin 8.1.1 (for building uber JARs)
  - Jackson 2.18.3 (JSON serialization)
  - SLF4J 2.0.17 (Logging)
  - Jakarta Annotation API 3.0.0
- **CLI**: Go with connect-go
- **UI**: React 19, Vite
- **API Definition**: Protocol Buffers v3
- **Build Tools**:
  - Gradle 8.13
  - Gradle Versions Plugin 0.52.0 (dependency management)

## Development Environment
- Java 21 JDK
- Go 1.21+ with connect-go
- Node.js 20+
- Protocol Buffer compiler 3.25.x
- Gradle 8.13+

## Coding Conventions
- Use standard conventions for each language
- Follow proto-first API development
- REST endpoints mirror RPC methods
- Consistent error handling across components
- Generated code should not be edited manually
- Make proto field additions backward compatible

## Common Commands
- **Build all**: `./gradlew build`
- **Build server JAR**: `./gradlew :server:shadowJar` 
- **Run server**: `java -jar server/build/libs/goodmem-server.jar`
- **Run tests**: `./gradlew test`
- **Generate proto for server**: `./gradlew :server:generateProto`
- **Build CLI**: `./cli/build.sh` (uses Docker for reproducible builds)

## Developer Tools
The project includes several developer tools to streamline development workflows. When creating new scripts, always document them in this section and ensure they follow consistent patterns.

### Server Tools

- **`server/build.sh`**: Reproducible server build script
  - Uses Docker to create a consistent build environment
  - Extracts the JAR file to the dist directory
  - Usage: `./server/build.sh`

- **`server/check_outdated.sh`**: Dependency version checker
  - Identifies outdated dependencies in the project
  - Shows available updates for libraries and Gradle
  - Usage: `./server/check_outdated.sh`

### CLI Tools

- **`cli/build.sh`**: Reproducible CLI build script
  - Uses Docker to create a consistent build environment
  - Extracts the binary to the dist directory
  - Usage: `./cli/build.sh`

### Adding New Tools
When creating new scripts:
- Place them in the relevant component directory
- Use snake_case for script names
- Make scripts executable (`chmod +x`)
- Add documentation to this file
- Include usage instructions in a comment at the top of the script
- Ensure consistent error handling and reporting
- Consider using similar patterns to existing scripts

## Project Structure
```
goodmem/
├── dist/             # Build artifacts output directory
├── proto/            # Protocol Buffer definitions
│   └── goodmem/
│       └── v1/       # API version 1 definitions
│           └── space.proto  # Space service definition
├── server/           # Java server implementation
│   ├── build.gradle.kts     # Server-specific Gradle build file
│   ├── build.sh             # Server build script using Docker
│   ├── check_outdated.sh    # Dependency version checker
│   └── src/
│       └── main/
│           └── java/
│               └── com/
│                   └── goodmem/   # Server implementation code
├── cli/              # Go command-line client
│   ├── build.sh      # CLI build script (uses Docker)
│   ├── go.mod        # Go module definition
│   ├── cmd/          # CLI commands implementations
│   └── gen/          # Generated Go code from protobufs
└── ui/               # React UI (planned)
```

## Contribution Guidelines
- Update proto file first when changing APIs
- Regenerate client code after proto changes
- Write tests for all new features
- Follow existing code style and patterns