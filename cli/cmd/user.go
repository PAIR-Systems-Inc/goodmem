package cmd

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/bufbuild/connect-go"
	v1 "github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	v1connect "github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
)

// Variables for the user command flags
var (
	userEmail string
)

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
	Long: `Get details for a user in the GoodMem system.

If called with no arguments, retrieves the current user (based on API key).
Can be called with either a user ID or email address to look up a specific user.`,
	Args: cobra.MaximumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		// Silence usage for server-side errors (no real client-side validation to do)
		cmd.SilenceUsage = true
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewUserServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Initialize the request
		request := &v1.GetUserRequest{}
		
		// Set the request parameters based on what was provided
		if len(args) > 0 {
			// User ID was provided as positional argument
			request.UserId = []byte(args[0])
		} else if userEmail != "" {
			// Email was provided as a flag - need to convert to pointer
			email := userEmail // Create a local copy
			request.Email = &email
		}
		// If neither provided, request will be empty which will return the current user

		// Create the request
		req := connect.NewRequest(request)

		// Add API key header from global config
		if apiKey != "" {
			req.Header().Set("x-api-key", apiKey)
		} else {
			return fmt.Errorf("API key is required. Set it using the --api-key flag or GOODMEM_API_KEY environment variable")
		}

		resp, err := client.GetUser(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				return fmt.Errorf("%v", connectErr.Message())
			}
			return fmt.Errorf("unexpected error: %w", err)
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
	
	// Add email flag to the get command
	getUserCmd.Flags().StringVar(&userEmail, "email", "", "Email address of the user to look up")
}