package cmd

import (
	"testing"
)

func TestRootCommand(t *testing.T) {
	// Basic test to ensure the root command can be initialized
	if rootCmd == nil {
		t.Error("Root command should not be nil")
	}
}