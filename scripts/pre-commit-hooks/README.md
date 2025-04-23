# Pre-Commit Hooks

This directory contains git pre-commit hooks that can be used to ensure code quality before committing changes.

## Available Hooks

- `check_java_format.sh`: Ensures that all Java files are properly formatted according to Google Java Format

## How to Use

To use a pre-commit hook, you can either:

### Option 1: Symlink individual hooks

Create a symbolic link to the hook you want to use:

```bash
ln -sf ../../scripts/pre-commit-hooks/check_java_format.sh .git/hooks/pre-commit
```

### Option 2: Use a combined pre-commit hook

Create a `.git/hooks/pre-commit` file that calls all the hooks you want to use:

```bash
#!/bin/bash
set -e

# Run Java format check
./scripts/pre-commit-hooks/check_java_format.sh

# Add additional hooks here
# ./scripts/pre-commit-hooks/another_hook.sh
```

Then make it executable:

```bash
chmod +x .git/hooks/pre-commit
```

## Bypassing Hooks

If you need to bypass the pre-commit hooks for a specific commit, you can use:

```bash
git commit --no-verify
```