#!/bin/bash
set -e

# This pre-commit hook checks if Java files are correctly formatted according to Google Java Format

# Exit if not in a git repository
if ! git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
  echo "Not in a git repository."
  exit 1
fi

# Find the project root directory
PROJECT_ROOT=$(git rev-parse --show-toplevel)
cd "$PROJECT_ROOT"

# Create scripts directory if it doesn't exist
mkdir -p scripts/pre-commit-hooks

# Path to the Java formatter script
FORMATTER_SCRIPT="$PROJECT_ROOT/server/format_java.sh"

if [ ! -f "$FORMATTER_SCRIPT" ]; then
  echo "Java formatter script not found at: $FORMATTER_SCRIPT"
  exit 1
fi

# Only check Java files that are staged for commit
STAGED_JAVA_FILES=$(git diff --cached --name-only --diff-filter=ACMR | grep "\.java$" || true)

if [ -z "$STAGED_JAVA_FILES" ]; then
  # No Java files to check
  exit 0
fi

# Check each staged Java file
UNFORMATTED_FILES=()
for FILE in $STAGED_JAVA_FILES; do
  if [ -f "$FILE" ]; then
    if ! "$FORMATTER_SCRIPT" --check "$FILE" > /dev/null 2>&1; then
      UNFORMATTED_FILES+=("$FILE")
    fi
  fi
done

# If there are unformatted files, show error message
if [ ${#UNFORMATTED_FILES[@]} -gt 0 ]; then
  echo "❌ The following files are not formatted according to Google Java Format:"
  printf "   %s\n" "${UNFORMATTED_FILES[@]}"
  echo ""
  echo "Please format these files with:"
  echo "  $FORMATTER_SCRIPT --fix <file>"
  echo ""
  echo "You can also run the formatter on all changed files with:"
  echo "  $FORMATTER_SCRIPT --fix \$(git diff --cached --name-only --diff-filter=ACMR | grep \"\.java$\")"
  echo ""
  echo "To bypass this check, use git commit with --no-verify"
  exit 1
fi

exit 0