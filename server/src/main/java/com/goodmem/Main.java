package com.goodmem;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.config.MinioConfig;
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
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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

  // gRPC blocking stubs for the REST-to-gRPC bridge
  private final SpaceServiceGrpc.SpaceServiceBlockingStub spaceService;
  private final UserServiceGrpc.UserServiceBlockingStub userService;
  private final MemoryServiceGrpc.MemoryServiceBlockingStub memoryService;
  private final ApiKeyServiceGrpc.ApiKeyServiceBlockingStub apiKeyService;

  private final MinioConfig minioConfig;

  public Main() {
    // Initialize database connection pool
    this.dataSource = setupDataSource();
    
    // Get MinIO configuration
    minioConfig = new MinioConfig(
        System.getProperty("MINIO_ENDPOINT"),
        System.getProperty("MINIO_ACCESS_KEY"),
        System.getProperty("MINIO_SECRET_KEY"),
        System.getProperty("MINIO_BUCKET")
    );
    Logger.info("Configured MinIO.", minioConfig);

    // Create service configs
    var userServiceConfig = new UserServiceImpl.Config(dataSource);
    
    // Create service implementations with connection pool
    this.spaceServiceImpl = new SpaceServiceImpl(new SpaceServiceImpl.Config(
        dataSource, "openai-ada-002")); // Default embedding model
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

  /**
   * TODO: fill this in
   * @return
   */
  private HikariDataSource setupDataSource() {
    // Get database configuration from environment properties
    String dbUrl = System.getProperty("DB_URL");
    String dbUser = System.getProperty("DB_USER");
    String dbPassword = System.getProperty("DB_PASSWORD");
    
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

    Logger.info("Initializing database connection pool with URL: {}", dbUrl);
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

    grpcServer =
        Grpc.newServerBuilderForPort(GRPC_PORT, credentials)
            .addService(ServerInterceptors.intercept(spaceServiceImpl, new AuthInterceptor()))
            // For user service, we need to allow InitializeSystem to be called without auth
            .addService(ServerInterceptors.intercept(userServiceImpl, new ConditionalAuthInterceptor(initMethodAuthorizer)))
            .addService(ServerInterceptors.intercept(memoryServiceImpl, new AuthInterceptor()))
            .addService(ServerInterceptors.intercept(apiKeyServiceImpl, new AuthInterceptor()))
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
                config -> {
                  config.bundledPlugins.enableCors(
                      cors -> {
                        cors.addRule(it -> it.anyHost());
                      });
                })
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

    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }

    Space response = spaceService.createSpace(requestBuilder.build());
    ctx.json(protoToMap(response));
  }

  /**
   * TODO: fill this in
   * @param ctx
   */
  private void handleGetSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST GetSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isOk()) {
      GetSpaceRequest request = GetSpaceRequest
          .newBuilder()
          .setSpaceId(spaceIdOr.getValue())
          .build();

      Space response = spaceService.getSpace(request);
      ctx.json(protoToMap(response));
    }
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
   * // TODO: fill this in
   * @param ctx
   */
  private void handleUpdateSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateSpace request for ID: {} with API key: {}",
        spaceIdHex, apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
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

    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }

    Space response = spaceService.updateSpace(requestBuilder.build());
    ctx.json(protoToMap(response));
  }

  /**
   * TODO: fill this in
   * @param ctx
   */
  private void handleDeleteSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST DeleteSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
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
      return;
    }

    User response = userService.getUser(
        GetUserRequest.newBuilder().setUserId(userIdOr.getValue()).build());
    ctx.json(protoToMap(response));
  }

  // Memory handlers

  /**
   * TODO: fill this in
   * @param ctx
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
        // TODO: create a helper method that sets an HTTP error code appropriately, and also an error message, and returns that. Look for any other mid-method returns in handleXXX methods and fix those too.
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
   * TODO: document this
   * @param ctx
   */
  private void handleGetMemory(Context ctx) {
    String memoryIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetMemory request for ID: {} with API key: {}",
        memoryIdHex, apiKey);

    StatusOr<ByteString> memoryIdOr = convertHexToUuidBytes(memoryIdHex);
    if (memoryIdOr.isNotOk()) {
      return;
    }

    Memory response = memoryService.getMemory(
        GetMemoryRequest.newBuilder().setMemoryId(memoryIdOr.getValue()).build()
    );
    ctx.json(protoToMap(response));
  }

  /**
   * TODO: document this
   * @param ctx
   */
  private void handleListMemories(Context ctx) {
    String spaceIdHex = ctx.pathParam("spaceId");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST ListMemories request for space ID: " + spaceIdHex + " with API key: " + apiKey);

    StatusOr<ByteString> spaceIdOr = convertHexToUuidBytes(spaceIdHex);
    if (spaceIdOr.isNotOk()) {
      return;
    }

    ListMemoriesResponse response = memoryService.listMemories(
        ListMemoriesRequest.newBuilder().setSpaceId(spaceIdOr.getValue()).build());
    ctx.json(
        Map.of("memories", response.getMemoriesList().stream().map(this::protoToMap).toList()));
  }

  /**
   * TODO: document this
   * @param ctx
   */
  private void handleDeleteMemory(Context ctx) {
    String memoryIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteMemory request for ID: {} with API key: {}", memoryIdHex, apiKey);

    StatusOr<ByteString> memoryIdOr = convertHexToUuidBytes(memoryIdHex);
    if (memoryIdOr.isNotOk()) {
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
   * TODO: document this
   * @param ctx
   */
  private void handleCreateApiKey(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateApiKey request with API key: {}", apiKey);

    CreateApiKeyRequest.Builder requestBuilder = CreateApiKeyRequest.newBuilder();
    Map<String, Object> json = ctx.bodyAsClass(Map.class);

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
   * TODO: document this
   * @param ctx
   */
  private void handleListApiKeys(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListApiKeys request with API key: {}", apiKey);

    ListApiKeysRequest request = ListApiKeysRequest.newBuilder().build();

    ListApiKeysResponse response = apiKeyService.listApiKeys(request);
    ctx.json(Map.of("keys", response.getKeysList().stream().map(this::protoToMap).toList()));
  }

  /**
   * TODO: document this
   * @param ctx
   */
  private void handleUpdateApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info(
        "REST UpdateApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    StatusOr<ByteString> apiKeyIdOr = convertHexToUuidBytes(apiKeyIdHex);
    if (apiKeyIdOr.isNotOk()) {
      return;
    }
    UpdateApiKeyRequest.Builder requestBuilder =
        UpdateApiKeyRequest.newBuilder().setApiKeyId(apiKeyIdOr.getValue());

    Map<String, Object> json = ctx.bodyAsClass(Map.class);

    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }

    if (json.containsKey("status")) {
      String statusStr = (String) json.get("status");
      Apikey.Status status;
      switch (statusStr.toUpperCase()) {
        case "ACTIVE":
          status = Apikey.Status.ACTIVE;
          break;
        case "INACTIVE":
          status = Apikey.Status.INACTIVE;
          break;
        default:
          status = Apikey.Status.STATUS_UNSPECIFIED;
          break;
      }
      requestBuilder.setStatus(status);
    }

    ApiKey response = apiKeyService.updateApiKey(requestBuilder.build());
    ctx.json(protoToMap(response));
  }

  /**
   * TODO: document this
   * @param ctx
   */
  private void handleDeleteApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    StatusOr<ByteString> apiKeyIdOr = convertHexToUuidBytes(apiKeyIdHex);
    if (apiKeyIdOr.isNotOk()) {
      return;
    }
    apiKeyService.deleteApiKey(
        DeleteApiKeyRequest.newBuilder().setApiKeyId(apiKeyIdOr.getValue()).build());
    ctx.status(204);
  }

  // Utility methods for converting protocol buffer messages to Maps for JSON serialization
  private Map<String, Object> protoToMap(Space space) {
    Map<String, Object> map = new HashMap<>();
    map.put("space_id", bytesToHex(space.getSpaceId().toByteArray()));
    map.put("name", space.getName());
    map.put("labels", space.getLabelsMap());
    map.put("embedding_model", space.getEmbeddingModel());
    map.put("created_at", Timestamps.toMillis(space.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(space.getUpdatedAt()));
    map.put("owner_id", bytesToHex(space.getOwnerId().toByteArray()));
    map.put("created_by_id", bytesToHex(space.getCreatedById().toByteArray()));
    map.put("updated_by_id", bytesToHex(space.getUpdatedById().toByteArray()));
    map.put("public_read", space.getPublicRead());
    return map;
  }

  private Map<String, Object> protoToMap(User user) {
    Map<String, Object> map = new HashMap<>();
    map.put("user_id", bytesToHex(user.getUserId().toByteArray()));
    map.put("email", user.getEmail());
    map.put("display_name", user.getDisplayName());
    map.put("username", user.getUsername());
    map.put("created_at", Timestamps.toMillis(user.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(user.getUpdatedAt()));
    return map;
  }

  private Map<String, Object> protoToMap(Memory memory) {
    Map<String, Object> map = new HashMap<>();
    map.put("memory_id", bytesToHex(memory.getMemoryId().toByteArray()));
    map.put("space_id", bytesToHex(memory.getSpaceId().toByteArray()));
    map.put("original_content_ref", memory.getOriginalContentRef());
    map.put("content_type", memory.getContentType());
    map.put("metadata", memory.getMetadataMap());
    map.put("processing_status", memory.getProcessingStatus());
    map.put("created_at", Timestamps.toMillis(memory.getCreatedAt()));
    map.put("updated_at", Timestamps.toMillis(memory.getUpdatedAt()));
    map.put("created_by_id", bytesToHex(memory.getCreatedById().toByteArray()));
    map.put("updated_by_id", bytesToHex(memory.getUpdatedById().toByteArray()));
    return map;
  }

  private Map<String, Object> protoToMap(ApiKey apiKey) {
    Map<String, Object> map = new HashMap<>();
    map.put("api_key_id", bytesToHex(apiKey.getApiKeyId().toByteArray()));
    map.put("user_id", bytesToHex(apiKey.getUserId().toByteArray()));
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
    map.put("created_by_id", bytesToHex(apiKey.getCreatedById().toByteArray()));
    map.put("updated_by_id", bytesToHex(apiKey.getUpdatedById().toByteArray()));
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

  private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

  private String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }

    // Format as standard UUID with dashes
    if (bytes.length == 16) {
      String hex = new String(hexChars);
      return hex.substring(0, 8)
          + "-"
          + hex.substring(8, 12)
          + "-"
          + hex.substring(12, 16)
          + "-"
          + hex.substring(16, 20)
          + "-"
          + hex.substring(20, 32);
    } else {
      return new String(hexChars);
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
        Logger.error("System initialization failed: {}", result.getErrorMessage());
        ctx.status(500).json(Map.of("error", result.getErrorMessage()));
        return;
      }

      if (result.isAlreadyInitialized()) {
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
                  "root_api_key", result.getApiKey(),
                  "user_id", result.getUserId().toString()));

    } catch (Exception e) {
      Logger.error(e, "Error during system initialization.");
      ctx.status(500).json(Map.of("error", "Internal server error: " + e.getMessage()));
    }
  }

  public static void main(String[] args) throws IOException {
    Main server = new Main();
    server.startGrpcServer();
    server.startJavalinServer();
  }
}
