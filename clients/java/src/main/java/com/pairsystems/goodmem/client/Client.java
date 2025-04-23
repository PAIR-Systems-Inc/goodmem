package com.pairsystems.goodmem.client;

/**
 * Client for interacting with GoodMem services.
 * 
 * <p>This client is compatible with Java 8 and higher.</p>
 */
public class Client {
    private final String serverEndpoint;

    /**
     * Creates a new client instance.
     * 
     * @param serverEndpoint the base URL of the server (e.g., "http://localhost:8080")
     */
    public Client(String serverEndpoint) {
        this.serverEndpoint = serverEndpoint;
    }
}
