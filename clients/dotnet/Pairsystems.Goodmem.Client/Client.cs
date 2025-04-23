namespace Pairsystems.Goodmem.Client
{
    /// <summary>
    /// Client for interacting with GoodMem services.
    /// </summary>
    public class Client
    {
        private readonly string _serverEndpoint;

        /// <summary>
        /// Creates a new client instance.
        /// </summary>
        /// <param name="serverEndpoint">The base URL of the server (e.g., "http://localhost:8080")</param>
        public Client(string serverEndpoint)
        {
            _serverEndpoint = serverEndpoint;
        }
    }
}
