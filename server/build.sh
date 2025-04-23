#!/bin/bash
set -e

# Define paths
SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SERVER_DIR}/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

# Get the current git commit hash
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")

echo "Building GoodMem Server using Docker for reproducible builds..."
echo "Using Docker to build version: ${GIT_COMMIT}"

# Remove existing images if they exist
if docker image inspect goodmem-server-builder &>/dev/null; then
    echo "Removing existing builder image..."
    docker rmi goodmem-server-builder
fi

# Build the Docker image with build arguments
echo "Building Docker image..."
docker build \
    --build-arg GIT_COMMIT="${GIT_COMMIT}" \
    -t goodmem-server-builder \
    -f "${SERVER_DIR}/Dockerfile" \
    "${ROOT_DIR}"

# Extract the JAR from the Docker image
echo "Extracting JAR from Docker image..."
CONTAINER_ID=$(docker create goodmem-server-builder)
docker cp "${CONTAINER_ID}:/app/goodmem-server.jar" "${DIST_DIR}/goodmem-server.jar"
docker rm "${CONTAINER_ID}"

echo -e "\n-----------------------------------------------------"
echo "✅ Build complete!"
echo "-----------------------------------------------------"
echo "Server JAR: ${DIST_DIR}/goodmem-server.jar"
echo "Version:    ${GIT_COMMIT}"
echo "Run with:   java -jar ${DIST_DIR}/goodmem-server.jar"
echo "-----------------------------------------------------"