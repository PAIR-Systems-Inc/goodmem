# GoodMem - Go Client

A Go client library for interacting with GoodMem services.

## Installation

```bash
go get github.com/PAIR-Systems-Inc/goodmem/clients/go
```

## Usage

```go
import "github.com/PAIR-Systems-Inc/goodmem/clients/go"

// Initialize client with server endpoint
client := goodmemclient.NewClient("http://localhost:8080")
```

## Building

```bash
go build ./...
```

## Running Tests

```bash
go test ./...
```
