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
	// Common embedder command variables
	displayName        string
	description        string
	providerType       string
	endpointURL        string
	apiPath            string
	modelIdentifier    string
	dimensionality     int32
	maxSequenceLength  int32
	supportedModalities []string
	credentials        string
	embedderVersion    string // Renamed from version to avoid conflict
	monitoringEndpoint string
	embedderLabels     []string

	// Variables for listEmbeddersCmd
	providerTypeFilter string
)

// embedderCmd represents the embedder command
var embedderCmd = &cobra.Command{
	Use:   "embedder",
	Short: "Manage GoodMem embedders",
	Long:  `Create, get, list, update, and delete embedders in the GoodMem service.`,
}

// parseModalities converts string modalitities to their enum values
func parseModalities(modalityStrings []string) ([]v1.Modality, error) {
	var modalities []v1.Modality
	
	for _, modalityStr := range modalityStrings {
		var modality v1.Modality
		
		switch strings.ToUpper(modalityStr) {
		case "TEXT":
			modality = v1.Modality_MODALITY_TEXT
		case "IMAGE":
			modality = v1.Modality_MODALITY_IMAGE
		case "AUDIO":
			modality = v1.Modality_MODALITY_AUDIO
		case "VIDEO":
			modality = v1.Modality_MODALITY_VIDEO
		default:
			return nil, fmt.Errorf("invalid modality: %s (should be one of: TEXT, IMAGE, AUDIO, VIDEO)", modalityStr)
		}
		
		modalities = append(modalities, modality)
	}
	
	return modalities, nil
}

// parseProviderType converts a string provider type to its enum value
func parseProviderType(providerTypeStr string) (v1.ProviderType, error) {
	switch strings.ToUpper(providerTypeStr) {
	case "OPENAI":
		return v1.ProviderType_PROVIDER_TYPE_OPENAI, nil
	case "VLLM":
		return v1.ProviderType_PROVIDER_TYPE_VLLM, nil
	case "TEI":
		return v1.ProviderType_PROVIDER_TYPE_TEI, nil
	default:
		return v1.ProviderType_PROVIDER_TYPE_UNSPECIFIED, fmt.Errorf("invalid provider type: %s (should be one of: OPENAI, VLLM, TEI)", providerTypeStr)
	}
}

// createEmbedderCmd represents the create command
var createEmbedderCmd = &cobra.Command{
	Use:   "create",
	Short: "Create a new embedder",
	Long:  `Create a new embedder in the GoodMem service with the specified configuration.`,
	Example: `  # Create a basic OpenAI embedder
  goodmem embedder create --display-name "OpenAI Ada" --provider-type OPENAI --endpoint-url "https://api.openai.com" --model-identifier "text-embedding-3-small" --dimensionality 1536 --credentials "YOUR_API_KEY"

  # Create a vLLM embedder with labels
  goodmem embedder create --display-name "vLLM Embedder" --provider-type VLLM --endpoint-url "http://localhost:8000" --model-identifier "all-MiniLM-L6-v2" --dimensionality 384 --credentials "none" --label environment=dev --label team=ml

  # Create an embedder with multiple modalities
  goodmem embedder create --display-name "Multimodal Embedder" --provider-type OPENAI --endpoint-url "https://api.openai.com" --model-identifier "multi-embed" --dimensionality 1536 --credentials "YOUR_API_KEY" --modality TEXT --modality IMAGE
  
  # Create an embedder for another user (requires admin permissions)
  goodmem embedder create --display-name "Team Embedder" --provider-type OPENAI --endpoint-url "https://api.openai.com" --model-identifier "text-embedding-3-small" --dimensionality 1536 --credentials "YOUR_API_KEY" --owner 123e4567-e89b-12d3-a456-426614174000`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Validate required inputs
		if displayName == "" {
			return fmt.Errorf("display name is required")
		}
		
		if providerType == "" {
			return fmt.Errorf("provider type is required")
		}
		
		if endpointURL == "" {
			return fmt.Errorf("endpoint URL is required")
		}
		
		if modelIdentifier == "" {
			return fmt.Errorf("model identifier is required")
		}
		
		if dimensionality <= 0 {
			return fmt.Errorf("dimensionality must be a positive integer")
		}
		
		if credentials == "" {
			return fmt.Errorf("credentials are required")
		}

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

		// Create the client
		httpClient := createHTTPClient(true, serverAddress)
		client := v1connect.NewEmbedderServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse the provider type
		protoProviderType, err := parseProviderType(providerType)
		if err != nil {
			return err
		}

		// Parse the labels
		labelsMap, err := parseLabels(embedderLabels)
		if err != nil {
			return err
		}

		// Parse the modalities
		var modalities []v1.Modality
		if len(supportedModalities) > 0 {
			modalities, err = parseModalities(supportedModalities)
			if err != nil {
				return err
			}
		}

		// Create the request
		req := &v1.CreateEmbedderRequest{
			DisplayName:      displayName,
			ProviderType:     protoProviderType,
			EndpointUrl:      endpointURL,
			ModelIdentifier:  modelIdentifier,
			Dimensionality:   dimensionality,
			Credentials:      credentials,
			Labels:           labelsMap,
			ApiPath:          apiPath,
			SupportedModalities: modalities,
		}

		// Set optional fields if provided
		if cmd.Flags().Changed("description") {
			req.Description = description
		}

		if cmd.Flags().Changed("max-sequence-length") {
			req.MaxSequenceLength = &maxSequenceLength
		}

		if cmd.Flags().Changed("version") {
			req.Version = embedderVersion
		}

		if cmd.Flags().Changed("monitoring-endpoint") {
			req.MonitoringEndpoint = monitoringEndpoint
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
		resp, err := client.CreateEmbedder(context.Background(), connectReq)
		if err != nil {
			// Improve error handling with specific messages based on error type
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeUnauthenticated:
					return fmt.Errorf("authentication failed: %v", connectErr.Message())
				case connect.CodePermissionDenied:
					if ownerIDStr != "" {
						return fmt.Errorf("you don't have permission to create embedders for other users")
					}
					return fmt.Errorf("you don't have permission to create embedders")
				case connect.CodeAlreadyExists:
					return fmt.Errorf("an embedder with display name '%s' already exists for this owner", displayName)
				case connect.CodeInvalidArgument:
					return fmt.Errorf("invalid request: %v", connectErr.Message())
				default:
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Process the response
		embedder := resp.Msg

		// Display the created embedder information
		fmt.Println("Embedder created successfully!")
		fmt.Println()

		// Format and display the embedder ID
		embedderIDStr, err := uuidBytesToString(embedder.EmbedderId)
		if err != nil {
			embedderIDStr = fmt.Sprintf("<invalid-uuid: %x>", embedder.EmbedderId)
		}
		fmt.Printf("ID:               %s\n", embedderIDStr)

		// Display other embedder properties
		fmt.Printf("Display Name:     %s\n", embedder.DisplayName)
		
		if embedder.Description != "" {
			fmt.Printf("Description:      %s\n", embedder.Description)
		}

		// Format and display owner ID
		ownerIDStr, err := uuidBytesToString(embedder.OwnerId)
		if err != nil {
			ownerIDStr = fmt.Sprintf("<invalid-uuid: %x>", embedder.OwnerId)
		}
		fmt.Printf("Owner:            %s\n", ownerIDStr)

		// Show provider type
		fmt.Printf("Provider Type:    %s\n", strings.TrimPrefix(embedder.ProviderType.String(), "PROVIDER_TYPE_"))

		// Show endpoint details
		fmt.Printf("Endpoint URL:     %s\n", embedder.EndpointUrl)
		if embedder.ApiPath != "" {
			fmt.Printf("API Path:         %s\n", embedder.ApiPath)
		}
		fmt.Printf("Model:            %s\n", embedder.ModelIdentifier)
		fmt.Printf("Dimensionality:   %d\n", embedder.Dimensionality)
		
		if embedder.MaxSequenceLength != nil {
			fmt.Printf("Max Seq Length:   %d\n", embedder.MaxSequenceLength)
		}

		// Show modalities
		if len(embedder.SupportedModalities) > 0 {
			fmt.Printf("Modalities:       ")
			modalityStrings := make([]string, 0, len(embedder.SupportedModalities))
			for _, m := range embedder.SupportedModalities {
				modalityStrings = append(modalityStrings, strings.TrimPrefix(m.String(), "MODALITY_"))
			}
			fmt.Printf("%s\n", strings.Join(modalityStrings, ", "))
		}

		// Show additional configuration
		if embedder.Version != "" {
			fmt.Printf("Version:          %s\n", embedder.Version)
		}
		if embedder.MonitoringEndpoint != "" {
			fmt.Printf("Monitor Endpoint: %s\n", embedder.MonitoringEndpoint)
		}

		// Format and display creator ID
		creatorIDStr, err := uuidBytesToString(embedder.CreatedById)
		if err != nil {
			creatorIDStr = fmt.Sprintf("<invalid-uuid: %x>", embedder.CreatedById)
		}
		fmt.Printf("Created by:       %s\n", creatorIDStr)

		// Display creation time
		if embedder.CreatedAt != nil {
			fmt.Printf("Created at:       %s\n", formatTimestamp(embedder.CreatedAt))
		}

		// Display labels if present
		if len(embedder.Labels) > 0 {
			fmt.Println("Labels:")
			for k, v := range embedder.Labels {
				fmt.Printf("  %s: %s\n", k, v)
			}
		}

		return nil
	},
}

// getEmbedderCmd represents the get command
var getEmbedderCmd = &cobra.Command{
	Use:   "get [embedder-id]",
	Short: "Get embedder details",
	Long:  `Get details for a specific embedder by its ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		embedderIDStr := args[0]

		// Convert string UUID to binary format
		embedderID, err := uuidStringToBytes(embedderIDStr)
		if err != nil {
			return fmt.Errorf("invalid embedder ID: %w", err)
		}

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)

		client := v1connect.NewEmbedderServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.GetEmbedderRequest{
			EmbedderId: embedderID,
		})

		// Add API key header
		if err := addAuthHeader(req); err != nil {
			return err
		}

		resp, err := client.GetEmbedder(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeNotFound:
					return fmt.Errorf("embedder not found: %s", embedderIDStr)
				case connect.CodePermissionDenied:
					return fmt.Errorf("you don't have permission to access this embedder")
				default:
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Print the embedder details using our custom JSON formatter
		jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
		if err != nil {
			return fmt.Errorf("error formatting response as JSON: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

// listEmbeddersCmd represents the list command
var listEmbeddersCmd = &cobra.Command{
	Use:   "list",
	Short: "List embedders",
	Long:  `List embedders in the GoodMem service with filtering and output options.`,
	Example: `  # List all embedders with default settings
  goodmem embedder list

  # Filter embedders by labels
  goodmem embedder list --label project=demo --label env=test

  # Filter embedders by provider type
  goodmem embedder list --provider-type OPENAI

  # Filter embedders by owner
  goodmem embedder list --owner 123e4567-e89b-12d3-a456-426614174000

  # Get output in different formats
  goodmem embedder list --format json     # Detailed JSON output
  goodmem embedder list --format table    # Tabular output with headers (default)
  goodmem embedder list --format compact  # Compact single-line format

  # Get only embedder IDs (for scripting)
  goodmem embedder list --quiet`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Validate client-side args
		if outputFormat != "" && outputFormat != "json" && outputFormat != "table" && outputFormat != "compact" {
			return fmt.Errorf("format must be 'json', 'table', or 'compact'")
		}

		// Silence usage for server-side errors after client validation passes
		cmd.SilenceUsage = true

		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)

		client := v1connect.NewEmbedderServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse labels from key=value format
		labelsMap, err := parseLabels(embedderLabels)
		if err != nil {
			return err
		}

		// Create the request
		reqMsg := &v1.ListEmbeddersRequest{
			LabelSelectors: labelsMap,
		}

		// Add optional fields
		if cmd.Flags().Changed("owner") {
			// Convert string UUID to binary format
			ownerID, err := uuidStringToBytes(ownerIDStr)
			if err != nil {
				return fmt.Errorf("invalid owner ID: %w", err)
			}
			reqMsg.OwnerId = ownerID
		}

		if cmd.Flags().Changed("provider-type") {
			protoProviderType, err := parseProviderType(providerTypeFilter)
			if err != nil {
				return err
			}
			reqMsg.ProviderType = &protoProviderType
		}

		req := connect.NewRequest(reqMsg)

		// Add API key header from global config
		if err := addAuthHeader(req); err != nil {
			return err
		}

		resp, err := client.ListEmbedders(context.Background(), req)
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
		totalCount := len(resp.Msg.Embedders)
		if quietOutput {
			// Only output embedder IDs
			for _, embedder := range resp.Msg.Embedders {
				embedderIDStr, err := uuidBytesToString(embedder.EmbedderId)
				if err != nil {
					embedderIDStr = fmt.Sprintf("<invalid-uuid:%x>", embedder.EmbedderId)
				}
				fmt.Println(embedderIDStr)
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
			for _, embedder := range resp.Msg.Embedders {
				embedderIDStr, err := uuidBytesToString(embedder.EmbedderId)
				if err != nil {
					embedderIDStr = fmt.Sprintf("<invalid-uuid:%x>", embedder.EmbedderId)
				}

				// Format created time
				createdTime := "N/A"
				if embedder.CreatedAt != nil {
					createdTime = formatTimestamp(embedder.CreatedAt)
				}
				
				// Get readable provider type
				providerTypeStr := strings.TrimPrefix(embedder.ProviderType.String(), "PROVIDER_TYPE_")

				fmt.Printf("%s\t%s\t%s\t%s\n", embedderIDStr, truncateString(embedder.DisplayName, 30), providerTypeStr, createdTime)
			}
		} else { // Default table format
			if totalCount == 0 {
				fmt.Println("No embedders found matching the criteria.")
			} else {
				// Print table header
				fmt.Printf("%-36s %-30s %-10s %-12s %-20s\n",
					"EMBEDDER ID", "DISPLAY NAME", "PROVIDER", "DIMENSIONS", "CREATED")
				fmt.Println(strings.Repeat("-", 111))

				// Print table rows
				for _, embedder := range resp.Msg.Embedders {
					embedderIDStr, err := uuidBytesToString(embedder.EmbedderId)
					if err != nil {
						embedderIDStr = fmt.Sprintf("<invalid-uuid:%x>", embedder.EmbedderId)
					}

					// Format created time
					createdTime := "N/A"
					if embedder.CreatedAt != nil {
						// Use the formatTimestamp function but convert to table display style
						formattedTime := formatTimestamp(embedder.CreatedAt)
						// Parse the RFC3339 format and reformat to a more compact table display format
						if t, err := time.Parse(time.RFC3339, formattedTime); err == nil {
							createdTime = t.Format("2006-01-02 15:04:05")
						} else {
							createdTime = formattedTime // Fallback to the original format
						}
					}
					
					// Get readable provider type
					providerTypeStr := strings.TrimPrefix(embedder.ProviderType.String(), "PROVIDER_TYPE_")

					fmt.Printf("%-36s %-30s %-10s %-12d %-20s\n", 
						embedderIDStr, 
						truncateString(embedder.DisplayName, 30), 
						providerTypeStr,
						embedder.Dimensionality,
						createdTime)
				}
			}
		}

		return nil
	},
}

// deleteEmbedderCmd represents the delete command
var deleteEmbedderCmd = &cobra.Command{
	Use:   "delete [embedder-id]",
	Short: "Delete an embedder",
	Long:  `Delete an embedder from the GoodMem service by its ID.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		embedderIDStr := args[0]

		// Convert string UUID to binary format
		embedderID, err := uuidStringToBytes(embedderIDStr)
		if err != nil {
			return fmt.Errorf("invalid embedder ID: %w", err)
		}

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)

		client := v1connect.NewEmbedderServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		req := connect.NewRequest(&v1.DeleteEmbedderRequest{
			EmbedderId: embedderID,
		})

		// Add API key header
		if err := addAuthHeader(req); err != nil {
			return err
		}

		_, err = client.DeleteEmbedder(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeNotFound:
					return fmt.Errorf("embedder not found: %s", embedderIDStr)
				case connect.CodePermissionDenied:
					return fmt.Errorf("you don't have permission to delete this embedder")
				default:
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		fmt.Printf("Embedder %s deleted successfully\n", embedderIDStr)
		return nil
	},
}

// updateEmbedderCmd represents the update command
var updateEmbedderCmd = &cobra.Command{
	Use:   "update [embedder-id]",
	Short: "Update an embedder",
	Long:  `Update an embedder in the GoodMem service.`,
	Example: `  # Update an embedder's display name
  goodmem embedder update 123e4567-e89b-12d3-a456-426614174000 --display-name "New Name"

  # Update an embedder's endpoint URL
  goodmem embedder update 123e4567-e89b-12d3-a456-426614174000 --endpoint-url "https://new-api.example.com"

  # Update an embedder's credentials
  goodmem embedder update 123e4567-e89b-12d3-a456-426614174000 --credentials "NEW_API_KEY"

  # Replace all labels with new ones
  goodmem embedder update 123e4567-e89b-12d3-a456-426614174000 --label key1=value1 --label key2=value2 --label-strategy replace

  # Merge new labels with existing ones
  goodmem embedder update 123e4567-e89b-12d3-a456-426614174000 --label key1=newvalue --label-strategy merge`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		embedderIDStr := args[0]

		// Convert string UUID to binary format
		embedderID, err := uuidStringToBytes(embedderIDStr)
		if err != nil {
			return fmt.Errorf("invalid embedder ID: %w", err)
		}

		// After client-side validation passes, silence usage for server-side errors
		cmd.SilenceUsage = true

		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(true, serverAddress)

		client := v1connect.NewEmbedderServiceClient(
			httpClient,
			serverAddress,
			connect.WithGRPC(),
		)

		// Parse labels from key=value format
		labelsMap, err := parseLabels(embedderLabels)
		if err != nil {
			return err
		}

		updateReq := &v1.UpdateEmbedderRequest{
			EmbedderId: embedderID,
		}

		// Only set fields that were provided
		if cmd.Flags().Changed("display-name") {
			updateReq.DisplayName = &displayName
		}

		if cmd.Flags().Changed("description") {
			updateReq.Description = &description
		}

		if cmd.Flags().Changed("endpoint-url") {
			updateReq.EndpointUrl = &endpointURL
		}

		if cmd.Flags().Changed("api-path") {
			updateReq.ApiPath = &apiPath
		}

		if cmd.Flags().Changed("model-identifier") {
			updateReq.ModelIdentifier = &modelIdentifier
		}

		if cmd.Flags().Changed("dimensionality") {
			updateReq.Dimensionality = &dimensionality
		}

		if cmd.Flags().Changed("max-sequence-length") {
			updateReq.MaxSequenceLength = &maxSequenceLength
		}

		if cmd.Flags().Changed("credentials") {
			updateReq.Credentials = &credentials
		}

		if cmd.Flags().Changed("version") {
			updateReq.Version = &embedderVersion
		}

		if cmd.Flags().Changed("monitoring-endpoint") {
			updateReq.MonitoringEndpoint = &monitoringEndpoint
		}

		// Handle modalities if provided
		if cmd.Flags().Changed("modality") {
			modalities, err := parseModalities(supportedModalities)
			if err != nil {
				return err
			}
			updateReq.SupportedModalities = modalities
		}

		// Handle label updates using the appropriate oneof strategy with StringMap wrapper
		if cmd.Flags().Changed("label") {
			stringMap := &v1.StringMap{
				Labels: labelsMap,
			}

			switch strings.ToLower(labelUpdateStrategy) {
			case "merge":
				updateReq.LabelUpdateStrategy = &v1.UpdateEmbedderRequest_MergeLabels{
					MergeLabels: stringMap,
				}
			case "replace":
				updateReq.LabelUpdateStrategy = &v1.UpdateEmbedderRequest_ReplaceLabels{
					ReplaceLabels: stringMap,
				}
			default:
				return fmt.Errorf("invalid label update strategy: %s (use 'replace' or 'merge')", labelUpdateStrategy)
			}
		}

		req := connect.NewRequest(updateReq)

		// Add API key header
		if err := addAuthHeader(req); err != nil {
			return err
		}

		resp, err := client.UpdateEmbedder(context.Background(), req)
		if err != nil {
			var connectErr *connect.Error
			if errors.As(err, &connectErr) {
				switch connectErr.Code() {
				case connect.CodeNotFound:
					return fmt.Errorf("embedder not found: %s", embedderIDStr)
				case connect.CodePermissionDenied:
					return fmt.Errorf("you don't have permission to update this embedder")
				case connect.CodeInvalidArgument:
					return fmt.Errorf("invalid request: %v", connectErr.Message())
				default:
					return fmt.Errorf("%v", connectErr.Message())
				}
			}
			return fmt.Errorf("unexpected error: %w", err)
		}

		// Print the updated embedder using our custom JSON formatter
		jsonBytes, err := formatProtoMessageAsJSON(resp.Msg)
		if err != nil {
			return fmt.Errorf("error formatting response as JSON: %w", err)
		}
		fmt.Println(string(jsonBytes))
		return nil
	},
}

func init() {
	rootCmd.AddCommand(embedderCmd)
	embedderCmd.AddCommand(createEmbedderCmd)
	embedderCmd.AddCommand(getEmbedderCmd)
	embedderCmd.AddCommand(listEmbeddersCmd)
	embedderCmd.AddCommand(updateEmbedderCmd)
	embedderCmd.AddCommand(deleteEmbedderCmd)

	// Flags for create command
	createEmbedderCmd.Flags().StringVar(&displayName, "display-name", "", "Display name of the embedder")
	createEmbedderCmd.Flags().StringVar(&description, "description", "", "Description of the embedder")
	createEmbedderCmd.Flags().StringVar(&providerType, "provider-type", "", "Provider type (OPENAI, VLLM, TEI)")
	createEmbedderCmd.Flags().StringVar(&endpointURL, "endpoint-url", "", "API endpoint URL")
	createEmbedderCmd.Flags().StringVar(&apiPath, "api-path", "", "API path (defaults to /v1/embeddings if not specified)")
	createEmbedderCmd.Flags().StringVar(&modelIdentifier, "model-identifier", "", "Model identifier (e.g., text-embedding-3-small)")
	createEmbedderCmd.Flags().Int32Var(&dimensionality, "dimensionality", 0, "Output vector dimensions")
	createEmbedderCmd.Flags().Int32Var(&maxSequenceLength, "max-sequence-length", 0, "Maximum input sequence length")
	createEmbedderCmd.Flags().StringSliceVar(&supportedModalities, "modality", []string{}, "Supported modalities (TEXT, IMAGE, AUDIO, VIDEO)")
	createEmbedderCmd.Flags().StringVar(&credentials, "credentials", "", "API credentials (will be encrypted)")
	createEmbedderCmd.Flags().StringSliceVarP(&embedderLabels, "label", "l", []string{}, "Labels in key=value format (can be specified multiple times)")
	createEmbedderCmd.Flags().StringVar(&embedderVersion, "version", "", "Optional version information")
	createEmbedderCmd.Flags().StringVar(&monitoringEndpoint, "monitoring-endpoint", "", "Optional monitoring endpoint")
	createEmbedderCmd.Flags().StringVar(&ownerIDStr, "owner", "", "Owner ID for the embedder (requires admin permissions)")

	// Required flags for create command
	if err := createEmbedderCmd.MarkFlagRequired("display-name"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'display-name' as required: %v", err))
	}
	if err := createEmbedderCmd.MarkFlagRequired("provider-type"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'provider-type' as required: %v", err))
	}
	if err := createEmbedderCmd.MarkFlagRequired("endpoint-url"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'endpoint-url' as required: %v", err))
	}
	if err := createEmbedderCmd.MarkFlagRequired("model-identifier"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'model-identifier' as required: %v", err))
	}
	if err := createEmbedderCmd.MarkFlagRequired("dimensionality"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'dimensionality' as required: %v", err))
	}
	if err := createEmbedderCmd.MarkFlagRequired("credentials"); err != nil {
		panic(fmt.Sprintf("Failed to mark flag 'credentials' as required: %v", err))
	}

	// Flags for list command
	listEmbeddersCmd.Flags().StringSliceVarP(&embedderLabels, "label", "l", []string{}, "Filter embedders by label in key=value format (can be specified multiple times)")
	listEmbeddersCmd.Flags().StringVarP(&providerTypeFilter, "provider-type", "p", "", "Filter embedders by provider type (OPENAI, VLLM, TEI)")
	listEmbeddersCmd.Flags().StringVarP(&ownerIDStr, "owner", "o", "", "Filter embedders by owner ID (UUID)")
	listEmbeddersCmd.Flags().StringVarP(&outputFormat, "format", "f", "table", "Output format (json, table, or compact)")
	listEmbeddersCmd.Flags().BoolVar(&noTruncate, "no-trunc", false, "Do not truncate output values")
	listEmbeddersCmd.Flags().BoolVarP(&quietOutput, "quiet", "q", false, "Output only embedder IDs")

	// Flags for update command
	updateEmbedderCmd.Flags().StringVar(&displayName, "display-name", "", "New display name for the embedder")
	updateEmbedderCmd.Flags().StringVar(&description, "description", "", "New description for the embedder")
	updateEmbedderCmd.Flags().StringVar(&endpointURL, "endpoint-url", "", "New API endpoint URL")
	updateEmbedderCmd.Flags().StringVar(&apiPath, "api-path", "", "New API path")
	updateEmbedderCmd.Flags().StringVar(&modelIdentifier, "model-identifier", "", "New model identifier")
	updateEmbedderCmd.Flags().Int32Var(&dimensionality, "dimensionality", 0, "New output vector dimensions")
	updateEmbedderCmd.Flags().Int32Var(&maxSequenceLength, "max-sequence-length", 0, "New maximum input sequence length")
	updateEmbedderCmd.Flags().StringSliceVar(&supportedModalities, "modality", []string{}, "New supported modalities (TEXT, IMAGE, AUDIO, VIDEO)")
	updateEmbedderCmd.Flags().StringVar(&credentials, "credentials", "", "New API credentials")
	updateEmbedderCmd.Flags().StringSliceVarP(&embedderLabels, "label", "l", []string{}, "New labels in key=value format (can be specified multiple times)")
	updateEmbedderCmd.Flags().StringVar(&labelUpdateStrategy, "label-strategy", "replace", "Label update strategy: 'replace' to overwrite all existing labels, 'merge' to add to existing labels")
	updateEmbedderCmd.Flags().StringVar(&embedderVersion, "version", "", "New version information")
	updateEmbedderCmd.Flags().StringVar(&monitoringEndpoint, "monitoring-endpoint", "", "New monitoring endpoint")
}