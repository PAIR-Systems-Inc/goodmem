package cmd

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/bufbuild/connect-go"
	v1 "github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	v1connect "github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
)

// nolint:unused
var (
	apiKeyID   string
	keyStatus  string
	apiKeyLabels []string
)

// apikeyCmd represents the apikey command
var apikeyCmd = &cobra.Command{
	Use:   "apikey",
	Short: "Manage GoodMem API keys",
	Long:  `Create, list, update, and delete API keys in the GoodMem service.`,
}

// createApiKeyCmd represents the create command
var createApiKeyCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new API key",
	Long:  `Create a new API key in the GoodMem service.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewApiKeyServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse labels from key=value format
		labelsMap := make(map[string]string)
		for _, label := range apiKeyLabels {
			parts := strings.SplitN(label, "=", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid label format: %s (should be key=value)", label)
			}
			labelsMap[parts[0]] = parts[1]
		}

		req := connect.NewRequest(&v1.CreateApiKeyRequest{
			Labels: labelsMap,
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.CreateApiKey(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error creating API key: %w", err)
		}

		// Print the created API key with raw key
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))

		// Special warning about saving the raw key
		fmt.Println("\nIMPORTANT: Save the raw_api_key value. It will not be shown again.")
		return nil
	},
}

// listApiKeysCmd represents the list command
var listApiKeysCmd = &cobra.Command{
	Use:   "list",
	Short: "List API keys",
	Long:  `List all API keys for the authenticated user in the GoodMem service.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewApiKeyServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.ListApiKeysRequest{})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.ListApiKeys(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error listing API keys: %w", err)
		}

		// Print the API keys
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// updateApiKeyCmd represents the update command
var updateApiKeyCmd = &cobra.Command{
	Use:   "update [api-key-id]",
	Short: "Update an API key",
	Long:  `Update an existing API key in the GoodMem service.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		apiKeyID := args[0]
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewApiKeyServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse labels from key=value format
		labelsMap := make(map[string]string)
		for _, label := range apiKeyLabels {
			parts := strings.SplitN(label, "=", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid label format: %s (should be key=value)", label)
			}
			labelsMap[parts[0]] = parts[1]
		}

		// Create update request
		updateReq := &v1.UpdateApiKeyRequest{
			ApiKeyId: []byte(apiKeyID),
			Labels:   labelsMap,
		}

		// Set status if provided
		if keyStatus != "" {
			switch strings.ToUpper(keyStatus) {
			case "ACTIVE":
				updateReq.Status = v1.Status_ACTIVE
			case "INACTIVE":
				updateReq.Status = v1.Status_INACTIVE
			default:
				return fmt.Errorf("invalid status: %s (should be ACTIVE or INACTIVE)", keyStatus)
			}
		}

		req := connect.NewRequest(updateReq)

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.UpdateApiKey(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error updating API key: %w", err)
		}

		// Print the updated API key
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// deleteApiKeyCmd represents the delete command
var deleteApiKeyCmd = &cobra.Command{
	Use:   "delete [api-key-id]",
	Short: "Delete an API key",
	Long:  `Delete an API key from the GoodMem service by its ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		apiKeyID := args[0]
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewApiKeyServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.DeleteApiKeyRequest{
			ApiKeyId: []byte(apiKeyID),
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		_, err := client.DeleteApiKey(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error deleting API key: %w", err)
		}

		fmt.Printf("API key %s deleted\n", apiKeyID)
		return nil
	},
}

func init() {
	rootCmd.AddCommand(apikeyCmd)
	apikeyCmd.AddCommand(createApiKeyCmd)
	apikeyCmd.AddCommand(listApiKeysCmd)
	apikeyCmd.AddCommand(updateApiKeyCmd)
	apikeyCmd.AddCommand(deleteApiKeyCmd)

	// Global flags for all apikey commands
	apikeyCmd.PersistentFlags().StringVar(&serverAddress, "server", "http://localhost:9090", "GoodMem server address (gRPC API)")

	// Flags for create
	createApiKeyCmd.Flags().StringSliceVar(&apiKeyLabels, "label", []string{}, "Labels in key=value format (can be specified multiple times)")

	// Flags for update
	updateApiKeyCmd.Flags().StringVar(&keyStatus, "status", "", "Status of the API key (ACTIVE or INACTIVE)")
	updateApiKeyCmd.Flags().StringSliceVar(&apiKeyLabels, "label", []string{}, "Labels in key=value format (can be specified multiple times)")
}