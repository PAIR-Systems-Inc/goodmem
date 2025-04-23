package goodmemclient

// Client represents a GoodMem API client.
type Client struct {
	serverEndpoint string
}

// NewClient creates a new GoodMem client instance.
func NewClient(serverEndpoint string) *Client {
	return &Client{
		serverEndpoint: serverEndpoint,
	}
}
