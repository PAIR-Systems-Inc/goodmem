package cmd

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/bufbuild/connect-go"
	"github.com/google/uuid"
	v1 "github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	v1connect "github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
)

// nolint:unused
var (
	apiKeyID   string
	keyStatus  string
	apiKeyLabels []string
	apikeyOutputFormat string
	apiKeyLabelUpdateStrategy string // "replace" or "merge", similar to space implementation
	// Use the shared labelUpdateStrategy variable from space.go
	// Commented out to avoid redeclaration: labelUpdateStrategy string
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
		// Silence usage for server-side errors
		cmd.SilenceUsage = true

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

		// Add API key header from global config
		if apiKey != "" {
			req.Header().Set("x-api-key", apiKey)
		} else {
			return fmt.Errorf("API key is required. Set it using the --api-key flag or GOODMEM_API_KEY environment variable")
		}

		resp, err := client.CreateApiKey(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				return fmt.Errorf("%v", connectErr.Message())
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Output the result based on format
		switch strings.ToLower(apikeyOutputFormat) {
		case "json":
			// Format with our JSON formatter
			jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
			if err != nil {
				return fmt.Errorf("error formatting response as JSON: %w", err)
			}
			fmt.Println(string(jsonBytes))

		case "table":
			// Output as table
			apiKey := resp.Msg.ApiKeyMetadata
			fmt.Println("API KEY CREATED SUCCESSFULLY")
			fmt.Println("============================")
			fmt.Printf("API Key ID:   %s\n", formatUUID(apiKey.ApiKeyId))
			fmt.Printf("Key Prefix:   %s\n", apiKey.KeyPrefix)
			fmt.Printf("Status:       %s\n", apiKey.Status)

			if len(apiKey.Labels) > 0 {
				fmt.Println("Labels:")
				for k, v := range apiKey.Labels {
					fmt.Printf("  %s: %s\n", k, v)
				}
			}

			if apiKey.ExpiresAt != nil {
				fmt.Printf("Expires At:   %s\n", formatTimestamp(apiKey.ExpiresAt))
			}

			fmt.Printf("Created At:   %s\n", formatTimestamp(apiKey.CreatedAt))
			fmt.Printf("\nRaw API Key:  %s\n", resp.Msg.RawApiKey)

		default:
			// Simple output format
			fmt.Printf("API Key Created: %s\n", formatUUID(resp.Msg.ApiKeyMetadata.ApiKeyId))
			fmt.Printf("Raw API Key:     %s\n", resp.Msg.RawApiKey)
		}

		// Special warning about saving the raw key
		fmt.Println("\nIMPORTANT: Save the raw API key value. It will not be shown again.")
		return nil
	},
}

// listApiKeysCmd represents the list command
var listApiKeysCmd = &cobra.Command{
	Use:   "list",
	Short: "List API keys",
	Long:  `List all API keys for the authenticated user in the GoodMem service.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Silence usage for server-side errors
		cmd.SilenceUsage = true

		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)

		client := v1connect.NewApiKeyServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.ListApiKeysRequest{})

		// Add API key header from global config
		if apiKey != "" {
			req.Header().Set("x-api-key", apiKey)
		} else {
			return fmt.Errorf("API key is required. Set it using the --api-key flag or GOODMEM_API_KEY environment variable")
		}

		resp, err := client.ListApiKeys(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				return fmt.Errorf("%v", connectErr.Message())
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Output based on format
		switch strings.ToLower(apikeyOutputFormat) {
		case "json":
			// Use the JSON formatter
			jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
			if err != nil {
				return fmt.Errorf("error formatting response as JSON: %w", err)
			}
			fmt.Println(string(jsonBytes))

		case "table":
			// Table output with headers
			fmt.Printf("%-36s %-10s %-20s %-8s %s\n",
				"API KEY ID", "PREFIX", "CREATED", "STATUS", "LABELS")
			fmt.Println(strings.Repeat("-", 90))

			for _, apiKey := range resp.Msg.Keys {
				// Format the label map as a string
				labelStr := ""
				for k, v := range apiKey.Labels {
					if labelStr != "" {
						labelStr += ", "
					}
					labelStr += k + "=" + v
				}

				// Format the created at timestamp
				createdStr := formatTimestamp(apiKey.CreatedAt)

				// Print the table row
				fmt.Printf("%-36s %-10s %-20s %-8s %s\n",
					formatUUID(apiKey.ApiKeyId),
					apiKey.KeyPrefix,
					createdStr,
					apiKey.Status,
					labelStr)
			}

			if len(resp.Msg.Keys) == 0 {
				fmt.Println("No API keys found")
			}

		default:
			// Simple list format
			fmt.Printf("API Keys: %d\n", len(resp.Msg.Keys))
			for _, apiKey := range resp.Msg.Keys {
				fmt.Printf("  %s (%s) - %s\n",
					formatUUID(apiKey.ApiKeyId),
					apiKey.Status,
					apiKey.KeyPrefix)
			}
		}

		return nil
	},
}

// updateApiKeyCmd represents the update command
var updateApiKeyCmd = &cobra.Command{
	Use:   "update [api-key-id]",
	Short: "Update an API key",
	Long:  `Update an existing API key in the GoodMem service.`,
	Example: `  # Update API key status
  goodmem apikey update 123e4567-e89b-12d3-a456-426614174000 --status INACTIVE

  # Replace all labels with new ones (old labels are removed)
  goodmem apikey update 123e4567-e89b-12d3-a456-426614174000 --label key1=value1 --label key2=value2 --label-strategy replace

  # Merge new labels with existing ones (old labels are preserved)
  goodmem apikey update 123e4567-e89b-12d3-a456-426614174000 --label key1=newvalue --label-strategy merge

  # Default label strategy is 'replace' if not specified
  goodmem apikey update 123e4567-e89b-12d3-a456-426614174000 --label environment=production`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		apiKeyID := args[0]

		// Silence usage for server-side errors
		cmd.SilenceUsage = true

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

		// Create update request with proper UUID handling
		updateReq := &v1.UpdateApiKeyRequest{}

		// Parse and set API key ID as UUID
		keyUUID, err := uuid.Parse(apiKeyID)
		if err != nil {
			return fmt.Errorf("invalid API key ID format: %w", err)
		}

		// Convert UUID to binary representation
		keyBytes, err := keyUUID.MarshalBinary()
		if err != nil {
			return fmt.Errorf("failed to convert UUID to binary: %w", err)
		}
		updateReq.ApiKeyId = keyBytes

		// Set status if provided
		if keyStatus != "" {
			switch strings.ToUpper(keyStatus) {
			case "ACTIVE":
				status := v1.Status_ACTIVE
				updateReq.Status = &status
			case "INACTIVE":
				status := v1.Status_INACTIVE
				updateReq.Status = &status
			default:
				return fmt.Errorf("invalid status: %s (should be ACTIVE or INACTIVE)", keyStatus)
			}
		}

		// Handle label updates using the appropriate oneof strategy with StringMap wrapper
		if len(apiKeyLabels) > 0 {
			stringMap := &v1.StringMap{
				Labels: labelsMap,
			}

			switch strings.ToLower(labelUpdateStrategy) {
			case "merge":
				updateReq.LabelUpdateStrategy = &v1.UpdateApiKeyRequest_MergeLabels{
					MergeLabels: stringMap,
				}
			case "replace":
				updateReq.LabelUpdateStrategy = &v1.UpdateApiKeyRequest_ReplaceLabels{
					ReplaceLabels: stringMap,
				}
			default:
				return fmt.Errorf("invalid label update strategy: %s (use 'replace' or 'merge')", labelUpdateStrategy)
			}
		}

		req := connect.NewRequest(updateReq)

		// Add API key header from global config
		if apiKey != "" {
			req.Header().Set("x-api-key", apiKey)
		} else {
			return fmt.Errorf("API key is required. Set it using the --api-key flag or GOODMEM_API_KEY environment variable")
		}

		resp, err := client.UpdateApiKey(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				return fmt.Errorf("%v", connectErr.Message())
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Output based on format
		switch strings.ToLower(apikeyOutputFormat) {
		case "json":
			// Use the JSON formatter
			jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
			if err != nil {
				return fmt.Errorf("error formatting response as JSON: %w", err)
			}
			fmt.Println(string(jsonBytes))

		case "table":
			// Detailed output
			apiKey := resp.Msg
			fmt.Println("API KEY UPDATED SUCCESSFULLY")
			fmt.Println("============================")
			fmt.Printf("API Key ID:   %s\n", formatUUID(apiKey.ApiKeyId))
			fmt.Printf("Key Prefix:   %s\n", apiKey.KeyPrefix)
			fmt.Printf("Status:       %s\n", apiKey.Status)

			if len(apiKey.Labels) > 0 {
				fmt.Println("Labels:")
				for k, v := range apiKey.Labels {
					fmt.Printf("  %s: %s\n", k, v)
				}
			}

			if apiKey.ExpiresAt != nil {
				fmt.Printf("Expires At:   %s\n", formatTimestamp(apiKey.ExpiresAt))
			}

			fmt.Printf("Updated At:   %s\n", formatTimestamp(apiKey.UpdatedAt))

		default:
			// Simple output
			fmt.Printf("API Key Updated: %s\n", formatUUID(resp.Msg.ApiKeyId))
			fmt.Printf("Status: %s\n", resp.Msg.Status)
		}

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

		// Silence usage for server-side errors
		cmd.SilenceUsage = true

		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)

		client := v1connect.NewApiKeyServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse API key ID as UUID
		keyUUID, err := uuid.Parse(apiKeyID)
		if err != nil {
			return fmt.Errorf("invalid API key ID format: %w", err)
		}

		// Convert UUID to binary representation
		keyBytes, err := keyUUID.MarshalBinary()
		if err != nil {
			return fmt.Errorf("failed to convert UUID to binary: %w", err)
		}

		req := connect.NewRequest(&v1.DeleteApiKeyRequest{
			ApiKeyId: keyBytes,
		})

		// Add API key header from global config
		if apiKey != "" {
			req.Header().Set("x-api-key", apiKey)
		} else {
			return fmt.Errorf("API key is required. Set it using the --api-key flag or GOODMEM_API_KEY environment variable")
		}

		_, err = client.DeleteApiKey(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				return fmt.Errorf("%v", connectErr.Message())
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		fmt.Printf("API key %s deleted successfully\n", apiKeyID)
		return nil
	},
}


func init() {
	rootCmd.AddCommand(apikeyCmd)
	apikeyCmd.AddCommand(createApiKeyCmd)
	apikeyCmd.AddCommand(listApiKeysCmd)
	apikeyCmd.AddCommand(updateApiKeyCmd)
	apikeyCmd.AddCommand(deleteApiKeyCmd)

	// Common flags for all commands
	apikeyCmd.PersistentFlags().StringVar(&apikeyOutputFormat, "output", "table", "Output format: table, json, or simple")

	// Flags for create
	createApiKeyCmd.Flags().StringSliceVar(&apiKeyLabels, "label", []string{}, "Labels in key=value format (can be specified multiple times)")

	// Flags for update
	updateApiKeyCmd.Flags().StringVar(&keyStatus, "status", "", "Status of the API key (ACTIVE or INACTIVE)")
	updateApiKeyCmd.Flags().StringSliceVar(&apiKeyLabels, "label", []string{}, "Labels in key=value format (can be specified multiple times)")
	updateApiKeyCmd.Flags().StringVar(&labelUpdateStrategy, "label-strategy", "replace", "Label update strategy: 'replace' to overwrite all existing labels, 'merge' to add to existing labels")
}