package com.goodmem.rest;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;
import static io.javalin.apibuilder.ApiBuilder.put;

import goodmem.v1.ApiKeyServiceGrpc;
import goodmem.v1.EmbedderServiceGrpc;
import goodmem.v1.MemoryServiceGrpc;
import goodmem.v1.SpaceServiceGrpc;
import goodmem.v1.UserServiceGrpc;
import io.grpc.ManagedChannel;
import io.javalin.Javalin;
import io.javalin.config.RouterConfig;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * Factory for creating REST adapters for all service endpoints.
 *
 * <p>This factory creates and manages all REST adapter instances, ensuring they are properly
 * initialized with their corresponding gRPC service stubs.
 */
public class RestAdapterFactory {

  private final List<RestAdapter> adapters = new ArrayList<>();
  private final SpaceServiceRestAdapter spaceAdapter;
  private final UserServiceRestAdapter userAdapter;
  private final MemoryServiceRestAdapter memoryAdapter;
  private final ApiKeyServiceRestAdapter apiKeyAdapter;
  private final EmbedderServiceRestAdapter embedderAdapter;

  /**
   * Creates a new RestAdapterFactory with the specified gRPC service stubs.
   *
   * @param userService The UserService gRPC stub
   * @param spaceService The SpaceService gRPC stub
   * @param memoryService The MemoryService gRPC stub
   * @param apiKeyService The ApiKeyService gRPC stub
   * @param embedderService The EmbedderService gRPC stub
   * @param dataSource The data source for database connections
   */
  public RestAdapterFactory(
      UserServiceGrpc.UserServiceBlockingStub userService,
      SpaceServiceGrpc.SpaceServiceBlockingStub spaceService,
      MemoryServiceGrpc.MemoryServiceBlockingStub memoryService,
      ApiKeyServiceGrpc.ApiKeyServiceBlockingStub apiKeyService,
      EmbedderServiceGrpc.EmbedderServiceBlockingStub embedderService,
      DataSource dataSource) {

    // Create all adapters
    this.userAdapter = new UserServiceRestAdapter(userService, dataSource);
    this.spaceAdapter = new SpaceServiceRestAdapter(spaceService);
    this.memoryAdapter = new MemoryServiceRestAdapter(memoryService);
    this.apiKeyAdapter = new ApiKeyServiceRestAdapter(apiKeyService);
    this.embedderAdapter = new EmbedderServiceRestAdapter(embedderService);

    // Add all adapters to the list
    adapters.add(userAdapter);
    adapters.add(spaceAdapter);
    adapters.add(memoryAdapter);
    adapters.add(apiKeyAdapter);
    adapters.add(embedderAdapter);
  }

  /**
   * Creates a new RestAdapterFactory with service stubs created from the provided channel.
   *
   * @param channel The gRPC managed channel to use for creating service stubs
   * @param dataSource The data source for database connections
   * @return A new RestAdapterFactory instance
   */
  public static RestAdapterFactory createWithChannel(
      ManagedChannel channel, DataSource dataSource) {
    return new RestAdapterFactory(
        UserServiceGrpc.newBlockingStub(channel),
        SpaceServiceGrpc.newBlockingStub(channel),
        MemoryServiceGrpc.newBlockingStub(channel),
        ApiKeyServiceGrpc.newBlockingStub(channel),
        EmbedderServiceGrpc.newBlockingStub(channel),
        dataSource);
  }

  /**
   * Configures the Javalin router to use the REST adapters.
   */
  public void configureRoutes(RouterConfig router) {
    router.apiBuilder(
        () -> {
          // Space endpoints
          path(
              "/v1/spaces",
              () -> {
                post(spaceAdapter::handleCreateSpace);
                get(spaceAdapter::handleListSpaces);
                path(
                    "{id}",
                    () -> {
                      get(spaceAdapter::handleGetSpace);
                      put(spaceAdapter::handleUpdateSpace);
                      delete(spaceAdapter::handleDeleteSpace);
                    });
                path(
                    "{spaceId}/memories",
                    () -> {
                      get(memoryAdapter::handleListMemories);
                    });
              });

          // User endpoints
          path(
              "/v1/users",
              () -> {
                path(
                    "{id}",
                    () -> {
                      get(userAdapter::handleGetUser);
                    });
              });

          // System init endpoint
          path(
              "/v1/system/init",
              () -> {
                post(userAdapter::handleSystemInit);
              });

          // Memory endpoints
          path(
              "/v1/memories",
              () -> {
                post(memoryAdapter::handleCreateMemory);
                path(
                    "{id}",
                    () -> {
                      get(memoryAdapter::handleGetMemory);
                      delete(memoryAdapter::handleDeleteMemory);
                    });
              });

          // API Key endpoints
          path(
              "/v1/apikeys",
              () -> {
                post(apiKeyAdapter::handleCreateApiKey);
                get(apiKeyAdapter::handleListApiKeys);
                path(
                    "{id}",
                    () -> {
                      put(apiKeyAdapter::handleUpdateApiKey);
                      delete(apiKeyAdapter::handleDeleteApiKey);
                    });
              });

          // Embedder endpoints
          path(
              "/v1/embedders",
              () -> {
                post(embedderAdapter::handleCreateEmbedder);
                get(embedderAdapter::handleListEmbedders);
                path(
                    "{id}",
                    () -> {
                      get(embedderAdapter::handleGetEmbedder);
                      put(embedderAdapter::handleUpdateEmbedder);
                      delete(embedderAdapter::handleDeleteEmbedder);
                    });
              });
        });
  }

  /**
   * Gets the SpaceServiceRestAdapter instance.
   *
   * @return The SpaceServiceRestAdapter
   */
  public SpaceServiceRestAdapter getSpaceAdapter() {
    return spaceAdapter;
  }

  /**
   * Gets the UserServiceRestAdapter instance.
   *
   * @return The UserServiceRestAdapter
   */
  public UserServiceRestAdapter getUserAdapter() {
    return userAdapter;
  }

  /**
   * Gets the MemoryServiceRestAdapter instance.
   *
   * @return The MemoryServiceRestAdapter
   */
  public MemoryServiceRestAdapter getMemoryAdapter() {
    return memoryAdapter;
  }

  /**
   * Gets the ApiKeyServiceRestAdapter instance.
   *
   * @return The ApiKeyServiceRestAdapter
   */
  public ApiKeyServiceRestAdapter getApiKeyAdapter() {
    return apiKeyAdapter;
  }

  /**
   * Gets the EmbedderServiceRestAdapter instance.
   *
   * @return The EmbedderServiceRestAdapter
   */
  public EmbedderServiceRestAdapter getEmbedderAdapter() {
    return embedderAdapter;
  }
}
