package cmd

import (
	"testing"
)

func TestVersionCommand(t *testing.T) {
	// Basic test to ensure the version command can be initialized
	if versionCmd == nil {
		t.Error("Version command should not be nil")
	}
}