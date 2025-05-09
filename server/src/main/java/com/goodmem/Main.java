package com.goodmem;

import com.goodmem.common.status.Status;
import com.goodmem.config.MinioConfig;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.ConditionalAuthInterceptor;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.ApiKeyServiceGrpc;
import goodmem.v1.EmbedderServiceGrpc;
import goodmem.v1.MemoryServiceGrpc;
import goodmem.v1.SpaceServiceGrpc;
import goodmem.v1.UserServiceGrpc;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.TlsServerCredentials;
import io.grpc.TlsServerCredentials.ClientAuth;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import io.javalin.Javalin;
import io.javalin.openapi.OpenApiInfo;
import io.javalin.openapi.OpenApiServer;
import io.javalin.openapi.plugin.OpenApiPlugin;
import io.javalin.openapi.plugin.redoc.ReDocPlugin;
import io.javalin.openapi.plugin.swagger.SwaggerPlugin;
import io.javalin.plugin.bundled.CorsPluginConfig.CorsRule;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import org.tinylog.Logger;

/**
 * Main server class for the GoodMem API.
 *
 * <p>This class serves as the entry point and primary implementation of the GoodMem REST API. It
 * initializes both gRPC and REST servers, configures routing, and implements all REST API endpoint
 * handlers that bridge to the underlying gRPC service implementations.
 *
 * <h2>Implementation Notes</h2>
 *
 * <h3>Protocol Buffer Import Convention</h3>
 *
 * <p>When referring to Protocol Buffer generated classes, import the outer class and reference
 * inner classes with qualified names, rather than importing inner classes directly:
 *
 * <pre>
 * // Preferred
 * import goodmem.v1.UserOuterClass;
 * // Then reference as UserOuterClass.GetUserRequest, UserOuterClass.User, etc.
 *
 * // Avoid
 * import goodmem.v1.UserOuterClass.GetUserRequest;
 * import goodmem.v1.UserOuterClass.User;
 * </pre>
 *
 * <h3>REST-to-gRPC Bridge Pattern</h3>
 *
 * <p>Each REST endpoint handler follows a consistent pattern:
 *
 * <ol>
 *   <li>Extract parameters from the request (path, query, header)
 *   <li>Parse and validate the request body using the appropriate DTO
 *   <li>Convert the DTO to a Protocol Buffer request message
 *   <li>Call the corresponding gRPC service method
 *   <li>Convert the Protocol Buffer response to a DTO
 *   <li>Return the DTO as JSON
 * </ol>
 *
 * <h3>Error Handling</h3>
 *
 * <p>Error handling is consistent across all handlers:
 *
 * <ul>
 *   <li>Validation errors return 400 Bad Request
 *   <li>Authentication errors return 401 Unauthorized
 *   <li>Permission errors return 403 Forbidden
 *   <li>Not found errors return 404 Not Found
 *   <li>Server errors return 500 Internal Server Error
 * </ul>
 */
public class Main {

  private static final int GRPC_PORT = 9090;
  private static final int REST_PORT = 8080;

  private Server grpcServer;
  private final SpaceServiceImpl spaceServiceImpl;
  private final UserServiceImpl userServiceImpl;
  private final MemoryServiceImpl memoryServiceImpl;
  private final ApiKeyServiceImpl apiKeyServiceImpl;
  private final EmbedderServiceImpl embedderServiceImpl;
  private final HikariDataSource dataSource;

  private final MinioConfig minioConfig;
  private final MinioClient minioClient;

  // gRPC blocking stubs for the REST-to-gRPC bridge
  private final SpaceServiceGrpc.SpaceServiceBlockingStub spaceService;
  private final UserServiceGrpc.UserServiceBlockingStub userService;
  private final MemoryServiceGrpc.MemoryServiceBlockingStub memoryService;
  private final ApiKeyServiceGrpc.ApiKeyServiceBlockingStub apiKeyService;
  private final EmbedderServiceGrpc.EmbedderServiceBlockingStub embedderService;

  public Main() {
    // Initialize database connection pool
    this.dataSource = setupDataSource();

    // Initialize the connection to MinIO.
    InitializedMinio minioInit = setupMinioSource();
    minioConfig = minioInit.config();
    minioClient = minioInit.client();

    // Create service configs
    var userServiceConfig = new UserServiceImpl.Config(dataSource);

    // Create service implementations with connection pool
    // For the SpaceServiceImpl, use a default embedder ID (this would typically come from
    // configuration)
    java.util.UUID defaultEmbedderId =
        java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"); // Placeholder UUID
    this.spaceServiceImpl =
        new SpaceServiceImpl(new SpaceServiceImpl.Config(dataSource, defaultEmbedderId));
    this.userServiceImpl = new UserServiceImpl(userServiceConfig);
    this.memoryServiceImpl =
        new MemoryServiceImpl(new MemoryServiceImpl.Config(dataSource, minioConfig));
    this.apiKeyServiceImpl = new ApiKeyServiceImpl(new ApiKeyServiceImpl.Config(dataSource));
    this.embedderServiceImpl = new EmbedderServiceImpl(new EmbedderServiceImpl.Config(dataSource));

    // Create an in-process channel for REST-to-gRPC communication
    ManagedChannel channel = InProcessChannelBuilder.forName("in-process").build();

    // Create blocking stubs for each service
    this.spaceService = SpaceServiceGrpc.newBlockingStub(channel);
    this.userService = UserServiceGrpc.newBlockingStub(channel);
    this.memoryService = MemoryServiceGrpc.newBlockingStub(channel);
    this.apiKeyService = ApiKeyServiceGrpc.newBlockingStub(channel);
    this.embedderService = EmbedderServiceGrpc.newBlockingStub(channel);
  }

  private record InitializedMinio(MinioConfig config, MinioClient client) {}

  private InitializedMinio setupMinioSource() {
    // Get MinIO configuration
    var minioConfig =
        new MinioConfig(
            System.getenv("MINIO_ENDPOINT"),
            System.getenv("MINIO_ACCESS_KEY"),
            System.getenv("MINIO_SECRET_KEY"),
            System.getenv("MINIO_BUCKET"));
    Logger.info("Configured MinIO: {}", minioConfig.toSecureString());

    var minioClient =
        MinioClient.builder()
            .endpoint(minioConfig.minioEndpoint())
            .credentials(minioConfig.minioAccessKey(), minioConfig.minioSecretKey())
            .build();
    try {
      boolean exists =
          minioClient.bucketExists(
              BucketExistsArgs.builder().bucket(minioConfig.minioBucket()).build());
      if (exists) {
        Logger.info("Found MinIO memory storage bucket {}.", minioConfig.minioBucket());
      } else {
        Logger.info("MinIO memory storage bucket {} does NOT exist.", minioConfig.minioBucket());
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.minioBucket()).build());
        Logger.info("Storage bucket {} was created.", minioConfig.minioBucket());
      }
    } catch (ErrorResponseException
        | InsufficientDataException
        | InternalException
        | InvalidKeyException
        | InvalidResponseException
        | IOException
        | NoSuchAlgorithmException
        | ServerException
        | XmlParserException e) {
      Logger.error(
          e,
          "Unexpected failure checking MinIO memory storage bucket {}.",
          minioConfig.minioBucket());
    }

    return new InitializedMinio(minioConfig, minioClient);
  }

  /**
   * Sets up and configures the HikariCP connection pool with database properties from system
   * properties.
   *
   * @return A configured HikariDataSource for database connections
   */
  private HikariDataSource setupDataSource() {
    // Get database configuration from environment properties
    String dbUrl = System.getenv("DB_URL");
    String dbUser = System.getenv("DB_USER");
    String dbPassword = System.getenv("DB_PASSWORD");

    // Configure HikariCP
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(dbUrl);
    config.setUsername(dbUser);
    config.setPassword(dbPassword);
    config.setMaximumPoolSize(10);
    config.setMinimumIdle(2);
    config.setIdleTimeout(30000);
    config.setMaxLifetime(1800000);
    config.setConnectionTimeout(30000);
    config.setAutoCommit(true);
    config.setPoolName("GoodMemPool");
    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

    Logger.info("Initializing database connection pool with URL: {} and user {}", dbUrl, dbUser);
    return new HikariDataSource(config);
  }

  public Status startGrpcServer() throws IOException {
    // Create special interceptor for the initializeSystem method (no auth required)
    var initMethodAuthorizer =
        new MethodAuthorizer().allowMethod("goodmem.v1.UserService/InitializeSystem");

    // Load certificate and key files from resources using absolute paths
    var serverCrt = Main.class.getResource("/certs/server.crt");
    var serverKey = Main.class.getResource("/certs/server.key");

    if (serverCrt == null) {
      throw new IllegalArgumentException("certs/server.crt could not be loaded.");
    }
    if (serverKey == null) {
      throw new IllegalArgumentException("certs/server.key could not be loaded.");
    }

    ByteSource serverCrtBs = Resources.asByteSource(serverCrt);
    ByteSource serverKeyBs = Resources.asByteSource(serverKey);

    // Create TLS credentials with the certificate and key files
    var credentials =
        TlsServerCredentials.newBuilder()
            .clientAuth(ClientAuth.NONE) // Don't require client certificates
            .keyManager(serverCrtBs.openBufferedStream(), serverKeyBs.openBufferedStream())
            .build();

    // Log certificate information
    Logger.info(
        "TLS enabled for gRPC server with: Certificate {}, Private key {}", serverCrt, serverKey);

    // Create a shared AuthInterceptor instance
    var authInterceptor = new AuthInterceptor(dataSource);

    grpcServer =
        Grpc.newServerBuilderForPort(GRPC_PORT, credentials)
            .addService(ServerInterceptors.intercept(spaceServiceImpl, authInterceptor))
            // For user service, we need to allow InitializeSystem to be called without auth
            .addService(
                ServerInterceptors.intercept(
                    userServiceImpl,
                    new ConditionalAuthInterceptor(initMethodAuthorizer, authInterceptor)))
            .addService(ServerInterceptors.intercept(memoryServiceImpl, authInterceptor))
            .addService(ServerInterceptors.intercept(apiKeyServiceImpl, authInterceptor))
            .addService(ServerInterceptors.intercept(embedderServiceImpl, authInterceptor))
            .addService(ProtoReflectionServiceV1.newInstance())
            .build()
            .start();
    Logger.info("gRPC Server started, listening on port {}", GRPC_PORT);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  Logger.info("Shutting down server since JVM is shutting down");
                  try {
                    Main.this.stopGrpcServer();
                    Main.this.shutdown();
                  } catch (InterruptedException e) {
                    Logger.error(e, "Server shutdown interrupted.");
                  } catch (Exception e) {
                    Logger.error(e, "Error during shutdown.");
                  }
                }));
    return Status.ok();
  }

  private void stopGrpcServer() throws InterruptedException {
    if (grpcServer != null) {
      grpcServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
    }
  }

  private void shutdown() {
    // Shut down HikariCP connection pool
    if (dataSource != null && !dataSource.isClosed()) {
      Logger.info("Shutting down database connection pool");
      dataSource.close();
    }
  }

  /**
   * Configures the OpenAPI information for the service documentation.
   *
   * <p>This method provides detailed metadata about the GoodMem API service, including its purpose,
   * licensing, support information, and version. The information is used to generate the
   * OpenAPI/Swagger documentation accessible via the /openapi endpoint.
   *
   * @param openApiInfo The OpenApiInfo object to configure
   * @return The configured OpenApiInfo instance
   */
  private OpenApiInfo getOpenApiInfo(OpenApiInfo openApiInfo) {
    return openApiInfo
        .title("GoodMem API")
        .description(
            "API for interacting with the GoodMem service, providing vector-based memory storage and retrieval "
                + "with multiple embedder support. The service enables creation of memory spaces, storing memories "
                + "with vector representations, and efficient similarity-based retrieval.")
        .version("v1")
        .termsOfService("https://goodmem.io/terms")
        .contact("GoodMem API Support", "https://goodmem.io/support", "support@goodmem.io")
        .license("Apache 2.0", "https://www.apache.org/licenses/LICENSE-2.0", "Apache-2.0");
  }

  /**
   * Configures the OpenAPI server information for the service documentation.
   *
   * <p>This method defines the server URLs and variables used in the OpenAPI documentation. It
   * configures how client applications can connect to the GoodMem service, including the base path,
   * port configurations, and server descriptions. The GoodMem service provides both REST (port
   * 8080) and gRPC (port 9090) interfaces, though this configuration focuses on the REST endpoint.
   *
   * @param version The API version string to include in the server URL
   * @param openApiServer The OpenApiServer object to configure
   * @return The configured OpenApiServer instance
   */
  private OpenApiServer getOpenApiServer(String version, OpenApiServer openApiServer) {
    return openApiServer
        .description("GoodMem REST API server endpoint")
        .url("http://localhost:{port}{basePath}/" + version + "/")
        .variable("port", "Server's REST port", "8080", "8080")
        .variable("basePath", "Base path of the API", "/v1", "", "/v1");
  }

  public void startJavalinServer() {

    // Create the REST adapter factory with our existing gRPC stubs
    com.goodmem.rest.RestAdapterFactory restAdapterFactory =
        new com.goodmem.rest.RestAdapterFactory(
            userService,
            spaceService,
            memoryService,
            apiKeyService,
            embedderService,
            dataSource);

    // Note: redoc is available at /openapi
    Javalin app =
        Javalin.create(
            config -> {
              config.bundledPlugins.enableCors(cors -> cors.addRule(CorsRule::anyHost));
              config.registerPlugin(
                  new OpenApiPlugin(
                      openApiConfig ->
                          openApiConfig
                              .withPrettyOutput()
                              .withDefinitionConfiguration(
                                  (version, openApiDefinition) ->
                                      openApiDefinition
                                          .withInfo(this::getOpenApiInfo)
                                          .withServer(
                                              openApiServer ->
                                                  getOpenApiServer(version, openApiServer))
                                          .withSecurity(
                                              openApiSecurity ->
                                                  openApiSecurity
                                                      .withBearerAuth()
                                                      .withApiKeyAuth(
                                                          "ApiKeyAuth", "x-api-key")))));

              config.registerPlugin(
                  new ReDocPlugin(
                      reDocConfiguration -> reDocConfiguration.setDocumentationPath("/openapi")));

              config.registerPlugin(
                  new SwaggerPlugin(
                      swaggerConfiguration ->
                          swaggerConfiguration.setDocumentationPath("/openapi")));

              restAdapterFactory.configureRoutes(config.router);
            });

    // Configure all routes using the REST adapter factory

    // Start the server
    app.start(REST_PORT);

    Logger.info("REST server started, listening on port {}.", REST_PORT);
  }

  // Utility methods for converting between UUID formats
  private static final ByteString ZEROS_BYTESTRING = ByteString.copyFrom(new byte[16]);

  public static void main(String[] args) throws IOException {
    Main server = new Main();
    server.startGrpcServer();
    server.startJavalinServer();
  }
}
