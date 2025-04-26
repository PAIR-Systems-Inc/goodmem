# GoodMem

Memory APIs with CLI and UI interfaces.

## Components

- **Server**: Java 21 service with gRPC and REST APIs
- **CLI**: Go-based command-line client 
- **Client Libraries**: 
  - Python (3.8+)
  - Java (8+)
  - .NET (C#, NET 8.0)
  - Go (1.22+)
  - JavaScript (Node.js)

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

We provide a streamlined development setup using Docker Compose with a helper script:

```bash
# Start all services with default configuration
./run_localhost.sh

# For IntelliJ development, start only dependencies (DB, MinIO) without the server
./run_localhost.sh --exclude-server

# See all available options
./run_localhost.sh --help
```

The configuration for local development is stored in `config/local_dev.env`. You can modify this file to customize your development environment.

For IntelliJ users, we provide a tool to automatically update run configurations:

```bash
# After changing config/local_dev.env, update your IntelliJ config
./config/update_intellij_config.sh
```

#### Docker Compose Manual Commands

```bash
# Start all services manually (not recommended, use run_localhost.sh instead)
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

### Client Libraries Quick Start

GoodMem provides client libraries in multiple programming languages.

#### Building the Clients

All clients can be built using Docker, ensuring reproducible builds regardless of your local environment:

```bash
# Build all clients
./clients/build_all.sh

# Or build specific clients
./clients/python/build.sh
./clients/java/build.sh
./clients/dotnet/build.sh
./clients/go/build.sh
./clients/js/build.sh
```

#### Usage Examples

Python:
```python
from goodmem_client import Client

client = Client("http://localhost:8080")
```

Java:
```java
import com.pairsystems.goodmem.client.Client;

Client client = new Client("http://localhost:8080");
```

.NET (C#):
```csharp
using Pairsystems.Goodmem.Client;

var client = new Client("http://localhost:8080");
```

Go:
```go
import "github.com/PAIR-Systems-Inc/goodmem/clients/go"

client := goodmemclient.NewClient("http://localhost:8080")
```

JavaScript:
```javascript
import { Client } from '@pairsystems/goodmem-client-js';

const client = new Client('http://localhost:8080');
```