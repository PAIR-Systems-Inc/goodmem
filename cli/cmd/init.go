package cmd

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"time"

	"github.com/spf13/cobra"
)

// InitCommand response from the server
type InitResponse struct {
	Initialized bool   `json:"initialized"`
	Message     string `json:"message"`
	RootApiKey  string `json:"root_api_key,omitempty"`
	UserId      string `json:"user_id,omitempty"`
	Error       string `json:"error,omitempty"`
}

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

		// Make the init request to the server
		endpoint := fmt.Sprintf("%s/v1/system/init", serverAddress)
		resp, err := http.Post(endpoint, "application/json", bytes.NewBuffer([]byte("{}")))
		if err != nil {
			return fmt.Errorf("error connecting to server: %w", err)
		}
		defer resp.Body.Close()

		// Read the response body
		body, err := io.ReadAll(resp.Body)
		if err != nil {
			return fmt.Errorf("error reading response: %w", err)
		}

		// Parse the response
		var initResponse InitResponse
		if err := json.Unmarshal(body, &initResponse); err != nil {
			return fmt.Errorf("error parsing response: %w", err)
		}

		// Check for error in response
		if initResponse.Error != "" {
			return fmt.Errorf("server error: %s", initResponse.Error)
		}

		// Print the initialization status
		fmt.Println(initResponse.Message)

		// If already initialized on server side
		if initResponse.Initialized && initResponse.RootApiKey == "" {
			fmt.Println("The server is already initialized, but no API key was returned.")
			fmt.Println("If you need a new API key, use the 'apikey create' command with existing credentials.")
			return nil
		}

		// If initialization was successful and we got an API key
		if initResponse.RootApiKey != "" {
			fmt.Printf("Root API key: %s\n", initResponse.RootApiKey)
			fmt.Printf("User ID: %s\n", initResponse.UserId)
			fmt.Println("\nIMPORTANT: Save these values securely. The API key will not be shown again.")

			// Save to config file if requested
			if saveConfig {
				config := ConfigFile{
					ServerAddress: serverAddress,
					ApiKey:        initResponse.RootApiKey,
					UserId:        initResponse.UserId,
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

func init() {
	rootCmd.AddCommand(initCmd)

	// Local flags
	initCmd.Flags().StringVar(&serverAddress, "server", "http://localhost:8080", "GoodMem server address")
	initCmd.Flags().StringVar(&configDir, "config-dir", "", "Config directory path (defaults to ~/.goodmem)")
	initCmd.Flags().StringVar(&configFile, "config-file", "", "Config file path (defaults to ~/.goodmem/config.json)")
	initCmd.Flags().BoolVar(&saveConfig, "save-config", true, "Save the API key to the config file")
	initCmd.Flags().BoolVar(&forceInit, "force", false, "Force re-initialization even if already initialized")
}