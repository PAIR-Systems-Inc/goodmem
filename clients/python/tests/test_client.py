import pytest
from goodmem_client import Client

def test_client_creation():
    client = Client("http://localhost:8080")
    assert client._server_endpoint == "http://localhost:8080"
