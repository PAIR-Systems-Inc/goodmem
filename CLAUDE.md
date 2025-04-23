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
  - Javalin 6.1.4 (REST API framework)
  - gRPC 1.72.0 (RPC framework)
  - Protocol Buffers 3.25.3
  - Gradle 8.13 with Shadow plugin (for building uber JARs)
- **CLI**: Go with connect-go
- **UI**: React 19, Vite
- **API Definition**: Protocol Buffers v3

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