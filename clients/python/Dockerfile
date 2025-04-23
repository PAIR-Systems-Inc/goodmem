# Build stage for Python client
FROM python:3.8-slim

WORKDIR /app

# Install uv, which is used for package management
RUN pip install uv

# Copy the source code
COPY goodmem_client/ /app/goodmem_client/
COPY tests/ /app/tests/
COPY pyproject.toml /app/

# Create virtual environment and install dependencies
RUN uv venv /app/.venv \
    && . /app/.venv/bin/activate \
    && uv pip install -e ".[dev]"

# Run tests
RUN . /app/.venv/bin/activate && python -m pytest

# Package the client library
RUN . /app/.venv/bin/activate && python -m build

# The output will be in /app/dist/ (both .whl and .tar.gz files)
