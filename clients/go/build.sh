#!/bin/bash
set -e

echo "Building Go client using Docker for reproducible builds..."

# Define paths
CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLIENT_DIR}/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist/clients/go"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

# Build the Docker image
docker build -t goodmem-go-client-builder "${CLIENT_DIR}"

# Run container to extract built artifacts
CONTAINER_ID=$(docker create goodmem-go-client-builder)

# Extract all source files from the container
docker cp "${CONTAINER_ID}:/app/." "${DIST_DIR}/src/"

# Clean up container
docker rm "${CONTAINER_ID}" > /dev/null

# Create a package for distribution
tar -czf "${DIST_DIR}/goodmem-client-go.tar.gz" -C "${DIST_DIR}/src" .

# Check if source files were copied successfully
if [ -f "${DIST_DIR}/goodmem-client-go.tar.gz" ]; then
    echo "📦 Go client built and packaged successfully!"
    echo "📂 Source files available in: ${DIST_DIR}/src/"
    echo "📦 Package available at: ${DIST_DIR}/goodmem-client-go.tar.gz"
else
    echo "⚠️ Build failed. No source files were copied."
    exit 1
fi
