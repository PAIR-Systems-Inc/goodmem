# GoodMem Project Guide

## Project Overview
- **Name**: GoodMem
- **License**: Apache-2.0
- **Purpose**: Memory APIs with CLI and UI interfaces

## Architecture
- **Source of Truth**: `proto/goodmem.proto`
- **Components**:
  - Java 17 / Javalin+gRPC server (REST mirrors every RPC)
  - Go CLI client
  - React 19 + Vite UI for browsing memory contents

## Tech Stack
- **Server**: Java 17, Javalin, gRPC
- **CLI**: Go
- **UI**: React 19, Vite
- **API Definition**: Protocol Buffers

## Development Environment
- Java 17 JDK
- Go 1.21+
- Node.js 20+
- Protocol Buffer compiler

## Coding Conventions
- Use standard conventions for each language
- Follow proto-first API development
- REST endpoints mirror RPC methods
- Consistent error handling across components

## Common Commands
- **Build all**: TBD
- **Run server**: TBD
- **Run tests**: TBD
- **Generate proto**: TBD
- **Start UI dev server**: TBD
- **Build CLI**: `./cli/build.sh` (uses Docker for reproducible builds)

## Project Structure
```
goodmem/
├── dist/             # Build artifacts output directory
├── proto/            # Protocol Buffer definitions
├── server/           # Java server implementation
├── cli/              # Go command-line client
│   └── build.sh      # CLI build script (uses Docker)
└── ui/               # React UI
```

## Contribution Guidelines
- Update proto file first when changing APIs
- Regenerate client code after proto changes
- Write tests for all new features
- Follow existing code style and patterns