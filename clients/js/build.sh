#!/bin/bash
set -e

echo "Building JavaScript client using Docker for reproducible builds..."

# Define paths
CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLIENT_DIR}/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist/clients/js"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

# Build the Docker image
docker build -t goodmem-js-client-builder "${CLIENT_DIR}"

# Run container to extract built artifacts
CONTAINER_ID=$(docker create goodmem-js-client-builder)

# Extract all content from the container
docker cp "${CONTAINER_ID}:/app/." "${DIST_DIR}/"

# Clean up container
docker rm "${CONTAINER_ID}" > /dev/null

# Check if build produced the tarball package
if ls "${DIST_DIR}"/*.tgz &>/dev/null; then
    echo "📦 JavaScript client built successfully!"
    echo "📂 Package available at: $(ls ${DIST_DIR}/*.tgz)"
else
    echo "⚠️ No build artifacts found. Build may have failed."
    # List what files were created to help diagnose the issue
    echo "Files in ${DIST_DIR}:"
    ls -la "${DIST_DIR}"
    exit 1
fi