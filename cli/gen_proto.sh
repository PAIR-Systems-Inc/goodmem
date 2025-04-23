#!/bin/bash
set -e

# Define paths
CLI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLI_DIR}/.." && pwd)"
PROTO_DIR="${ROOT_DIR}/proto"
GEN_DIR="${CLI_DIR}/gen"

echo "==> Generating Proto files for CLI..."

# Create a temporary directory to hold the generated files
TMP_DIR=$(mktemp -d)
trap "rm -rf ${TMP_DIR}" EXIT

# Ensure gen directory exists
mkdir -p "${GEN_DIR}"

# Create a Dockerfile for the protoc container
cat > "${TMP_DIR}/Dockerfile" << EOF
FROM golang:1.22

WORKDIR /app

# Install protoc
RUN apt-get update && apt-get install -y protobuf-compiler

# Install Go protoc plugins
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.6
RUN go install github.com/bufbuild/connect-go/cmd/protoc-gen-connect-go@v1.10.0

# Add the Go bin directory to the PATH
ENV PATH="\${PATH}:/go/bin"

# Copy proto files
COPY proto/ /app/proto/

# Copy go.mod and go.sum
COPY cli/go.mod cli/go.sum /app/cli/

WORKDIR /app/cli
RUN go mod download

WORKDIR /app

# Generate code
RUN mkdir -p /app/gen && \
    protoc -I=/app/proto \
    --go_out=/app/gen --go_opt=paths=source_relative \
    --connect-go_out=/app/gen --connect-go_opt=paths=source_relative \
    goodmem/v1/space.proto
EOF

echo "==> Building protoc container..."
docker build -t goodmem-protogen -f "${TMP_DIR}/Dockerfile" "${ROOT_DIR}"

echo "==> Extracting generated files..."
# Create a container and copy the files out
container_id=$(docker create goodmem-protogen)
docker cp "${container_id}:/app/gen/." "${GEN_DIR}/"
docker rm "${container_id}"

echo "==> Cleanup..."
docker rmi goodmem-protogen

echo "==> Proto generation complete!"
echo "Generated files are in: ${GEN_DIR}"