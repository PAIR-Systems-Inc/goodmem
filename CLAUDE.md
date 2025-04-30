# GoodMem Project Guide

## Project Overview
- **Name**: GoodMem
- **License**: Apache-2.0
- **Purpose**: Memory APIs with CLI and UI interfaces

## Architecture
- **Source of Truth**: Protocol Buffer definitions in `proto/goodmem/v1/`
- **Components**:
  - Java 21 / Javalin+gRPC server (REST mirrors every RPC)
  - Go CLI client
  - React 19 + Vite UI for browsing memory contents
  - PostgreSQL database with pgvector extension for vector storage
- **Service Design**:
  - Dual protocol API (gRPC and REST)
  - In-process service bridging between protocols
  - REST endpoints automatically mapped to gRPC service methods
  - Standard organization for service implementations:
    - Each proto service has a corresponding `*ServiceImpl.java` class
    - REST handlers in `Main.java` with common naming pattern: `handle<Service><Method>`
    - Utility methods for converting between protocol buffer messages and JSON

## Tech Stack
- **Server**:
  - Java 21
  - Javalin 6.6.0 (REST API framework)
  - gRPC 1.72.0 (RPC framework)
  - Protocol Buffers 3.25.5
  - Protobuf-java-util 3.25.5 (For Timestamp conversions and other utilities)
  - Gradle 8.13 with Shadow plugin 8.1.1 (for building uber JARs)
  - Jackson 2.18.3 (JSON serialization)
  - SLF4J 2.0.17 (Logging)
  - Jakarta Annotation API 3.0.0
- **Database**:
  - PostgreSQL 16+ with pgvector extension
  - Vector dimensions: 1536 (OpenAI Ada-002 default)
  - HNSW indexing with L2 distance (vector_l2_ops)
- **CLI**: Go with connect-go
- **UI**: React 19, Vite
- **API Definition**: Protocol Buffers v3
- **Build Tools**:
  - Gradle 8.13
  - Gradle Versions Plugin 0.52.0 (dependency management)

## Development Environment
- Java 21 JDK
- Go 1.22+ with connect-go
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

### Server Implementation Patterns
- **Service Implementation Classes**: Create a separate `*ServiceImpl.java` class for each protocol buffer service
  - Extend the generated `*ImplBase` class (e.g., `SpaceServiceImplBase`)
  - Implement all methods defined in the service with the StreamObserver pattern
  - Return dummy data in implementation stubs until database integration
  - Use Java records for configuration objects (immutable configuration)
- **Authentication Flow Pattern**:
  - Every service method first retrieves the authenticated user from the context
  - Use `com.goodmem.security.User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();`
  - Check authentication before any other operations
  - Return early with UNAUTHENTICATED error if no authentication context exists
  - Example pattern:
    ```java
    com.goodmem.security.User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }
    ```
- **Permission Check Pattern**:
  - Check permissions immediately after authentication validation
  - Distinguish between broad permissions (ANY) vs limited permissions (OWN)
  - Implement special handling when user only has limited permissions
  - All permission validation must happen before any database operations
  - Example pattern:
    ```java
    boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.DISPLAY_RESOURCE_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.DISPLAY_RESOURCE_OWN);
    
    if (!hasAnyPermission && !hasOwnPermission) {
      Logger.error("User lacks necessary permissions for this operation");
      responseObserver.onError(
          io.grpc.Status.PERMISSION_DENIED
              .withDescription("Permission denied")
              .asRuntimeException());
      return;
    }
    ```
- **Fallback to Authenticated User**:
  - When no explicit parameters are provided, service should default to using the authenticated user's data
  - This pattern applies for both broad and limited permission sets
  - Example:
    ```java
    // If neither field is provided, default to the authenticated user
    if (!userIdProvided && !emailProvided) {
      requestedUserId = authenticatedUser.getId();
    }
    ```
- **Parameter Validation with Contextual Logic**:
  - Implement parameter validation based on permission context
  - For limited permissions (OWN), validate parameters match the authenticated user
  - For broad permissions (ANY), handle multiple parameters with clear priority
  - Log warnings when ignoring parameters (e.g., when both user_id and email are provided)
- **Multi-criteria Lookup Pattern**:
  - Implement clear priority and fallback logic for multiple lookup parameters
  - Document the complete lookup behavior in method comments
  - Follow consistent patterns for handling multiple parameters across all services
- **REST to gRPC Bridging**:
  - Create a blocking stub for each service in `Main.java`
  - Use in-process channels for efficiency: `InProcessChannelBuilder.forName("in-process").build()`
  - Follow consistent naming pattern for handler methods: `handle<Service><Method>`
  - Convert JSON request bodies to protocol buffer requests using appropriate builders
  - Convert protocol buffer responses to JSON using utility methods
- **Database Operation Pattern**:
  - Use try-with-resources for connection handling
  - Use StatusOr pattern for error handling and database operation results
  - Implement conditional database operations based on available parameters
  - Example pattern:
    ```java
    try (Connection connection = config.dataSource().getConnection()) {
      StatusOr<Optional<User>> userOr;
      
      if (requestedUserId != null) {
        userOr = com.goodmem.db.Users.loadById(connection, requestedUserId);
      } else if (requestedEmail != null) {
        userOr = com.goodmem.db.Users.loadByEmail(connection, requestedEmail);
      } else {
        // Handle error case
      }
      
      // Process the result
    }
    ```
- **Error Handling**:
  - Use detailed error logging internally, but return generic error messages to clients
  - Log specific error details with `Logger.error()` including exception information
  - Return standardized generic error messages to clients to avoid information leakage
  - For internal errors (SQLException, general exceptions), always use the message "Unexpected error while processing request."
  - For validation errors (missing required fields, format issues), use specific but non-revealing messages
  - Example implementation pattern:
    ```java
    try {
      // Database operation
    } catch (SQLException e) {
      // Log detailed error information for troubleshooting
      Logger.error(e, "Database error during operation: {}", e.getMessage());
      // Return generic error message to client
      responseObserver.onError(io.grpc.Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
    } catch (Exception e) {
      // Log the unexpected exception details
      Logger.error(e, "Unexpected error during operation: {}", e.getMessage());
      // Same generic error message to client
      responseObserver.onError(io.grpc.Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
    }
    ```
- **UUID Handling**:
  - Protocol buffer definitions use binary UUIDs (`bytes`) for better performance
  - REST API uses hex string representation with standard UUID formatting (8-4-4-4-12)
  - Use utility methods for conversion: `convertHexToUuidBytes()` and `bytesToHex()`
- **Timestamps**:
  - Protocol buffers use `google.protobuf.Timestamp`
  - REST API uses milliseconds since epoch (long integers)
  - Use `Timestamps.toMillis()` from protobuf-java-util for conversion

### Database Schema Patterns
- **Table Organization**:
  - Use singular table names to match entity concepts (e.g., `user`, `space`, `memory`)
  - Quote reserved words like `"user"` when using them as table names
  - Include standard audit fields on all tables:
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp`
    - `updated_at TIMESTAMPTZ NOT NULL DEFAULT current_timestamp`
    - `created_by_id UUID NOT NULL REFERENCES "user"(user_id)`
    - `updated_by_id UUID NOT NULL REFERENCES "user"(user_id)`
  - Use `ON DELETE CASCADE` for child entities (e.g., memories belong to spaces)
  - Use triggers to automatically update `updated_at` timestamps
- **PostgreSQL Extensions**:
  - `uuid-ossp`: For UUID generation (`uuid_generate_v4()`)
  - `pgvector`: For vector embeddings and similarity search
  - `pgcrypto`: For cryptographic functions
- **Vector Storage**:
  - Use `vector(1536)` type for embedding storage
  - Create HNSW index for efficient similarity search:
    - `CREATE INDEX ... USING hnsw (embedding_vector vector_l2_ops)`
  - Consider available distance metrics: L2 (Euclidean), cosine, or inner product
- **Docker Setup**:
  - Store database-related files in `/database` directory
  - Use `database/initdb` for startup SQL scripts:
    - `00-extensions.sql`: Enable required extensions
    - `01-schema.sql`: Create tables, indexes, and triggers
  - Add database service to docker-compose.yml with appropriate health checks

### Protocol Buffer Best Practices
- **Always include `go_package` option** in proto files to ensure correct Go import paths
  - Format: `option go_package = "github.com/pairsys/goodmem/cli/gen/goodmem/v1";`
- Run `./cli/gen_proto.sh` after any proto file changes
- Never manually edit generated protobuf code
- Follow semantic versioning for all API changes
- Use compatible protobuf versions with gRPC ecosystem:
  - Server: Protobuf 3.25.x
  - CLI: google.golang.org/protobuf v1.36.x
- **Use binary UUIDs**: Store UUIDs as `bytes` (16 bytes) instead of strings for better performance
  - Example: `bytes user_id = 1; // UUID (16 bytes)`
  - Server implementation must handle conversion between binary UUIDs and hex strings for REST API
- **Generated class capitalization**: Be aware that generated Java classes may use different capitalization than proto files
  - Example: A file named `apikey.proto` generates a class named `Apikey.java`, not `ApikeyOuterClass.java`
  - Always check the actual generated class names in `build/generated/sources/proto/main/java/`

### Go Best Practices
- Always check all error return values (required by golangci-lint)
- Use specific Go version in go.mod (e.g., `go 1.22`)
- Prefer standard library packages when possible
- For CLI flags, properly check the return value of `MarkFlagRequired`:
  ```go
  if err := cmd.MarkFlagRequired("name"); err != nil {
      panic(fmt.Sprintf("Failed to mark flag as required: %v", err))
  }
  ```
- Manage compatible dependency versions:
  - github.com/bufbuild/connect-go v1.10.0 (not higher)
  - github.com/spf13/cobra v1.9.x (not v2.x)
  - google.golang.org/protobuf v1.36.x

## Common Commands
- **Build all**: `./gradlew build`
- **Build server JAR**: `./gradlew :server:shadowJar` 
- **Run server**: `java -jar server/build/libs/goodmem-server.jar`
- **Run tests**: `./gradlew test`
- **Generate proto for server**: `./gradlew :server:generateProto`
- **Build CLI**: `./cli/build.sh` (uses Docker for reproducible builds)

## Local Development Setup

The project includes a streamlined development setup for local environment:

- **Start all services**: `./run_localhost.sh`
- **Start for IntelliJ development**: `./run_localhost.sh --exclude-server`
- **Start without UI**: `./run_localhost.sh --exclude-ui`
- **Get help on script options**: `./run_localhost.sh --help`

### Configuration Management

Configuration for the local development environment is managed through a unified approach:

- **Configuration file**: `config/local_dev.env` contains the shared configuration values
- **Update IntelliJ config**: `./config/update_intellij_config.sh` syncs IntelliJ run configurations with the shared config
- **Environment variables**: The run_localhost.sh script sources variables from config/local_dev.env

This approach ensures configuration stays in sync between Docker Compose and IntelliJ, with a single source of truth for all environment variables.

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

- **`server/format_java.sh`**: Java code formatter
  - Uses Google Java Format to standardize code style
  - Downloads the formatter automatically if needed
  - Can check code formatting or fix issues in-place
  - Usage:
    - Format a specific file: `./server/format_java.sh path/to/File.java`
    - Fix formatting in-place: `./server/format_java.sh --fix path/to/File.java`
    - Check formatting only: `./server/format_java.sh --check path/to/File.java`
    - Format entire directory: `./server/format_java.sh path/to/directory`
    - Default (no args): formats all Java files in server/src

### CLI Tools

- **`cli/build.sh`**: Reproducible CLI build script
  - Uses Docker to create a consistent build environment
  - Extracts the binary to the dist directory
  - Usage: `./cli/build.sh`

- **`cli/gen_proto.sh`**: Protocol Buffer code generator for CLI
  - Uses Docker to create a consistent protobuf generation environment
  - Generates Go code from protocol buffer definitions
  - Updates import paths and package names automatically
  - Usage: `./cli/gen_proto.sh`

### Git Hooks

- **`scripts/pre-commit-hooks/check_java_format.sh`**: Pre-commit hook for Java formatting
  - Checks if staged Java files are formatted according to Google Java Format
  - Prevents commits that would introduce formatting violations
  - Install with: `ln -sf ../../scripts/pre-commit-hooks/check_java_format.sh .git/hooks/pre-commit`
  - See `scripts/pre-commit-hooks/README.md` for more details

### Adding New Tools
When creating new scripts:
- Place them in the relevant component directory
- Use snake_case for script names (e.g., `do_something.sh`, NOT `do-something.sh`)
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
├── scripts/          # Shared scripts and tools
│   └── pre-commit-hooks/   # Git pre-commit hooks
│       ├── check_java_format.sh  # Java formatting check hook
│       └── README.md      # Instructions for using hooks
├── server/           # Java server implementation
│   ├── build.gradle.kts     # Server-specific Gradle build file
│   ├── build.sh             # Server build script using Docker
│   ├── check_outdated.sh    # Dependency version checker
│   ├── format_java.sh       # Google Java Format tool
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

## CI/CD Pipeline

The project uses GitHub Actions for continuous integration and deployment. The workflows are defined in `.github/workflows/`.

### Main Build Pipeline (`build.yml`)

- **Triggered by**: Push to main, Pull requests, Manual dispatch
- **Path-based filtering**: Only builds components with changes
  - Changes to `server/` trigger server build
  - Changes to `cli/` trigger CLI build
  - Changes to `proto/` trigger both
- **Server build**:
  - Compiles Java code
  - Runs Java tests
  - Builds server JAR with Gradle
  - Checks Java formatting
  - Uploads server JAR as artifact
- **CLI build**:
  - Verifies Go dependencies
  - Builds Go binary
  - Runs Go tests
  - Uploads CLI binary as artifact
- **Docker build** (main branch only):
  - Builds server Docker image
  - Pushes image to GitHub Container Registry

### Code Quality Checks (`code-quality.yml`)

- **Triggered by**: Push to main, Pull requests, Manual dispatch
- **Java formatting**: Uses `format_java.sh` to check Google Java Format
- **Go linting**: Uses golangci-lint for Go code quality
- **Dependency review**: Reviews PR dependencies for vulnerabilities

### Dependency Management

#### Automated Dependency Checks (`dependency-check.yml`)
- **Runs weekly** on Mondays
- Uses `check_outdated.sh` to find outdated Java dependencies
- Uses go-mod-outdated to find outdated Go dependencies
- Creates GitHub issues with dependency update reports

#### Dependabot Integration (`dependabot.yml`, `dependabot-auto-merge.yml`)
- **Weekly dependency scans** for Gradle and Go modules
- **Monthly scans** for GitHub Actions
- Groups related dependencies to reduce PR noise
- Automatically approves patch and minor updates
- Automatically merges patch updates

### Issue Templates
- Bug report template
- Dependency update report templates
- Configuration for directing feature requests to discussions