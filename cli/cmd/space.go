package cmd

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/bufbuild/connect-go"
	"github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	"github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
)

var (
	serverAddress string
	spaceName     string
	publicRead    bool
	labels        []string
)

// spaceCmd represents the space command
var spaceCmd = &cobra.Command{
	Use:   "space",
	Short: "Manage GoodMem spaces",
	Long:  `Create, list, and delete spaces in the GoodMem service.`,
}

// createSpaceCmd represents the create command
var createSpaceCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new space",
	Long:  `Create a new space in the GoodMem service with the specified name and labels.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		client := v1connect.NewSpaceServiceClient(
			http.DefaultClient,
			serverAddress,
		)

		// Parse labels from key=value format
		labelsMap := make(map[string]string)
		for _, label := range labels {
			parts := strings.SplitN(label, "=", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid label format: %s (should be key=value)", label)
			}
			labelsMap[parts[0]] = parts[1]
		}

		req := connect.NewRequest(&v1.CreateSpaceRequest{
			Name:       spaceName,
			Labels:     labelsMap,
			PublicRead: publicRead,
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		resp, err := client.CreateSpace(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error creating space: %w", err)
		}

		// Print the created space
		jsonBytes, err := json.MarshalIndent(resp.Msg, "", "  ")
		if err != nil {
			return fmt.Errorf("error marshaling response: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// listSpacesCmd represents the list command
var listSpacesCmd = &cobra.Command{
	Use:   "list",
	Short: "List spaces",
	Long:  `List spaces in the GoodMem service, optionally filtered by labels.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		client := v1connect.NewSpaceServiceClient(
			http.DefaultClient,
			serverAddress,
		)

		// Parse labels from key=value format
		labelsMap := make(map[string]string)
		for _, label := range labels {
			parts := strings.SplitN(label, "=", 2)
			if len(parts) != 2 {
				return fmt.Errorf("invalid label format: %s (should be key=value)", label)
			}
			labelsMap[parts[0]] = parts[1]
		}

		req := connect.NewRequest(&v1.ListSpacesRequest{
			LabelSelectors: labelsMap,
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

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
		spaceID := args[0]
		client := v1connect.NewSpaceServiceClient(
			http.DefaultClient,
			serverAddress,
		)

		req := connect.NewRequest(&v1.DeleteSpaceRequest{
			SpaceId: spaceID,
		})

		// Add API key header
		req.Header().Set("x-api-key", "test-key")

		_, err := client.DeleteSpace(context.Background(), req)
		if err != nil {
			return fmt.Errorf("error deleting space: %w", err)
		}

		fmt.Printf("Space %s deleted\n", spaceID)
		return nil
	},
}

func init() {
	rootCmd.AddCommand(spaceCmd)
	spaceCmd.AddCommand(createSpaceCmd)
	spaceCmd.AddCommand(listSpacesCmd)
	spaceCmd.AddCommand(deleteSpaceCmd)

	// Global flags for all space commands
	spaceCmd.PersistentFlags().StringVar(&serverAddress, "server", "http://localhost:9090", "GoodMem server address")

	// Flags for create
	createSpaceCmd.Flags().StringVar(&spaceName, "name", "", "Name of the space")
	createSpaceCmd.Flags().StringSliceVar(&labels, "label", []string{}, "Labels in key=value format (can be specified multiple times)")
	createSpaceCmd.Flags().BoolVar(&publicRead, "public-read", false, "Whether the space is publicly readable")
	if err := createSpaceCmd.MarkFlagRequired("name"); err != nil {
		// This should only happen if the flag doesn't exist
		panic(fmt.Sprintf("Failed to mark flag 'name' as required: %v", err))
	}

	// Flags for list
	listSpacesCmd.Flags().StringSliceVar(&labels, "label", []string{}, "Filter spaces by label in key=value format (can be specified multiple times)")
}