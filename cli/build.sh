#!/bin/bash
set -e

# Define paths
CLI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLI_DIR}/.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

echo "Building GoodMem CLI using Docker for reproducible builds..."

# Build the Docker image
docker build -t goodmem-cli-builder "${CLI_DIR}"

# Extract the binary from the Docker image
CONTAINER_ID=$(docker create goodmem-cli-builder)
docker cp "${CONTAINER_ID}:/goodmem" "${DIST_DIR}/goodmem"
docker rm "${CONTAINER_ID}"

# Set executable permissions
chmod +x "${DIST_DIR}/goodmem"

echo "Build complete! Binary available at: ${DIST_DIR}/goodmem"
echo "Try running: ${DIST_DIR}/goodmem version"