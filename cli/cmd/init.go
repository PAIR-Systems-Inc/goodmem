package cmd

import (
	"context"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"github.com/bufbuild/connect-go"
	"github.com/pairsys/goodmem/cli/gen/goodmem/v1"
	"github.com/pairsys/goodmem/cli/gen/goodmem/v1/v1connect"
	"github.com/spf13/cobra"
)

// ConfigFile structure
type ConfigFile struct {
	ServerAddress string    `json:"server_address"`
	ApiKey        string    `json:"api_key"`
	UserId        string    `json:"user_id"`
	Initialized   bool      `json:"initialized"`
	InitializedAt time.Time `json:"initialized_at"`
}

var (
	configFile    string
	saveConfig    bool
	forceInit     bool
	configDir     string
	insecure      bool
	// serverAddress is defined in space.go and shared across commands
)

// initCmd represents the init command
var initCmd = &cobra.Command{
	Use:   "init",
	Short: "Initialize the GoodMem system",
	Long: `Initializes the GoodMem system by creating a root user and an API key.
If the system is already initialized, it will report that information.

The init command will:
1. Create a root user if one doesn't exist
2. Generate an API key for the root user
3. Optionally save configuration to a local file for future use`,
	RunE: func(cmd *cobra.Command, args []string) error {
		// Default user config directory
		if configDir == "" {
			// Get user's home directory
			home, err := os.UserHomeDir()
			if err != nil {
				return fmt.Errorf("error getting user home directory: %w", err)
			}
			configDir = filepath.Join(home, ".goodmem")
		}

		// Create config directory if it doesn't exist
		if _, err := os.Stat(configDir); os.IsNotExist(err) {
			err = os.MkdirAll(configDir, 0755)
			if err != nil {
				return fmt.Errorf("error creating config directory: %w", err)
			}
		}

		// Default config file
		if configFile == "" {
			configFile = filepath.Join(configDir, "config.json")
		}

		// Check if config file exists and we're not forcing initialization
		if !forceInit {
			if _, err := os.Stat(configFile); err == nil {
				// Config file exists, read it
				config, err := readConfigFile(configFile)
				if err != nil {
					return fmt.Errorf("error reading config file: %w", err)
				}

				if config.Initialized {
					fmt.Println("GoodMem system already initialized locally.")
					fmt.Printf("Server address: %s\n", config.ServerAddress)
					fmt.Printf("API key: %s\n", maskAPIKey(config.ApiKey))
					fmt.Printf("User ID: %s\n", config.UserId)
					fmt.Printf("Initialized at: %s\n", config.InitializedAt.Format(time.RFC3339))
					
					// Ask user if they want to force re-initialization
					fmt.Println("\nTo reinitialize, run with --force flag")
					return nil
				}
			}
		}

		// Make the init request to the server using gRPC
		fmt.Printf("Connecting to gRPC API at %s\n", serverAddress)
		
		// Create HTTP client with proper HTTP/2 configuration for gRPC
		httpClient := createHTTPClient(insecure, serverAddress)
		
		if insecure || (len(serverAddress) >= 5 && serverAddress[:5] == "https") {
			fmt.Println("Using TLS with certificate verification disabled (insecure mode)")
		}
		
		// Create user service client with gRPC protocol
		userClient := v1connect.NewUserServiceClient(
			httpClient, 
			serverAddress, 
			connect.WithGRPC(),
		)
		
		// Create the initialization request
		req := connect.NewRequest(&v1.InitializeSystemRequest{})
		
		// Execute the request
		resp, err := userClient.InitializeSystem(context.Background(), req)
		if err != nil {
			if connectErr, ok := err.(*connect.Error); ok {
				return fmt.Errorf("gRPC error: %s - %s", connectErr.Code(), connectErr.Message())
			}
			return fmt.Errorf("error connecting to server: %w", err)
		}

		// Print the initialization status
		fmt.Println(resp.Msg.Message)

		// If already initialized on server side
		if resp.Msg.AlreadyInitialized && resp.Msg.RootApiKey == "" {
			fmt.Println("The server is already initialized, but no API key was returned.")
			fmt.Println("If you need a new API key, use the 'apikey create' command with existing credentials.")
			return nil
		}

		// If initialization was successful and we got an API key
		if resp.Msg.RootApiKey != "" {
			fmt.Printf("Root API key: %s\n", resp.Msg.RootApiKey)
			
			// Convert binary user ID to hex string
			userIdHex := hex.EncodeToString(resp.Msg.UserId)
			
			// Format the UUID with dashes
			formattedUserId := fmt.Sprintf("%s-%s-%s-%s-%s",
				userIdHex[0:8], userIdHex[8:12], userIdHex[12:16], userIdHex[16:20], userIdHex[20:])
			
			fmt.Printf("User ID: %s\n", formattedUserId)
			fmt.Println("\nIMPORTANT: Save these values securely. The API key will not be shown again.")

			// Save to config file if requested
			if saveConfig {
				config := ConfigFile{
					ServerAddress: serverAddress,
					ApiKey:        resp.Msg.RootApiKey,
					UserId:        formattedUserId,
					Initialized:   true,
					InitializedAt: time.Now(),
				}

				err := writeConfigFile(configFile, config)
				if err != nil {
					return fmt.Errorf("error saving config file: %w", err)
				}
				fmt.Printf("\nConfiguration saved to: %s\n", configFile)
				
				// Set permissions to user-only read/write
				err = os.Chmod(configFile, 0600)
				if err != nil {
					fmt.Printf("Warning: Could not set secure permissions on config file: %v\n", err)
				}
			}
		}

		return nil
	},
}

// Utility functions
func maskAPIKey(apiKey string) string {
	if len(apiKey) <= 8 {
		return "****"
	}
	return apiKey[:4] + "****" + apiKey[len(apiKey)-4:]
}

func readConfigFile(path string) (ConfigFile, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return ConfigFile{}, err
	}

	var config ConfigFile
	err = json.Unmarshal(data, &config)
	if err != nil {
		return ConfigFile{}, err
	}

	return config, nil
}

func writeConfigFile(path string, config ConfigFile) error {
	data, err := json.MarshalIndent(config, "", "  ")
	if err != nil {
		return err
	}

	return os.WriteFile(path, data, 0600)
}

// We're using the createHTTPClient function from root.go

func init() {
	rootCmd.AddCommand(initCmd)

	// Local flags
	initCmd.Flags().StringVar(&serverAddress, "server", "https://localhost:9090", "GoodMem server address (gRPC API)")
	initCmd.Flags().StringVar(&configDir, "config-dir", "", "Config directory path (defaults to ~/.goodmem)")
	initCmd.Flags().StringVar(&configFile, "config-file", "", "Config file path (defaults to ~/.goodmem/config.json)")
	initCmd.Flags().BoolVar(&saveConfig, "save-config", true, "Save the API key to the config file")
	initCmd.Flags().BoolVar(&forceInit, "force", false, "Force re-initialization even if already initialized")
	initCmd.Flags().BoolVar(&insecure, "insecure", true, "Skip TLS certificate validation (for self-signed certs)")
}