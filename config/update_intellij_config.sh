#!/bin/bash

# This script updates the IntelliJ run configuration with environment variables from local_dev.env
# Run this script after making changes to local_dev.env

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

# Create envs block for IntelliJ XML
ENV_BLOCK="    <envs>
      <env name=\"DB_URL\" value=\"jdbc:postgresql://localhost:5432/${POSTGRES_DB}\" />
      <env name=\"DB_USER\" value=\"${POSTGRES_USER}\" />
      <env name=\"DB_PASSWORD\" value=\"${POSTGRES_PASSWORD}\" />
      <env name=\"MINIO_ENDPOINT\" value=\"http://localhost:9000\" />
      <env name=\"MINIO_ACCESS_KEY\" value=\"${MINIO_ACCESS_KEY}\" />
      <env name=\"MINIO_SECRET_KEY\" value=\"${MINIO_SECRET_KEY}\" />
      <env name=\"MINIO_BUCKET\" value=\"${MINIO_BUCKET_NAME}\" />
    </envs>"

# Check if Main.xml exists
if [ -f "${MAIN_CONFIG}" ]; then
  # Update existing file
  echo "Updating existing configuration file: ${MAIN_CONFIG}"
  # Replace the env block using sed
  if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS sed requires an empty string for -i flag
    sed -i '' -e "/<envs>/,/<\/envs>/c\\
${ENV_BLOCK}" "${MAIN_CONFIG}"
  else
    # Linux sed
    sed -i -e "/<envs>/,/<\/envs>/c\\
${ENV_BLOCK}" "${MAIN_CONFIG}"
  fi
else
  # Create a new file
  echo "Creating new configuration file: ${MAIN_CONFIG}"
  cat > "${MAIN_CONFIG}" << EOF
<component name="ProjectRunConfigurationManager">
  <configuration default="false" name="Main" type="Application" factoryName="Application" nameIsGenerated="true">
${ENV_BLOCK}
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
fi

echo "IntelliJ run configuration updated successfully!"
echo "Environment variables set:"
echo "  DB_URL=jdbc:postgresql://localhost:5432/${POSTGRES_DB}"
echo "  DB_USER=${POSTGRES_USER}"
echo "  DB_PASSWORD=${POSTGRES_PASSWORD}"
echo "  MINIO_ENDPOINT=http://localhost:9000"
echo "  MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}"
echo "  MINIO_SECRET_KEY=${MINIO_SECRET_KEY}"
echo "  MINIO_BUCKET=${MINIO_BUCKET_NAME}"