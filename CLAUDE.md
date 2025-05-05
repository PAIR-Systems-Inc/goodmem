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

#### Service Implementation Class Pattern
- **Service Implementation Classes**: Create a separate `*ServiceImpl.java` class for each protocol buffer service
  - Extend the generated `*ImplBase` class (e.g., `SpaceServiceImplBase`)
  - Implement all methods defined in the service with the StreamObserver pattern
  - Return dummy data in implementation stubs until database integration
  - Use Java records for configuration objects (immutable configuration)

#### Service Operation Patterns

1. **Authentication Flow Pattern**

All service methods follow a standard authentication pattern that retrieves the authenticated user from the gRPC context and checks if the user is authenticated. This is consistently the *first* check in every service method.

```java
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.User;
import io.grpc.Status;
import org.tinylog.Logger;

// Get the authenticated user from the Context
User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();
if (authenticatedUser == null) {
  Logger.error("No authentication context found");
  responseObserver.onError(
      Status.UNAUTHENTICATED
          .withDescription("Authentication required")
          .asRuntimeException());
  return;
}
```

2. **Permission Checking Pattern**

After authentication, services check permissions using a consistent pattern that distinguishes between broad permissions (ANY) and limited permissions (OWN) with clear permission naming that follows the pattern: `[ACTION]_[RESOURCE]_[SCOPE]`.

```java
import com.goodmem.security.Permission;
import io.grpc.Status;
import org.tinylog.Logger;

// Check permissions
boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.ACTION_RESOURCE_ANY);
boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.ACTION_RESOURCE_OWN);

// User must have at least OWN permission
if (!hasAnyPermission && !hasOwnPermission) {
  Logger.error("User lacks necessary permissions for this operation");
  responseObserver.onError(
      Status.PERMISSION_DENIED
          .withDescription("Permission denied")
          .asRuntimeException());
  return;
}
```

3. **Ownership-Based Permission Enforcement Pattern**

When checking permissions for specific resources, the code first loads the resource to check ownership, then applies permission checks based on whether the user owns the resource or not.

```java
import com.goodmem.security.Permission;
import io.grpc.Status;
import org.tinylog.Logger;

// Check permissions based on ownership
boolean isOwner = resource.ownerId().equals(authenticatedUser.getId());
boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.ACTION_RESOURCE_ANY);
boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.ACTION_RESOURCE_OWN);

// If user is not the owner, they must have ACTION_RESOURCE_ANY permission
if (!isOwner && !hasAnyPermission) {
  Logger.error("User lacks permission for resources owned by others");
  responseObserver.onError(
      Status.PERMISSION_DENIED
          .withDescription("Permission denied")
          .asRuntimeException());
  return;
}

// If user is the owner, they must have at least ACTION_RESOURCE_OWN permission
if (isOwner && !hasAnyPermission && !hasOwnPermission) {
  Logger.error("User lacks necessary permissions for their own resources");
  responseObserver.onError(
      Status.PERMISSION_DENIED
          .withDescription("Permission denied")
          .asRuntimeException());
  return;
}
```

4. **Parameter Validation Pattern**

Input parameters are validated before any database operations occur. The validation follows a consistent pattern with detailed error messages, using Guava's utility methods for string validation when appropriate.

```java
import com.google.common.base.Strings;
import io.grpc.Status;
import org.tinylog.Logger;

// Validate required string fields using Guava
if (Strings.isNullOrEmpty(request.getSomeRequiredField())) {
  Logger.error("Field is required");
  responseObserver.onError(
      Status.INVALID_ARGUMENT
          .withDescription("Field is required")
          .asRuntimeException());
  return;
}

// For optional fields, check the has* methods generated by protobuf
if (request.hasSomeOptionalField()) {
  // Process the optional field...
}
```

5. **UUID Conversion Pattern**

Binary UUIDs from protocol buffers are consistently converted to Java UUIDs using utility methods that provide error handling.

```java
import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.UuidUtil;
import io.grpc.Status;
import org.tinylog.Logger;

import java.util.UUID;

// Validate and convert UUID
StatusOr<UUID> idOr = UuidUtil.fromProtoBytes(request.getId());

if (idOr.isNotOk()) {
  Logger.error("Invalid ID format: {}", idOr.getStatus().getMessage());
  responseObserver.onError(
      Status.INVALID_ARGUMENT
          .withDescription("Invalid ID format")
          .asRuntimeException());
  return;
}

UUID id = idOr.getValue();
```

6. **Fallback to Authenticated User Pattern**

When certain parameters are optional (such as owner_id), the code falls back to using the authenticated user's ID.

```java
// No owner_id provided, use authenticated user's ID
if (!request.hasOwnerId()) {
  ownerId = authenticatedUser.getId();
}
```

7. **Database Operation Pattern**

Database operations use try-with-resources for connection handling and a StatusOr pattern for error handling.

```java
import com.goodmem.common.status.StatusOr;
import io.grpc.Status;
import org.tinylog.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

try (Connection connection = config.dataSource().getConnection()) {
  // Load the resource to check ownership
  StatusOr<Optional<Entity>> entityOr = 
      com.goodmem.db.Entities.loadById(connection, entityId);
      
  if (entityOr.isNotOk()) {
    Logger.error("Error loading entity: {}", entityOr.getStatus().getMessage());
    responseObserver.onError(
        Status.INTERNAL
            .withDescription("Unexpected error while processing request.")
            .asRuntimeException());
    return;
  }
  
  // Check if resource exists
  if (entityOr.getValue().isEmpty()) {
    Logger.error("Entity not found: {}", entityId);
    responseObserver.onError(
        Status.NOT_FOUND
            .withDescription("Entity not found")
            .asRuntimeException());
    return;
  }
  
  // Continue processing...
}
```

8. **Generic Error Handling Pattern**

Error handling follows a standard pattern with detailed internal logging but generic messages to clients.

```java
import io.grpc.Status;
import org.tinylog.Logger;

import java.sql.SQLException;

try {
  // Database or processing operation
} catch (SQLException e) {
  Logger.error(e, "Database error: {}", e.getMessage());
  responseObserver.onError(
      Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
} catch (Exception e) {
  Logger.error(e, "Unexpected error: {}", e.getMessage());
  responseObserver.onError(
      Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
}
```

9. **Pagination Token Pattern**

For paginated methods, there's a consistent pattern for handling pagination tokens.

```java
import com.goodmem.common.status.StatusOr;
import io.grpc.Status;
import org.tinylog.Logger;

// Handle pagination token if provided
if (request.hasNextToken()) {
  StatusOr<NextPageToken> tokenOr = 
      decodeAndValidateNextPageToken(request.getNextToken(), authenticatedUser);
  
  if (tokenOr.isNotOk()) {
    Logger.error("Invalid pagination token: {}", tokenOr.getStatus().getMessage());
    responseObserver.onError(
        Status.INVALID_ARGUMENT
            .withDescription("Invalid pagination token")
            .asRuntimeException());
    return;
  }
  
  // Extract parameters from token
  // ...
}
```

10. **Response Builder Pattern**

When constructing responses, especially for list operations, the code uses a builder pattern with conditional fields.

```java
// Create the response builder
ResponseType.Builder responseBuilder = ResponseType.newBuilder();

// Add the entities to the response
for (Entity entity : queryResult.getEntities()) {
  responseBuilder.addEntities(entity.toProto());
}

// Add next page token if needed
if (queryResult.hasMore(offset, limit)) {
  int nextOffset = queryResult.getNextOffset(offset, limit);
  String encodedToken = encodeNextPageToken(createNextPageToken(...));
  responseBuilder.setNextToken(encodedToken);
}

// Return the response
responseObserver.onNext(responseBuilder.build());
responseObserver.onCompleted();
```

11. **Update Strategy Pattern with Oneof**

The update operations use a pattern with the Protocol Buffer `oneof` feature to handle different update strategies, such as replacing vs. merging field values.

```java
import java.util.HashMap;
import java.util.Map;

// Check which update strategy is being used
boolean hasReplaceLabels = request.getLabelUpdateStrategyCase() == UpdateRequest.LabelUpdateStrategyCase.REPLACE_LABELS;
boolean hasMergeLabels = request.getLabelUpdateStrategyCase() == UpdateRequest.LabelUpdateStrategyCase.MERGE_LABELS;

// Process based on update strategy
Map<String, String> newLabels;
if (hasReplaceLabels) {
  // Replace all existing labels with the ones provided
  newLabels = new HashMap<>(request.getReplaceLabels().getLabelsMap());
} else if (hasMergeLabels) {
  // Merge existing labels with new ones
  newLabels = new HashMap<>(existingEntity.labels());
  newLabels.putAll(request.getMergeLabels().getLabelsMap());
} else {
  // No label changes, keep existing labels
  newLabels = existingEntity.labels();
}
```

12. **Multi-criteria Lookup Pattern**:
  - Implement clear priority and fallback logic for multiple lookup parameters
  - Document the complete lookup behavior in method comments
  - Follow consistent patterns for handling multiple parameters across all services

13. **REST to gRPC Bridging**:
  - Create a blocking stub for each service in `Main.java`
  - Use in-process channels for efficiency: `InProcessChannelBuilder.forName("in-process").build()`
  - Follow consistent naming pattern for handler methods: `handle<Service><Method>`
  - Convert JSON request bodies to protocol buffer requests using appropriate builders
  - Convert protocol buffer responses to JSON using utility methods

14. **Authorization and Resource Filtering Pattern**

For list operations, there's a pattern for handling different permission levels with resource filtering:

```java
import com.goodmem.security.Permission;
import io.grpc.Status;
import org.tinylog.Logger;

import java.util.UUID;

// Determine the filtering based on permissions
UUID ownerIdFilter = null;
boolean includePublic = hasAnyPermission;

if (hasAnyPermission) {
  // With LIST_RESOURCE_ANY permission:
  // - If an owner ID was requested, use it as a filter
  // - If no owner ID was requested, show all resources (no owner filter)
  ownerIdFilter = requestedOwnerId;
} else if (hasOwnPermission) {
  // With only LIST_RESOURCE_OWN permission:
  // - If an owner ID was requested and it's not the authenticated user, reject
  // - Otherwise, filter to only show the authenticated user's resources
  if (requestedOwnerId != null && !requestedOwnerId.equals(authenticatedUser.getId())) {
    Logger.error("User lacks permission to list resources owned by others");
    responseObserver.onError(
        Status.PERMISSION_DENIED
            .withDescription("Permission denied")
            .asRuntimeException());
    return;
  }
  
  // Always filter by authenticated user ID when only having LIST_RESOURCE_OWN
  ownerIdFilter = authenticatedUser.getId();
  includePublic = false; // Don't include public resources with only OWN permission
}
```

15. **Service Configuration Pattern**

Services use Java records for configuration objects, making them immutable and providing clarity about dependencies.

```java
import com.zaxxer.hikari.HikariDataSource;

/**
 * Configuration for the service implementation.
 */
public record Config(HikariDataSource dataSource, String defaultModel) {}

public ServiceImpl(Config config) {
  this.config = config;
}
```

#### Entity and Data Access Patterns

1. **Record-Based Entity Definition Pattern**

Entities use Java records for immutability and clear field definition. Each entity record includes:
- Standard audit fields (created_at, updated_at, created_by_id, updated_by_id)
- A `toProto()` method to convert from database representation to protocol buffer representation

```java
import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Entity(
    UUID entityId,
    UUID ownerId,
    String name,
    Map<String, String> labels,
    boolean publicRead,
    Instant createdAt,
    Instant updatedAt,
    UUID createdById,
    UUID updatedById) {
  
  /**
   * Converts this database record to its corresponding Protocol Buffer message.
   */
  public proto.Entity toProto() {
    proto.Entity.Builder builder =
        proto.Entity.newBuilder()
            .setEntityId(UuidUtil.toProtoBytes(entityId))
            .setOwnerId(UuidUtil.toProtoBytes(ownerId))
            .setName(name)
            .setPublicRead(publicRead)
            .setCreatedAt(DbUtil.toProtoTimestamp(createdAt))
            .setUpdatedAt(DbUtil.toProtoTimestamp(updatedAt))
            .setCreatedById(UuidUtil.toProtoBytes(createdById))
            .setUpdatedById(UuidUtil.toProtoBytes(updatedById));

    // Add labels if present
    if (labels != null) {
      builder.putAllLabels(labels);
    }

    return builder.build();
  }
}
```

2. **Database Access Pattern**

Database access follows a consistent pattern with:
- Static utility methods for operations (save, load, delete, query)
- Methods that return StatusOr for robust error handling
- Consistent error propagation
- SQL queries defined as multi-line strings
- Parameter binding using prepared statements

```java
import com.goodmem.common.status.StatusOr;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Nonnull
public static StatusOr<Optional<Entity>> loadById(Connection conn, UUID entityId) {
  String sql =
      """
      SELECT entity_id, owner_id, name, labels, public_read,
             created_at, updated_at, created_by_id, updated_by_id
        FROM entity
       WHERE entity_id = ?
      """;
  try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setObject(1, entityId);
    try (ResultSet rs = stmt.executeQuery()) {
      if (rs.next()) {
        StatusOr<Entity> entityOr = extractEntity(rs);
        if (entityOr.isNotOk()) {
          return StatusOr.ofStatus(entityOr.getStatus());
        }
        return StatusOr.ofValue(Optional.of(entityOr.getValue()));
      }
      return StatusOr.ofValue(Optional.empty());
    }
  } catch (SQLException e) {
    return StatusOr.ofException(e);
  }
}
```

#### Protocol Buffer Conventions

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

#### General Go Guidelines
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

#### Cobra CLI Implementation Patterns

- **Command Structure**:
  - Group related commands under a common parent (e.g., `space list`, `space create`)
  - Use consistent command naming across services (`list`, `get`, `create`, `update`, `delete`)
  - Define shared variables at the package level for flags used by multiple commands
  - Initialize all commands and flags in the `init()` function

- **Flag Design**:
  - Use short flags (-l) for common options, full flags (--label) for all options
  - Follow flag naming conventions from popular CLI tools (kubectl, docker, etc.)
  - Provide descriptive help text for all flags
  - Set sensible defaults for optional flags
  - Mark required flags using `MarkFlagRequired()`

- **Command Documentation**:
  - Use the `Example:` field to show real-world usage patterns
  - Include complete command examples with flags
  - Add line comments explaining the purpose of each example
  - Follow a consistent command example format throughout the CLI

- **Protocol Buffer Handling**:
  - Use helper functions for common conversions (UUID, timestamp, etc.)
  - Handle optional fields properly with pointers in request messages
  - Reference field presence with `cmd.Flags().Changed()` before setting optional fields
  - Use proper oneof patterns for mutually exclusive fields

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

### CLI Development

#### CLI Command Design Patterns

- **Output Formats**: Each list/get command should support multiple output formats:
  - `table`: Human-readable tabular output with headers (default)
  - `json`: REST-friendly JSON for programmatic consumption
  - `compact`: Condensed single-line format for scripting
  - `quiet`: Output only identifiers for piping to other commands

- **Table Format Guidelines**:
  - Display complete entity IDs to enable direct use in follow-up commands
  - Include sort indicators (↑/↓) in column headers when sorting is applied
  - Limit column width with appropriate truncation for readability
  - Organize columns from most to least important, left to right
  - Use consistent date/time formatting across all commands

- **JSON Formatting Standards**:
  - Convert binary UUIDs to standard string format (8-4-4-4-12)
  - Format timestamps as ISO 8601 strings (e.g., "2025-05-02T00:05:40.841Z")
  - Use camelCase property names to follow REST conventions
  - Maintain proper ID capitalization (e.g., "spaceID" not "spaceId")
  - Remove empty/null fields to reduce response size

- **Pagination Pattern**:
  - Support pagination tokens for large result sets
  - Show helpful "next page" command examples in outputs
  - Display tokens in a way that can be easily copied and reused
  - Include all filter parameters in the next-page example command

- **Error Handling Approach**:
  - Provide user-friendly error messages for common errors
  - Distinguish between client-side validation and server-side errors
  - Use `SilenceUsage: true` after client-side validation to avoid redundant help text
  - Map server error codes to specific, actionable error messages

#### CLI Infrastructure

- **`cli/build.sh`**: Reproducible CLI build script
  - Uses Docker to create a consistent build environment
  - Extracts the binary to the dist directory
  - Usage: `./cli/build.sh`

- **`cli/gen_proto.sh`**: Protocol Buffer code generator for CLI
  - Uses Docker to create a consistent protobuf generation environment
  - Generates Go code from protocol buffer definitions
  - Updates import paths and package names automatically
  - Usage: `./cli/gen_proto.sh`
  - **Important**: Include all proto files in the generation command, including common.proto

- **Utility Functions**: Implement reusable utilities for common operations:
  - `json_formatter.go`: Central utility for REST-friendly JSON formatting
  - `truncateString()`: Standard string truncation with ellipsis for display
  - UUID conversion: Binary UUID to string representation and vice versa

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