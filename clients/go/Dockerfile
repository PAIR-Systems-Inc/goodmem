# Build stage for Go client
FROM golang:1.22-alpine

WORKDIR /app

# Copy go.mod and go.sum first to leverage Docker cache
COPY go.mod ./

# Download dependencies
RUN go mod download

# Copy the source code
COPY *.go ./

# Build the client package
RUN go build ./...

# Run tests
RUN go test -v ./...

# The Go client is a library, so there's no final executable to extract
