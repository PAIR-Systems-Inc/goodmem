package goodmemclient

import (
	"testing"
)

func TestClientCreation(t *testing.T) {
	client := NewClient("http://localhost:8080")
	if client == nil {
		t.Fatal("Expected non-nil client")
	}
	if client.serverEndpoint != "http://localhost:8080" {
		t.Fatalf("Expected serverEndpoint to be 'http://localhost:8080', got '%s'", client.serverEndpoint)
	}
}
