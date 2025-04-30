package cmd

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/bufbuild/connect-go"
	"github.com/google/uuid"
	v1 "github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	v1connect "github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
	"google.golang.org/protobuf/types/known/timestamppb"
)

var (
	// Common space command variables
	spaceName     string
	publicRead    bool
	labels        []string

	// Variables for createSpaceCmd
	embeddingModel string
	ownerIDStr     string
)

// spaceCmd represents the space command
var spaceCmd = &cobra.Command{
	Use:   "space",
	Short: "Manage GoodMem spaces",
	Long:  `Create, get, list, update, and delete spaces in the GoodMem service.`,
}

// uuidStringToBytes converts a string UUID to binary representation for protobuf
func uuidStringToBytes(s string) ([]byte, error) {
	id, err := uuid.Parse(s)
	if err != nil {
		return nil, fmt.Errorf("invalid UUID format: %w", err)
	}
	return id.MarshalBinary()
}

// uuidBytesToString converts binary UUID from protobuf to canonical string representation
func uuidBytesToString(b []byte) (string, error) {
	if len(b) != 16 {
		return "", fmt.Errorf("invalid UUID bytes: expected 16 bytes, got %d", len(b))
	}
	
	var id uuid.UUID
	err := id.UnmarshalBinary(b)
	if err != nil {
		return "", fmt.Errorf("failed to unmarshal UUID: %w", err)
	}
	
	return id.String(), nil
}

// formatTimestamp converts a protocol buffer timestamp to a formatted string
func formatTimestamp(ts *timestamppb.Timestamp) string {
	if ts == nil {
		return "N/A"
	}
	t := time.Unix(ts.Seconds, int64(ts.Nanos)).UTC()
	return t.Format(time.RFC3339)
}

// parseLabels converts key=value pairs from command line to a map
func parseLabels(labelSlice []string) (map[string]string, error) {
	labelsMap := make(map[string]string)
	
	for _, label := range labelSlice {
		parts := strings.SplitN(label, "=", 2)
		if len(parts) != 2 {
			return nil, fmt.Errorf("invalid label format: %s (should be key=value)", label)
		}
		
		key := strings.TrimSpace(parts[0])
		value := parts[1]
		
		if key == "" {
			return nil, fmt.Errorf("label key cannot be empty in '%s'", label)
		}
		
		labelsMap[key] = value
	}
	
	return labelsMap, nil
}

// addAuthHeader adds the API key authentication header to a connect request
func addAuthHeader(req connect.AnyRequest) error {
	if apiKey == "" {
		return fmt.Errorf("API key is required. Set it using the --api-key flag or GOODMEM_API_KEY environment variable")
	}
	req.Header().Set("x-api-key", apiKey)
	return nil
}

// createSpaceCmd represents the create command
var createSpaceCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new space",
	Long:  `Create a new space in the GoodMem service with the specified name, labels, and settings.`,
	Example: `  # Create a basic space
  goodmem space create --name "My Project"
  
  # Create a space with labels
  goodmem space create --name "My Project" --label user=alice --label project=demo
  
  # Create a public-readable space
  goodmem space create --name "Public Knowledge Base" --public-read
  
  # Create a space with a specific embedding model
  goodmem space create --name "Custom Embeddings" --embedding-model openai-ada-002
  
  # Create a space for another user (requires admin permissions)
  goodmem space create --name "Team Space" --owner 123e4567-e89b-12d3-a456-426614174000`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Validate required inputs
		if spaceName == "" {
			return fmt.Errorf("space name is required")
		}
		
		// Create the client
		httpClient := createHTTPClient(true, serverAddress)
		client := v1connect.NewSpaceServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)
		
		// Parse the labels
		labelsMap, err := parseLabels(labels)
		if err != nil {
			return err
		}
		
		// Create the request
		req := &v1.CreateSpaceRequest{
			Name:           spaceName,
			Labels:         labelsMap,
			PublicRead:     publicRead,
			EmbeddingModel: embeddingModel,
		}
		
		// Add owner_id if specified (for admin use)
		if ownerIDStr != "" {
			ownerIDBytes, err := uuidStringToBytes(ownerIDStr)
			if err != nil {
				return fmt.Errorf("invalid owner ID: %w", err)
			}
			req.OwnerId = ownerIDBytes
		}
		
		// Create the connect request
		connectReq := connect.NewRequest(req)
		
		// Add authentication
		if err := addAuthHeader(connectReq); err != nil {
			return err
		}
		
		// Make the API call
		resp, err := client.CreateSpace(context.Background(), connectReq)
		if err != nil {
			// Improve error handling with specific messages based on error type
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeUnauthenticated:
					return fmt.Errorf("authentication failed: %v", connectErr.Message())
				case connect.CodePermissionDenied:
					if ownerIDStr != "" {
						return fmt.Errorf("you don't have permission to create spaces for other users")
					}
					return fmt.Errorf("you don't have permission to create spaces")
				case connect.CodeAlreadyExists:
					return fmt.Errorf("a space with name '%s' already exists for this owner", spaceName)
				case connect.CodeInvalidArgument:
					return fmt.Errorf("invalid request: %v", connectErr.Message())
				default:
					return fmt.Errorf("error creating space: %v", connectErr.Message())
				}
			}
			return fmt.Errorf("error creating space: %w", err)
		}
		
		// Process the response
		space := resp.Msg
		
		// Display the created space information
		fmt.Println("Space created successfully!")
		fmt.Println()
		
		// Format and display the space ID
		spaceIDStr, err := uuidBytesToString(space.SpaceId)
		if err != nil {
			spaceIDStr = fmt.Sprintf("<invalid-uuid: %x>", space.SpaceId)
		}
		fmt.Printf("ID:         %s\n", spaceIDStr)
		
		// Display other space properties
		fmt.Printf("Name:       %s\n", space.Name)
		
		// Format and display owner ID
		ownerIDStr, err := uuidBytesToString(space.OwnerId)
		if err != nil {
			ownerIDStr = fmt.Sprintf("<invalid-uuid: %x>", space.OwnerId)
		}
		fmt.Printf("Owner:      %s\n", ownerIDStr)
		
		// Format and display creator ID
		creatorIDStr, err := uuidBytesToString(space.CreatedById)
		if err != nil {
			creatorIDStr = fmt.Sprintf("<invalid-uuid: %x>", space.CreatedById)
		}
		fmt.Printf("Created by: %s\n", creatorIDStr)
		
		// Display other attributes
		if space.CreatedAt != nil {
			fmt.Printf("Created at: %s\n", formatTimestamp(space.CreatedAt))
		}
		fmt.Printf("Public:     %v\n", space.PublicRead)
		fmt.Printf("Model:      %s\n", space.EmbeddingModel)
		
		// Display labels if present
		if len(space.Labels) > 0 {
			fmt.Println("Labels:")
			for k, v := range space.Labels {
				fmt.Printf("  %s: %s\n", k, v)
			}
		}
		
		return nil
	},
}

// listSpacesCmd represents the list command
var listSpacesCmd = &cobra.Command{
	Use:   "list",
	Short: "List spaces",
	Long:  `List spaces in the GoodMem service, optionally filtered by labels.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewSpaceServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse labels from key=value format
		labelsMap, err := parseLabels(labels)
		if err != nil {
			return err
		}

		req := connect.NewRequest(&v1.ListSpacesRequest{
			LabelSelectors: labelsMap,
		})

		// Add API key header from global config
		if err := addAuthHeader(req); err != nil {
			return err
		}

		resp, err := client.ListSpaces(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error listing spaces: %w", err)
		}

		// Print the spaces
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// deleteSpaceCmd represents the delete command
var deleteSpaceCmd = &cobra.Command{
	Use:   "delete [space-id]",
	Short: "Delete a space",
	Long:  `Delete a space from the GoodMem service by its ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		spaceIDStr := args[0]
		
		// Convert string UUID to binary format
		spaceID, err := uuidStringToBytes(spaceIDStr)
		if err != nil {
			return fmt.Errorf("invalid space ID: %w", err)
		}
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewSpaceServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.DeleteSpaceRequest{
			SpaceId: spaceID,
		})

		// Add API key header
		if err := addAuthHeader(req); err != nil {
			return err
		}

		_, err = client.DeleteSpace(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeNotFound:
					return fmt.Errorf("space not found: %s", spaceIDStr)
				case connect.CodePermissionDenied:
					return fmt.Errorf("you don't have permission to delete this space")
				default:
					return fmt.Errorf("error deleting space: %v", connectErr.Message())
				}
			}
			return fmt.Errorf("error deleting space: %w", err)
		}

		fmt.Printf("Space %s deleted successfully\n", spaceIDStr)
		return nil
	},
}

// getSpaceCmd represents the get command
var getSpaceCmd = &cobra.Command{
	Use:   "get [space-id]",
	Short: "Get space details",
	Long:  `Get details for a specific space by its ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		spaceIDStr := args[0]
		
		// Convert string UUID to binary format
		spaceID, err := uuidStringToBytes(spaceIDStr)
		if err != nil {
			return fmt.Errorf("invalid space ID: %w", err)
		}
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewSpaceServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.GetSpaceRequest{
			SpaceId: spaceID,
		})

		// Add API key header
		if err := addAuthHeader(req); err != nil {
			return err
		}

		resp, err := client.GetSpace(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeNotFound:
					return fmt.Errorf("space not found: %s", spaceIDStr)
				case connect.CodePermissionDenied:
					return fmt.Errorf("you don't have permission to access this space")
				default:
					return fmt.Errorf("error getting space: %v", connectErr.Message())
				}
			}
			return fmt.Errorf("error getting space: %w", err)
		}

		// Print the space details (for now keeping JSON format)
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// updateSpaceCmd represents the update command
var updateSpaceCmd = &cobra.Command{
	Use:   "update [space-id]",
	Short: "Update a space",
	Long:  `Update a space in the GoodMem service.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		spaceIDStr := args[0]
		
		// Convert string UUID to binary format
		spaceID, err := uuidStringToBytes(spaceIDStr)
		if err != nil {
			return fmt.Errorf("invalid space ID: %w", err)
		}
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)
		
		client := v1connect.NewSpaceServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse labels from key=value format
		labelsMap, err := parseLabels(labels)
		if err != nil {
			return err
		}

		updateReq := &v1.UpdateSpaceRequest{
			SpaceId: spaceID,
		}

		// Only set fields that were provided
		if cmd.Flags().Changed("name") {
			updateReq.Name = spaceName
		}
		if cmd.Flags().Changed("label") {
			updateReq.Labels = labelsMap
		}
		if cmd.Flags().Changed("public-read") {
			updateReq.PublicRead = publicRead
		}

		req := connect.NewRequest(updateReq)

		// Add API key header
		if err := addAuthHeader(req); err != nil {
			return err
		}

		resp, err := client.UpdateSpace(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeNotFound:
					return fmt.Errorf("space not found: %s", spaceIDStr)
				case connect.CodePermissionDenied:
					return fmt.Errorf("you don't have permission to update this space")
				case connect.CodeAlreadyExists:
					return fmt.Errorf("cannot update: a space with name '%s' already exists for this owner", spaceName)
				default:
					return fmt.Errorf("error updating space: %v", connectErr.Message())
				}
			}
			return fmt.Errorf("error updating space: %w", err)
		}

		// Print the updated space (for now keeping JSON format)
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

func init() {
	rootCmd.AddCommand(spaceCmd)
	spaceCmd.AddCommand(createSpaceCmd)
	spaceCmd.AddCommand(getSpaceCmd)
	spaceCmd.AddCommand(listSpacesCmd)
	spaceCmd.AddCommand(updateSpaceCmd)
	spaceCmd.AddCommand(deleteSpaceCmd)

	// Flags for create command
	createSpaceCmd.Flags().StringVar(&spaceName, "name", "", "Name of the space")
	createSpaceCmd.Flags().StringSliceVarP(&labels, "label", "l", []string{}, "Labels in key=value format (can be specified multiple times)")
	createSpaceCmd.Flags().BoolVar(&publicRead, "public-read", false, "Whether the space is publicly readable")
	createSpaceCmd.Flags().StringVar(&embeddingModel, "embedding-model", "", "Embedding model to use (default from server config if not specified)")
	createSpaceCmd.Flags().StringVar(&ownerIDStr, "owner", "", "Owner ID for the space (requires admin permissions)")
	
	if err := createSpaceCmd.MarkFlagRequired("name"); err != nil {
		// This should only happen if the flag doesn't exist
		panic(fmt.Sprintf("Failed to mark flag 'name' as required: %v", err))
	}

	// Flags for list
	listSpacesCmd.Flags().StringSliceVarP(&labels, "label", "l", []string{}, "Filter spaces by label in key=value format (can be specified multiple times)")
	
	// Flags for update
	updateSpaceCmd.Flags().StringVar(&spaceName, "name", "", "New name for the space")
	updateSpaceCmd.Flags().StringSliceVarP(&labels, "label", "l", []string{}, "New labels in key=value format (can be specified multiple times)")
	updateSpaceCmd.Flags().BoolVar(&publicRead, "public-read", false, "New public-read setting for the space")
}