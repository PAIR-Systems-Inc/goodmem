#!/bin/bash
#
# update_intellij_config.sh - IntelliJ Run Configuration Updater for GoodMem
#
# This script automatically updates the IntelliJ run configuration with
# environment variables from the shared config/local_dev.env file. It ensures
# that your IntelliJ configuration stays in sync with the Docker Compose
# environment variables.
#
# The script:
# - Reads configuration values from config/local_dev.env
# - Generates or updates the .idea/runConfigurations/Main.xml file with proper environment variables
# - Sets up the Java main class and other configuration parameters
#
# USAGE:
#   ./config/update_intellij_config.sh
#
# NOTES:
# - Run this script whenever you make changes to config/local_dev.env
# - The script completely recreates the run configuration file
# - IntelliJ will automatically detect the changes the next time you open the project
#
# This script works together with run_localhost.sh, which uses the same configuration
# for Docker Compose services, ensuring a consistent development environment.
#
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${SCRIPT_DIR}/local_dev.env"
RUN_CONFIG_DIR="${SCRIPT_DIR}/../.idea/runConfigurations"
MAIN_CONFIG="${RUN_CONFIG_DIR}/Main.xml"

echo "Updating IntelliJ run configuration from ${CONFIG_FILE}..."

# Ensure the runConfigurations directory exists
mkdir -p "${RUN_CONFIG_DIR}"

# Source the configuration file to get variable values
source "${CONFIG_FILE}"

# Check if Main.xml exists
if [ -f "${MAIN_CONFIG}" ]; then
  # We'll create a completely new file rather than trying to modify in place
  echo "Updating existing configuration file: ${MAIN_CONFIG}"
  rm -f "${MAIN_CONFIG}"
else
  echo "Creating new configuration file: ${MAIN_CONFIG}"
fi

# Create the file from scratch with the current environment variables
cat > "${MAIN_CONFIG}" << EOF
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="Main" type="Application" factoryName="Application" nameIsGenerated="true">
    <envs>
      <env name="DB_URL" value="jdbc:postgresql://localhost:5432/${POSTGRES_DB}" />
      <env name="DB_USER" value="${POSTGRES_USER}" />
      <env name="DB_PASSWORD" value="${POSTGRES_PASSWORD}" />
      <env name="MINIO_ENDPOINT" value="http://localhost:9000" />
      <env name="MINIO_ACCESS_KEY" value="${MINIO_ACCESS_KEY}" />
      <env name="MINIO_SECRET_KEY" value="${MINIO_SECRET_KEY}" />
      <env name="MINIO_BUCKET" value="${MINIO_BUCKET_NAME}" />
    </envs>
    <option name="MAIN_CLASS_NAME" value="com.goodmem.Main" />
    <module name="goodmem.server.main" />
    <extension name="coverage">
      <pattern>
        <option name="PATTERN" value="com.goodmem.*" />
        <option name="ENABLED" value="true" />
      </pattern>
    </extension>
    <method v="2">
      <option name="Make" enabled="true" />
    </method>
  </configuration>
</component>
EOF

echo "IntelliJ run configuration updated successfully!"
echo "Environment variables set:"
echo "  DB_URL=jdbc:postgresql://localhost:5432/${POSTGRES_DB}"
echo "  DB_USER=${POSTGRES_USER}"
echo "  DB_PASSWORD=${POSTGRES_PASSWORD}"
echo "  MINIO_ENDPOINT=http://localhost:9000"
echo "  MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}"
echo "  MINIO_SECRET_KEY=${MINIO_SECRET_KEY}"
echo "  MINIO_BUCKET=${MINIO_BUCKET_NAME}"