package com.goodmem.rest;

import static io.javalin.apibuilder.ApiBuilder.path;

import com.goodmem.common.status.StatusOr;
import com.goodmem.util.RestMapper;
import com.google.protobuf.ByteString;
import goodmem.v1.MemoryOuterClass.CreateMemoryRequest;
import goodmem.v1.MemoryOuterClass.DeleteMemoryRequest;
import goodmem.v1.MemoryOuterClass.GetMemoryRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesResponse;
import goodmem.v1.MemoryOuterClass.Memory;
import goodmem.v1.MemoryServiceGrpc;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import java.util.Map;
import org.tinylog.Logger;

/**
 * REST adapter for memory-related endpoints.
 * 
 * <p>This adapter handles all REST API endpoints related to memory management,
 * including creating, retrieving, listing, and deleting memories.
 * 
 * <p>Note: This adapter follows the same pattern as other service adapters but serves
 * as a placeholder since proper DTOs have not yet been created for memory objects.
 * It uses raw Maps for request/response handling until proper DTOs are available.
 */
public class MemoryServiceRestAdapter implements RestAdapter {
  
  private final MemoryServiceGrpc.MemoryServiceBlockingStub memoryService;
  
  /**
   * Creates a new MemoryServiceRestAdapter with the specified gRPC service stub.
   * 
   * @param memoryService The gRPC service stub to delegate to
   */
  public MemoryServiceRestAdapter(MemoryServiceGrpc.MemoryServiceBlockingStub memoryService) {
    this.memoryService = memoryService;
  }
  
  @Override
  public void registerRoutes() {
    // No implementation required - route registration is handled by the caller
  }

  /**
   * Handles a REST request to create a new Memory. Builds the create request from JSON and calls
   * the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/memories",
      methods = { HttpMethod.POST },
      summary = "Create a new memory",
      description = "Creates a new memory with the provided content reference, metadata, and space assignment.",
      operationId = "createMemory",
      tags = "Memories",
      requestBody =
          @OpenApiRequestBody(
              description = "Memory creation details",
              required = true,
              content =
                  @OpenApiContent(
                      type = "application/json",
                      example =
                          """
              {
                "space_id": "550e8400-e29b-41d4-a716-446655440000",
                "original_content_ref": "s3://bucket/object.txt",
                "content_type": "text/plain",
                "metadata": {
                  "source": "document",
                  "author": "John Doe"
                }
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully created memory",
              content = @OpenApiContent(type = "application/json")),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - missing required fields or invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to create memories")
      })
  public void handleCreateMemory(Context ctx) {
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
  @OpenApi(
      path = "/v1/memories/{id}",
      methods = { HttpMethod.GET },
      summary = "Get a memory by ID",
      description = "Retrieves a specific memory by its unique identifier.",
      operationId = "getMemory",
      tags = "Memories",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the memory to retrieve",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved memory",
              content = @OpenApiContent(type = "application/json")),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - memory ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to view this memory"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - memory with the specified ID does not exist")
      })
  public void handleGetMemory(Context ctx) {
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
  @OpenApi(
      path = "/v1/spaces/{spaceId}/memories",
      methods = { HttpMethod.GET },
      summary = "List memories in a space",
      description = "Retrieves a list of memories contained within a specific space.",
      operationId = "listMemories",
      tags = "Memories",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "spaceId",
              description = "The unique identifier of the space containing the memories",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved memories",
              content = @OpenApiContent(type = "application/json")),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - space ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to list memories in this space"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - space with the specified ID does not exist")
      })
  public void handleListMemories(Context ctx) {
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
  @OpenApi(
      path = "/v1/memories/{id}",
      methods = { HttpMethod.DELETE },
      summary = "Delete a memory",
      description = "Deletes a memory. This operation cannot be undone.",
      operationId = "deleteMemory",
      tags = "Memories",
      pathParams = {
          @io.javalin.openapi.OpenApiParam(
              name = "id",
              description = "The unique identifier of the memory to delete",
              required = true,
              type = String.class,
              example = "550e8400-e29b-41d4-a716-446655440000")
      },
      responses = {
          @OpenApiResponse(
              status = "204",
              description = "Memory successfully deleted"),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - memory ID in invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to delete this memory"),
          @OpenApiResponse(
              status = "404",
              description = "Not found - memory with the specified ID does not exist")
      })
  public void handleDeleteMemory(Context ctx) {
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
}