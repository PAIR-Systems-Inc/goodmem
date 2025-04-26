#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Configuration Variables ---
# Use environment variables if set, otherwise use defaults.
# IMPORTANT: Change default passwords for any real use!
export DATA_DIR_BASE="${DATA_DIR_BASE:-$HOME/data/goodmem}" # Base directory for host volumes
export POSTGRES_USER="${POSTGRES_USER:-goodmem_user}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-CHANGEME_pg_password}" # !! CHANGE THIS !!
export POSTGRES_DB="${POSTGRES_DB:-goodmem_db}"
export MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-CHANGEME_minio_key}"     # !! CHANGE THIS !!
export MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-CHANGEME_minio_secret}" # !! CHANGE THIS !!
export MINIO_BUCKET_NAME="${MINIO_BUCKET_NAME:-goodmem-content}"

# --- Ensure Data Directories Exist ---
# Create host directories before mounting them in containers
echo "Ensuring data directories exist under $DATA_DIR_BASE..."
mkdir -p "${DATA_DIR_BASE}/pgdata"
mkdir -p "${DATA_DIR_BASE}/minio_data"
echo "Data directories ready."
echo ""

# --- Start Docker Compose ---
echo "Starting Docker Compose stack..."
echo "Using configuration:"
echo "  Data Base Dir:  $DATA_DIR_BASE"
echo "  Postgres User:  $POSTGRES_USER"
echo "  Postgres DB:    $POSTGRES_DB"
echo "  MinIO Key:      $MINIO_ACCESS_KEY"
echo "  MinIO Bucket:   $MINIO_BUCKET_NAME"
echo ""
echo "WARN: Using default passwords if not overridden via environment variables."
echo "      POSTGRES_PASSWORD='${POSTGRES_PASSWORD}'"
echo "      MINIO_SECRET_KEY='${MINIO_SECRET_KEY}'"
echo ""

# Bring up the stack in detached mode (-d)
# Remove '-d' if you want to see logs in the foreground
docker compose up -d --remove-orphans

echo ""
echo "Stack started."
echo "Access points:"
echo "  - Postgres DB: localhost:5432 (User: ${POSTGRES_USER})"
echo "  - MinIO API:   http://localhost:9000 (Key: ${MINIO_ACCESS_KEY})"
echo "  - MinIO Console: http://localhost:9001 (User: ${MINIO_ACCESS_KEY})"
echo "  - Server API:  http://localhost:8080"
echo "  - Server gRPC: localhost:9090"
echo "  - UI:          http://localhost:5173"
echo ""
echo "To view logs: docker compose logs -f"
echo "To stop:      docker compose down"