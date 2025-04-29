#!/bin/bash
#
# run_localhost.sh - Local Development Environment Manager for GoodMem
#
# This script provides a streamlined way to manage the local development environment
# for the GoodMem project using Docker Compose. It handles:
#
# - Starting the required services (PostgreSQL, MinIO, server, UI)
# - Setting up environment variables from a shared configuration file
# - Creating and mounting host directories for data persistence
# - Providing flexible options for different development workflows
#
# The script supports several modes:
# 1. Full stack mode (default): Runs all services in Docker
# 2. IDE development mode (--exclude-server): Runs only dependencies,
#    allowing you to run the server from your IDE/IntelliJ
# 3. API-only mode (--exclude-ui): Runs everything except the UI
#
# Configuration is sourced from config/local_dev.env, which contains shared
# settings used by both this script and the IntelliJ run configuration.
#
# USAGE:
#   ./run_localhost.sh                 # Start all services
#   ./run_localhost.sh --exclude-server # Start dependencies only (for IDE development)
#   ./run_localhost.sh --exclude-ui    # Start without the UI container
#   ./run_localhost.sh --reinit-db     # Reinitialize the database (destroys all data)
#   ./run_localhost.sh --help          # Show help message
#
# After changing the config/local_dev.env file, run ./config/update_intellij_config.sh
# to update your IntelliJ run configuration with the new values.
#
# Exit immediately if a command exits with a non-zero status.
set -e

# --- Parse Command Line Arguments ---
EXCLUDE_SERVER=false
EXCLUDE_UI=false
SHOW_HELP=false
REINIT_DB=false

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
    --reinit-db)
      REINIT_DB=true
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
  echo "  --reinit-db        Reinitialize the database (WARNING: destroys all data)"
  echo "                     Use this after schema changes to recreate the database"
  echo "  --help, -h         Show this help message"
  echo ""
  exit 0
fi

# --- Load Configuration Variables ---
# Source the shared configuration file
CONFIG_FILE="$(dirname "$0")/config/local_dev.env"
if [ -f "$CONFIG_FILE" ]; then
  echo "Loading configuration from $CONFIG_FILE"
  source "$CONFIG_FILE"
else
  echo "Warning: Configuration file $CONFIG_FILE not found. Using default values."
fi

# Use environment variables if set, otherwise use values from config file or defaults
export DATA_DIR_BASE="${DATA_DIR_BASE:-$HOME/data/goodmem}" # Base directory for host volumes
export POSTGRES_USER="${POSTGRES_USER:-goodmem_user}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-CHANGEME_pg_password}"
export POSTGRES_DB="${POSTGRES_DB:-goodmem_db}"
export MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-CHANGEME_minio_key}"
export MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-CHANGEME_minio_secret}"
export MINIO_BUCKET_NAME="${MINIO_BUCKET_NAME:-goodmem-content}"

# --- Ensure Data Directories Exist ---
# Create host directories before mounting them in containers
echo "Ensuring data directories exist under $DATA_DIR_BASE..."
mkdir -p "${DATA_DIR_BASE}/pgdata"
mkdir -p "${DATA_DIR_BASE}/minio_data"
echo "Data directories ready."
echo ""

# --- Handle Database Reinitialization if requested ---
if [[ "$REINIT_DB" == "true" ]]; then
  echo "WARNING: Database reinitialization requested!"
  echo "This will delete all data in the database and recreate the schema."
  echo ""
  read -p "Are you sure you want to continue? [y/N] " -n 1 -r
  echo ""
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "Stopping existing containers..."
    docker compose down
    
    echo "Removing database data directory..."
    echo "This may require sudo privileges to remove the PostgreSQL data directory."
    
    if [ -d "${DATA_DIR_BASE}/pgdata" ]; then
      # Check if we can write to the directory directly
      if [ -w "${DATA_DIR_BASE}/pgdata" ]; then
        rm -rf "${DATA_DIR_BASE}/pgdata"
      else
        # Try with sudo if direct removal fails
        echo "Using sudo to remove PostgreSQL data directory..."
        sudo rm -rf "${DATA_DIR_BASE}/pgdata"
      fi
    fi
    
    # Create fresh directory with current user ownership
    mkdir -p "${DATA_DIR_BASE}/pgdata"
    
    echo "Database directory cleared. The database will be recreated on startup."
    echo ""
  else
    echo "Database reinitialization cancelled."
    echo ""
    REINIT_DB=false
  fi
fi

# --- Start Docker Compose ---
echo "Starting Docker Compose stack..."
echo "Using configuration:"
echo "  Data Base Dir:  $DATA_DIR_BASE"
echo "  Postgres User:  $POSTGRES_USER"
echo "  Postgres DB:    $POSTGRES_DB"
echo "  MinIO Key:      $MINIO_ACCESS_KEY"
echo "  MinIO Bucket:   $MINIO_BUCKET_NAME"
if [[ "$REINIT_DB" == "true" ]]; then
  echo "  Database:      REINITIALIZED (all data destroyed)"
fi
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

# Print IntelliJ information if server is excluded
if [[ "$EXCLUDE_SERVER" == "true" ]]; then
  echo "For IntelliJ / local development, you can update your run configuration automatically:"
  echo "  ./config/update_intellij_config.sh"
  echo ""
  echo "This will update the IntelliJ run configuration with these environment variables:"
  echo "  DB_URL=jdbc:postgresql://localhost:5432/${POSTGRES_DB}"
  echo "  DB_USER=${POSTGRES_USER}"
  echo "  DB_PASSWORD=${POSTGRES_PASSWORD}"
  echo "  MINIO_ENDPOINT=http://localhost:9000"
  echo "  MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}"
  echo "  MINIO_SECRET_KEY=${MINIO_SECRET_KEY}"
  echo "  MINIO_BUCKET=${MINIO_BUCKET_NAME}"
  echo ""
  echo "Note: Run the update script after making changes to config/local_dev.env"
  echo ""
fi