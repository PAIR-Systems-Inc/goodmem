#!/bin/bash
set -e

# Define paths
CLI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLI_DIR}/.." && pwd)"
PROTO_DIR="${ROOT_DIR}/proto"
OUTPUT_DIR="${CLI_DIR}/gen"

echo "==> Generating Proto files for CLI..."

# Create a temporary directory for the Docker build
TMP_DIR=$(mktemp -d)
trap "rm -rf ${TMP_DIR}" EXIT

# Make sure gen directory exists
mkdir -p "${OUTPUT_DIR}"

# Create a Dockerfile for protoc with mounted volumes
cat > "${TMP_DIR}/Dockerfile" << EOF
FROM golang:1.22-bookworm

# Install protoc and dependencies
RUN apt-get update && \
    apt-get install -y protobuf-compiler && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install Go protoc plugins
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.6
RUN go install github.com/bufbuild/connect-go/cmd/protoc-gen-connect-go@v1.10.0

# Create directories with correct permissions
RUN mkdir -p /proto /output && \
    chmod -R 777 /output

# Environment setup
ENV PATH="/go/bin:${PATH}"
WORKDIR /
EOF

# Build the Docker image
echo "==> Building Docker image for protoc generation..."
docker build -t goodmem-protoc -f "${TMP_DIR}/Dockerfile" .

# We'll use a container for generation but mount volumes
echo "==> Running protoc in Docker..."
docker run --rm \
  -v "${PROTO_DIR}:/proto" \
  -v "${OUTPUT_DIR}:/output" \
  goodmem-protoc \
  bash -c "cd / && \
           protoc \
           -I=/proto \
           --go_out=/output \
           --go_opt=paths=source_relative \
           --connect-go_out=/output \
           --connect-go_opt=paths=source_relative \
           /proto/goodmem/v1/common.proto \
           /proto/goodmem/v1/space.proto \
           /proto/goodmem/v1/user.proto \
           /proto/goodmem/v1/memory.proto \
           /proto/goodmem/v1/apikey.proto && \
           chmod -R 777 /output/*"

# Fix any possible permissions
echo "==> Fixing permissions..."
chmod -R u+rw "${OUTPUT_DIR}" || true

# Clean up the Docker image
echo "==> Cleaning up..."
docker rmi goodmem-protoc > /dev/null 2>&1 || true

echo "==> Proto generation complete!"
echo "Generated files are in: ${OUTPUT_DIR}"