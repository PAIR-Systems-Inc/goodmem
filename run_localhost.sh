#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Parse Command Line Arguments ---
EXCLUDE_SERVER=false
EXCLUDE_UI=false
SHOW_HELP=false

# Process command-line options
while [[ $# -gt 0 ]]; do
  case "$1" in
    --exclude-server)
      EXCLUDE_SERVER=true
      shift
      ;;
    --exclude-ui)
      EXCLUDE_UI=true
      shift
      ;;
    --help|-h)
      SHOW_HELP=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      SHOW_HELP=true
      shift
      ;;
  esac
done

# Show help if requested
if [[ "$SHOW_HELP" == "true" ]]; then
  echo "Usage: $0 [OPTIONS]"
  echo ""
  echo "Options:"
  echo "  --exclude-server   Start dependencies only (DB, MinIO) without the server"
  echo "                     Use this when running the server from IDE/IntelliJ"
  echo "  --exclude-ui       Start without the UI container"
  echo "  --help, -h         Show this help message"
  echo ""
  exit 0
fi

# --- Configuration Variables ---
# Use environment variables if set, otherwise use defaults.
# IMPORTANT: Change default passwords for any real use!
export DATA_DIR_BASE="${DATA_DIR_BASE:-$HOME/data/goodmem}" # Base directory for host volumes
export POSTGRES_USER="${POSTGRES_USER:-pg_goodmem}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-ayk317gk}"
export POSTGRES_DB="${POSTGRES_DB:-goodmem_db}"
export MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minio_goodmem}"
export MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-frkefi2b}"
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

# Determine which services to include/exclude
SERVICES="db minio"
if [[ "$EXCLUDE_SERVER" == "false" ]]; then
  SERVICES="$SERVICES server"
else
  echo "Excluding server service (for IDE development)"
fi

if [[ "$EXCLUDE_UI" == "false" && "$EXCLUDE_SERVER" == "false" ]]; then
  SERVICES="$SERVICES ui"
else
  if [[ "$EXCLUDE_UI" == "true" ]]; then
    echo "Excluding UI service"
  elif [[ "$EXCLUDE_SERVER" == "true" ]]; then
    echo "Excluding UI service (because server is excluded)"
  fi
fi

echo "Services to start: $SERVICES"
echo ""

# Bring up the stack in detached mode (-d)
docker compose up -d --remove-orphans $SERVICES

echo ""
echo "Stack started."
echo "Access points:"
echo "  - Postgres DB: localhost:5432 (User: ${POSTGRES_USER})"
echo "  - MinIO API:   http://localhost:9000 (Key: ${MINIO_ACCESS_KEY})"
echo "  - MinIO Console: http://localhost:9001 (User: ${MINIO_ACCESS_KEY})"

if [[ "$EXCLUDE_SERVER" == "false" ]]; then
  echo "  - Server API:  http://localhost:8080"
  echo "  - Server gRPC: localhost:9090"
  
  if [[ "$EXCLUDE_UI" == "false" ]]; then
    echo "  - UI:          http://localhost:5173"
  fi
fi

echo ""
echo "To view logs: docker compose logs -f"
echo "To stop:      docker compose down"
echo ""

# Print IntelliJ environment variables if server is excluded
if [[ "$EXCLUDE_SERVER" == "true" ]]; then
  echo "For IntelliJ / local development, add these environment variables to your Run Configuration:"
  echo "DB_URL=jdbc:postgresql://localhost:5432/${POSTGRES_DB}"
  echo "DB_USER=${POSTGRES_USER}"
  echo "DB_PASSWORD=${POSTGRES_PASSWORD}"
  echo "MINIO_ENDPOINT=http://localhost:9000"
  echo "MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}"
  echo "MINIO_SECRET_KEY=${MINIO_SECRET_KEY}"
  echo "MINIO_BUCKET=${MINIO_BUCKET_NAME}"
  echo ""
fi