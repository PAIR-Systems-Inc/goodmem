# GoodMem Client Libraries

This directory contains client libraries for interacting with GoodMem services in various programming languages.

## Available Clients

- [Python](./python/) - Python client library (Python 3.8+)
- [Java](./java/) - Java client library (Java 8+)
- [.NET](./dotnet/) - .NET (C#) client library (NET 8.0)
- [Go](./go/) - Go client library (Go 1.22+)
- [JavaScript](./js/) - JavaScript client library (Node.js)

## Building the Clients

All clients can be built with Docker, ensuring a consistent and reproducible build environment regardless of your local machine setup.

### Building All Clients

To build all the clients at once:

```bash
./build_all.sh
```

This will build each client in its Docker container and extract the build artifacts to the `dist/clients/` directory.

### Building Individual Clients

Each client can also be built individually:

```bash
# Build the Python client
./python/build.sh

# Build the Java client
./java/build.sh

# Build the .NET client
./dotnet/build.sh

# Build the Go client
./go/build.sh

# Build the JavaScript client
./js/build.sh
```

## Client Features

All clients:
- Accept a server endpoint URL in their constructor
- Store this endpoint internally
- Include testing capabilities

## Development Status

These clients are currently in an initial setup phase. They provide the basic structure for implementing the GoodMem API client functionality.

## License

Apache-2.0