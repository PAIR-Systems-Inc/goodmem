# GoodMem - Python Client

A Python client library for interacting with GoodMem services.

## Installation

```bash
uv pip install goodmem-client
```

## Usage

```python
from goodmem_client import Client

# Initialize client with server endpoint
client = Client("http://localhost:8080")
```

## Building

```bash
uv pip install -e .
```

## Running Tests

```bash
uv venv
uv pip install -e ".[dev]"
python -m pytest
```
