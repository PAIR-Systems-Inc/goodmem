package com.goodmem;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.util.Timestamps;
import goodmem.v1.Apikey.ApiKey;
import goodmem.v1.Apikey.CreateApiKeyRequest;
import goodmem.v1.Apikey.CreateApiKeyResponse;
import goodmem.v1.Apikey.DeleteApiKeyRequest;
import goodmem.v1.Apikey.ListApiKeysRequest;
import goodmem.v1.Apikey.ListApiKeysResponse;
import goodmem.v1.Apikey.Status;
import goodmem.v1.Apikey.UpdateApiKeyRequest;
import goodmem.v1.ApiKeyServiceGrpc;
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
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Main {
  private static final Logger logger = Logger.getLogger(Main.class.getName());
  private static final int GRPC_PORT = 9090;
  private static final int REST_PORT = 8080;

  private Server grpcServer;
  private final SpaceServiceImpl spaceServiceImpl;
  private final UserServiceImpl userServiceImpl;
  private final MemoryServiceImpl memoryServiceImpl;
  private final ApiKeyServiceImpl apiKeyServiceImpl;
  
  // gRPC blocking stubs for the REST-to-gRPC bridge
  private final SpaceServiceGrpc.SpaceServiceBlockingStub spaceService;
  private final UserServiceGrpc.UserServiceBlockingStub userService;
  private final MemoryServiceGrpc.MemoryServiceBlockingStub memoryService;
  private final ApiKeyServiceGrpc.ApiKeyServiceBlockingStub apiKeyService;

  public Main() {
    // Create service implementations
    this.spaceServiceImpl = new SpaceServiceImpl();
    this.userServiceImpl = new UserServiceImpl();
    this.memoryServiceImpl = new MemoryServiceImpl();
    this.apiKeyServiceImpl = new ApiKeyServiceImpl();
    
    // Create an in-process channel for REST-to-gRPC communication
    ManagedChannel channel = InProcessChannelBuilder.forName("in-process").build();
    
    // Create blocking stubs for each service
    this.spaceService = SpaceServiceGrpc.newBlockingStub(channel);
    this.userService = UserServiceGrpc.newBlockingStub(channel);
    this.memoryService = MemoryServiceGrpc.newBlockingStub(channel);
    this.apiKeyService = ApiKeyServiceGrpc.newBlockingStub(channel);
  }

  public void startGrpcServer() throws IOException {
    grpcServer =
        ServerBuilder.forPort(GRPC_PORT)
            .addService(ServerInterceptors.intercept(spaceServiceImpl, new AuthInterceptor()))
            .addService(ServerInterceptors.intercept(userServiceImpl, new AuthInterceptor()))
            .addService(ServerInterceptors.intercept(memoryServiceImpl, new AuthInterceptor()))
            .addService(ServerInterceptors.intercept(apiKeyServiceImpl, new AuthInterceptor()))
            .build()
            .start();
    logger.info("gRPC Server started, listening on port " + GRPC_PORT);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  logger.info("Shutting down gRPC server since JVM is shutting down");
                  try {
                    Main.this.stopGrpcServer();
                  } catch (InterruptedException e) {
                    logger.severe("Server shutdown interrupted: " + e.getMessage());
                  }
                }));
  }

  private void stopGrpcServer() throws InterruptedException {
    if (grpcServer != null) {
      grpcServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
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

    logger.info("REST server started, listening on port " + REST_PORT);
  }

  // Space handlers
  private void handleCreateSpace(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    logger.info("REST CreateSpace request with API key: " + apiKey);

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
  
  private void handleGetSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST GetSpace request for ID: " + spaceIdHex + " with API key: " + apiKey);
    
    ByteString spaceId = convertHexToUuidBytes(spaceIdHex);
    GetSpaceRequest request = GetSpaceRequest.newBuilder().setSpaceId(spaceId).build();
    
    Space response = spaceService.getSpace(request);
    ctx.json(protoToMap(response));
  }

  private void handleListSpaces(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    logger.info("REST ListSpaces request with API key: " + apiKey);

    ListSpacesRequest.Builder requestBuilder = ListSpacesRequest.newBuilder();

    // Handle label selectors from query parameters
    ctx.queryParamMap()
        .forEach(
            (key, values) -> {
              if (key.startsWith("label.") && !values.isEmpty()) {
                String labelKey = key.substring("label.".length());
                requestBuilder.putLabelSelectors(labelKey, values.get(0));
              }
            });
    
    // Handle owner_id filter if provided
    if (ctx.queryParam("owner_id") != null) {
      ByteString ownerId = convertHexToUuidBytes(ctx.queryParam("owner_id"));
      requestBuilder.setOwnerId(ownerId);
    }

    ListSpacesResponse response = spaceService.listSpaces(requestBuilder.build());
    ctx.json(Map.of("spaces", response.getSpacesList().stream().map(this::protoToMap).toList()));
  }
  
  private void handleUpdateSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST UpdateSpace request for ID: " + spaceIdHex + " with API key: " + apiKey);
    
    ByteString spaceId = convertHexToUuidBytes(spaceIdHex);
    UpdateSpaceRequest.Builder requestBuilder = UpdateSpaceRequest.newBuilder().setSpaceId(spaceId);
    
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

  private void handleDeleteSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST DeleteSpace request for ID: " + spaceIdHex + " with API key: " + apiKey);

    ByteString spaceId = convertHexToUuidBytes(spaceIdHex);
    DeleteSpaceRequest request = DeleteSpaceRequest.newBuilder().setSpaceId(spaceId).build();

    spaceService.deleteSpace(request);
    ctx.status(204);
  }
  
  // User handlers
  private void handleGetUser(Context ctx) {
    String userIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST GetUser request for ID: " + userIdHex + " with API key: " + apiKey);
    
    ByteString userId = convertHexToUuidBytes(userIdHex);
    GetUserRequest request = GetUserRequest.newBuilder().setUserId(userId).build();
    
    User response = userService.getUser(request);
    ctx.json(protoToMap(response));
  }
  
  // Memory handlers
  private void handleCreateMemory(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    logger.info("REST CreateMemory request with API key: " + apiKey);
    
    CreateMemoryRequest.Builder requestBuilder = CreateMemoryRequest.newBuilder();
    Map<String, Object> json = ctx.bodyAsClass(Map.class);
    
    if (json.containsKey("space_id")) {
      ByteString spaceId = convertHexToUuidBytes((String) json.get("space_id"));
      requestBuilder.setSpaceId(spaceId);
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
  
  private void handleGetMemory(Context ctx) {
    String memoryIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST GetMemory request for ID: " + memoryIdHex + " with API key: " + apiKey);
    
    ByteString memoryId = convertHexToUuidBytes(memoryIdHex);
    GetMemoryRequest request = GetMemoryRequest.newBuilder().setMemoryId(memoryId).build();
    
    Memory response = memoryService.getMemory(request);
    ctx.json(protoToMap(response));
  }
  
  private void handleListMemories(Context ctx) {
    String spaceIdHex = ctx.pathParam("spaceId");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST ListMemories request for space ID: " + spaceIdHex + " with API key: " + apiKey);
    
    ByteString spaceId = convertHexToUuidBytes(spaceIdHex);
    ListMemoriesRequest request = ListMemoriesRequest.newBuilder().setSpaceId(spaceId).build();
    
    ListMemoriesResponse response = memoryService.listMemories(request);
    ctx.json(Map.of("memories", response.getMemoriesList().stream().map(this::protoToMap).toList()));
  }
  
  private void handleDeleteMemory(Context ctx) {
    String memoryIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST DeleteMemory request for ID: " + memoryIdHex + " with API key: " + apiKey);
    
    ByteString memoryId = convertHexToUuidBytes(memoryIdHex);
    DeleteMemoryRequest request = DeleteMemoryRequest.newBuilder().setMemoryId(memoryId).build();
    
    memoryService.deleteMemory(request);
    ctx.status(204);
  }
  
  // API Key handlers
  private void handleCreateApiKey(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    logger.info("REST CreateApiKey request with API key: " + apiKey);
    
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
  
  private void handleListApiKeys(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    logger.info("REST ListApiKeys request with API key: " + apiKey);
    
    ListApiKeysRequest request = ListApiKeysRequest.newBuilder().build();
    
    ListApiKeysResponse response = apiKeyService.listApiKeys(request);
    ctx.json(Map.of("keys", response.getKeysList().stream().map(this::protoToMap).toList()));
  }
  
  private void handleUpdateApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST UpdateApiKey request for ID: " + apiKeyIdHex + " with API key: " + apiKey);
    
    ByteString apiKeyId = convertHexToUuidBytes(apiKeyIdHex);
    UpdateApiKeyRequest.Builder requestBuilder = UpdateApiKeyRequest.newBuilder().setApiKeyId(apiKeyId);
    
    Map<String, Object> json = ctx.bodyAsClass(Map.class);
    
    if (json.containsKey("labels") && json.get("labels") instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, String> labels = (Map<String, String>) json.get("labels");
      requestBuilder.putAllLabels(labels);
    }
    
    if (json.containsKey("status")) {
      String statusStr = (String) json.get("status");
      Status status;
      switch (statusStr.toUpperCase()) {
        case "ACTIVE":
          status = Status.ACTIVE;
          break;
        case "INACTIVE":
          status = Status.INACTIVE;
          break;
        default:
          status = Status.STATUS_UNSPECIFIED;
          break;
      }
      requestBuilder.setStatus(status);
    }
    
    ApiKey response = apiKeyService.updateApiKey(requestBuilder.build());
    ctx.json(protoToMap(response));
  }
  
  private void handleDeleteApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    logger.info("REST DeleteApiKey request for ID: " + apiKeyIdHex + " with API key: " + apiKey);
    
    ByteString apiKeyId = convertHexToUuidBytes(apiKeyIdHex);
    DeleteApiKeyRequest request = DeleteApiKeyRequest.newBuilder().setApiKeyId(apiKeyId).build();
    
    apiKeyService.deleteApiKey(request);
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
  private ByteString convertHexToUuidBytes(String hexString) {
    try {
      // Remove any hyphens or prefixes
      String cleanHex = hexString.replace("-", "").replace("0x", "");
      
      // Check if we have a valid hex string of the right length
      if (cleanHex.length() != 32) {
        throw new IllegalArgumentException("Invalid UUID hex string: " + hexString);
      }
      
      // Convert hex to byte array
      byte[] bytes = new byte[16];
      for (int i = 0; i < 16; i++) {
        int j = i * 2;
        bytes[i] = (byte) Integer.parseInt(cleanHex.substring(j, j + 2), 16);
      }
      
      return ByteString.copyFrom(bytes);
    } catch (Exception e) {
      logger.warning("Failed to convert hex to UUID bytes: " + e.getMessage());
      return ByteString.copyFrom(new byte[16]); // Return zeros in case of error
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
      return hex.substring(0, 8) + "-" + 
             hex.substring(8, 12) + "-" + 
             hex.substring(12, 16) + "-" + 
             hex.substring(16, 20) + "-" + 
             hex.substring(20, 32);
    } else {
      return new String(hexChars);
    }
  }

  public static void main(String[] args) throws IOException {
    Main server = new Main();
    server.startGrpcServer();
    server.startJavalinServer();
  }
}