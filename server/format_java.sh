#!/bin/bash
set -e

# Constants
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TOOLS_DIR="${ROOT_DIR}/.tools"
LATEST_VERSION="1.21.0"
JAR_NAME="google-java-format-${LATEST_VERSION}-all-deps.jar"
JAR_PATH="${TOOLS_DIR}/${JAR_NAME}"
JAR_URL="https://github.com/google/google-java-format/releases/download/v${LATEST_VERSION}/${JAR_NAME}"

# Command line arguments
FIX_MODE=false
CHECK_MODE=false
VERBOSE=false
ARGS=()

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    --fix)
      FIX_MODE=true
      shift
      ;;
    --check)
      CHECK_MODE=true
      shift
      ;;
    --verbose)
      VERBOSE=true
      shift
      ;;
    -*)
      ARGS+=("$1")
      shift
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done

# Function to log messages if verbose mode is enabled
log() {
  if [[ "$VERBOSE" == true ]]; then
    echo "➡️  $1"
  fi
}

# Create tools directory if it doesn't exist
mkdir -p "${TOOLS_DIR}"

# Download the formatter if it doesn't exist
if [ ! -f "${JAR_PATH}" ]; then
  echo "Downloading Google Java Format ${LATEST_VERSION}..."
  if command -v curl &>/dev/null; then
    curl -L -o "${JAR_PATH}" "${JAR_URL}"
  elif command -v wget &>/dev/null; then
    wget -O "${JAR_PATH}" "${JAR_URL}"
  else
    echo "Error: Neither curl nor wget is available. Please install one of them."
    exit 1
  fi
  echo "✅ Google Java Format downloaded to ${JAR_PATH}"
fi

# Make sure we have the gitignore entry
if ! grep -q "^/.tools/" "${ROOT_DIR}/.gitignore" 2>/dev/null; then
  echo -e "\n# Tool dependencies\n/.tools/" >> "${ROOT_DIR}/.gitignore"
  echo "✅ Added .tools directory to .gitignore"
fi

# Verify Java version
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ -z "$JAVA_VERSION" ] || [ "$JAVA_VERSION" -lt 17 ]; then
  echo "❌ Error: Google Java Format requires Java 17 or newer. Found version: $JAVA_VERSION"
  echo "   Please install a compatible JDK version."
  exit 1
fi

# Function to run the formatter
run_formatter() {
  local target="$1"
  local replace_flag="$2"
  
  # Prepare the command
  local cmd=(java)
  
  # Add the required JVM flags for Java 16+
  cmd+=(--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED)
  cmd+=(--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED)
  cmd+=(--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED)
  cmd+=(--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED)
  cmd+=(--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED)
  cmd+=(--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED)
  
  # Add the jar and any additional args
  cmd+=(-jar "${JAR_PATH}" "${ARGS[@]}")
  
  # Add the replace flag if specified
  if [ -n "$replace_flag" ]; then
    cmd+=("$replace_flag")
  fi
  
  # Add the target file or directory
  if [ -d "$target" ]; then
    # If target is a directory, find all Java files
    log "Finding Java files in $target..."
    readarray -t java_files < <(find "$target" -name "*.java" -type f)
    
    if [ ${#java_files[@]} -eq 0 ]; then
      echo "⚠️  No Java files found in $target"
      return 0
    fi
    
    log "Found ${#java_files[@]} Java files."
    cmd+=("${java_files[@]}")
  else
    # If target is a file, just add it
    cmd+=("$target")
  fi
  
  # Log the command if verbose
  if [[ "$VERBOSE" == true ]]; then
    echo "Running: ${cmd[*]}"
  fi
  
  # Run the command
  if [ "$CHECK_MODE" == true ]; then
    local temp_file
    temp_file=$(mktemp)
    "${cmd[@]}" > "$temp_file"
    
    if ! cmp -s "$target" "$temp_file"; then
      echo "❌ $target needs formatting"
      if [ "$VERBOSE" == true ]; then
        diff -u "$target" "$temp_file"
      fi
      rm "$temp_file"
      return 1
    fi
    
    echo "✅ $target is properly formatted"
    rm "$temp_file"
    return 0
  else
    "${cmd[@]}"
    return 0
  fi
}

# Process the arguments
if [ ${#ARGS[@]} -eq 0 ]; then
  # Default to the server src directory if no args provided
  target="${SCRIPT_DIR}/src"
  echo "No target specified, defaulting to: $target"
else
  target="${ARGS[-1]}"  # Use the last argument as the target
  unset 'ARGS[-1]'      # Remove the target from args array
fi

# Determine if we should replace files in-place
replace_flag=""
if [ "$FIX_MODE" == true ]; then
  replace_flag="--replace"
  echo "Running in fix mode (will modify files in-place)"
elif [ "$CHECK_MODE" == true ]; then
  echo "Running in check mode (will not modify files)"
else
  echo "Running in default mode (output to stdout)"
fi

# Run the formatter
if [ "$CHECK_MODE" == true ]; then
  failures=0
  
  if [ -d "$target" ]; then
    # Process directory
    readarray -t java_files < <(find "$target" -name "*.java" -type f)
    for file in "${java_files[@]}"; do
      if ! run_formatter "$file" "$replace_flag"; then
        ((failures++))
      fi
    done
  else
    # Process single file
    if ! run_formatter "$target" "$replace_flag"; then
      ((failures++))
    fi
  fi
  
  if [ "$failures" -gt 0 ]; then
    echo "❌ $failures file(s) need formatting"
    exit 1
  else
    echo "✅ All files are properly formatted"
    exit 0
  fi
else
  run_formatter "$target" "$replace_flag"
  echo "✅ Formatting complete"
  exit 0
fi