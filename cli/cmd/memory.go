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
	spaceID            string
	memoryID           string
	originalContentRef string
	contentType        string
	metadata           []string
)

// memoryCmd represents the memory command
var memoryCmd = &cobra.Command{
	Use:   "memory",
	Short: "Manage GoodMem memories",
	Long:  `Create, get, list, and delete memories in the GoodMem service.`,
}

// createMemoryCmd represents the create command
var createMemoryCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new memory",
	Long:  `Create a new memory in the GoodMem service.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewMemoryServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse metadata from key=value format
		metadataMap := make(map[string]string)
		for _, meta := range metadata {
			parts := strings.SplitN(meta, "=", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid metadata format: %s (should be key=value)", meta)
			}
			metadataMap[parts[0]] = parts[1]
		}

		req := connect.NewRequest(&v1.CreateMemoryRequest{
			SpaceId:            []byte(spaceID),
			OriginalContentRef: originalContentRef,
			ContentType:        contentType,
			Metadata:           metadataMap,
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.CreateMemory(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error creating memory: %w", err)
		}

		// Print the created memory
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// getMemoryCmd represents the get command
var getMemoryCmd = &cobra.Command{
	Use:   "get [memory-id]",
	Short: "Get memory details",
	Long:  `Get details for a specific memory by its ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		memoryID := args[0]
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewMemoryServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.GetMemoryRequest{
			MemoryId: []byte(memoryID),
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.GetMemory(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error getting memory: %w", err)
		}

		// Print the memory details
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// listMemoriesCmd represents the list command
var listMemoriesCmd = &cobra.Command{
	Use:   "list --space-id [space-id]",
	Short: "List memories in a space",
	Long:  `List memories in a specific space.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		if spaceID == "" {
			return fmt.Errorf("space-id is required")
		}
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewMemoryServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.ListMemoriesRequest{
			SpaceId: []byte(spaceID),
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.ListMemories(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error listing memories: %w", err)
		}

		// Print the memories
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// deleteMemoryCmd represents the delete command
var deleteMemoryCmd = &cobra.Command{
	Use:   "delete [memory-id]",
	Short: "Delete a memory",
	Long:  `Delete a memory from the GoodMem service by its ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		memoryID := args[0]
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewMemoryServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.DeleteMemoryRequest{
			MemoryId: []byte(memoryID),
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		_, err := client.DeleteMemory(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error deleting memory: %w", err)
		}

		fmt.Printf("Memory %s deleted\n", memoryID)
		return nil
	},
}

func init() {
	rootCmd.AddCommand(memoryCmd)
	memoryCmd.AddCommand(createMemoryCmd)
	memoryCmd.AddCommand(getMemoryCmd)
	memoryCmd.AddCommand(listMemoriesCmd)
	memoryCmd.AddCommand(deleteMemoryCmd)

	// Global flags for all memory commands
	memoryCmd.PersistentFlags().StringVar(&serverAddress, "server", "http://localhost:9090", "GoodMem server address")

	// Flags for create
	createMemoryCmd.Flags().StringVar(&spaceID, "space-id", "", "ID of the space to create the memory in")
	createMemoryCmd.Flags().StringVar(&originalContentRef, "content-ref", "", "Reference to the original content")
	createMemoryCmd.Flags().StringVar(&contentType, "content-type", "", "Content type of the memory")
	createMemoryCmd.Flags().StringSliceVar(&metadata, "metadata", []string{}, "Metadata in key=value format (can be specified multiple times)")
	if err := createMemoryCmd.MarkFlagRequired("space-id"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'space-id' as required: %v", err))
	}
	if err := createMemoryCmd.MarkFlagRequired("content-ref"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'content-ref' as required: %v", err))
	}

	// Flags for list
	listMemoriesCmd.Flags().StringVar(&spaceID, "space-id", "", "ID of the space to list memories from")
	if err := listMemoriesCmd.MarkFlagRequired("space-id"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'space-id' as required: %v", err))
	}
}