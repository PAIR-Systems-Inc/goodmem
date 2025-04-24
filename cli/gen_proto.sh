#!/bin/bash
set -e

# Define paths
CLI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${CLI_DIR}/.." && pwd)"
PROTO_DIR="${ROOT_DIR}/proto"
GEN_DIR="${CLI_DIR}/gen"

# Ensure gen directory exists and writable
mkdir -p "${GEN_DIR}"
chmod -R u+w "${GEN_DIR}" 2>/dev/null || true

# Let's use the Go CLI client approach and generate the code directly
echo "==> Installing protoc plugins locally..."
go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.6
go install github.com/bufbuild/connect-go/cmd/protoc-gen-connect-go@v1.10.0

# Add Go bin to PATH
export PATH="$HOME/go/bin:$PATH"
echo "PATH is now: $PATH"

# Run protoc directly
echo "==> Generating protobuf code..."
protoc -I="${PROTO_DIR}" \
  --go_out="${GEN_DIR}" --go_opt=paths=source_relative \
  --connect-go_out="${GEN_DIR}" --connect-go_opt=paths=source_relative \
  "${PROTO_DIR}/goodmem/v1/space.proto" \
  "${PROTO_DIR}/goodmem/v1/user.proto" \
  "${PROTO_DIR}/goodmem/v1/memory.proto" \
  "${PROTO_DIR}/goodmem/v1/apikey.proto"

echo "==> Proto generation complete!"
echo "Generated files are in: ${GEN_DIR}"