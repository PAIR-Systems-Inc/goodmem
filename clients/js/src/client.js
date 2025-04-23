/**
 * Client for interacting with GoodMem services.
 */
export class Client {
  /**
   * Creates a new client instance.
   * 
   * @param {string} serverEndpoint - The base URL of the server (e.g., "http://localhost:8080")
   */
  constructor(serverEndpoint) {
    this._serverEndpoint = serverEndpoint;
  }
}
