#!/bin/bash
set -e

echo "Building Java client using Docker for reproducible builds..."

# Define paths
CLIENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLIENT_DIR}/../.." && pwd)"
DIST_DIR="${ROOT_DIR}/dist/clients/java"

# Ensure dist directory exists
mkdir -p "${DIST_DIR}"

# Copy Gradle wrapper and files from project root if needed
if [ -d "${ROOT_DIR}/gradle" ]; then
    cp -r "${ROOT_DIR}/gradle" "${CLIENT_DIR}/"
    cp "${ROOT_DIR}/gradlew" "${CLIENT_DIR}/"
    cp "${ROOT_DIR}/gradlew.bat" "${CLIENT_DIR}/"
    chmod +x "${CLIENT_DIR}/gradlew"
fi

# Build the Docker image
docker build -t goodmem-java-client-builder "${CLIENT_DIR}"

# Run container to extract built artifacts
CONTAINER_ID=$(docker create goodmem-java-client-builder)

# Extract all content from the container
docker cp "${CONTAINER_ID}:/app/build/libs/." "${DIST_DIR}/"

# Clean up container
docker rm "${CONTAINER_ID}" > /dev/null

# Check if build produced the JARs
if ls "${DIST_DIR}"/*.jar &>/dev/null; then
    echo "📦 Java client built successfully!"
    echo "📂 Output files available in: ${DIST_DIR}/"
else
    echo "⚠️ No build artifacts found. Build may have failed."
    exit 1
fi
