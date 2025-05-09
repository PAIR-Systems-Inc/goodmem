package com.goodmem.rest;

import static io.javalin.apibuilder.ApiBuilder.path;

import com.goodmem.common.status.StatusOr;
import com.goodmem.rest.dto.CreateSpaceRequest;
import com.goodmem.rest.dto.DeleteSpaceRequest;
import com.goodmem.rest.dto.GetSpaceRequest;
import com.goodmem.rest.dto.ListSpacesRequest;
import com.goodmem.rest.dto.ListSpacesResponse;
import com.goodmem.rest.dto.SortOrder;
import com.goodmem.rest.dto.Space;
import com.goodmem.rest.dto.UpdateSpaceRequest;
import com.goodmem.util.RestMapper;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import goodmem.v1.Common;
import goodmem.v1.SpaceOuterClass;
import goodmem.v1.SpaceServiceGrpc;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.tinylog.Logger;

/**
 * REST adapter for space-related endpoints.
 * 
 * <p>This adapter handles all REST API endpoints related to space management,
 * including creating, retrieving, listing, updating, and deleting spaces.
 */
public class SpaceServiceRestAdapter implements RestAdapter {
  
  private final SpaceServiceGrpc.SpaceServiceBlockingStub spaceService;
  
  /**
   * Creates a new SpaceServiceRestAdapter with the specified gRPC service stub.
   * 
   * @param spaceService The gRPC service stub to delegate to
   */
  public SpaceServiceRestAdapter(SpaceServiceGrpc.SpaceServiceBlockingStub spaceService) {
    this.spaceService = spaceService;
  }
  
  @Override
  public void registerRoutes() {
    // No implementation required - route registration is handled by the caller
  }

  /**
   * Handles a REST request to create a new Space. Converts the DTO from JSON and calls
   * the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
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
                      from = CreateSpaceRequest.class,
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
          @OpenApiResponse(
              status = "401", 
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to create spaces")
      })
  public void handleCreateSpace(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateSpace request with API key: {}", apiKey);

    // Parse the JSON body into our DTO
    CreateSpaceRequest requestDto = ctx.bodyAsClass(CreateSpaceRequest.class);
    
    // Convert the DTO to the gRPC request builder
    SpaceOuterClass.CreateSpaceRequest.Builder requestBuilder = SpaceOuterClass.CreateSpaceRequest.newBuilder();
    
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
        setError(ctx, 400, "Invalid embedder ID format");
        return;
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
        setError(ctx, 400, "Invalid owner ID format");
        return;
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
  public void handleGetSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    // Create the DTO from path parameter
    GetSpaceRequest requestDto = new GetSpaceRequest(spaceIdHex);
    
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
              content = @OpenApiContent(from = ListSpacesResponse.class)),
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
  public void handleListSpaces(Context ctx) {
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
    ListSpacesRequest requestDto = new ListSpacesRequest(
        ctx.queryParam("owner_id"),
        labelSelectors.isEmpty() ? null : labelSelectors,
        ctx.queryParam("name_filter"),
        maxResults,
        ctx.queryParam("next_token"),
        ctx.queryParam("sort_by"),
        SortOrder.fromString(sortOrderStr)
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
        setError(ctx, 400, "Invalid owner ID format");
        return;
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
        .collect(Collectors.toList());
    
    // Create and return the ListSpacesResponse DTO
    ListSpacesResponse responseDto = new ListSpacesResponse(
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
                      from = UpdateSpaceRequest.class,
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
  public void handleUpdateSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    // Create our DTO from the path parameter and request body
    // First parse the body as our DTO class
    UpdateSpaceRequest requestDto = ctx.bodyAsClass(UpdateSpaceRequest.class);
    
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
      Common.StringMap.Builder labelsBuilder = Common.StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.replaceLabels());
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    } else if (requestDto.mergeLabels() != null) {
      Common.StringMap.Builder labelsBuilder = Common.StringMap.newBuilder();
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
      description = "Deletes a space and all memories contained within it. This operation cannot be undone.",
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
  public void handleDeleteSpace(Context ctx) {
    String spaceIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteSpace request for ID: {} with API key: {}", spaceIdHex, apiKey);

    // Create our DTO from the path parameter
    DeleteSpaceRequest requestDto = new DeleteSpaceRequest(spaceIdHex);
    
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
}