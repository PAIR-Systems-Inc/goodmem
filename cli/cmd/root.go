package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var (
    serverAddress string // << central definition
)

var rootCmd = &cobra.Command{
	Use:   "goodmem",
	Short: "GoodMem CLI provides command-line access to memory APIs",
	Long: `GoodMem CLI is a command-line interface for interacting with 
the GoodMem memory management APIs. It provides various commands
for manipulating and inspecting memory contents.`,
}

func init() {
    rootCmd.PersistentFlags().
        StringVar(&serverAddress,
            "server",
            "https://localhost:9090",
            "GoodMem server address (gRPC API)")
}

var gitCommit string

func Execute(commit string) {
	gitCommit = commit
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
