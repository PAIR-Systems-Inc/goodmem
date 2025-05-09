package cmd

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/bufbuild/connect-go"
	v1 "github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	v1connect "github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
)

var (
	// Common space command variables
	spaceName     string
	publicRead    bool
	labels        []string

	// Variables for createSpaceCmd
	embedderIDStr  string
	ownerIDStr     string

	// Variables for listSpacesCmd
	nameFilter    string
	maxResults    int32
	nextToken     string
	sortBy        string
	sortOrder     string
	outputFormat  string
	noTruncate    bool
	quietOutput   bool

	// Variables for updateSpaceCmd
	labelUpdateStrategy string
)

// spaceCmd represents the space command
var spaceCmd = &cobra.Command{
	Use:   "space",
	Short: "Manage GoodMem spaces",
	Long:  `Create, get, list, update, and delete spaces in the GoodMem service.`,
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
  goodmem space create --name "My Project" --embedder-id 123e4567-e89b-12d3-a456-426614174000

  # Create a space with labels
  goodmem space create --name "My Project" --embedder-id 123e4567-e89b-12d3-a456-426614174000 --label user=alice --label project=demo

  # Create a public-readable space
  goodmem space create --name "Public Knowledge Base" --embedder-id 123e4567-e89b-12d3-a456-426614174000 --public-read

  # Create a space for another user (requires admin permissions)
  goodmem space create --name "Team Space" --embedder-id 123e4567-e89b-12d3-a456-426614174000 --owner 123e4567-e89b-12d3-a456-426614174001`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Validate required inputs
		if spaceName == "" {
			return fmt.Errorf("space name is required")
		}

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

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
			Name:       spaceName,
			Labels:     labelsMap,
			PublicRead: publicRead,
		}

		// Add embedder_id
		if embedderIDStr == "" {
			return fmt.Errorf("embedder ID is required")
		}
		embedderIDBytes, err := uuidStringToBytes(embedderIDStr)
		if err != nil {
			return fmt.Errorf("invalid embedder ID: %w", err)
		}
		req.EmbedderId = embedderIDBytes

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
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
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
		
		// Format and display embedder ID
		embedderIDStr, err := uuidBytesToString(space.EmbedderId)
		if err != nil {
			embedderIDStr = fmt.Sprintf("<invalid-uuid: %x>", space.EmbedderId)
		}
		fmt.Printf("Embedder:   %s\n", embedderIDStr)

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
	Long:  `List spaces in the GoodMem service with filtering, sorting, and pagination.`,
	Example: `  # List all spaces with default settings
  goodmem space list

  # Filter spaces by labels
  goodmem space list --label project=demo --label env=test

  # Filter spaces by name pattern (glob-style)
  goodmem space list --name "Project*"

  # Filter spaces by owner
  goodmem space list --owner 123e4567-e89b-12d3-a456-426614174000

  # Sort spaces by name in ascending order (shows a sort indicator in the header)
  goodmem space list --sort-by name --sort-order asc

  # Paginate results (default page size is 50)
  goodmem space list --max-results 10

  # Get the next page of results using the token from previous output
  goodmem space list --next-token "eyJzdGFydCI6MTAsIm..."

  # Get output in different formats
  goodmem space list --format json     # Detailed JSON output
  goodmem space list --format table    # Tabular output with headers (default)
  goodmem space list --format compact  # Compact single-line format

  # Get only space IDs (for scripting)
  goodmem space list --quiet`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Validate client-side args
		if sortOrder != "" && sortOrder != "asc" && sortOrder != "desc" {
			return fmt.Errorf("sort-order must be 'asc' or 'desc'")
		}

		if outputFormat != "" && outputFormat != "json" && outputFormat != "table" && outputFormat != "compact" {
			return fmt.Errorf("format must be 'json', 'table', or 'compact'")
		}

		// Silence usage for server-side errors after client validation passes
		cmd.SilenceUsage = true

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

		// Create the request
		reqMsg := &v1.ListSpacesRequest{
			LabelSelectors: labelsMap,
		}

		// Add optional fields
		if cmd.Flags().Changed("name") {
			reqMsg.NameFilter = &nameFilter
		}

		if cmd.Flags().Changed("owner") {
			// Convert string UUID to binary format
			ownerID, err := uuidStringToBytes(ownerIDStr)
			if err != nil {
				return fmt.Errorf("invalid owner ID: %w", err)
			}
			reqMsg.OwnerId = ownerID
		}

		if cmd.Flags().Changed("max-results") {
			reqMsg.MaxResults = &maxResults
		}

		if cmd.Flags().Changed("next-token") {
			reqMsg.NextToken = &nextToken
		}

		if cmd.Flags().Changed("sort-by") {
			reqMsg.SortBy = &sortBy
		}

		if cmd.Flags().Changed("sort-order") {
			var protoSortOrder v1.SortOrder
			if sortOrder == "asc" {
				protoSortOrder = v1.SortOrder_ASCENDING
			} else {
				protoSortOrder = v1.SortOrder_DESCENDING
			}
			reqMsg.SortOrder = &protoSortOrder
		}

		req := connect.NewRequest(reqMsg)

		// Add API key header from global config
		if err := addAuthHeader(req); err != nil {
			return err
		}

		resp, err := client.ListSpaces(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeInvalidArgument:
					return fmt.Errorf("invalid request: %v", connectErr.Message())
				case connect.CodePermissionDenied:
					return fmt.Errorf("permission denied: %v", connectErr.Message())
				default:
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Process and display results based on output format
		totalCount := len(resp.Msg.Spaces)
		if quietOutput {
			// Only output space IDs
			for _, space := range resp.Msg.Spaces {
				spaceIDStr, err := uuidBytesToString(space.SpaceId)
				if err != nil {
					spaceIDStr = fmt.Sprintf("<invalid-uuid:%x>", space.SpaceId)
				}
				fmt.Println(spaceIDStr)
			}
		} else if outputFormat == "json" {
			// Use our custom formatter for REST-friendly JSON
			jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
			if err != nil {
				return fmt.Errorf("error formatting response as JSON: %w", err)
			}
			fmt.Println(string(jsonBytes))
		} else if outputFormat == "compact" {
			// Compact single-line format
			for _, space := range resp.Msg.Spaces {
				spaceIDStr, err := uuidBytesToString(space.SpaceId)
				if err != nil {
					spaceIDStr = fmt.Sprintf("<invalid-uuid:%x>", space.SpaceId)
				}

				// Format created time
				createdTime := "N/A"
				if space.CreatedAt != nil {
					createdTime = formatTimestamp(space.CreatedAt)
				}

				fmt.Printf("%s\t%s\t%s\t%v\n", spaceIDStr, truncateString(space.Name, 30), createdTime, space.PublicRead)
			}
		} else { // Default table format
			if totalCount == 0 {
				fmt.Println("No spaces found matching the criteria.")
			} else {
				// Determine if we need to show sort indicator
				nameHeader := "NAME"
				createdHeader := "CREATED"
				if cmd.Flags().Changed("sort-by") && cmd.Flags().Changed("sort-order") {
					// Add sort indicator based on sort field and direction
					sortIndicator := "↑"
					if sortOrder == "desc" {
						sortIndicator = "↓"
					}

					switch sortBy {
					case "name":
						nameHeader = fmt.Sprintf("NAME %s", sortIndicator)
					case "created_at":
						createdHeader = fmt.Sprintf("CREATED %s", sortIndicator)
					}
				}

				// Print table header
				fmt.Printf("%-36s %-30s %-20s %-7s\n",
					"SPACE ID", nameHeader, createdHeader, "PUBLIC")
				fmt.Println(strings.Repeat("-", 95))

				// Print table rows
				for _, space := range resp.Msg.Spaces {
					spaceIDStr, err := uuidBytesToString(space.SpaceId)
					if err != nil {
						spaceIDStr = fmt.Sprintf("<invalid-uuid:%x>", space.SpaceId)
					}

					// Format created time
					createdTime := "N/A"
					if space.CreatedAt != nil {
						// Use the formatTimestamp function but convert to table display style
						formattedTime := formatTimestamp(space.CreatedAt)
						// Parse the RFC3339 format and reformat to a more compact table display format
						if t, err := time.Parse(time.RFC3339, formattedTime); err == nil {
							createdTime = t.Format("2006-01-02 15:04:05")
						} else {
							createdTime = formattedTime // Fallback to the original format
						}
					}

					fmt.Printf("%-36s %-30s %-20s %-7v\n", spaceIDStr, truncateString(space.Name, 30), createdTime, space.PublicRead)
				}
			}
		}

		// Print pagination information if there's a next token
		if resp.Msg.NextToken != "" {
			fmt.Println()
			fmt.Printf("Next page token: %s\n", resp.Msg.NextToken)
			fmt.Println()
			fmt.Printf("To fetch the next page, run:\n")
			fmt.Printf("  goodmem space list --next-token \"%s\"", resp.Msg.NextToken)

			// Include any other specified flags in the example command
			if cmd.Flags().Changed("format") {
				fmt.Printf(" --format %s", outputFormat)
			}
			if cmd.Flags().Changed("max-results") {
				fmt.Printf(" --max-results %d", maxResults)
			}
			if len(labels) > 0 {
				for _, label := range labels {
					fmt.Printf(" --label %s", label)
				}
			}
			if cmd.Flags().Changed("name") {
				fmt.Printf(" --name \"%s\"", nameFilter)
			}
			if cmd.Flags().Changed("owner") {
				fmt.Printf(" --owner %s", ownerIDStr)
			}
			if cmd.Flags().Changed("sort-by") {
				fmt.Printf(" --sort-by %s", sortBy)
			}
			if cmd.Flags().Changed("sort-order") {
				fmt.Printf(" --sort-order %s", sortOrder)
			}
			if cmd.Flags().Changed("no-trunc") && noTruncate {
				fmt.Printf(" --no-trunc")
			}
			if cmd.Flags().Changed("quiet") && quietOutput {
				fmt.Printf(" --quiet")
			}
			fmt.Println()
		}

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

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

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
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
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

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

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
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Print the space details using our custom JSON formatter
		jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
		if err != nil {
			return fmt.Errorf("error formatting response as JSON: %w", err)
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
	Example: `  # Update a space name
  goodmem space update 123e4567-e89b-12d3-a456-426614174000 --name "New Space Name"

  # Replace all labels with new ones
  goodmem space update 123e4567-e89b-12d3-a456-426614174000 --label key1=value1 --label key2=value2 --label-strategy replace

  # Merge new labels with existing ones
  goodmem space update 123e4567-e89b-12d3-a456-426614174000 --label key1=newvalue --label-strategy merge

  # Update public read setting
  goodmem space update 123e4567-e89b-12d3-a456-426614174000 --public-read=true`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		spaceIDStr := args[0]

		// Convert string UUID to binary format
		spaceID, err := uuidStringToBytes(spaceIDStr)
		if err != nil {
			return fmt.Errorf("invalid space ID: %w", err)
		}

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

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
			updateReq.Name = &spaceName
		}

		// Handle label updates using the appropriate oneof strategy with StringMap wrapper
		if cmd.Flags().Changed("label") {
			stringMap := &v1.StringMap{
				Labels: labelsMap,
			}

			switch strings.ToLower(labelUpdateStrategy) {
			case "merge":
				updateReq.LabelUpdateStrategy = &v1.UpdateSpaceRequest_MergeLabels{
					MergeLabels: stringMap,
				}
			case "replace":
				updateReq.LabelUpdateStrategy = &v1.UpdateSpaceRequest_ReplaceLabels{
					ReplaceLabels: stringMap,
				}
			default:
				return fmt.Errorf("invalid label update strategy: %s (use 'replace' or 'merge')", labelUpdateStrategy)
			}
		}

		if cmd.Flags().Changed("public-read") {
			updateReq.PublicRead = &publicRead
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
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Print the updated space using our custom JSON formatter
		jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
		if err != nil {
			return fmt.Errorf("error formatting response as JSON: %w", err)
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
	createSpaceCmd.Flags().StringVar(&embedderIDStr, "embedder-id", "", "ID of the embedder to use for this space")
	createSpaceCmd.Flags().StringVar(&ownerIDStr, "owner", "", "Owner ID for the space (requires admin permissions)")

	if err := createSpaceCmd.MarkFlagRequired("name"); err != nil {
		// This should only happen if the flag doesn't exist
		panic(fmt.Sprintf("Failed to mark flag 'name' as required: %v", err))
	}
	
	if err := createSpaceCmd.MarkFlagRequired("embedder-id"); err != nil {
		// This should only happen if the flag doesn't exist
		panic(fmt.Sprintf("Failed to mark flag 'embedder-id' as required: %v", err))
	}

	// Flags for list
	listSpacesCmd.Flags().StringSliceVarP(&labels, "label", "l", []string{}, "Filter spaces by label in key=value format (can be specified multiple times)")
	listSpacesCmd.Flags().StringVarP(&nameFilter, "name", "n", "", "Filter spaces by name pattern with glob-style matching (e.g., \"Project*\")")
	listSpacesCmd.Flags().StringVarP(&ownerIDStr, "owner", "o", "", "Filter spaces by owner ID (UUID)")
	listSpacesCmd.Flags().StringVar(&sortBy, "sort-by", "", "Sort spaces by field (name, created_at, updated_at)")
	listSpacesCmd.Flags().StringVar(&sortOrder, "sort-order", "", "Sort order (asc or desc)")
	listSpacesCmd.Flags().Int32Var(&maxResults, "max-results", 0, "Maximum number of results per page")
	listSpacesCmd.Flags().StringVar(&nextToken, "next-token", "", "Token for fetching the next page of results")
	listSpacesCmd.Flags().StringVarP(&outputFormat, "format", "f", "table", "Output format (json, table, or compact)")
	listSpacesCmd.Flags().BoolVar(&noTruncate, "no-trunc", false, "Do not truncate output values")
	listSpacesCmd.Flags().BoolVarP(&quietOutput, "quiet", "q", false, "Output only space IDs")

	// Flags for update
	updateSpaceCmd.Flags().StringVar(&spaceName, "name", "", "New name for the space")
	updateSpaceCmd.Flags().StringSliceVarP(&labels, "label", "l", []string{}, "New labels in key=value format (can be specified multiple times)")
	updateSpaceCmd.Flags().StringVar(&labelUpdateStrategy, "label-strategy", "replace", "Label update strategy: 'replace' to overwrite all existing labels, 'merge' to add to existing labels")
	updateSpaceCmd.Flags().BoolVar(&publicRead, "public-read", false, "New public-read setting for the space")
}