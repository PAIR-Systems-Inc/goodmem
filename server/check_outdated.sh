#!/bin/bash
set -e

# Define paths
SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SERVER_DIR}/.." && pwd)"

# Change to the project root directory
cd "${ROOT_DIR}"

echo "Checking for outdated dependencies..."
./gradlew dependencyUpdates -Drevision=release

echo -e "\nTo update dependencies, edit the build.gradle.kts file with the versions shown above."