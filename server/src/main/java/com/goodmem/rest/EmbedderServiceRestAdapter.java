package com.goodmem.rest;

import static io.javalin.apibuilder.ApiBuilder.path;

import com.goodmem.common.status.StatusOr;
import com.goodmem.rest.dto.CreateEmbedderRequest;
import com.goodmem.rest.dto.DeleteEmbedderRequest;
import com.goodmem.rest.dto.EmbedderResponse;
import com.goodmem.rest.dto.GetEmbedderRequest;
import com.goodmem.rest.dto.ListEmbeddersRequest;
import com.goodmem.rest.dto.ListEmbeddersResponse;
import com.goodmem.rest.dto.Modality;
import com.goodmem.rest.dto.ProviderType;
import com.goodmem.rest.dto.UpdateEmbedderRequest;
import com.goodmem.util.RestMapper;
import com.google.common.base.Strings;
import com.google.protobuf.ByteString;
import goodmem.v1.Common;
import goodmem.v1.EmbedderOuterClass;
import goodmem.v1.EmbedderOuterClass.Embedder;
import goodmem.v1.EmbedderServiceGrpc;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.tinylog.Logger;

/**
 * REST adapter for embedder-related endpoints.
 * 
 * <p>This adapter handles all REST API endpoints related to embedder management,
 * including creating, retrieving, listing, updating, and deleting embedders.
 */
public class EmbedderServiceRestAdapter implements RestAdapter {
  
  private final EmbedderServiceGrpc.EmbedderServiceBlockingStub embedderService;
  
  /**
   * Creates a new EmbedderServiceRestAdapter with the specified gRPC service stub.
   * 
   * @param embedderService The gRPC service stub to delegate to
   */
  public EmbedderServiceRestAdapter(EmbedderServiceGrpc.EmbedderServiceBlockingStub embedderService) {
    this.embedderService = embedderService;
  }
  
  @Override
  public void registerRoutes() {
    // No implementation required - route registration is handled by the caller
  }
  
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
                      from = CreateEmbedderRequest.class,
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
                "credentials": "sk-your-api-key-here",
                "labels": {
                  "environment": "production",
                  "team": "nlp"
                }
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully created embedder",
              content = @OpenApiContent(from = EmbedderResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - missing required fields or invalid format"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to create embedders")
      })
  public void handleCreateEmbedder(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST CreateEmbedder request with API key: {}", apiKey);

    // Parse the request body into our DTO
    CreateEmbedderRequest requestDto = ctx.bodyAsClass(CreateEmbedderRequest.class);
    
    // Validate required fields
    try {
      requestDto.validate();
    } catch (IllegalArgumentException e) {
      setError(ctx, 400, e.getMessage());
      return;
    }
    
    // Convert the DTO to the gRPC request
    EmbedderOuterClass.CreateEmbedderRequest.Builder requestBuilder = 
        EmbedderOuterClass.CreateEmbedderRequest.newBuilder();
    
    // Set required fields
    requestBuilder.setDisplayName(requestDto.displayName());
    requestBuilder.setProviderType(
        switch(requestDto.providerType()) {
          case OPENAI -> EmbedderOuterClass.ProviderType.PROVIDER_TYPE_OPENAI;
          case TEI -> EmbedderOuterClass.ProviderType.PROVIDER_TYPE_TEI;
          case VLLM -> EmbedderOuterClass.ProviderType.PROVIDER_TYPE_VLLM;
        }
    );
    requestBuilder.setEndpointUrl(requestDto.endpointUrl());
    requestBuilder.setModelIdentifier(requestDto.modelIdentifier());
    requestBuilder.setDimensionality(requestDto.dimensionality());
    requestBuilder.setCredentials(requestDto.credentials());
    
    // Set optional fields
    if (!Strings.isNullOrEmpty(requestDto.description())) {
      requestBuilder.setDescription(requestDto.description());
    }
    
    if (!Strings.isNullOrEmpty(requestDto.apiPath())) {
      requestBuilder.setApiPath(requestDto.apiPath());
    }
    
    if (requestDto.maxSequenceLength() != null) {
      requestBuilder.setMaxSequenceLength(requestDto.maxSequenceLength());
    }
    
    if (requestDto.supportedModalities() != null && !requestDto.supportedModalities().isEmpty()) {
      for (Modality modality : requestDto.supportedModalities()) {
        requestBuilder.addSupportedModalities(
            switch (modality) {
              case TEXT -> EmbedderOuterClass.Modality.MODALITY_TEXT;
              case AUDIO -> EmbedderOuterClass.Modality.MODALITY_AUDIO;
              case VIDEO -> EmbedderOuterClass.Modality.MODALITY_VIDEO;
              case IMAGE -> EmbedderOuterClass.Modality.MODALITY_IMAGE;
            }
        );
      }
    }
    
    if (requestDto.labels() != null && !requestDto.labels().isEmpty()) {
      requestBuilder.putAllLabels(requestDto.labels());
    }
    
    if (!Strings.isNullOrEmpty(requestDto.version())) {
      requestBuilder.setVersion(requestDto.version());
    }
    
    if (!Strings.isNullOrEmpty(requestDto.monitoringEndpoint())) {
      requestBuilder.setMonitoringEndpoint(requestDto.monitoringEndpoint());
    }
    
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
    
    // Call the gRPC service
    Embedder response = embedderService.createEmbedder(requestBuilder.build());
    
    // Convert the response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    EmbedderResponse responseDto = createEmbedderResponseFromMap(responseMap);
    
    // Return the response
    ctx.json(responseDto);
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
      description = "Retrieves the details of a specific embedder configuration by its unique identifier.",
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
              content = @OpenApiContent(from = EmbedderResponse.class)),
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
  public void handleGetEmbedder(Context ctx) {
    String embedderIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST GetEmbedder request for ID: {} with API key: {}", embedderIdHex, apiKey);

    // Create the DTO from the path parameter
    GetEmbedderRequest requestDto = new GetEmbedderRequest(embedderIdHex);
    
    // Validate and convert the embedder ID
    StatusOr<ByteString> embedderIdOr = convertHexToUuidBytes(requestDto.embedderId());
    if (embedderIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid embedder ID format");
      return;
    }
    
    // Build the gRPC request
    EmbedderOuterClass.GetEmbedderRequest request =
        EmbedderOuterClass.GetEmbedderRequest.newBuilder()
            .setEmbedderId(embedderIdOr.getValue())
            .build();
    
    // Call the gRPC service
    Embedder response = embedderService.getEmbedder(request);
    
    // Convert the response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    EmbedderResponse responseDto = createEmbedderResponseFromMap(responseMap);
    
    // Return the response
    ctx.json(responseDto);
  }

  /**
   * Handles a REST request to list available Embedders. Adds query parameters to the gRPC request
   * and calls the gRPC service.
   *
   * @param ctx The Javalin context containing the request and response
   */
  @OpenApi(
      path = "/v1/embedders",
      methods = { HttpMethod.GET },
      summary = "List embedders",
      description = "Retrieves a list of embedder configurations accessible to the caller, with optional filtering.",
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
              description = "Filter embedders by provider type (e.g., OPENAI, OPENAI_COMPATIBLE, COHERE, etc.)",
              required = false,
              type = String.class,
              example = "OPENAI"),
          @io.javalin.openapi.OpenApiParam(
              name = "label.*",
              description = "Filter by label value. Multiple label filters can be specified (e.g., ?label.environment=production&label.team=nlp)",
              required = false,
              type = String.class,
              example = "?label.environment=production&label.team=nlp")
      },
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully retrieved embedders",
              content = @OpenApiContent(from = ListEmbeddersResponse.class)),
          @OpenApiResponse(
              status = "400",
              description = "Invalid request - invalid filter parameters or pagination token"),
          @OpenApiResponse(
              status = "401",
              description = "Unauthorized - invalid or missing API key"),
          @OpenApiResponse(
              status = "403",
              description = "Forbidden - insufficient permissions to list embedders")
      })
  public void handleListEmbedders(Context ctx) {
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST ListEmbedders request with API key: {}", apiKey);

    // Extract query parameters for labels
    Map<String, String> labelSelectors = new java.util.HashMap<>();
    ctx.queryParamMap().forEach((key, values) -> {
      if (key.startsWith("label.") && !values.isEmpty()) {
        String labelKey = key.substring("label.".length());
        String value = values.getFirst();
        if (value != null) {
          labelSelectors.put(labelKey, value);
        }
      }
    });
    
    // Extract other query parameters
    String ownerId = ctx.queryParam("owner_id");
    String providerTypeStr = ctx.queryParam("provider_type");
    
    // Convert provider_type string to enum if provided
    ProviderType providerType = null;
    if (!Strings.isNullOrEmpty(providerTypeStr)) {
      try {
        providerType = ProviderType.valueOf(providerTypeStr.toUpperCase());
      } catch (IllegalArgumentException e) {
        Logger.warn("Invalid provider_type parameter: {}", providerTypeStr);
        setError(ctx, 400, "Invalid provider type: " + providerTypeStr);
        return;
      }
    }

    // Create the DTO from query parameters
    ListEmbeddersRequest requestDto = new ListEmbeddersRequest(
        ownerId, providerType, labelSelectors);

    // Convert the DTO to gRPC request
    EmbedderOuterClass.ListEmbeddersRequest.Builder requestBuilder = 
        EmbedderOuterClass.ListEmbeddersRequest.newBuilder();
    
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
    
    // Set provider type if provided
    if (requestDto.providerType() != null) {
      requestBuilder.setProviderType(
          switch(requestDto.providerType()) {
            case TEI -> EmbedderOuterClass.ProviderType.PROVIDER_TYPE_TEI;
            case OPENAI -> EmbedderOuterClass.ProviderType.PROVIDER_TYPE_OPENAI;
            case VLLM -> EmbedderOuterClass.ProviderType.PROVIDER_TYPE_VLLM;
          }
      );
    }
    
    // Add label selectors
    if (requestDto.labelSelectors() != null && !requestDto.labelSelectors().isEmpty()) {
      requestDto.labelSelectors().forEach(requestBuilder::putLabelSelectors);
    }
    
    // Call the gRPC service
    EmbedderOuterClass.ListEmbeddersResponse response
        = embedderService.listEmbedders(requestBuilder.build());
    
    // Convert the response to a list of EmbedderResponse DTOs
    List<EmbedderResponse> embedders = response.getEmbeddersList().stream()
        .map(embedder -> {
          Map<String, Object> embedderMap = RestMapper.toJsonMap(embedder);
          return createEmbedderResponseFromMap(embedderMap);
        })
        .collect(Collectors.toList());
    
    // Create and return the ListEmbeddersResponse DTO
    ListEmbeddersResponse responseDto = new ListEmbeddersResponse(embedders);
    
    // Return the response
    ctx.json(responseDto);
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
      description = "Updates an existing embedder configuration with new values for the specified fields.",
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
                      from = UpdateEmbedderRequest.class,
                      example =
                          """
              {
                "displayName": "Updated Embedding Model",
                "description": "Updated description for this embedder",
                "replaceLabels": {
                  "environment": "staging",
                  "team": "ml-ops"
                }
              }
              """)),
      responses = {
          @OpenApiResponse(
              status = "200",
              description = "Successfully updated embedder",
              content = @OpenApiContent(from = EmbedderResponse.class)),
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
  public void handleUpdateEmbedder(Context ctx) {
    String embedderIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST UpdateEmbedder request for ID: {} with API key: {}", embedderIdHex, apiKey);

    // Parse the request body into our DTO
    UpdateEmbedderRequest requestDto = ctx.bodyAsClass(UpdateEmbedderRequest.class);
    
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
    EmbedderOuterClass.UpdateEmbedderRequest.Builder requestBuilder =
        EmbedderOuterClass.UpdateEmbedderRequest.newBuilder()
            .setEmbedderId(embedderIdOr.getValue());
    
    // Set fields if provided in the request
    // All fields in UpdateEmbedderRequest are optional, so we should allow setting them to empty strings
    // This enables clearing field values through updates
    
    if (requestDto.displayName() != null) {  // Can be set to empty string
      requestBuilder.setDisplayName(requestDto.displayName());
    }
    
    if (requestDto.description() != null) {  // Can be set to empty string
      requestBuilder.setDescription(requestDto.description());
    }
    
    if (requestDto.endpointUrl() != null) {  // Can be set to empty string
      requestBuilder.setEndpointUrl(requestDto.endpointUrl());
    }
    
    if (requestDto.apiPath() != null) {  // Can be set to empty string
      requestBuilder.setApiPath(requestDto.apiPath());
    }
    
    if (requestDto.modelIdentifier() != null) {  // Can be set to empty string
      requestBuilder.setModelIdentifier(requestDto.modelIdentifier());
    }
    
    if (requestDto.dimensionality() != null) {
      requestBuilder.setDimensionality(requestDto.dimensionality());
    }
    
    if (requestDto.maxSequenceLength() != null) {
      requestBuilder.setMaxSequenceLength(requestDto.maxSequenceLength());
    }
    
    if (requestDto.credentials() != null) {  // Can be set to empty string
      requestBuilder.setCredentials(requestDto.credentials());
    }
    
    if (requestDto.version() != null) {  // Can be set to empty string
      requestBuilder.setVersion(requestDto.version());
    }
    
    if (requestDto.monitoringEndpoint() != null) {  // Can be set to empty string
      requestBuilder.setMonitoringEndpoint(requestDto.monitoringEndpoint());
    }
    
    // Handle label update strategies
    if (requestDto.replaceLabels() != null) {
      Common.StringMap.Builder labelsBuilder = Common.StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.replaceLabels());
      requestBuilder.setReplaceLabels(labelsBuilder.build());
    } else if (requestDto.mergeLabels() != null) {
      Common.StringMap.Builder labelsBuilder = Common.StringMap.newBuilder();
      labelsBuilder.putAllLabels(requestDto.mergeLabels());
      requestBuilder.setMergeLabels(labelsBuilder.build());
    }
    
    // Handle supported modalities if present
    if (requestDto.supportedModalities() != null) {
      for (Modality modality : requestDto.supportedModalities()) {
        requestBuilder.addSupportedModalities(
            switch(modality) {
              case AUDIO -> EmbedderOuterClass.Modality.MODALITY_AUDIO;
              case IMAGE -> EmbedderOuterClass.Modality.MODALITY_IMAGE;
              case TEXT -> EmbedderOuterClass.Modality.MODALITY_TEXT;
              case VIDEO -> EmbedderOuterClass.Modality.MODALITY_VIDEO;
            }
        );
      }
    }
    
    // Call the gRPC service
    Embedder response = embedderService.updateEmbedder(requestBuilder.build());
    
    // Convert the response to our DTO
    Map<String, Object> responseMap = RestMapper.toJsonMap(response);
    EmbedderResponse responseDto = createEmbedderResponseFromMap(responseMap);
    
    // Return the response
    ctx.json(responseDto);
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
  public void handleDeleteEmbedder(Context ctx) {
    String embedderIdHex = ctx.pathParam("id");
    String apiKey = ctx.header("x-api-key");
    Logger.info("REST DeleteEmbedder request for ID: {} with API key: {}", embedderIdHex, apiKey);

    // Create our DTO from the path parameter
    DeleteEmbedderRequest requestDto = new DeleteEmbedderRequest(embedderIdHex);
    
    // Validate and convert the embedder ID
    StatusOr<ByteString> embedderIdOr = convertHexToUuidBytes(requestDto.embedderId());
    if (embedderIdOr.isNotOk()) {
      setError(ctx, 400, "Invalid embedder ID format");
      return;
    }
    
    // Build the gRPC request
    EmbedderOuterClass.DeleteEmbedderRequest request =
        EmbedderOuterClass.DeleteEmbedderRequest.newBuilder()
            .setEmbedderId(embedderIdOr.getValue())
            .build();
    
    // Call the gRPC service
    embedderService.deleteEmbedder(request);
    
    // Return 204 No Content on successful deletion
    ctx.status(204);
  }
  
  /**
   * Helper method to create an EmbedderResponse from a Map.
   * 
   * @param embedderMap The map containing embedder data from gRPC
   * @return A new EmbedderResponse DTO
   */
  private EmbedderResponse createEmbedderResponseFromMap(Map<String, Object> embedderMap) {
    // Convert supported modalities list if present
    List<Modality> modalities = null;
    if (embedderMap.containsKey("supported_modalities")) {
      @SuppressWarnings("unchecked")
      List<String> modalityStrings = (List<String>) embedderMap.get("supported_modalities");
      if (modalityStrings != null && !modalityStrings.isEmpty()) {
        modalities = modalityStrings.stream()
            .map(m -> Modality.valueOf(m))
            .collect(Collectors.toList());
      }
    }
    
    // Handle provider_type conversion safely with a default if missing
    ProviderType providerType = ProviderType.OPENAI; // Default value
    if (embedderMap.containsKey("provider_type") && embedderMap.get("provider_type") != null) {
        try {
            providerType = ProviderType.valueOf((String) embedderMap.get("provider_type"));
        } catch (IllegalArgumentException e) {
            // Fallback to default if enum value is invalid
            Logger.warn("Invalid provider_type in response: {}", embedderMap.get("provider_type"));
        }
    }
    
    // Create the response DTO
    return new EmbedderResponse(
        (String) embedderMap.get("embedder_id"),
        (String) embedderMap.get("display_name"),
        (String) embedderMap.get("description"),
        providerType,
        (String) embedderMap.get("endpoint_url"),
        (String) embedderMap.get("api_path"),
        (String) embedderMap.get("model_identifier"),
        (Integer) embedderMap.get("dimensionality"),
        (Integer) embedderMap.get("max_sequence_length"),
        modalities,
        (Map<String, String>) embedderMap.get("labels"),
        (String) embedderMap.get("version"),
        (String) embedderMap.get("monitoring_endpoint"),
        (String) embedderMap.get("owner_id"),
        (Long) embedderMap.get("created_at"),
        (Long) embedderMap.get("updated_at"),
        (String) embedderMap.get("created_by_id"),
        (String) embedderMap.get("updated_by_id")
    );
  }
}