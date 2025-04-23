# GoodMem

Memory APIs with CLI and UI interfaces.

## Quick Start

### Server Quick Start

#### Build Locally

```bash
# Build the server with Gradle
./gradlew :server:shadowJar

# Run the built JAR
java -jar server/build/libs/goodmem-server.jar
```

The server starts two endpoints:
- gRPC service on port 9090
- REST API on port 8080 (mirrors the gRPC API)

#### Server Requirements

- Java JDK 21 or newer
- Protocol Buffer compiler (required for development, not for running the JAR)

#### Start with Docker Compose

```bash
# Start all services (server, vector-db placeholder, and UI if available)
docker compose up -d

# Verify server is running
curl -X POST http://localhost:8080/v1/spaces -H "Content-Type: application/json" -d '{"name":"test"}'

# View logs
docker compose logs -f server
```

#### Stop Services

```bash
# Stop all services
docker compose down

# Stop and remove volumes (for clean restart)
docker compose down -v
```

#### Testing

```bash
# Run server tests
./gradlew :server:test
```

### CLI Quick Start

#### Build Locally

```bash
# Using build script (recommended)
./cli/build.sh

# Or manually
cd cli && go build -o ../dist/goodmem .
../dist/goodmem version
```

#### Run with Docker

```bash
docker build -t goodmem-cli ./cli
docker run --rm goodmem-cli version
```

#### Managing Spaces

```bash
# create
./goodmem space create --name personal --label user=alice

# list
./goodmem space list --label user=alice

# delete
./goodmem space delete 00000000-0000-0000-0000-000000000001
```