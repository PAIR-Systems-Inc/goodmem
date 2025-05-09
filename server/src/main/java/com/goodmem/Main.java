package com.goodmem;

import static io.javalin.apibuilder.ApiBuilder.*;
import static io.javalin.apibuilder.ApiBuilder.post;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.config.MinioConfig;
import com.goodmem.rest.dto.CreateSpaceRequest;
import com.goodmem.rest.dto.Space;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.ConditionalAuthInterceptor;
import com.goodmem.util.EnumConverters;
import com.goodmem.util.RestMapper;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Timestamps;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.ApiKeyServiceGrpc;
import goodmem.v1.Apikey;
import goodmem.v1.Apikey.ApiKey;
import goodmem.v1.Apikey.CreateApiKeyRequest;
import goodmem.v1.Apikey.CreateApiKeyResponse;
import goodmem.v1.Apikey.DeleteApiKeyRequest;
import goodmem.v1.Apikey.ListApiKeysRequest;
import goodmem.v1.Apikey.ListApiKeysResponse;
import goodmem.v1.Apikey.UpdateApiKeyRequest;
import goodmem.v1.Common.StringMap;
import goodmem.v1.EmbedderOuterClass.CreateEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.DeleteEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.Embedder;
import goodmem.v1.EmbedderOuterClass.GetEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.ListEmbeddersRequest;
import goodmem.v1.EmbedderOuterClass.ListEmbeddersResponse;
import goodmem.v1.EmbedderOuterClass.UpdateEmbedderRequest;
import goodmem.v1.EmbedderServiceGrpc;
import goodmem.v1.MemoryOuterClass.CreateMemoryRequest;
import goodmem.v1.MemoryOuterClass.DeleteMemoryRequest;
import goodmem.v1.MemoryOuterClass.GetMemoryRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesResponse;
import goodmem.v1.MemoryOuterClass.Memory;
import goodmem.v1.MemoryServiceGrpc;
import goodmem.v1.SpaceOuterClass;
import goodmem.v1.SpaceServiceGrpc;
import goodmem.v1.UserOuterClass;
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
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiInfo;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
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
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.tinylog.Logger;

/**
 * Main server class for the GoodMem API.
 * 
 * <p>This class serves as the entry point and primary implementation of the GoodMem REST API.
 * It initializes both gRPC and REST servers, configures routing, and implements all REST
 * API endpoint handlers that bridge to the underlying gRPC service implementations.
 * 
 * <h2>Implementation Notes</h2>
 * 
 * <h3>Protocol Buffer Import Convention</h3>
 * <p>When referring to Protocol Buffer generated classes, import the outer class and reference 
 * inner classes with qualified names, rather than importing inner classes directly:
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
 * <p>Each REST endpoint handler follows a consistent pattern:
 * <ol>
 *   <li>Extract parameters from the request (path, query, header)</li>
 *   <li>Parse and validate the request body using the appropriate DTO</li>
 *   <li>Convert the DTO to a Protocol Buffer request message</li>
 *   <li>Call the corresponding gRPC service method</li>
 *   <li>Convert the Protocol Buffer response to a DTO</li>
 *   <li>Return the DTO as JSON</li>
 * </ol>
 * 
 * <h3>Error Handling</h3>
 * <p>Error handling is consistent across all handlers:
 * <ul>
 *   <li>Validation errors return 400 Bad Request</li>
 *   <li>Authentication errors return 401 Unauthorized</li>
 *   <li>Permission errors return 403 Forbidden</li>
 *   <li>Not found errors return 404 Not Found</li>
 *   <li>Server errors return 500 Internal Server Error</li>
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
                          swaggerConfiguration -> swaggerConfiguration.setDocumentationPath("/openapi")));

                  config.router.apiBuilder(
                      () -> {
                        // Space endpoints
                        path(
                            "/v1/spaces",
                            () -> {
                              post(this::handleCreateSpace);
                              get(this::handleListSpaces);
                              path(
                                  "{id}",
                                  () -> {
                                    get(this::handleGetSpace);
                                    put(this::handleUpdateSpace);
                                    delete(this::handleDeleteSpace);
                                  });
                            });

                        // User endpoints
                        path(
                            "/v1/users",
                            () -> {
                              path(
                                  "{id}",
                                  () -> {
                                    get(this::handleGetUser);
                                  });
                            });
                        path(
                            "/v1/system/init",
                            () -> {
                              post(this::handleSystemInit);
                            });
                        // TODO: Upgrade memory endpoints with proper DTOs and OpenAPI annotations before uncommenting
                        // All handler methods are implemented but need DTO/OpenAPI enhancement
                        // path(
                        //     "/v1/memories",
                        //     () -> {
                        //       post(this::handleCreateMemory);
                        //       path(
                        //           "{id}",
                        //           () -> {
                        //             get(this::handleGetMemory);
                        //             delete(this::handleDeleteMemory);
                        //           });
                        //     });
                        // path(
                        //     "/v1/spaces",
                        //     () -> {
                        //       path(
                        //           "{spaceId}/memories",
                        //           () -> {
                        //             get(this::handleListMemories);
                        //           });
                        //     });
//
                        // API Key endpoints
                        path(
                            "/v1/apikeys",
                            () -> {
                              post(this::handleCreateApiKey);
                              get(this::handleListApiKeys);
                              path(
                                  "{id}",
                                  () -> {
                                    put(this::handleUpdateApiKey);
                                    delete(this::handleDeleteApiKey);
                                  });
                            });
                        // Embedder endpoints
                        path(
                            "/v1/embedders",
                            () -> {
                              post(this::handleCreateEmbedder);
                              get(this::handleListEmbedders);
                              path(
                                  "{id}",
                                  () -> {
                                    get(this::handleGetEmbedder);
                                    put(this::handleUpdateEmbedder);
                                    delete(this::handleDeleteEmbedder);
                                  });
                            });
                      });
                })
            .start(REST_PORT);

    Logger.info("REST server started, listening on port {}.", REST_PORT);
  }

  // Space handlers

  /**
   * TODO(claude): add good Javadoc
   * @param ctx
   */
  @OpenApi(
      path = "/v1/spaces",
      methods = { HttpMethod.POST },
      summary = "Create a new Space",
      description =
          "Creates a new space with the provided name, labels, and embedder configuration. A space is a container for organizing related memories.",
      operationId = "createSpace",
      tags = "Spaces",
      requestBody =
          @OpenApiRequestBody(
              description = "Space configuration details",
              required = true,
              content =
                  @OpenApiContent(
                      from = com.goodmem.rest.dto.CreateSpaceRequest.class,
                      example =
                          """
              {
                "name": "My Research Space",
                "embedderId": "00000000-0000-0000-0000-000000000001",
                "publicRead": false,
                "labels": {
                  "category": "research",
                  "project": "ai-embeddings"
                }
              }
              """)),
      responses = {
        @OpenApiResponse(
            status = "200",
            description = "Successfully created space",
            content = @OpenApiContent(from = Space.class)),
        @OpenApiResponse(
            status = "400",
            description = "Invalid request - missing required fields or invalid format"),
        @OpenApiResponse(status = "401", description = "Unauthorized - invalid or missing API key"),
        @OpenApiResponse(
            status = "403",
            description = "Forbidden - insufficient permissions to create spaces")
      })
  private void handleCreateSpace(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateSpace request with API key: {}.", apiKey);

    // Parse the JSON body into our DTO
    CreateSpaceRequest requestDto = ctx.bodyAsClass(CreateSpaceRequest.class);
    
    // Convert the DTO to the gRPC request builder
    var requestBuilder = SpaceOuterClass.CreateSpaceRequest.newBuilder();
    
    // Set the name if provided
    if (!Strings.isNullOrEmpty(requestDto.name())) {
      requestBuilder.setName(requestDto.name());
    }

    // Convert and set the embedder ID if provided
    if (!Strings.isNullOrEmpty(requestDto.embedderId())) {
      StatusOr<ByteString> embedderIdOr = convertHexToUuidBytes(requestDto.embedderId());
      if (embedderIdOr.isOk()) {
        requestBuilder.setEmbedderId(embedderIdOr.getValue());
      } else {
        Logger.warn("Invalid embedder ID format: {}", requestDto.embedderId());
      }
    }

    // Set the public read flag if provided
    if (requestDto.publicRead() != null) {
      requestBuilder.setPublicRead(requestDto.publicRead());
    }

    // Set labels if provided
    if (requestDto.labels() != null && !requestDto.labels().isEmpty()) {
      requestBuilder.putAllLabels(requestDto.labels());
    }
    
    // Set owner ID if provided
    if (!Strings.isNullOrEmpty(requestDto.ownerId())) {
      StatusOr<ByteString> ownerIdOr = convertHexToUuidBytes(requestDto.ownerId());
      if (ownerIdOr.isOk()) {
        requestBuilder.setOwnerId(ownerIdOr.getValue());
      } else {
        Logger.warn("Invalid owner ID format: {}", requestDto.ownerId());
      }
    }

    // Call the gRPC service
    SpaceOuterClass.Space response = spaceService.createSpace(requestBuilder.build());
    
    // Map the gRPC response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    Space responseDto = new Space(
        (String) responseMap.get("space_id"),
        (String) responseMap.get("name"),
        (Map<String, String>) responseMap.get("labels"),
        (String) responseMap.get("embedder_id"),
        (Long) responseMap.get("created_at"),
        (Long) responseMap.get("updated_at"),
        (String) responseMap.get("owner_id"),
        (String) responseMap.get("created_by_id"),
        (String) responseMap.get("updated_by_id"),
        (Boolean) responseMap.get("public_read")
    );
    
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to retrieve a Space by ID. Converts the hex UUID to binary format and
   * calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/spaces/{id}",
      methods = { HttpMethod.GET },
      summary = "Get a space by ID",
      description = "Retrieves a specific space by its unique identifier. Returns the complete space information, including name, labels, embedder configuration, and metadata.",
      operationId = "getSpace",
      tags = "Spaces",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the space to retrieve",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved space",
              content = @OpenApiContent(from = Space.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - space ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to view this space"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - space with the specified ID does not exist")
      })
  private void handleGetSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    // Create the DTO from path parameter
    com.goodmem.rest.dto.GetSpaceRequest requestDto = new com.goodmem.rest.dto.GetSpaceRequest(spaceIdHex);
    
    // Validate and convert the spaceId
    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(requestDto.spaceId());
    if (spaceIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid space ID format");
      return;
    }
    
    // Create and execute the gRPC request
    SpaceOuterClass.GetSpaceRequest request = SpaceOuterClass.GetSpaceRequest.newBuilder()
        .setSpaceId(spaceIdOr.getValue())
        .build();
    
    SpaceOuterClass.Space response = spaceService.getSpace(request);
    
    // Map the gRPC response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    Space responseDto = new Space(
        (String) responseMap.get("space_id"),
        (String) responseMap.get("name"),
        (Map<String, String>) responseMap.get("labels"),
        (String) responseMap.get("embedder_id"),
        (Long) responseMap.get("created_at"),
        (Long) responseMap.get("updated_at"),
        (String) responseMap.get("owner_id"),
        (String) responseMap.get("created_by_id"),
        (String) responseMap.get("updated_by_id"),
        (Boolean) responseMap.get("public_read")
    );
    
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to list spaces with optional filtering and pagination. Converts query
   * parameters to the gRPC request format and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/spaces",
      methods = { HttpMethod.GET },
      summary = "List spaces",
      description = "Retrieves a list of spaces accessible to the caller, with optional filtering by owner, labels, and name. Results are paginated with a maximum number of spaces per response.",
      operationId = "listSpaces",
      tags = "Spaces",
      queryParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "owner_id",
              description = "Filter spaces by owner ID. If not provided, shows spaces based on permissions.",
              required = false,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000"),
          @io.javalin.openapi.OpenApiParam(
              name = "name_filter", 
              description = "Filter spaces by name using glob pattern matching",
              required = false,
              type = String.class,
              example = "Research*"),
          @io.javalin.openapi.OpenApiParam(
              name = "max_results",
              description = "Maximum number of results to return in a single page",
              required = false,
              type = Integer.class,
              example = "20"),
          @io.javalin.openapi.OpenApiParam(
              name = "next_token",
              description = "Pagination token for retrieving the next set of results",
              required = false,
              type = String.class,
              example = "eyJzdGFydCI6MjAsIm93bmVySWQiOiJiMzMwM2QwYS0..."),
          @io.javalin.openapi.OpenApiParam(
              name = "sort_by",
              description = "Field to sort by. Supported values: \"created_time\", \"name\", \"updated_time\"",
              required = false,
              type = String.class,
              example = "name"),
          @io.javalin.openapi.OpenApiParam(
              name = "sort_order",
              description = "Sort order (ASCENDING or DESCENDING)",
              required = false,
              type = String.class,
              example = "ASCENDING"),
          @io.javalin.openapi.OpenApiParam(
              name = "label.*",
              description = "Filter by label value. Multiple label filters can be specified (e.g., ?label.project=AI&label.team=NLP)",
              required = false,
              type = String.class,
              example = "?label.project=AI&label.team=NLP")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved spaces",
              content = @OpenApiContent(from = com.goodmem.rest.dto.ListSpacesResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - invalid filter parameters or pagination token"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to list spaces")
      })
  private void handleListSpaces(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListSpaces request with API key: {}", apiKey);

    // Extract query parameters and create request DTO
    Map<String, String> labelSelectors = new HashMap<>();
    ctx.queryParamMap().forEach((key, values) -> {
      if (key.startsWith("label.") && !values.isEmpty()) {
        String labelKey = key.substring("label.".length());
        String value = values.getFirst();
        if (value != null) {
          labelSelectors.put(labelKey, value);
        }
      }
    });
    
    // Parse max_results as Integer if provided
    Integer maxResults = null;
    String maxResultsStr = ctx.queryParam("max_results");
    if (!Strings.isNullOrEmpty(maxResultsStr)) {
      try {
        maxResults = Integer.parseInt(maxResultsStr);
      } catch (NumberFormatException e) {
        Logger.warn("Invalid max_results parameter: {}", maxResultsStr);
      }
    }
    
    // Parse sort_order if provided
    String sortOrderStr = ctx.queryParam("sort_order");
    
    // Create the DTO from query parameters
    com.goodmem.rest.dto.ListSpacesRequest requestDto = new com.goodmem.rest.dto.ListSpacesRequest(
        ctx.queryParam("owner_id"),
        labelSelectors.isEmpty() ? null : labelSelectors,
        ctx.queryParam("name_filter"),
        maxResults,
        ctx.queryParam("next_token"),
        ctx.queryParam("sort_by"),
        com.goodmem.rest.dto.SortOrder.fromString(sortOrderStr)
    );
    
    // Convert the DTO to gRPC request
    SpaceOuterClass.ListSpacesRequest.Builder requestBuilder = SpaceOuterClass.ListSpacesRequest.newBuilder();
    
    // Set the owner_id if provided
    if (!Strings.isNullOrEmpty(requestDto.ownerId())) {
      StatusOr<ByteString> ownerIdOr = convertHexToUuidBytes(requestDto.ownerId());
      if (ownerIdOr.isOk()) {
        requestBuilder.setOwnerId(ownerIdOr.getValue());
      } else {
        Logger.warn("Invalid owner_id format: {}", requestDto.ownerId());
      }
    }
    
    // Add label selectors
    if (requestDto.labelSelectors() != null && !requestDto.labelSelectors().isEmpty()) {
      requestDto.labelSelectors().forEach(requestBuilder::putLabelSelectors);
    }
    
    // Set the name filter if provided
    if (!Strings.isNullOrEmpty(requestDto.nameFilter())) {
      requestBuilder.setNameFilter(requestDto.nameFilter());
    }
    
    // Set pagination parameters
    if (requestDto.maxResults() != null) {
      requestBuilder.setMaxResults(requestDto.maxResults());
    }
    
    if (!Strings.isNullOrEmpty(requestDto.nextToken())) {
      requestBuilder.setNextToken(requestDto.nextToken());
    }
    
    // Set sorting parameters
    if (!Strings.isNullOrEmpty(requestDto.sortBy())) {
      requestBuilder.setSortBy(requestDto.sortBy());
    }
    
    // Set sort order if provided
    if (requestDto.sortOrder() != null) {
      requestBuilder.setSortOrder(requestDto.sortOrder().toProtoSortOrder());
    }

    // Call the gRPC service
    SpaceOuterClass.ListSpacesResponse response = spaceService.listSpaces(requestBuilder.build());
    
    // Convert the response to a list of Space DTOs
    List<Space> spaces = response.getSpacesList().stream()
        .map(space -> {
          Map<String, Object> spaceMap = RestMapper.toJsonMap(space);
          return new Space(
              (String) spaceMap.get("space_id"),
              (String) spaceMap.get("name"),
              (Map<String, String>) spaceMap.get("labels"),
              (String) spaceMap.get("embedder_id"),
              (Long) spaceMap.get("created_at"),
              (Long) spaceMap.get("updated_at"),
              (String) spaceMap.get("owner_id"),
              (String) spaceMap.get("created_by_id"),
              (String) spaceMap.get("updated_by_id"),
              (Boolean) spaceMap.get("public_read")
          );
        })
        .toList();
    
    // Create and return the ListSpacesResponse DTO
    com.goodmem.rest.dto.ListSpacesResponse responseDto = new com.goodmem.rest.dto.ListSpacesResponse(
        spaces,
        response.hasNextToken() ? response.getNextToken() : null
    );
    
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to update a Space by ID. Converts the hex UUID to binary format, builds
   * the update request from the DTO, and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/spaces/{id}",
      methods = { HttpMethod.PUT },
      summary = "Update a space",
      description = "Updates an existing space with new values for the specified fields. Fields not included in the request remain unchanged.",
      operationId = "updateSpace",
      tags = "Spaces",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the space to update",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      requestBody =
          @OpenApiRequestBody(
              description = "Space update details",
              required = true,
              content =
                  @OpenApiContent(
                      from = com.goodmem.rest.dto.UpdateSpaceRequest.class,
                      example =
                          """
              {
                "name": "Updated Research Space",
                "publicRead": true,
                "replaceLabels": {
                  "category": "updated-research",
                  "project": "ai-embedding-project"
                }
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully updated space",
              content = @OpenApiContent(from = Space.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - ID format or update parameters invalid"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to update this space"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - space with the specified ID does not exist")
      })
  private void handleUpdateSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    // Create our DTO from the path parameter and request body
    // First parse the body as our DTO class
    com.goodmem.rest.dto.UpdateSpaceRequest requestDto = ctx.bodyAsClass(com.goodmem.rest.dto.UpdateSpaceRequest.class);
    
    // Validate label strategy (only one of replaceLabels or mergeLabels can be set)
    try {
      requestDto.validateLabelStrategy();
    } catch (IllegalArgumentException e) {
      setError(ctx, 400, e.getMessage());
      return;
    }
    
    // Validate and convert the spaceId
    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid space ID format");
      return;
    }
    
    // Build the gRPC request
    SpaceOuterClass.UpdateSpaceRequest.Builder requestBuilder = 
        SpaceOuterClass.UpdateSpaceRequest.newBuilder()
            .setSpaceId(spaceIdOr.getValue());
    
    // Set the name if provided in the DTO
    if (requestDto.name() != null) {
      requestBuilder.setName(requestDto.name());
    }
    
    // Set the public read flag if provided
    if (requestDto.publicRead() != null) {
      requestBuilder.setPublicRead(requestDto.publicRead());
    }
    
    // Handle label update strategy
    if (requestDto.replaceLabels() != null) {
      StringMap.Builder labelsBuilder = StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.replaceLabels());
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    } else if (requestDto.mergeLabels() != null) {
      StringMap.Builder labelsBuilder = StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.mergeLabels());
      requestBuilder.setMergeLabels(labelsBuilder.build());
    }
    
    // Call the gRPC service
    SpaceOuterClass.Space response = spaceService.updateSpace(requestBuilder.build());
    
    // Map the gRPC response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    Space responseDto = new Space(
        (String) responseMap.get("space_id"),
        (String) responseMap.get("name"),
        (Map<String, String>) responseMap.get("labels"),
        (String) responseMap.get("embedder_id"),
        (Long) responseMap.get("created_at"),
        (Long) responseMap.get("updated_at"),
        (String) responseMap.get("owner_id"),
        (String) responseMap.get("created_by_id"),
        (String) responseMap.get("updated_by_id"),
        (Boolean) responseMap.get("public_read")
    );
    
    // Return the DTO as JSON
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to delete a Space by ID. Converts the hex UUID to binary format and
   * calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/spaces/{id}",
      methods = { HttpMethod.DELETE },
      summary = "Delete a space",
      description = "Deletes a space and all of its associated memories. This operation cannot be undone. A 204 status code indicates successful deletion.",
      operationId = "deleteSpace",
      tags = "Spaces",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the space to delete",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "204",
              description = "Space successfully deleted"),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - space ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to delete this space"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - space with the specified ID does not exist")
      })
  private void handleDeleteSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    // Create our DTO from the path parameter
    com.goodmem.rest.dto.DeleteSpaceRequest requestDto = new com.goodmem.rest.dto.DeleteSpaceRequest(spaceIdHex);
    
    // Validate and convert the spaceId
    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(requestDto.spaceId());
    if (spaceIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid space ID format");
      return;
    }
    
    // Build the gRPC request
    SpaceOuterClass.DeleteSpaceRequest request = 
        SpaceOuterClass.DeleteSpaceRequest.newBuilder()
            .setSpaceId(spaceIdOr.getValue())
            .build();
    
    // Call the gRPC service
    spaceService.deleteSpace(request);
    
    // Return 204 No Content on successful deletion
    ctx.status(204);
  }

  // User handlers
  
  /**
   * Handles a REST request to retrieve a User by ID. Converts the hex UUID to binary format and
   * calls the gRPC service to fetch the user details.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/users/{id}",
      methods = { HttpMethod.GET },
      summary = "Get a user by ID",
      description = "Retrieves a specific user by their unique identifier. Returns the user's information including email, display name, and creation time.",
      operationId = "getUser",
      tags = "Users",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the user to retrieve",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      queryParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "email",
              description = "Alternative lookup by email address (if ID is not specified)",
              required = false,
              type = String.class,
              example = "user@example.com")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved user",
              content = @OpenApiContent(from = com.goodmem.rest.dto.UserResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - user ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to view this user's information"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - user with the specified ID does not exist")
      })
  private void handleGetUser(Context ctx) {
    String userIdHex = ctx.pathParam("id");
    String email = ctx.queryParam("email");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetUser request for ID: {} with API key: {}", userIdHex, apiKey);

    // Create the DTO from the path parameter and query parameter
    com.goodmem.rest.dto.GetUserRequest requestDto = new com.goodmem.rest.dto.GetUserRequest(userIdHex, email);
    
    // Build the gRPC request builder
    UserOuterClass.GetUserRequest.Builder requestBuilder = UserOuterClass.GetUserRequest.newBuilder();
    
    // Set the user ID if provided
    if (!Strings.isNullOrEmpty(requestDto.userId())) {
      StatusOr<ByteString> userIdOr = convertHexToUuidBytes(requestDto.userId());
      if (userIdOr.isNotOk()) {
        setError(ctx, 400, "Invalid user ID format");
        return;
      }
      requestBuilder.setUserId(userIdOr.getValue());
    }
    
    // Set the email if provided
    if (!Strings.isNullOrEmpty(requestDto.email())) {
      requestBuilder.setEmail(requestDto.email());
    }
    
    // Call the gRPC service
    UserOuterClass.User response = userService.getUser(requestBuilder.build());
    
    // Map the gRPC response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    com.goodmem.rest.dto.UserResponse responseDto = new com.goodmem.rest.dto.UserResponse(
        (String) responseMap.get("user_id"),
        (String) responseMap.get("email"),
        (String) responseMap.get("display_name"),
        (String) responseMap.get("username"),
        (Long) responseMap.get("created_at"),
        (Long) responseMap.get("updated_at")
    );
    
    ctx.json(responseDto);
  }

  // Memory handlers

  /**
   * Handles a REST request to create a new Memory. Builds the create request from JSON and calls
   * the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleCreateMemory(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateMemory request with API key: {}", apiKey);

    CreateMemoryRequest.Builder requestBuilder = CreateMemoryRequest.newBuilder();
    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    if (json.containsKey("space_id")) {
      StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes((String) json.get("space_id"));
      if (spaceIdOr.isNotOk()) {
        setError(ctx, 400, "Invalid space ID format");
        return;
      }
      requestBuilder.setSpaceId(spaceIdOr.getValue());
    }

    if (json.containsKey("original_content_ref")) {
      requestBuilder.setOriginalContentRef((String) json.get("original_content_ref"));
    }

    if (json.containsKey("content_type")) {
      requestBuilder.setContentType((String) json.get("content_type"));
    }

    if (json.containsKey("metadata") && json.get("metadata") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> metadata = (Map<String, String>) json.get("metadata");
      requestBuilder.putAllMetadata(metadata);
    }

    Memory response = memoryService.createMemory(requestBuilder.build());
    ctx.json(RestMapper.toJsonMap(response));
  }

  /**
   * Handles a REST request to retrieve a Memory by ID. Converts the hex UUID to binary format and
   * calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleGetMemory(Context ctx) {
    String memoryIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetMemory request for ID: {} with API key: {}", memoryIdHex, apiKey);

    StatusOr<ByteString> memoryIdOr = convertHexToUuidBytes(memoryIdHex);
    if (memoryIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid memory ID format");
      return;
    }

    Memory response =
        memoryService.getMemory(
            GetMemoryRequest.newBuilder().setMemoryId(memoryIdOr.getValue()).build());
    ctx.json(RestMapper.toJsonMap(response));
  }

  /**
   * Handles a REST request to list Memories within a Space. Converts the space hex UUID to binary
   * format and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleListMemories(Context ctx) {
    String spaceIdHex = ctx.pathParam("spaceId");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST ListMemories request for space ID: " + spaceIdHex + " with API key: " + apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid space ID format");
      return;
    }

    ListMemoriesResponse response =
        memoryService.listMemories(
            ListMemoriesRequest.newBuilder().setSpaceId(spaceIdOr.getValue()).build());
    ctx.json(
        Map.of(
            "memories", response.getMemoriesList().stream().map(RestMapper::toJsonMap).toList()));
  }

  /**
   * Handles a REST request to delete a Memory by ID. Converts the hex UUID to binary format and
   * calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleDeleteMemory(Context ctx) {
    String memoryIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteMemory request for ID: {} with API key: {}", memoryIdHex, apiKey);

    StatusOr<ByteString> memoryIdOr = convertHexToUuidBytes(memoryIdHex);
    if (memoryIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid memory ID format");
      return;
    }
    memoryService.deleteMemory(
        DeleteMemoryRequest.newBuilder().setMemoryId(memoryIdOr.getValue()).build());
    ctx.status(204);
  }

  // API Key handlers
  /**
   * Handles a REST request to create a new API Key. Builds the create request from the DTO
   * and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/apikeys",
      methods = { HttpMethod.POST },
      summary = "Create a new API key",
      description = "Creates a new API key for the authenticated user. The raw API key is returned only in this response and cannot be retrieved again.",
      operationId = "createApiKey",
      tags = "API Keys",
      requestBody =
          @OpenApiRequestBody(
              description = "API key configuration details",
              required = true,
              content =
                  @OpenApiContent(
                      from = com.goodmem.rest.dto.CreateApiKeyRequest.class,
                      example =
                          """
              {
                "labels": {
                  "environment": "production",
                  "service": "recommendation-engine"
                },
                "expiresAt": 1714521600000
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully created API key",
              content = @OpenApiContent(from = com.goodmem.rest.dto.CreateApiKeyResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - missing required fields or invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to create API keys")
      })
  private void handleCreateApiKey(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateApiKey request with API key: {}", apiKey);

    // Parse the request body into our DTO
    com.goodmem.rest.dto.CreateApiKeyRequest requestDto = ctx.bodyAsClass(com.goodmem.rest.dto.CreateApiKeyRequest.class);
    
    // Convert the DTO to the gRPC request
    CreateApiKeyRequest.Builder requestBuilder = CreateApiKeyRequest.newBuilder();
    
    // Add labels if provided
    if (requestDto.labels() != null && !requestDto.labels().isEmpty()) {
      requestBuilder.putAllLabels(requestDto.labels());
    }
    
    // Add expiration timestamp if provided
    if (requestDto.expiresAt() != null) {
      // Convert from milliseconds to Timestamp
      requestBuilder.setExpiresAt(
          com.google.protobuf.Timestamp.newBuilder()
              .setSeconds(requestDto.expiresAt() / 1000)
              .setNanos((int) ((requestDto.expiresAt() % 1000) * 1000000))
              .build());
    }

    // Call the gRPC service
    CreateApiKeyResponse response = apiKeyService.createApiKey(requestBuilder.build());
    
    // Convert the ApiKey metadata to our DTO
    Map<String, Object> metadataMap = RestMapper.toJsonMap(response.getApiKeyMetadata());
    com.goodmem.rest.dto.ApiKeyResponse apiKeyResponseDto = new com.goodmem.rest.dto.ApiKeyResponse(
        (String) metadataMap.get("api_key_id"),
        (String) metadataMap.get("user_id"),
        (String) metadataMap.get("key_prefix"),
        (String) metadataMap.get("status"),
        (Map<String, String>) metadataMap.get("labels"),
        metadataMap.containsKey("expires_at") ? (Long) metadataMap.get("expires_at") : null,
        metadataMap.containsKey("last_used_at") ? (Long) metadataMap.get("last_used_at") : null,
        (Long) metadataMap.get("created_at"),
        (Long) metadataMap.get("updated_at"),
        (String) metadataMap.get("created_by_id"),
        (String) metadataMap.get("updated_by_id")
    );
    
    // Create the response DTO
    com.goodmem.rest.dto.CreateApiKeyResponse responseDto = 
        new com.goodmem.rest.dto.CreateApiKeyResponse(apiKeyResponseDto, response.getRawApiKey());
    
    // Return the response
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to list API Keys for the current user. Calls the gRPC service to get the
   * list of keys and converts them to DTOs.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/apikeys",
      methods = { HttpMethod.GET },
      summary = "List API keys",
      description = "Retrieves a list of API keys belonging to the authenticated user. The list includes metadata about each key but not the actual key values.",
      operationId = "listApiKeys",
      tags = "API Keys",
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved API keys",
              content = @OpenApiContent(from = com.goodmem.rest.dto.ListApiKeysResponse.class)),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to list API keys")
      })
  private void handleListApiKeys(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListApiKeys request with API key: {}", apiKey);

    // Create and execute the gRPC request (note: currently no parameters)
    ListApiKeysRequest request = ListApiKeysRequest.newBuilder().build();
    ListApiKeysResponse response = apiKeyService.listApiKeys(request);
    
    // Convert each ApiKey to our DTO format
    List<com.goodmem.rest.dto.ApiKeyResponse> apiKeys = response.getKeysList().stream()
        .map(key -> {
          Map<String, Object> keyMap = RestMapper.toJsonMap(key);
          return new com.goodmem.rest.dto.ApiKeyResponse(
              (String) keyMap.get("api_key_id"),
              (String) keyMap.get("user_id"),
              (String) keyMap.get("key_prefix"),
              (String) keyMap.get("status"),
              (Map<String, String>) keyMap.get("labels"),
              keyMap.containsKey("expires_at") ? (Long) keyMap.get("expires_at") : null,
              keyMap.containsKey("last_used_at") ? (Long) keyMap.get("last_used_at") : null,
              (Long) keyMap.get("created_at"),
              (Long) keyMap.get("updated_at"),
              (String) keyMap.get("created_by_id"),
              (String) keyMap.get("updated_by_id")
          );
        })
        .toList();
    
    // Create the response DTO
    com.goodmem.rest.dto.ListApiKeysResponse responseDto = 
        new com.goodmem.rest.dto.ListApiKeysResponse(apiKeys);
    
    // Return the response
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to update an API Key by ID. Converts the hex UUID to binary format,
   * builds the update request from the DTO, and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/apikeys/{id}",
      methods = { HttpMethod.PUT },
      summary = "Update an API key",
      description = "Updates an existing API key with new values for status or labels.",
      operationId = "updateApiKey",
      tags = "API Keys",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the API key to update",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      requestBody =
          @OpenApiRequestBody(
              description = "API key update details",
              required = true,
              content =
                  @OpenApiContent(
                      from = com.goodmem.rest.dto.UpdateApiKeyRequest.class,
                      example =
                          """
              {
                "status": "ACTIVE",
                "replaceLabels": {
                  "environment": "production",
                  "service": "recommendation-engine"
                }
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully updated API key",
              content = @OpenApiContent(from = com.goodmem.rest.dto.ApiKeyResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - ID format or update parameters invalid"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to update this API key"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - API key with the specified ID does not exist")
      })
  private void handleUpdateApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    // Parse the request body into our DTO
    com.goodmem.rest.dto.UpdateApiKeyRequest requestDto = ctx.bodyAsClass(com.goodmem.rest.dto.UpdateApiKeyRequest.class);
    
    // Validate label strategy (only one of replaceLabels or mergeLabels can be set)
    try {
      requestDto.validateLabelStrategy();
    } catch (IllegalArgumentException e) {
      setError(ctx, 400, e.getMessage());
      return;
    }
    
    // Validate and convert the API key ID
    StatusOr<ByteString> apiKeyIdOr = convertHexToUuidBytes(apiKeyIdHex);
    if (apiKeyIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid API key ID format");
      return;
    }
    
    // Build the gRPC request
    UpdateApiKeyRequest.Builder requestBuilder =
        UpdateApiKeyRequest.newBuilder().setApiKeyId(apiKeyIdOr.getValue());

    // Handle label update strategies using StringMap
    if (requestDto.replaceLabels() != null) {
      goodmem.v1.Common.StringMap.Builder labelsBuilder = goodmem.v1.Common.StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.replaceLabels());
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    } else if (requestDto.mergeLabels() != null) {
      goodmem.v1.Common.StringMap.Builder labelsBuilder = goodmem.v1.Common.StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.mergeLabels());
      requestBuilder.setMergeLabels(labelsBuilder.build());
    }

    // Set the status if provided
    if (requestDto.status() != null) {
      Apikey.Status status =
          switch (requestDto.status().toUpperCase()) {
            case "ACTIVE" -> Apikey.Status.ACTIVE;
            case "INACTIVE" -> Apikey.Status.INACTIVE;
            default -> Apikey.Status.STATUS_UNSPECIFIED;
          };
      requestBuilder.setStatus(status);
    }

    // Call the gRPC service
    ApiKey response = apiKeyService.updateApiKey(requestBuilder.build());
    
    // Convert the response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    com.goodmem.rest.dto.ApiKeyResponse responseDto = new com.goodmem.rest.dto.ApiKeyResponse(
        (String) responseMap.get("api_key_id"),
        (String) responseMap.get("user_id"),
        (String) responseMap.get("key_prefix"),
        (String) responseMap.get("status"),
        (Map<String, String>) responseMap.get("labels"),
        responseMap.containsKey("expires_at") ? (Long) responseMap.get("expires_at") : null,
        responseMap.containsKey("last_used_at") ? (Long) responseMap.get("last_used_at") : null,
        (Long) responseMap.get("created_at"),
        (Long) responseMap.get("updated_at"),
        (String) responseMap.get("created_by_id"),
        (String) responseMap.get("updated_by_id")
    );
    
    // Return the response
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to delete an API Key by ID. Converts the hex UUID to binary format and
   * calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/apikeys/{id}",
      methods = { HttpMethod.DELETE },
      summary = "Delete an API key",
      description = "Deletes (revokes) an API key. This operation cannot be undone.",
      operationId = "deleteApiKey",
      tags = "API Keys",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the API key to delete",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "204",
              description = "API key successfully deleted"),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - API key ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to delete this API key"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - API key with the specified ID does not exist")
      })
  private void handleDeleteApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    // Create our DTO from the path parameter
    com.goodmem.rest.dto.DeleteApiKeyRequest requestDto = new com.goodmem.rest.dto.DeleteApiKeyRequest(apiKeyIdHex);
    
    // Validate and convert the API key ID
    StatusOr<ByteString> apiKeyIdOr = convertHexToUuidBytes(requestDto.apiKeyId());
    if (apiKeyIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid API key ID format");
      return;
    }
    
    // Build the gRPC request
    DeleteApiKeyRequest request = DeleteApiKeyRequest.newBuilder()
        .setApiKeyId(apiKeyIdOr.getValue())
        .build();
    
    // Call the gRPC service
    apiKeyService.deleteApiKey(request);
    
    // Return 204 No Content on successful deletion
    ctx.status(204);
  }

  // Embedder handlers

  /**
   * Handles a REST request to create a new Embedder. Converts the DTO from JSON and calls
   * the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/embedders",
      methods = { HttpMethod.POST },
      summary = "Create a new embedder",
      description = 
          "Creates a new embedder configuration for vectorizing content. Embedders represent connections " +
          "to different embedding API services (like OpenAI, vLLM, etc.) and include all the necessary " +
          "configuration to use them with memory spaces.",
      operationId = "createEmbedder",
      tags = "Embedders",
      requestBody =
          @OpenApiRequestBody(
              description = "Embedder configuration details",
              required = true,
              content =
                  @OpenApiContent(
                      from = com.goodmem.rest.dto.CreateEmbedderRequest.class,
                      example =
                          """
              {
                "displayName": "OpenAI Embedding Model",
                "description": "OpenAI text embedding model with 1536 dimensions",
                "providerType": "OPENAI",
                "endpointUrl": "https://api.openai.com",
                "apiPath": "/v1/embeddings",
                "modelIdentifier": "text-embedding-3-small",
                "dimensionality": 1536,
                "maxSequenceLength": 8192,
                "supportedModalities": ["TEXT"],
                "credentials": "sk-1234567890abcdef",
                "labels": {
                  "environment": "production",
                  "team": "research"
                },
                "version": "1.0"
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully created embedder",
              content = @OpenApiContent(from = com.goodmem.rest.dto.EmbedderResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - missing required fields or invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to create embedders"),
          @OpenApiResponse(
              status = "409",
              description = "Conflict - an embedder with the same connection details already exists")
      })
  private void handleCreateEmbedder(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateEmbedder request with API key: {}", apiKey);

    // Parse the JSON body into our DTO
    com.goodmem.rest.dto.CreateEmbedderRequest requestDto = ctx.bodyAsClass(com.goodmem.rest.dto.CreateEmbedderRequest.class);
    
    // Validate the DTO
    try {
      requestDto.validate();
    } catch (IllegalArgumentException e) {
      setError(ctx, 400, e.getMessage());
      return;
    }
    
    // Build the gRPC request from the DTO
    CreateEmbedderRequest.Builder requestBuilder = CreateEmbedderRequest.newBuilder();
    
    // Set the required fields
    requestBuilder.setDisplayName(requestDto.displayName());
    
    if (requestDto.providerType() != null) {
      requestBuilder.setProviderType(requestDto.providerType().toProtoProviderType());
    }
    
    requestBuilder.setEndpointUrl(requestDto.endpointUrl());
    requestBuilder.setModelIdentifier(requestDto.modelIdentifier());
    requestBuilder.setDimensionality(requestDto.dimensionality());
    requestBuilder.setCredentials(requestDto.credentials());
    
    // Set the optional fields if provided
    if (requestDto.description() != null) {
      requestBuilder.setDescription(requestDto.description());
    }
    
    if (requestDto.apiPath() != null) {
      requestBuilder.setApiPath(requestDto.apiPath());
    }
    
    if (requestDto.maxSequenceLength() != null) {
      requestBuilder.setMaxSequenceLength(requestDto.maxSequenceLength());
    }
    
    if (requestDto.supportedModalities() != null && !requestDto.supportedModalities().isEmpty()) {
      for (com.goodmem.rest.dto.Modality modality : requestDto.supportedModalities()) {
        requestBuilder.addSupportedModalities(modality.toProtoModality());
      }
    }
    
    if (requestDto.labels() != null && !requestDto.labels().isEmpty()) {
      requestBuilder.putAllLabels(requestDto.labels());
    }
    
    if (requestDto.version() != null) {
      requestBuilder.setVersion(requestDto.version());
    }
    
    if (requestDto.monitoringEndpoint() != null) {
      requestBuilder.setMonitoringEndpoint(requestDto.monitoringEndpoint());
    }
    
    if (requestDto.ownerId() != null) {
      StatusOr<ByteString> ownerIdOr = convertHexToUuidBytes(requestDto.ownerId());
      if (ownerIdOr.isOk()) {
        requestBuilder.setOwnerId(ownerIdOr.getValue());
      } else {
        Logger.warn("Invalid owner ID format: {}", requestDto.ownerId());
        setError(ctx, 400, "Invalid owner ID format");
        return;
      }
    }
    
    try {
      // Call the gRPC service
      Embedder response = embedderService.createEmbedder(requestBuilder.build());
      
      // Convert the response to our DTO
      Map<String, Object> responseMap = RestMapper.toJsonMap(response);
      
      List<com.goodmem.rest.dto.Modality> modalityList = null;
      if (responseMap.containsKey("supported_modalities") && responseMap.get("supported_modalities") instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> modalityStrings = (List<String>) responseMap.get("supported_modalities");
        modalityList = modalityStrings.stream()
            .map(com.goodmem.rest.dto.Modality::fromString)
            .filter(modality -> modality != null)
            .toList();
      }
      
      com.goodmem.rest.dto.EmbedderResponse responseDto = new com.goodmem.rest.dto.EmbedderResponse(
          (String) responseMap.get("embedder_id"),
          (String) responseMap.get("display_name"),
          (String) responseMap.get("description"),
          com.goodmem.rest.dto.ProviderType.fromString((String) responseMap.get("provider_type")),
          (String) responseMap.get("endpoint_url"),
          (String) responseMap.get("api_path"),
          (String) responseMap.get("model_identifier"),
          responseMap.get("dimensionality") instanceof Number ? ((Number) responseMap.get("dimensionality")).intValue() : null,
          responseMap.get("max_sequence_length") instanceof Number ? ((Number) responseMap.get("max_sequence_length")).intValue() : null,
          modalityList,
          (Map<String, String>) responseMap.get("labels"),
          (String) responseMap.get("version"),
          (String) responseMap.get("monitoring_endpoint"),
          (String) responseMap.get("owner_id"),
          (Long) responseMap.get("created_at"),
          (Long) responseMap.get("updated_at"),
          (String) responseMap.get("created_by_id"),
          (String) responseMap.get("updated_by_id")
      );
      
      ctx.json(responseDto);
    } catch (io.grpc.StatusRuntimeException e) {
      if (e.getStatus().getCode() == io.grpc.Status.Code.ALREADY_EXISTS) {
        setError(ctx, 409, "An embedder with these connection details already exists");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) {
        setError(ctx, 403, "Permission denied");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
        setError(ctx, 401, "Authentication required");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.INVALID_ARGUMENT) {
        setError(ctx, 400, e.getStatus().getDescription());
      } else {
        Logger.error(e, "Error creating embedder: {}", e.getMessage());
        setError(ctx, 500, "Internal server error");
      }
    }
  }

  /**
   * Handles a REST request to retrieve an Embedder by ID. Converts the hex UUID to binary format
   * and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/embedders/{id}",
      methods = { HttpMethod.GET },
      summary = "Get an embedder by ID",
      description = "Retrieves a specific embedder by its unique identifier. Returns the complete embedder configuration information.",
      operationId = "getEmbedder",
      tags = "Embedders",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the embedder to retrieve",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved embedder",
              content = @OpenApiContent(from = com.goodmem.rest.dto.EmbedderResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - embedder ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to view this embedder"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - embedder with the specified ID does not exist")
      })
  private void handleGetEmbedder(Context ctx) {
    String embedderIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetEmbedder request for ID: {} with API key: {}", embedderIdHex, apiKey);

    // Create the DTO from the path parameter
    com.goodmem.rest.dto.GetEmbedderRequest requestDto = new com.goodmem.rest.dto.GetEmbedderRequest(embedderIdHex);
    
    // Validate and convert the embedder ID
    StatusOr<ByteString> embedderIdOr = convertHexToUuidBytes(requestDto.embedderId());
    if (embedderIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid embedder ID format");
      return;
    }
    
    try {
      // Create and execute the gRPC request
      GetEmbedderRequest request = GetEmbedderRequest.newBuilder()
          .setEmbedderId(embedderIdOr.getValue())
          .build();
      
      Embedder response = embedderService.getEmbedder(request);
      
      // Convert the response to our DTO
      Map<String, Object> responseMap = RestMapper.toJsonMap(response);
      
      List<com.goodmem.rest.dto.Modality> modalityList = null;
      if (responseMap.containsKey("supported_modalities") && responseMap.get("supported_modalities") instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> modalityStrings = (List<String>) responseMap.get("supported_modalities");
        modalityList = modalityStrings.stream()
            .map(com.goodmem.rest.dto.Modality::fromString)
            .filter(modality -> modality != null)
            .toList();
      }
      
      com.goodmem.rest.dto.EmbedderResponse responseDto = new com.goodmem.rest.dto.EmbedderResponse(
          (String) responseMap.get("embedder_id"),
          (String) responseMap.get("display_name"),
          (String) responseMap.get("description"),
          com.goodmem.rest.dto.ProviderType.fromString((String) responseMap.get("provider_type")),
          (String) responseMap.get("endpoint_url"),
          (String) responseMap.get("api_path"),
          (String) responseMap.get("model_identifier"),
          responseMap.get("dimensionality") instanceof Number ? ((Number) responseMap.get("dimensionality")).intValue() : null,
          responseMap.get("max_sequence_length") instanceof Number ? ((Number) responseMap.get("max_sequence_length")).intValue() : null,
          modalityList,
          (Map<String, String>) responseMap.get("labels"),
          (String) responseMap.get("version"),
          (String) responseMap.get("monitoring_endpoint"),
          (String) responseMap.get("owner_id"),
          (Long) responseMap.get("created_at"),
          (Long) responseMap.get("updated_at"),
          (String) responseMap.get("created_by_id"),
          (String) responseMap.get("updated_by_id")
      );
      
      ctx.json(responseDto);
    } catch (io.grpc.StatusRuntimeException e) {
      if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
        setError(ctx, 404, "Embedder not found");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) {
        setError(ctx, 403, "Permission denied");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
        setError(ctx, 401, "Authentication required");
      } else {
        Logger.error(e, "Error retrieving embedder: {}", e.getMessage());
        setError(ctx, 500, "Internal server error");
      }
    }
  }

  /**
   * Handles a REST request to list Embedders with optional filters. Builds the list request from
   * query parameters and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/embedders",
      methods = { HttpMethod.GET },
      summary = "List embedders",
      description = "Retrieves a list of embedders accessible to the caller, with optional filtering by owner, provider type, and labels.",
      operationId = "listEmbedders",
      tags = "Embedders",
      queryParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "owner_id",
              description = "Filter embedders by owner ID. If not provided, shows embedders based on permissions.",
              required = false,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000"),
          @io.javalin.openapi.OpenApiParam(
              name = "provider_type", 
              description = "Filter embedders by provider type (OPENAI, VLLM, TEI)",
              required = false,
              type = String.class,
              example = "OPENAI"),
          @io.javalin.openapi.OpenApiParam(
              name = "label.*",
              description = "Filter by label value. Multiple label filters can be specified (e.g., ?label.environment=prod&label.team=nlp)",
              required = false,
              type = String.class,
              example = "?label.environment=prod&label.team=nlp")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved embedders",
              content = @OpenApiContent(from = com.goodmem.rest.dto.ListEmbeddersResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - invalid filter parameters"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to list embedders")
      })
  private void handleListEmbedders(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListEmbedders request with API key: {}", apiKey);

    // Extract query parameters and create request DTO
    Map<String, String> labelSelectors = new HashMap<>();
    ctx.queryParamMap().forEach((key, values) -> {
      if (key.startsWith("label.") && !values.isEmpty()) {
        String labelKey = key.substring("label.".length());
        String value = values.getFirst();
        if (value != null) {
          labelSelectors.put(labelKey, value);
        }
      }
    });
    
    // Create the DTO from query parameters
    com.goodmem.rest.dto.ProviderType providerType = null;
    if (ctx.queryParam("provider_type") != null) {
      providerType = com.goodmem.rest.dto.ProviderType.fromString(ctx.queryParam("provider_type"));
    }
    
    com.goodmem.rest.dto.ListEmbeddersRequest requestDto = new com.goodmem.rest.dto.ListEmbeddersRequest(
        ctx.queryParam("owner_id"),
        providerType,
        labelSelectors.isEmpty() ? null : labelSelectors
    );
    
    // Build the gRPC request 
    ListEmbeddersRequest.Builder requestBuilder = ListEmbeddersRequest.newBuilder();
    
    // Add owner_id if provided
    if (requestDto.ownerId() != null) {
      StatusOr<ByteString> ownerIdOr = convertHexToUuidBytes(requestDto.ownerId());
      if (ownerIdOr.isOk()) {
        requestBuilder.setOwnerId(ownerIdOr.getValue());
      } else {
        Logger.warn("Invalid owner ID format: {}", requestDto.ownerId());
        setError(ctx, 400, "Invalid owner ID format");
        return;
      }
    }
    
    // Add provider_type if provided
    if (requestDto.providerType() != null) {
      requestBuilder.setProviderType(requestDto.providerType().toProtoProviderType());
    }
    
    // Add label selectors if provided
    if (requestDto.labelSelectors() != null && !requestDto.labelSelectors().isEmpty()) {
      requestDto.labelSelectors().forEach(requestBuilder::putLabelSelectors);
    }
    
    try {
      // Call the gRPC service
      ListEmbeddersResponse response = embedderService.listEmbedders(requestBuilder.build());
      
      // Convert the embedders to DTOs
      List<com.goodmem.rest.dto.EmbedderResponse> embedderDtos = response.getEmbeddersList().stream()
          .map(embedder -> {
            Map<String, Object> embedderMap = RestMapper.toJsonMap(embedder);
            
            List<com.goodmem.rest.dto.Modality> modalityList = null;
            if (embedderMap.containsKey("supported_modalities") && embedderMap.get("supported_modalities") instanceof List) {
              @SuppressWarnings("unchecked")
              List<String> modalityStrings = (List<String>) embedderMap.get("supported_modalities");
              modalityList = modalityStrings.stream()
                  .map(com.goodmem.rest.dto.Modality::fromString)
                  .filter(modality -> modality != null)
                  .toList();
            }
            
            return new com.goodmem.rest.dto.EmbedderResponse(
                (String) embedderMap.get("embedder_id"),
                (String) embedderMap.get("display_name"),
                (String) embedderMap.get("description"),
                com.goodmem.rest.dto.ProviderType.fromString((String) embedderMap.get("provider_type")),
                (String) embedderMap.get("endpoint_url"),
                (String) embedderMap.get("api_path"),
                (String) embedderMap.get("model_identifier"),
                embedderMap.get("dimensionality") instanceof Number ? ((Number) embedderMap.get("dimensionality")).intValue() : null,
                embedderMap.get("max_sequence_length") instanceof Number ? ((Number) embedderMap.get("max_sequence_length")).intValue() : null,
                modalityList,
                (Map<String, String>) embedderMap.get("labels"),
                (String) embedderMap.get("version"),
                (String) embedderMap.get("monitoring_endpoint"),
                (String) embedderMap.get("owner_id"),
                (Long) embedderMap.get("created_at"),
                (Long) embedderMap.get("updated_at"),
                (String) embedderMap.get("created_by_id"),
                (String) embedderMap.get("updated_by_id")
            );
          })
          .toList();
      
      // Create and return the response DTO
      com.goodmem.rest.dto.ListEmbeddersResponse responseDto = new com.goodmem.rest.dto.ListEmbeddersResponse(embedderDtos);
      ctx.json(responseDto);
    } catch (io.grpc.StatusRuntimeException e) {
      if (e.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) {
        setError(ctx, 403, "Permission denied");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
        setError(ctx, 401, "Authentication required");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.INVALID_ARGUMENT) {
        setError(ctx, 400, e.getStatus().getDescription());
      } else {
        Logger.error(e, "Error listing embedders: {}", e.getMessage());
        setError(ctx, 500, "Internal server error");
      }
    }
  }

  /**
   * Handles a REST request to update an Embedder by ID. Converts the hex UUID to binary format,
   * builds the update request from the DTO, and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/embedders/{id}",
      methods = { HttpMethod.PUT },
      summary = "Update an embedder",
      description = "Updates an existing embedder with new values for the specified fields. Fields not included in the request remain unchanged.",
      operationId = "updateEmbedder",
      tags = "Embedders",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the embedder to update",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      requestBody =
          @OpenApiRequestBody(
              description = "Embedder update details",
              required = true,
              content =
                  @OpenApiContent(
                      from = com.goodmem.rest.dto.UpdateEmbedderRequest.class,
                      example =
                          """
              {
                "displayName": "Updated OpenAI Embedding Model",
                "description": "Updated description for OpenAI embedding model",
                "endpointUrl": "https://api.openai.com",
                "credentials": "sk-updatedcredentials",
                "replaceLabels": {
                  "environment": "staging",
                  "team": "nlp-research"
                }
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully updated embedder",
              content = @OpenApiContent(from = com.goodmem.rest.dto.EmbedderResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - ID format or update parameters invalid"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to update this embedder"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - embedder with the specified ID does not exist")
      })
  private void handleUpdateEmbedder(Context ctx) {
    String embedderIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateEmbedder request for ID: {} with API key: {}", embedderIdHex, apiKey);

    // Parse the request body as our DTO
    com.goodmem.rest.dto.UpdateEmbedderRequest requestDto = ctx.bodyAsClass(com.goodmem.rest.dto.UpdateEmbedderRequest.class);
    
    // Validate label strategy (only one of replaceLabels or mergeLabels can be set)
    try {
      requestDto.validateLabelStrategy();
    } catch (IllegalArgumentException e) {
      setError(ctx, 400, e.getMessage());
      return;
    }
    
    // Validate and convert the embedder ID
    StatusOr<ByteString> embedderIdOr = convertHexToUuidBytes(embedderIdHex);
    if (embedderIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid embedder ID format");
      return;
    }
    
    // Build the gRPC request
    UpdateEmbedderRequest.Builder requestBuilder = UpdateEmbedderRequest.newBuilder()
        .setEmbedderId(embedderIdOr.getValue());
        
    // Set optional fields if provided in the DTO
    if (requestDto.displayName() != null) {
      requestBuilder.setDisplayName(requestDto.displayName());
    }
    
    if (requestDto.description() != null) {
      requestBuilder.setDescription(requestDto.description());
    }
    
    if (requestDto.endpointUrl() != null) {
      requestBuilder.setEndpointUrl(requestDto.endpointUrl());
    }
    
    if (requestDto.apiPath() != null) {
      requestBuilder.setApiPath(requestDto.apiPath());
    }
    
    if (requestDto.modelIdentifier() != null) {
      requestBuilder.setModelIdentifier(requestDto.modelIdentifier());
    }
    
    if (requestDto.dimensionality() != null) {
      requestBuilder.setDimensionality(requestDto.dimensionality());
    }
    
    if (requestDto.maxSequenceLength() != null) {
      requestBuilder.setMaxSequenceLength(requestDto.maxSequenceLength());
    }
    
    if (requestDto.supportedModalities() != null && !requestDto.supportedModalities().isEmpty()) {
      for (com.goodmem.rest.dto.Modality modality : requestDto.supportedModalities()) {
        requestBuilder.addSupportedModalities(modality.toProtoModality());
      }
    }
    
    if (requestDto.credentials() != null) {
      requestBuilder.setCredentials(requestDto.credentials());
    }
    
    if (requestDto.version() != null) {
      requestBuilder.setVersion(requestDto.version());
    }
    
    if (requestDto.monitoringEndpoint() != null) {
      requestBuilder.setMonitoringEndpoint(requestDto.monitoringEndpoint());
    }
    
    // Handle label update strategy
    if (requestDto.replaceLabels() != null) {
      StringMap.Builder labelsBuilder = StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.replaceLabels());
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    } else if (requestDto.mergeLabels() != null) {
      StringMap.Builder labelsBuilder = StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.mergeLabels());
      requestBuilder.setMergeLabels(labelsBuilder.build());
    }
    
    try {
      // Call the gRPC service
      Embedder response = embedderService.updateEmbedder(requestBuilder.build());
      
      // Convert the response to our DTO
      Map<String, Object> responseMap = RestMapper.toJsonMap(response);
      
      List<com.goodmem.rest.dto.Modality> modalityList = null;
      if (responseMap.containsKey("supported_modalities") && responseMap.get("supported_modalities") instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> modalityStrings = (List<String>) responseMap.get("supported_modalities");
        modalityList = modalityStrings.stream()
            .map(com.goodmem.rest.dto.Modality::fromString)
            .filter(modality -> modality != null)
            .toList();
      }
      
      com.goodmem.rest.dto.EmbedderResponse responseDto = new com.goodmem.rest.dto.EmbedderResponse(
          (String) responseMap.get("embedder_id"),
          (String) responseMap.get("display_name"),
          (String) responseMap.get("description"),
          com.goodmem.rest.dto.ProviderType.fromString((String) responseMap.get("provider_type")),
          (String) responseMap.get("endpoint_url"),
          (String) responseMap.get("api_path"),
          (String) responseMap.get("model_identifier"),
          responseMap.get("dimensionality") instanceof Number ? ((Number) responseMap.get("dimensionality")).intValue() : null,
          responseMap.get("max_sequence_length") instanceof Number ? ((Number) responseMap.get("max_sequence_length")).intValue() : null,
          modalityList,
          (Map<String, String>) responseMap.get("labels"),
          (String) responseMap.get("version"),
          (String) responseMap.get("monitoring_endpoint"),
          (String) responseMap.get("owner_id"),
          (Long) responseMap.get("created_at"),
          (Long) responseMap.get("updated_at"),
          (String) responseMap.get("created_by_id"),
          (String) responseMap.get("updated_by_id")
      );
      
      ctx.json(responseDto);
    } catch (io.grpc.StatusRuntimeException e) {
      if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
        setError(ctx, 404, "Embedder not found");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) {
        setError(ctx, 403, "Permission denied");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
        setError(ctx, 401, "Authentication required");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.INVALID_ARGUMENT) {
        setError(ctx, 400, e.getStatus().getDescription());
      } else {
        Logger.error(e, "Error updating embedder: {}", e.getMessage());
        setError(ctx, 500, "Internal server error");
      }
    }
  }

  /**
   * Handles a REST request to delete an Embedder by ID. Converts the hex UUID to binary format and
   * calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/embedders/{id}",
      methods = { HttpMethod.DELETE },
      summary = "Delete an embedder",
      description = "Deletes an embedder configuration. This operation cannot be undone.",
      operationId = "deleteEmbedder",
      tags = "Embedders",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the embedder to delete",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "204",
              description = "Embedder successfully deleted"),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - embedder ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to delete this embedder"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - embedder with the specified ID does not exist")
      })
  private void handleDeleteEmbedder(Context ctx) {
    String embedderIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteEmbedder request for ID: {} with API key: {}", embedderIdHex, apiKey);

    // Create the DTO from the path parameter
    com.goodmem.rest.dto.DeleteEmbedderRequest requestDto = new com.goodmem.rest.dto.DeleteEmbedderRequest(embedderIdHex);
    
    // Validate and convert the embedder ID
    StatusOr<ByteString> embedderIdOr = convertHexToUuidBytes(requestDto.embedderId());
    if (embedderIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid embedder ID format");
      return;
    }
    
    try {
      // Create and execute the gRPC request
      DeleteEmbedderRequest request = DeleteEmbedderRequest.newBuilder()
          .setEmbedderId(embedderIdOr.getValue())
          .build();
      
      // Call the gRPC service
      embedderService.deleteEmbedder(request);
      
      // Return 204 No Content on successful deletion
      ctx.status(204);
    } catch (io.grpc.StatusRuntimeException e) {
      if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
        setError(ctx, 404, "Embedder not found");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) {
        setError(ctx, 403, "Permission denied");
      } else if (e.getStatus().getCode() == io.grpc.Status.Code.UNAUTHENTICATED) {
        setError(ctx, 401, "Authentication required");
      } else {
        Logger.error(e, "Error deleting embedder: {}", e.getMessage());
        setError(ctx, 500, "Internal server error");
      }
    }
  }

  // Utility methods for converting protocol buffer messages to Maps for JSON serialization
  private Map<String, Object> protoToMap(SpaceOuterClass.Space space) {
    Map<String, Object> map = new HashMap<>();
    map.put("space_id", Uuids.bytesToHex(space.getSpaceId().toByteArray()));
    map.put("name", space.getName());
    map.put("labels", space.getLabelsMap());
    map.put("embedder_id", Uuids.bytesToHex(space.getEmbedderId().toByteArray()));
    map.put("created_at", Timestamps.toMillis(space.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(space.getUpdatedAt()));
    map.put("owner_id", Uuids.bytesToHex(space.getOwnerId().toByteArray()));
    map.put("created_by_id", Uuids.bytesToHex(space.getCreatedById().toByteArray()));
    map.put("updated_by_id", Uuids.bytesToHex(space.getUpdatedById().toByteArray()));
    map.put("public_read", space.getPublicRead());
    return map;
  }

  private Map<String, Object> protoToMap(UserOuterClass.User user) {
    Map<String, Object> map = new HashMap<>();
    map.put("user_id", Uuids.bytesToHex(user.getUserId().toByteArray()));
    map.put("email", user.getEmail());
    map.put("display_name", user.getDisplayName());
    map.put("username", user.getUsername());
    map.put("created_at", Timestamps.toMillis(user.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(user.getUpdatedAt()));
    return map;
  }

  private Map<String, Object> protoToMap(Memory memory) {
    Map<String, Object> map = new HashMap<>();
    map.put("memory_id", Uuids.bytesToHex(memory.getMemoryId().toByteArray()));
    map.put("space_id", Uuids.bytesToHex(memory.getSpaceId().toByteArray()));
    map.put("original_content_ref", memory.getOriginalContentRef());
    map.put("content_type", memory.getContentType());
    map.put("metadata", memory.getMetadataMap());
    map.put("processing_status", memory.getProcessingStatus());
    map.put("created_at", Timestamps.toMillis(memory.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(memory.getUpdatedAt()));
    map.put("created_by_id", Uuids.bytesToHex(memory.getCreatedById().toByteArray()));
    map.put("updated_by_id", Uuids.bytesToHex(memory.getUpdatedById().toByteArray()));
    return map;
  }

  private Map<String, Object> protoToMap(ApiKey apiKey) {
    Map<String, Object> map = new HashMap<>();
    map.put("api_key_id", Uuids.bytesToHex(apiKey.getApiKeyId().toByteArray()));
    map.put("user_id", Uuids.bytesToHex(apiKey.getUserId().toByteArray()));
    map.put("key_prefix", apiKey.getKeyPrefix());
    map.put("status", apiKey.getStatus().name());
    map.put("labels", apiKey.getLabelsMap());

    if (apiKey.hasExpiresAt()) {
      map.put("expires_at", Timestamps.toMillis(apiKey.getExpiresAt()));
    }

    if (apiKey.hasLastUsedAt()) {
      map.put("last_used_at", Timestamps.toMillis(apiKey.getLastUsedAt()));
    }

    map.put("created_at", Timestamps.toMillis(apiKey.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(apiKey.getUpdatedAt()));
    map.put("created_by_id", Uuids.bytesToHex(apiKey.getCreatedById().toByteArray()));
    map.put("updated_by_id", Uuids.bytesToHex(apiKey.getUpdatedById().toByteArray()));
    return map;
  }

  private Map<String, Object> protoToMap(Embedder embedder) {
    Map<String, Object> map = new HashMap<>();
    map.put("embedder_id", Uuids.bytesToHex(embedder.getEmbedderId().toByteArray()));
    map.put("display_name", embedder.getDisplayName());
    map.put("description", embedder.getDescription());
    map.put("provider_type", embedder.getProviderType().name());
    map.put("endpoint_url", embedder.getEndpointUrl());
    map.put("api_path", embedder.getApiPath());
    map.put("model_identifier", embedder.getModelIdentifier());
    map.put("dimensionality", embedder.getDimensionality());

    if (embedder.hasMaxSequenceLength()) {
      map.put("max_sequence_length", embedder.getMaxSequenceLength());
    }

    map.put(
        "supported_modalities",
        embedder.getSupportedModalitiesList().stream().map(Enum::name).toList());
    map.put("labels", embedder.getLabelsMap());
    map.put("version", embedder.getVersion());
    map.put("monitoring_endpoint", embedder.getMonitoringEndpoint());
    map.put("owner_id", Uuids.bytesToHex(embedder.getOwnerId().toByteArray()));
    map.put("created_at", Timestamps.toMillis(embedder.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(embedder.getUpdatedAt()));
    map.put("created_by_id", Uuids.bytesToHex(embedder.getCreatedById().toByteArray()));
    map.put("updated_by_id", Uuids.bytesToHex(embedder.getUpdatedById().toByteArray()));
    return map;
  }

  // Utility methods for converting between UUID formats
  private static final ByteString ZEROS_BYTESTRING = ByteString.copyFrom(new byte[16]);

  private StatusOr<ByteString> convertHexToUuidBytes(String hexString) {
    if (Strings.isNullOrEmpty(hexString)) {
      return StatusOr.ofValue(ZEROS_BYTESTRING);
    }
    try {
      // Remove any hyphens or prefixes
      String cleanHex = hexString.replace("-", "").replace("0x", "");
      byte[] bytes = BaseEncoding.base16().decode(cleanHex);
      if (bytes.length != 16) {
        return StatusOr.ofStatus(Status.invalidArgument("Invalid string detected."));
      }
      return StatusOr.ofValue(ByteString.copyFrom(bytes));
    } catch (IllegalArgumentException e) {
      return StatusOr.ofStatus(Status.invalidArgument("Invalid string detected."));
    }
  }

  /**
   * Handles the system initialization endpoint. This special endpoint doesn't require
   * authentication and is used to set up the initial admin user.
   * 
   * <p>If the root user already exists, it returns a message indicating that the system
   * is already initialized. If the root user doesn't exist, it creates the root user
   * and generates an API key for administrative access.
   * 
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/system/init",
      methods = { HttpMethod.POST },
      summary = "Initialize the system",
      description = 
          "Initializes the system by creating a root user and API key. This endpoint should only be called once during " +
          "first-time setup. If the system is already initialized, the endpoint will return a success response without " +
          "creating new credentials.",
      operationId = "initializeSystem",
      tags = "System",
      requestBody =
          @OpenApiRequestBody(
              description = "Empty request body - no parameters required",
              required = false,
              content = @OpenApiContent(from = com.goodmem.rest.dto.SystemInitRequest.class)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "System initialization successful",
              content = @OpenApiContent(from = com.goodmem.rest.dto.SystemInitResponse.class)),
          @OpenApiResponse(
              status = "500", 
              description = "Internal server error during initialization")
      })
  private void handleSystemInit(Context ctx) {
    Logger.info("REST SystemInit request");

    // Parse the empty request body
    // This isn't strictly necessary since we don't need any parameters, but follows the pattern
    ctx.bodyAsClass(com.goodmem.rest.dto.SystemInitRequest.class);
    
    // Set up database connection from connection pool
    try (Connection connection = dataSource.getConnection()) {

      // Create and execute the operation
      var operation = new com.goodmem.operations.SystemInitOperation(connection);
      var result = operation.execute();

      if (!result.isSuccess()) {
        Logger.error("System initialization failed: {}", result.errorMessage());
        setError(ctx, 500, result.errorMessage());
        return;
      }

      // Create the appropriate response DTO based on the result
      com.goodmem.rest.dto.SystemInitResponse responseDto;
      
      if (result.alreadyInitialized()) {
        Logger.info("System is already initialized");
        responseDto = com.goodmem.rest.dto.SystemInitResponse.alreadyInitializedResponse();
      } else {
        // System was just initialized
        Logger.info("System initialized successfully");
        responseDto = com.goodmem.rest.dto.SystemInitResponse.newlyInitialized(
            result.apiKey(),
            result.userId().toString()
        );
      }
      
      // Return the response
      ctx.status(200).json(responseDto);

    } catch (Exception e) {
      Logger.error(e, "Error during system initialization.");
      setError(ctx, 500, "Internal server error: " + e.getMessage());
    }
  }

  /**
   * Helper method to set an error response with appropriate HTTP status code.
   *
   * @param ctx The Javalin context to set the response on
   * @param statusCode The HTTP status code to set
   * @param message The error message to include in the response
   */
  private void setError(Context ctx, int statusCode, String message) {
    ctx.status(statusCode).json(Map.of("error", message));
    Logger.error("Error response: {} - {}", statusCode, message);
  }

  public static void main(String[] args) throws IOException {
    Main server = new Main();
    server.startGrpcServer();
    server.startJavalinServer();
  }
}
