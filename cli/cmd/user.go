package cmd

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/bufbuild/connect-go"
	v1 "github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	v1connect "github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
)

// nolint:unused
var userID string

// userCmd represents the user command
var userCmd = &cobra.Command{
	Use:   "user",
	Short: "Manage GoodMem users",
	Long:  `Get information about users in the GoodMem service.`,
}

// getUserCmd represents the get command
var getUserCmd = &cobra.Command{
	Use:   "get [user-id]",
	Short: "Get user details",
	Long:  `Get details for a specific user by their ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		userID := args[0]
		client := v1connect.NewUserServiceClient(
			http.DefaultClient,
			serverAddress,
		)

		req := connect.NewRequest(&v1.GetUserRequest{
			UserId: []byte(userID),
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.GetUser(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error getting user: %w", err)
		}

		// Print the user details
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

func init() {
	rootCmd.AddCommand(userCmd)
	userCmd.AddCommand(getUserCmd)

	// Global flags for all user commands
	userCmd.PersistentFlags().StringVar(&serverAddress, "server", "http://localhost:9090", "GoodMem server address")
}