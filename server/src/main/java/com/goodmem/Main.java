package com.goodmem;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.config.MinioConfig;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.ConditionalAuthInterceptor;
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
import goodmem.v1.MemoryOuterClass.CreateMemoryRequest;
import goodmem.v1.MemoryOuterClass.DeleteMemoryRequest;
import goodmem.v1.MemoryOuterClass.GetMemoryRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesResponse;
import goodmem.v1.MemoryOuterClass.Memory;
import goodmem.v1.MemoryServiceGrpc;
import goodmem.v1.SpaceOuterClass.CreateSpaceRequest;
import goodmem.v1.SpaceOuterClass.DeleteSpaceRequest;
import goodmem.v1.SpaceOuterClass.GetSpaceRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesResponse;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.SpaceOuterClass.StringMap;
import goodmem.v1.SpaceOuterClass.UpdateSpaceRequest;
import goodmem.v1.SpaceServiceGrpc;
import goodmem.v1.UserOuterClass.GetUserRequest;
import goodmem.v1.UserOuterClass.User;
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
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import org.tinylog.Logger;

public class Main {

  private static final int GRPC_PORT = 9090;
  private static final int REST_PORT = 8080;

  private Server grpcServer;
  private final SpaceServiceImpl spaceServiceImpl;
  private final UserServiceImpl userServiceImpl;
  private final MemoryServiceImpl memoryServiceImpl;
  private final ApiKeyServiceImpl apiKeyServiceImpl;
  private final HikariDataSource dataSource;

  private final MinioConfig minioConfig;
  private final MinioClient minioClient;

  // gRPC blocking stubs for the REST-to-gRPC bridge
  private final SpaceServiceGrpc.SpaceServiceBlockingStub spaceService;
  private final UserServiceGrpc.UserServiceBlockingStub userService;
  private final MemoryServiceGrpc.MemoryServiceBlockingStub memoryService;
  private final ApiKeyServiceGrpc.ApiKeyServiceBlockingStub apiKeyService;

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
    this.spaceServiceImpl = new SpaceServiceImpl(
        new SpaceServiceImpl.Config(dataSource, "openai-ada-002"));
    this.userServiceImpl = new UserServiceImpl(userServiceConfig);
    this.memoryServiceImpl = new MemoryServiceImpl(
        new MemoryServiceImpl.Config(dataSource, minioConfig));
    this.apiKeyServiceImpl = new ApiKeyServiceImpl(
        new ApiKeyServiceImpl.Config(dataSource));

    // Create an in-process channel for REST-to-gRPC communication
    ManagedChannel channel = InProcessChannelBuilder.forName("in-process").build();

    // Create blocking stubs for each service
    this.spaceService = SpaceServiceGrpc.newBlockingStub(channel);
    this.userService = UserServiceGrpc.newBlockingStub(channel);
    this.memoryService = MemoryServiceGrpc.newBlockingStub(channel);
    this.apiKeyService = ApiKeyServiceGrpc.newBlockingStub(channel);
  }

  private record InitializedMinio(
      MinioConfig config,
      MinioClient client) {}

  private InitializedMinio setupMinioSource() {
    // Get MinIO configuration
    var minioConfig = new MinioConfig(
        System.getenv("MINIO_ENDPOINT"),
        System.getenv("MINIO_ACCESS_KEY"),
        System.getenv("MINIO_SECRET_KEY"),
        System.getenv("MINIO_BUCKET")
    );
    Logger.info("Configured MinIO: {}", minioConfig.toSecureString());

    var minioClient =
        MinioClient.builder()
            .endpoint(minioConfig.minioEndpoint())
            .credentials(minioConfig.minioAccessKey(), minioConfig.minioSecretKey())
            .build();
    try {
      boolean exists = minioClient.bucketExists(
          BucketExistsArgs.builder().bucket(minioConfig.minioBucket()).build());
      if (exists) {
        Logger.info("Found MinIO memory storage bucket {}.", minioConfig.minioBucket());
      } else {
        Logger.info("MinIO memory storage bucket {} does NOT exist.", minioConfig.minioBucket());
        minioClient.makeBucket(
            MakeBucketArgs.builder().bucket(minioConfig.minioBucket()).build());
        Logger.info("Storage bucket {} was created.", minioConfig.minioBucket());
      }
    } catch (ErrorResponseException | InsufficientDataException | InternalException |
             InvalidKeyException | InvalidResponseException | IOException |
             NoSuchAlgorithmException | ServerException | XmlParserException e) {
      Logger.error(
          e, "Unexpected failure checking MinIO memory storage bucket {}.", minioConfig.minioBucket());
    }

    return new InitializedMinio(minioConfig, minioClient);
  }

  /**
   * Sets up and configures the HikariCP connection pool with database properties
   * from system properties.
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
    var initMethodAuthorizer = new MethodAuthorizer()
            .allowMethod("goodmem.v1.UserService/InitializeSystem");

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
    var credentials = TlsServerCredentials.newBuilder()
        .clientAuth(ClientAuth.NONE) // Don't require client certificates
        .keyManager(serverCrtBs.openBufferedStream(), serverKeyBs.openBufferedStream())
        .build();

    // Log certificate information
    Logger.info(
        "TLS enabled for gRPC server with: Certificate {}, Private key {}",
        serverCrt, serverKey);

    // Create a shared AuthInterceptor instance
    var authInterceptor = new AuthInterceptor(dataSource);

    grpcServer =
        Grpc.newServerBuilderForPort(GRPC_PORT, credentials)
            .addService(ServerInterceptors.intercept(spaceServiceImpl, authInterceptor))
            // For user service, we need to allow InitializeSystem to be called without auth
            .addService(ServerInterceptors.intercept(userServiceImpl, new ConditionalAuthInterceptor(initMethodAuthorizer, authInterceptor)))
            .addService(ServerInterceptors.intercept(memoryServiceImpl, authInterceptor))
            .addService(ServerInterceptors.intercept(apiKeyServiceImpl, authInterceptor))
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

  public void startJavalinServer() {
    Javalin app =
        Javalin.create(
                config -> config.bundledPlugins.enableCors(
                    cors -> cors.addRule(CorsRule::anyHost)))
            .start(REST_PORT);

    // Configure REST routes that map to gRPC methods

    // Space endpoints
    app.post("/v1/spaces", this::handleCreateSpace);
    app.get("/v1/spaces/{id}", this::handleGetSpace);
    app.get("/v1/spaces", this::handleListSpaces);
    app.put("/v1/spaces/{id}", this::handleUpdateSpace);
    app.delete("/v1/spaces/{id}", this::handleDeleteSpace);

    // User endpoints
    app.get("/v1/users/{id}", this::handleGetUser);
    app.post("/v1/system/init", this::handleSystemInit);

    // Memory endpoints
    app.post("/v1/memories", this::handleCreateMemory);
    app.get("/v1/memories/{id}", this::handleGetMemory);
    app.get("/v1/spaces/{spaceId}/memories", this::handleListMemories);
    app.delete("/v1/memories/{id}", this::handleDeleteMemory);

    // API Key endpoints
    app.post("/v1/apikeys", this::handleCreateApiKey);
    app.get("/v1/apikeys", this::handleListApiKeys);
    app.put("/v1/apikeys/{id}", this::handleUpdateApiKey);
    app.delete("/v1/apikeys/{id}", this::handleDeleteApiKey);

    Logger.info("REST server started, listening on port {}.", REST_PORT);
  }

  // Space handlers
  private void handleCreateSpace(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateSpace request with API key: {}.", apiKey);

    CreateSpaceRequest.Builder requestBuilder = CreateSpaceRequest.newBuilder();
    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    if (json.containsKey("name")) {
      requestBuilder.setName((String) json.get("name"));
    }

    if (json.containsKey("embedding_model")) {
      requestBuilder.setEmbeddingModel((String) json.get("embedding_model"));
    }

    if (json.containsKey("public_read")) {
      requestBuilder.setPublicRead((Boolean) json.get("public_read"));
    }

    // CreateSpaceRequest uses direct map rather than StringMap wrapper
    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }

    // Support for replace_labels/merge_labels fields from clients using the new pattern
    // Both get treated the same for CreateSpaceRequest since it only has one labels field
    if (json.containsKey("replace_labels") && json.get("replace_labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> replaceLabels = (Map<String, String>) json.get("replace_labels");
      requestBuilder.putAllLabels(replaceLabels);
    }

    if (json.containsKey("merge_labels") && json.get("merge_labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> mergeLabels = (Map<String, String>) json.get("merge_labels");
      requestBuilder.putAllLabels(mergeLabels);
    }

    Space response = spaceService.createSpace(requestBuilder.build());
    ctx.json(protoToMap(response));
  }

  /**
   * Handles a REST request to retrieve a Space by ID.
   * Converts the hex UUID to binary format and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleGetSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST GetSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid space ID format");
      return;
    }
    GetSpaceRequest request = GetSpaceRequest
        .newBuilder()
        .setSpaceId(spaceIdOr.getValue())
        .build();

    Space response = spaceService.getSpace(request);
    ctx.json(protoToMap(response));
  }

  private void handleListSpaces(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListSpaces request with API key: {}", apiKey);

    ListSpacesRequest.Builder requestBuilder = ListSpacesRequest.newBuilder();

    // Handle label selectors from query parameters
    ctx.queryParamMap()
        .forEach(
            (key, values) -> {
              if (key.startsWith("label.") && !values.isEmpty()) {
                String labelKey = key.substring("label.".length());
                requestBuilder.putLabelSelectors(labelKey, values.getFirst());
              }
            });

    // Handle owner_id filter if provided
    if (ctx.queryParam("owner_id") != null) {
      StatusOr<ByteString> ownerIdOr =
          convertHexToUuidBytes(ctx.queryParam("owner_id"));
      if (ownerIdOr.isOk()) {
        requestBuilder.setOwnerId(ownerIdOr.getValue());
      }
    }

    ListSpacesResponse response = spaceService.listSpaces(requestBuilder.build());
    ctx.json(Map.of("spaces", response.getSpacesList().stream().map(this::protoToMap).toList()));
  }

  /**
   * Handles a REST request to update a Space by ID.
   * Converts the hex UUID to binary format, builds the update request from JSON,
   * and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleUpdateSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateSpace request for ID: {} with API key: {}",
        spaceIdHex, apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid space ID format");
      return;
    }
    UpdateSpaceRequest.Builder requestBuilder =
        UpdateSpaceRequest.newBuilder().setSpaceId(spaceIdOr.getValue());

    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    if (json.containsKey("name")) {
      requestBuilder.setName((String) json.get("name"));
    }

    if (json.containsKey("public_read")) {
      requestBuilder.setPublicRead((Boolean) json.get("public_read"));
    }

    if (json.containsKey("replace_labels") && json.get("replace_labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> replaceLabels = (Map<String, String>) json.get("replace_labels");
      StringMap.Builder labelsBuilder = StringMap.newBuilder();
      labelsBuilder.putAllLabels(replaceLabels);
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    }

    if (json.containsKey("merge_labels") && json.get("merge_labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> mergeLabels = (Map<String, String>) json.get("merge_labels");
      StringMap.Builder labelsBuilder = StringMap.newBuilder();
      labelsBuilder.putAllLabels(mergeLabels);
      requestBuilder.setMergeLabels(labelsBuilder.build());
    }

    // Legacy support for "labels" field - treat as replace_labels
    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      StringMap.Builder labelsBuilder = StringMap.newBuilder();
      labelsBuilder.putAllLabels(labels);
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    }

    Space response = spaceService.updateSpace(requestBuilder.build());
    ctx.json(protoToMap(response));
  }

  /**
   * Handles a REST request to delete a Space by ID.
   * Converts the hex UUID to binary format and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleDeleteSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST DeleteSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid space ID format");
      return;
    }
    spaceService.deleteSpace(
        DeleteSpaceRequest.newBuilder().setSpaceId(spaceIdOr.getValue()).build()
    );
    ctx.status(204);
  }

  // User handlers
  private void handleGetUser(Context ctx) {
    String userIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST GetUser request for ID: {} with API key: {}", userIdHex, apiKey);

    StatusOr<ByteString> userIdOr = convertHexToUuidBytes(userIdHex);
    if (userIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid user ID format");
      return;
    }

    User response = userService.getUser(
        GetUserRequest.newBuilder().setUserId(userIdOr.getValue()).build());
    ctx.json(protoToMap(response));
  }

  // Memory handlers

  /**
   * Handles a REST request to create a new Memory.
   * Builds the create request from JSON and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleCreateMemory(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateMemory request with API key: {}", apiKey);

    CreateMemoryRequest.Builder requestBuilder = CreateMemoryRequest.newBuilder();
    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    if (json.containsKey("space_id")) {
      StatusOr<ByteString> spaceIdOr =
          convertHexToUuidBytes((String) json.get("space_id"));
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
    ctx.json(protoToMap(response));
  }

  /**
   * Handles a REST request to retrieve a Memory by ID.
   * Converts the hex UUID to binary format and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleGetMemory(Context ctx) {
    String memoryIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetMemory request for ID: {} with API key: {}",
        memoryIdHex, apiKey);

    StatusOr<ByteString> memoryIdOr = convertHexToUuidBytes(memoryIdHex);
    if (memoryIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid memory ID format");
      return;
    }

    Memory response = memoryService.getMemory(
        GetMemoryRequest.newBuilder().setMemoryId(memoryIdOr.getValue()).build()
    );
    ctx.json(protoToMap(response));
  }

  /**
   * Handles a REST request to list Memories within a Space.
   * Converts the space hex UUID to binary format and calls the gRPC service.
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

    ListMemoriesResponse response = memoryService.listMemories(
        ListMemoriesRequest.newBuilder().setSpaceId(spaceIdOr.getValue()).build());
    ctx.json(
        Map.of("memories", response.getMemoriesList().stream().map(this::protoToMap).toList()));
  }

  /**
   * Handles a REST request to delete a Memory by ID.
   * Converts the hex UUID to binary format and calls the gRPC service.
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
        DeleteMemoryRequest
            .newBuilder()
            .setMemoryId(memoryIdOr.getValue())
            .build());
    ctx.status(204);
  }

  // API Key handlers
  /**
   * Handles a REST request to create a new API Key.
   * Builds the create request from JSON and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleCreateApiKey(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateApiKey request with API key: {}", apiKey);

    CreateApiKeyRequest.Builder requestBuilder = CreateApiKeyRequest.newBuilder();
    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    // ApiKey uses direct map rather than StringMap wrapper
    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }

    // Note: handling expires_at would require timestamp parsing which is omitted for brevity

    CreateApiKeyResponse response = apiKeyService.createApiKey(requestBuilder.build());
    Map<String, Object> responseMap = new HashMap<>();
    responseMap.put("api_key_metadata", protoToMap(response.getApiKeyMetadata()));
    responseMap.put("raw_api_key", response.getRawApiKey());
    ctx.json(responseMap);
  }

  /**
   * Handles a REST request to list API Keys for the current user.
   * Calls the gRPC service to get the list of keys.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleListApiKeys(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListApiKeys request with API key: {}", apiKey);

    ListApiKeysRequest request = ListApiKeysRequest.newBuilder().build();

    ListApiKeysResponse response = apiKeyService.listApiKeys(request);
    ctx.json(Map.of("keys", response.getKeysList().stream().map(this::protoToMap).toList()));
  }

  /**
   * Handles a REST request to update an API Key by ID.
   * Converts the hex UUID to binary format, builds the update request from JSON,
   * and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleUpdateApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST UpdateApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    StatusOr<ByteString> apiKeyIdOr = convertHexToUuidBytes(apiKeyIdHex);
    if (apiKeyIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid API key ID format");
      return;
    }
    UpdateApiKeyRequest.Builder requestBuilder =
        UpdateApiKeyRequest.newBuilder().setApiKeyId(apiKeyIdOr.getValue());

    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    // ApiKey uses direct map rather than StringMap wrapper
    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }

    if (json.containsKey("status")) {
      String statusStr = (String) json.get("status");
      Apikey.Status status = switch (statusStr.toUpperCase()) {
        case "ACTIVE" -> Apikey.Status.ACTIVE;
        case "INACTIVE" -> Apikey.Status.INACTIVE;
        default -> Apikey.Status.STATUS_UNSPECIFIED;
      };
      requestBuilder.setStatus(status);
    }

    ApiKey response = apiKeyService.updateApiKey(requestBuilder.build());
    ctx.json(protoToMap(response));
  }

  /**
   * Handles a REST request to delete an API Key by ID.
   * Converts the hex UUID to binary format and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  private void handleDeleteApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    StatusOr<ByteString> apiKeyIdOr = convertHexToUuidBytes(apiKeyIdHex);
    if (apiKeyIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid API key ID format");
      return;
    }
    apiKeyService.deleteApiKey(
        DeleteApiKeyRequest.newBuilder().setApiKeyId(apiKeyIdOr.getValue()).build());
    ctx.status(204);
  }

  // Utility methods for converting protocol buffer messages to Maps for JSON serialization
  private Map<String, Object> protoToMap(Space space) {
    Map<String, Object> map = new HashMap<>();
    map.put("space_id", Uuids.bytesToHex(space.getSpaceId().toByteArray()));
    map.put("name", space.getName());
    map.put("labels", space.getLabelsMap());
    map.put("embedding_model", space.getEmbeddingModel());
    map.put("created_at", Timestamps.toMillis(space.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(space.getUpdatedAt()));
    map.put("owner_id", Uuids.bytesToHex(space.getOwnerId().toByteArray()));
    map.put("created_by_id", Uuids.bytesToHex(space.getCreatedById().toByteArray()));
    map.put("updated_by_id", Uuids.bytesToHex(space.getUpdatedById().toByteArray()));
    map.put("public_read", space.getPublicRead());
    return map;
  }

  private Map<String, Object> protoToMap(User user) {
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
   * authentication and is used to set up the initial admin user. If the root user already exists,
   * it returns a message indicating the system is already initialized. If the root user doesn't
   * exist, it creates the root user and an API key.
   */
  private void handleSystemInit(Context ctx) {
    Logger.info("REST SystemInit request");

    // Set up database connection from connection pool
    try (Connection connection = dataSource.getConnection()) {

      // Create and execute the operation
      var operation = new com.goodmem.operations.SystemInitOperation(connection);
      var result = operation.execute();

      if (!result.isSuccess()) {
        Logger.error("System initialization failed: {}", result.errorMessage());
        ctx.status(500).json(Map.of("error", result.errorMessage()));
        return;
      }

      if (result.alreadyInitialized()) {
        Logger.info("System is already initialized");
        ctx.status(200)
            .json(Map.of("initialized", true, "message", "System is already initialized"));
        return;
      }

      // Return the successful initialization with the API key
      Logger.info("System initialized successfully");
      ctx.status(200)
          .json(
              Map.of(
                  "initialized", true,
                  "message", "System initialized successfully",
                  "root_api_key", result.apiKey(),
                  "user_id", result.userId().toString()));

    } catch (Exception e) {
      Logger.error(e, "Error during system initialization.");
      ctx.status(500).json(Map.of("error", "Internal server error: " + e.getMessage()));
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
