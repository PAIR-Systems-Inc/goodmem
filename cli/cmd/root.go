package cmd

import (
	"fmt"
	"os"
	"crypto/tls"
	"net/http"
	"net"
	"strings"
	"time"

	"github.com/spf13/cobra"
	"golang.org/x/net/http2"
)

var (
    serverAddress string // << central definition
    apiKey string       // API key for authentication
)

var rootCmd = &cobra.Command{
	Use:   "goodmem",
	Short: "GoodMem CLI provides command-line access to memory APIs",
	Long: `GoodMem CLI is a command-line interface for interacting with 
the GoodMem memory management APIs. It provides various commands
for manipulating and inspecting memory contents.`,
}

func init() {
    // Check for API key in environment variable
    envApiKey := os.Getenv("GOODMEM_API_KEY")
    
    rootCmd.PersistentFlags().
        StringVar(&serverAddress,
            "server",
            "https://localhost:9090",
            "GoodMem server address (gRPC API)")
            
    rootCmd.PersistentFlags().
        StringVar(&apiKey,
            "api-key",
            envApiKey,
            "API key for authentication (can also be set via GOODMEM_API_KEY environment variable)")
}

var gitCommit string

// createHTTPClient creates an HTTP client with proper HTTP/2 configuration
// This is critical for gRPC operations to work correctly
func createHTTPClient(insecure bool, serverAddr string) *http.Client {
    // Plain HTTP? -> use an h2c transport
    if strings.HasPrefix(serverAddr, "http://") {
        h2cTransport := &http2.Transport{
            AllowHTTP: true,
            DialTLS: func(network, addr string, _ *tls.Config) (net.Conn, error) {
                return net.Dial(network, addr) // plain TCP, no TLS
            },
        }
        return &http.Client{
            Timeout:   30 * time.Second,
            Transport: h2cTransport,
        }
    }

    // HTTPS -> regular transport with (optional) insecure TLS
    tlsCfg := &tls.Config{InsecureSkipVerify: insecure} //nolint:gosec
    transport := &http.Transport{
        TLSClientConfig:    tlsCfg,
        ForceAttemptHTTP2:  true,
    }
    if err := http2.ConfigureTransport(transport); err != nil {
        return &http.Client{
            Timeout: 30 * time.Second,
            // Fall back to standard HTTP/1.1 if HTTP/2 configuration fails
            Transport: transport,
        }
    }

    return &http.Client{
        Timeout:   30 * time.Second,
        Transport: transport,
    }
}

func Execute(commit string) {
	gitCommit = commit
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}