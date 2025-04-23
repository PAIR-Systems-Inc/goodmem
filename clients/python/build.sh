#!/bin/bash
set -e

echo "Building Python client using Docker for reproducible builds..."

# Define paths
CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLIENT_DIR}/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist/clients/python"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

# Build the Docker image
docker build -t goodmem-python-client-builder "${CLIENT_DIR}"

# Run container to extract built artifacts
CONTAINER_ID=$(docker create goodmem-python-client-builder)

# Extract all content from the container
docker cp "${CONTAINER_ID}:/app/dist/." "${DIST_DIR}/"

# Clean up container
docker rm "${CONTAINER_ID}" > /dev/null

# Check if build produced the wheel and source distribution
if ls "${DIST_DIR}"/*.whl "${DIST_DIR}"/*.tar.gz &>/dev/null; then
    echo "📦 Python client built successfully!"
    echo "📂 Output files available in: ${DIST_DIR}/"
else
    echo "⚠️ No build artifacts found. Build may have failed."
    exit 1
fi
