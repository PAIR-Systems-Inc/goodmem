#!/bin/bash
set -e

echo "Building all GoodMem clients using Docker for reproducible builds..."

# Get the directory of this script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Track success/failure for each client
PYTHON_STATUS=0
JAVA_STATUS=0
DOTNET_STATUS=0
GO_STATUS=0
JS_STATUS=0

# Build Python client
echo -e "\n=== Building Python Client ==="
(cd "$SCRIPT_DIR/python" && ./build.sh) || PYTHON_STATUS=$?

# Build Java client
echo -e "\n=== Building Java Client ==="
(cd "$SCRIPT_DIR/java" && ./build.sh) || JAVA_STATUS=$?

# Build .NET client
echo -e "\n=== Building .NET Client ==="
(cd "$SCRIPT_DIR/dotnet" && ./build.sh) || DOTNET_STATUS=$?

# Build Go client
echo -e "\n=== Building Go Client ==="
(cd "$SCRIPT_DIR/go" && ./build.sh) || GO_STATUS=$?

# Build JavaScript client
echo -e "\n=== Building JavaScript Client ==="
(cd "$SCRIPT_DIR/js" && ./build.sh) || JS_STATUS=$?

# Print summary
echo -e "\n=== Build Summary ==="
echo "Python client: $([ $PYTHON_STATUS -eq 0 ] && echo '✅ SUCCESS' || echo '❌ FAILED')"
echo "Java client: $([ $JAVA_STATUS -eq 0 ] && echo '✅ SUCCESS' || echo '❌ FAILED')"
echo ".NET client: $([ $DOTNET_STATUS -eq 0 ] && echo '✅ SUCCESS' || echo '❌ FAILED')"
echo "Go client: $([ $GO_STATUS -eq 0 ] && echo '✅ SUCCESS' || echo '❌ FAILED')"
echo "JavaScript client: $([ $JS_STATUS -eq 0 ] && echo '✅ SUCCESS' || echo '❌ FAILED')"

# Calculate total build status
BUILD_STATUS=0
[ $PYTHON_STATUS -ne 0 ] && BUILD_STATUS=1
[ $JAVA_STATUS -ne 0 ] && BUILD_STATUS=1
[ $DOTNET_STATUS -ne 0 ] && BUILD_STATUS=1
[ $GO_STATUS -ne 0 ] && BUILD_STATUS=1
[ $JS_STATUS -ne 0 ] && BUILD_STATUS=1

# Exit with appropriate status
if [ $BUILD_STATUS -eq 0 ]; then
  echo -e "\n✅ All clients built successfully!"
  exit 0
else
  echo -e "\n❌ Some clients failed to build. See summary above."
  exit 1
fi