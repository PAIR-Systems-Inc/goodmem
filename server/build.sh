#!/bin/bash
set -e

# Define paths
SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SERVER_DIR}/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

echo "Building GoodMem Server using Docker for reproducible builds..."

# Build the Docker image
docker build -t goodmem-server-builder -f "${SERVER_DIR}/Dockerfile" "${ROOT_DIR}"

# Extract the JAR from the Docker image
CONTAINER_ID=$(docker create goodmem-server-builder)
docker cp "${CONTAINER_ID}:/app/goodmem-server.jar" "${DIST_DIR}/goodmem-server.jar"
docker rm "${CONTAINER_ID}"

# Get the current git commit hash
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

echo "Build complete! Server JAR available at: ${DIST_DIR}/goodmem-server.jar"
echo "Server version: ${GIT_COMMIT}"
echo "You can run it with: java -jar ${DIST_DIR}/goodmem-server.jar"