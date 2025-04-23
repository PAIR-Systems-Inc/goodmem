package cmd

import (
	"fmt"

	"github.com/spf13/cobra"
)

const version = "v0.1.0"

func init() {
	rootCmd.AddCommand(versionCmd)
}

var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "Print the version number of GoodMem CLI",
	Long:  `Display the version and build information for the GoodMem CLI.`,
	Run: func(cmd *cobra.Command, args []string) {
		fmt.Printf("GoodMem CLI %s (commit: %s)\n", version, gitCommit)
	},
}