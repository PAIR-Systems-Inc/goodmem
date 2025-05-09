package com.goodmem.rest;

import com.goodmem.common.status.StatusOr;
import com.goodmem.rest.dto.ApiKeyResponse;
import com.goodmem.rest.dto.CreateApiKeyRequest;
import com.goodmem.rest.dto.CreateApiKeyResponse;
import com.goodmem.rest.dto.DeleteApiKeyRequest;
import com.goodmem.rest.dto.ListApiKeysResponse;
import com.goodmem.rest.dto.UpdateApiKeyRequest;
import com.goodmem.util.RestMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import goodmem.v1.ApiKeyServiceGrpc;
import goodmem.v1.Apikey;
import goodmem.v1.Apikey.ApiKey;
import goodmem.v1.Apikey.ListApiKeysRequest;
import goodmem.v1.Common;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import java.util.List;
import java.util.Map;
import org.tinylog.Logger;

/**
 * REST adapter for API key-related endpoints.
 * 
 * <p>This adapter handles all REST API endpoints related to API key management,
 * including creating, listing, updating, and deleting API keys.
 */
public class ApiKeyServiceRestAdapter implements RestAdapter {
  
  private final ApiKeyServiceGrpc.ApiKeyServiceBlockingStub apiKeyService;
  
  /**
   * Creates a new ApiKeyServiceRestAdapter with the specified gRPC service stub.
   * 
   * @param apiKeyService The gRPC service stub to delegate to
   */
  public ApiKeyServiceRestAdapter(ApiKeyServiceGrpc.ApiKeyServiceBlockingStub apiKeyService) {
    this.apiKeyService = apiKeyService;
  }
  
  @Override
  public void registerRoutes() {
    // No implementation required - route registration is handled by the caller
  }
  
  /**
   * Handles a REST request to create a new API Key. Converts the DTO to a gRPC request
   * and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/apikeys",
      methods = { HttpMethod.POST },
      summary = "Create a new API key",
      description = "Creates a new API key for authenticating with the API. The response includes the one-time raw API key value which will not be retrievable later.",
      operationId = "createApiKey",
      tags = "API Keys",
      requestBody =
          @OpenApiRequestBody(
              description = "API key configuration",
              required = true,
              content =
                  @OpenApiContent(
                      from = CreateApiKeyRequest.class,
                      example =
                          """
              {
                "labels": {
                  "environment": "development",
                  "service": "chat-ui"
                },
                "expiresAt": 1735689600000
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully created API key",
              content = @OpenApiContent(from = CreateApiKeyResponse.class)),
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
  public void handleCreateApiKey(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateApiKey request with API key: {}", apiKey);

    // Parse the request body into our DTO
    CreateApiKeyRequest requestDto = ctx.bodyAsClass(CreateApiKeyRequest.class);
    
    // Convert the DTO to the gRPC request
    Apikey.CreateApiKeyRequest.Builder requestBuilder = Apikey.CreateApiKeyRequest.newBuilder();
    
    // Add labels if provided
    if (requestDto.labels() != null && !requestDto.labels().isEmpty()) {
      requestBuilder.putAllLabels(requestDto.labels());
    }
    
    // Add expiration timestamp if provided
    if (requestDto.expiresAt() != null) {
      // Convert from milliseconds to Timestamp
      requestBuilder.setExpiresAt(
          Timestamp.newBuilder()
              .setSeconds(requestDto.expiresAt() / 1000)
              .setNanos((int) ((requestDto.expiresAt() % 1000) * 1000000))
              .build());
    }

    // Call the gRPC service
    Apikey.CreateApiKeyResponse response = apiKeyService.createApiKey(requestBuilder.build());
    
    // Convert the ApiKey metadata to our DTO
    Map<String, Object> metadataMap = RestMapper.toJsonMap(response.getApiKeyMetadata());
    ApiKeyResponse apiKeyResponseDto = new ApiKeyResponse(
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
    CreateApiKeyResponse responseDto = 
        new CreateApiKeyResponse(apiKeyResponseDto, response.getRawApiKey());
    
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
              content = @OpenApiContent(from = ListApiKeysResponse.class)),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to list API keys")
      })
  public void handleListApiKeys(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListApiKeys request with API key: {}", apiKey);

    // Create and execute the gRPC request (note: currently no parameters)
    ListApiKeysRequest request = ListApiKeysRequest.newBuilder().build();
    Apikey.ListApiKeysResponse response = apiKeyService.listApiKeys(request);
    
    // Convert each ApiKey to our DTO format
    List<ApiKeyResponse> apiKeys = response.getKeysList().stream()
        .map(key -> {
          Map<String, Object> keyMap = RestMapper.toJsonMap(key);
          return new ApiKeyResponse(
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
    ListApiKeysResponse responseDto = 
        new ListApiKeysResponse(apiKeys);
    
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
                      from = UpdateApiKeyRequest.class,
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
              content = @OpenApiContent(from = ApiKeyResponse.class)),
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
  public void handleUpdateApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    // Parse the request body into our DTO
    UpdateApiKeyRequest requestDto = ctx.bodyAsClass(UpdateApiKeyRequest.class);
    
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
    Apikey.UpdateApiKeyRequest.Builder requestBuilder =
        Apikey.UpdateApiKeyRequest.newBuilder().setApiKeyId(apiKeyIdOr.getValue());

    // Handle label update strategies using StringMap
    if (requestDto.replaceLabels() != null) {
      Common.StringMap.Builder labelsBuilder = Common.StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.replaceLabels());
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    } else if (requestDto.mergeLabels() != null) {
      Common.StringMap.Builder labelsBuilder = Common.StringMap.newBuilder();
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
    ApiKeyResponse responseDto = new ApiKeyResponse(
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
  public void handleDeleteApiKey(Context ctx) {
    String apiKeyIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteApiKey request for ID: {} with API key: {}", apiKeyIdHex, apiKey);

    // Create our DTO from the path parameter
    DeleteApiKeyRequest requestDto = new DeleteApiKeyRequest(apiKeyIdHex);
    
    // Validate and convert the API key ID
    StatusOr<ByteString> apiKeyIdOr = convertHexToUuidBytes(requestDto.apiKeyId());
    if (apiKeyIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid API key ID format");
      return;
    }
    
    // Build the gRPC request
    Apikey.DeleteApiKeyRequest request = Apikey.DeleteApiKeyRequest.newBuilder()
        .setApiKeyId(apiKeyIdOr.getValue())
        .build();
    
    // Call the gRPC service
    apiKeyService.deleteApiKey(request);
    
    // Return 204 No Content on successful deletion
    ctx.status(204);
  }
}