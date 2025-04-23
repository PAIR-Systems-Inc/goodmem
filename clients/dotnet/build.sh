#!/bin/bash
set -e

echo "Building .NET client using Docker for reproducible builds..."

# Define paths
CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLIENT_DIR}/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist/clients/dotnet"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

# Build the Docker image
docker build -t goodmem-dotnet-client-builder "${CLIENT_DIR}"

# Run container to extract built artifacts
CONTAINER_ID=$(docker create goodmem-dotnet-client-builder)

# Extract all content from the container
docker cp "${CONTAINER_ID}:/app/Pairsystems.Goodmem.Client/bin/Release/net8.0/." "${DIST_DIR}/"

# Clean up container
docker rm "${CONTAINER_ID}" > /dev/null

# Check if build produced the DLLs
if ls "${DIST_DIR}"/*.dll &>/dev/null; then
    echo "📦 .NET client built successfully!"
    echo "📂 Output files available in: ${DIST_DIR}/"
else
    echo "⚠️ No build artifacts found. Build may have failed."
    exit 1
fi
