package com.goodmem;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.EmbedderModality;
import com.goodmem.db.EmbedderProviderType;
import com.goodmem.db.util.UuidUtil;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.Permission;
import com.goodmem.security.User;
import com.goodmem.util.EnumConverters;
import com.goodmem.util.LabelUtils;
import com.google.protobuf.Empty;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.EmbedderOuterClass.CreateEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.DeleteEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.Embedder;
import goodmem.v1.EmbedderOuterClass.GetEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.ListEmbeddersRequest;
import goodmem.v1.EmbedderOuterClass.ListEmbeddersResponse;
import goodmem.v1.EmbedderOuterClass.Modality;
import goodmem.v1.EmbedderOuterClass.UpdateEmbedderRequest;
import goodmem.v1.EmbedderServiceGrpc.EmbedderServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.tinylog.Logger;

/**
 * Implementation of the EmbedderService which provides methods for managing vectorization
 * components (embedders) in the system.
 *
 * <p>This service allows creating, retrieving, updating, listing, and deleting embedder resources. 
 * Each operation enforces authentication and permission checks based on the user context.
 */
public class EmbedderServiceImpl extends EmbedderServiceImplBase {
  
  private final Config config;

  /**
   * Configuration for the EmbedderServiceImpl.
   * 
   * @param dataSource The HikariDataSource for database connections
   */
  public record Config(HikariDataSource dataSource) {}
  
  /**
   * Constructs a new EmbedderServiceImpl with the provided configuration.
   * 
   * @param config The service configuration
   */
  public EmbedderServiceImpl(Config config) {
    this.config = config;
  }
  
  /**
   * Creates a new Embedder with the owner and creator derived from authentication context.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Check permissions (CREATE_EMBEDDER_OWN, CREATE_EMBEDDER_ANY, or MANAGE_EMBEDDER)
   * 3. Validate request fields (name, provider_type, modality, etc.)
   * 4. Determine owner (from request if specified, otherwise from authenticated user)
   * 5. Persist the new embedder in the database
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - PERMISSION_DENIED: User lacks necessary permissions to create embedders
   * - INVALID_ARGUMENT: Required fields missing or invalid
   * - ALREADY_EXISTS: An embedder with the same connection details already exists
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void createEmbedder(CreateEmbedderRequest request, StreamObserver<Embedder> responseObserver) {
    Logger.info("Creating embedder: {}", request.getDisplayName());

    // Get the authenticated user from the Context
    User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }

    // Check permissions based on whether owner_id is specified
    boolean ownerIdProvided = request.hasOwnerId();
    boolean hasManagePermission = authenticatedUser.hasPermission(Permission.MANAGE_EMBEDDER);
    boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.CREATE_EMBEDDER_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.CREATE_EMBEDDER_OWN);
    
    // If trying to create an embedder for another user, must have CREATE_EMBEDDER_ANY permission
    UUID ownerId;
    if (ownerIdProvided) {
      StatusOr<UUID> ownerIdOr = UuidUtil.fromProtoBytes(request.getOwnerId());
      
      if (ownerIdOr.isNotOk()) {
        Logger.error("Invalid owner ID format: {}", ownerIdOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INVALID_ARGUMENT
                .withDescription("Invalid owner ID format")
                .asRuntimeException());
        return;
      }
      
      ownerId = ownerIdOr.getValue();
      
      // If owner_id specified and not equal to authenticated user, require CREATE_EMBEDDER_ANY permission
      if (!ownerId.equals(authenticatedUser.getId()) && !hasManagePermission && !hasAnyPermission) {
        Logger.error("User lacks permission to create embedders for other users");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
    } else {
      // No owner_id provided, use authenticated user's ID
      ownerId = authenticatedUser.getId();
      
      // Must have at least CREATE_EMBEDDER_OWN permission
      if (!hasManagePermission && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to create embedders");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
    }

    // Validate required fields
    if (request.getDisplayName() == null || request.getDisplayName().trim().isEmpty()) {
      Logger.error("Embedder display name is required");
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Display name is required")
              .asRuntimeException());
      return;
    }
    
    if (request.getProviderType() == goodmem.v1.EmbedderOuterClass.ProviderType.PROVIDER_TYPE_UNSPECIFIED) {
      Logger.error("Valid provider type is required");
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Valid provider type is required")
              .asRuntimeException());
      return;
    }
    
    if (request.getEndpointUrl() == null || request.getEndpointUrl().trim().isEmpty()) {
      Logger.error("Endpoint URL is required");
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Endpoint URL is required")
              .asRuntimeException());
      return;
    }
    
    if (request.getModelIdentifier() == null || request.getModelIdentifier().trim().isEmpty()) {
      Logger.error("Model identifier is required");
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Model identifier is required")
              .asRuntimeException());
      return;
    }
    
    if (request.getDimensionality() <= 0) {
      Logger.error("Dimensionality must be a positive integer");
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Dimensionality must be a positive integer")
              .asRuntimeException());
      return;
    }
    
    if (request.getCredentials() == null || request.getCredentials().trim().isEmpty()) {
      Logger.error("Credentials are required");
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Credentials are required")
              .asRuntimeException());
      return;
    }

    // Creator is always the authenticated user
    UUID creatorId = authenticatedUser.getId();

    // Convert ProviderType from proto to database enum
    EmbedderProviderType providerType = EnumConverters.fromProtoProviderType(request.getProviderType());
    
    // Convert Modalities from proto to database enum
    List<EmbedderModality> supportedModalities = new ArrayList<>();
    for (Modality modality : request.getSupportedModalitiesList()) {
      supportedModalities.add(EnumConverters.fromProtoModality(modality));
    }
    
    // Use default API path if not provided
    String apiPath = request.getApiPath();
    if (apiPath == null || apiPath.isEmpty()) {
      apiPath = "/v1/embeddings"; // Default API path for embedding services
    }

    try (Connection connection = config.dataSource().getConnection()) {
      // Check if an embedder with the same connection details already exists
      StatusOr<java.util.Optional<com.goodmem.db.Embedder>> existingEmbedderOr = 
          com.goodmem.db.Embedders.loadByConnectionDetails(
              connection, 
              request.getEndpointUrl(), 
              apiPath, 
              request.getModelIdentifier());
          
      if (existingEmbedderOr.isNotOk()) {
        Logger.error("Error checking for existing embedder: {}", existingEmbedderOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      if (existingEmbedderOr.getValue().isPresent()) {
        Logger.error("Embedder with same connection details already exists");
        responseObserver.onError(
            io.grpc.Status.ALREADY_EXISTS
                .withDescription("An embedder with these connection details already exists")
                .asRuntimeException());
        return;
      }
      
      // Create a new embedder record
      UUID embedderId = UUID.randomUUID();
      Instant now = Instant.now();
      
      com.goodmem.db.Embedder embedderRecord = new com.goodmem.db.Embedder(
          embedderId,
          request.getDisplayName(),
          request.getDescription(),
          providerType,
          request.getEndpointUrl(),
          apiPath,
          request.getModelIdentifier(),
          request.getDimensionality(),
          request.hasMaxSequenceLength() ? request.getMaxSequenceLength() : null,
          supportedModalities,
          request.getCredentials(),
          request.getLabelsMap(),
          request.getVersion(),
          request.getMonitoringEndpoint(),
          ownerId,
          now,
          now,
          creatorId,
          creatorId
      );
      
      // Persist the embedder record in the database
      StatusOr<Integer> saveResult = com.goodmem.db.Embedders.save(connection, embedderRecord);
      
      if (saveResult.isNotOk()) {
        Logger.error("Failed to save embedder: {}", saveResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Convert the database record to a proto message and return it
      Embedder protoEmbedder = embedderRecord.toProto();
      responseObserver.onNext(protoEmbedder);
      responseObserver.onCompleted();
      Logger.info("Embedder created successfully: {}", embedderId);
      
    } catch (SQLException e) {
      Logger.error(e, "Database error during embedder creation: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during embedder creation: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Retrieves details of a specific Embedder by ID.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Validate the embedder ID format
   * 3. Load the embedder to check ownership
   * 4. Check permissions (READ_EMBEDDER_OWN or READ_EMBEDDER_ANY based on ownership)
   * 5. Return the embedder details
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - INVALID_ARGUMENT: Invalid embedder ID format
   * - NOT_FOUND: Embedder with the given ID does not exist
   * - PERMISSION_DENIED: User lacks necessary permissions to view the embedder
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void getEmbedder(GetEmbedderRequest request, StreamObserver<Embedder> responseObserver) {
    // Get the authenticated user from the Context
    User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }

    // Validate and convert embedder ID
    StatusOr<UUID> embedderIdOr = UuidUtil.fromProtoBytes(request.getEmbedderId());
    
    if (embedderIdOr.isNotOk()) {
      Logger.error("Invalid embedder ID format: {}", embedderIdOr.getStatus().getMessage());
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid embedder ID format")
              .asRuntimeException());
      return;
    }
    
    UUID embedderId = embedderIdOr.getValue();
    Logger.info("Getting embedder: {}", embedderId);

    try (Connection connection = config.dataSource().getConnection()) {
      // Load the embedder to check ownership
      StatusOr<java.util.Optional<com.goodmem.db.Embedder>> embedderOr = 
          com.goodmem.db.Embedders.loadById(connection, embedderId);
          
      if (embedderOr.isNotOk()) {
        Logger.error("Error loading embedder: {}", embedderOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Check if embedder exists
      if (embedderOr.getValue().isEmpty()) {
        Logger.error("Embedder not found: {}", embedderId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("Embedder not found")
                .asRuntimeException());
        return;
      }
      
      com.goodmem.db.Embedder embedder = embedderOr.getValue().get();
      
      // Check permissions based on ownership
      boolean isOwner = embedder.ownerId().equals(authenticatedUser.getId());
      boolean hasManagePermission = authenticatedUser.hasPermission(Permission.MANAGE_EMBEDDER);
      boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.READ_EMBEDDER_ANY);
      boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.READ_EMBEDDER_OWN);
      
      // If user is not the owner, they must have READ_EMBEDDER_ANY permission
      if (!isOwner && !hasManagePermission && !hasAnyPermission) {
        Logger.error("User lacks permission to view embedders owned by others");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // If user is the owner, they must have at least READ_EMBEDDER_OWN permission
      if (isOwner && !hasManagePermission && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to view their own embedders");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // Convert the database record to a proto message and return it
      Embedder protoEmbedder = embedder.toProto();
      responseObserver.onNext(protoEmbedder);
      responseObserver.onCompleted();
      
    } catch (SQLException e) {
      Logger.error(e, "Database error during embedder retrieval: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during embedder retrieval: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Lists embedders based on filters.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Check permissions (LIST_EMBEDDER_OWN or LIST_EMBEDDER_ANY)
   * 3. Apply filters (owner_id, provider_type, label_selectors)
   * 4. Query the database with filters
   * 5. Return the list of embedders
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - PERMISSION_DENIED: User lacks necessary permissions to list embedders
   * - INVALID_ARGUMENT: Invalid filters
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void listEmbedders(
      ListEmbeddersRequest request, StreamObserver<ListEmbeddersResponse> responseObserver) {
    
    // Get the authenticated user from the Context
    User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }
    
    Logger.info("Listing embedders with label selectors: {}", request.getLabelSelectorsMap());
    
    // Check permissions
    boolean hasManagePermission = authenticatedUser.hasPermission(Permission.MANAGE_EMBEDDER);
    boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.LIST_EMBEDDER_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.LIST_EMBEDDER_OWN);
    
    // User must have at least LIST_EMBEDDER_OWN permission
    if (!hasManagePermission && !hasAnyPermission && !hasOwnPermission) {
      Logger.error("User lacks necessary permissions to list embedders");
      responseObserver.onError(
          io.grpc.Status.PERMISSION_DENIED
              .withDescription("Permission denied")
              .asRuntimeException());
      return;
    }
    
    try {
      // Process filter parameters from the request
      UUID requestedOwnerId = null;
      EmbedderProviderType providerType = null;
      Map<String, String> labelSelectors = null;
      
      // Extract owner_id filter if provided
      if (request.hasOwnerId()) {
        StatusOr<UUID> ownerIdOr = UuidUtil.fromProtoBytes(request.getOwnerId());
        
        if (ownerIdOr.isNotOk()) {
          Logger.error("Invalid owner ID format: {}", ownerIdOr.getStatus().getMessage());
          responseObserver.onError(
              io.grpc.Status.INVALID_ARGUMENT
                  .withDescription("Invalid owner ID format")
                  .asRuntimeException());
          return;
        }
        
        requestedOwnerId = ownerIdOr.getValue();
      }
      
      // Extract provider_type filter if provided
      if (request.hasProviderType() && request.getProviderType() != goodmem.v1.EmbedderOuterClass.ProviderType.PROVIDER_TYPE_UNSPECIFIED) {
        providerType = EnumConverters.fromProtoProviderType(request.getProviderType());
      }
      
      // Extract label_selectors filter if provided
      if (request.getLabelSelectorsCount() > 0) {
        labelSelectors = request.getLabelSelectorsMap();
      }
      
      // Determine the owner ID to filter by based on permissions
      UUID ownerIdFilter = null;
      
      if (hasManagePermission || hasAnyPermission) {
        // With LIST_EMBEDDER_ANY permission:
        // - If an owner ID was requested, use it as a filter (show only that owner's embedders)
        // - If no owner ID was requested, show all embedders (no owner filter)
        ownerIdFilter = requestedOwnerId;
      } else if (hasOwnPermission) {
        // With only LIST_EMBEDDER_OWN permission:
        // - If an owner ID was requested and it's not the authenticated user, reject with PERMISSION_DENIED
        // - Otherwise, filter to only show the authenticated user's embedders
        if (requestedOwnerId != null && !requestedOwnerId.equals(authenticatedUser.getId())) {
          Logger.error("User lacks permission to list embedders owned by others");
          responseObserver.onError(
              io.grpc.Status.PERMISSION_DENIED
                  .withDescription("Permission denied")
                  .asRuntimeException());
          return;
        }
        
        // Always filter by authenticated user ID when only having LIST_EMBEDDER_OWN
        ownerIdFilter = authenticatedUser.getId();
      }

      // Query the database with the provided filters
      try (Connection connection = config.dataSource().getConnection()) {
        StatusOr<List<com.goodmem.db.Embedder>> embeddersOr;
        
        if (ownerIdFilter != null) {
          // Filter by owner ID
          embeddersOr = com.goodmem.db.Embedders.loadByOwnerId(connection, ownerIdFilter);
        } else if (providerType != null) {
          // Filter by provider type
          embeddersOr = com.goodmem.db.Embedders.loadByProviderType(connection, providerType);
        } else {
          // No filters, load all embedders
          embeddersOr = com.goodmem.db.Embedders.loadAll(connection);
        }
        
        if (embeddersOr.isNotOk()) {
          Logger.error("Database query error: {}", embeddersOr.getStatus().getMessage());
          responseObserver.onError(
              io.grpc.Status.INTERNAL
                  .withDescription("Unexpected error while processing request.")
                  .asRuntimeException());
          return;
        }
        
        // Apply label selector filtering in memory (since no database method for this yet)
        List<com.goodmem.db.Embedder> filteredEmbedders = embeddersOr.getValue();
        if (labelSelectors != null && !labelSelectors.isEmpty()) {
          final Map<String, String> finalLabelSelectors = labelSelectors;
          filteredEmbedders = filteredEmbedders.stream()
              .filter(embedder -> LabelUtils.matchesLabelSelectors(embedder.labels(), finalLabelSelectors))
              .toList();
        }
        
        // Create the response builder
        ListEmbeddersResponse.Builder responseBuilder = ListEmbeddersResponse.newBuilder();
        
        // Add the embedders to the response
        for (com.goodmem.db.Embedder embedder : filteredEmbedders) {
          responseBuilder.addEmbedders(embedder.toProto());
        }
        
        // Send the response
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
        
      } catch (SQLException e) {
        Logger.error(e, "Database error during embedder listing: {}", e.getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
      }
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during embedder listing: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Updates mutable properties of an Embedder.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Validate the embedder ID format and update request fields
   * 3. Load the embedder to check ownership
   * 4. Check permissions (UPDATE_EMBEDDER_OWN or UPDATE_EMBEDDER_ANY based on ownership)
   * 5. Apply the updates to the embedder
   * 6. Save the updated embedder
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - INVALID_ARGUMENT: Invalid embedder ID format or invalid update parameters
   * - NOT_FOUND: Embedder with the given ID does not exist
   * - PERMISSION_DENIED: User lacks necessary permissions to update the embedder
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void updateEmbedder(UpdateEmbedderRequest request, StreamObserver<Embedder> responseObserver) {
    // Get the authenticated user from the Context
    User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }

    // Check which label update strategy is being used (oneof ensures only one can be set)
    boolean hasReplaceLabels = request.getLabelUpdateStrategyCase() == UpdateEmbedderRequest.LabelUpdateStrategyCase.REPLACE_LABELS;
    boolean hasMergeLabels = request.getLabelUpdateStrategyCase() == UpdateEmbedderRequest.LabelUpdateStrategyCase.MERGE_LABELS;
    
    // Validate and convert embedder ID
    StatusOr<UUID> embedderIdOr = UuidUtil.fromProtoBytes(request.getEmbedderId());
    
    if (embedderIdOr.isNotOk()) {
      Logger.error("Invalid embedder ID format: {}", embedderIdOr.getStatus().getMessage());
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid embedder ID format")
              .asRuntimeException());
      return;
    }
    
    UUID embedderId = embedderIdOr.getValue();
    Logger.info("Updating embedder: {}", embedderId);

    try (Connection connection = config.dataSource().getConnection()) {
      // Load the embedder to check ownership
      StatusOr<java.util.Optional<com.goodmem.db.Embedder>> embedderOr = 
          com.goodmem.db.Embedders.loadById(connection, embedderId);
          
      if (embedderOr.isNotOk()) {
        Logger.error("Error loading embedder: {}", embedderOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Check if embedder exists
      if (embedderOr.getValue().isEmpty()) {
        Logger.error("Embedder not found: {}", embedderId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("Embedder not found")
                .asRuntimeException());
        return;
      }
      
      com.goodmem.db.Embedder existingEmbedder = embedderOr.getValue().get();
      
      // Check permissions based on ownership
      boolean isOwner = existingEmbedder.ownerId().equals(authenticatedUser.getId());
      boolean hasManagePermission = authenticatedUser.hasPermission(Permission.MANAGE_EMBEDDER);
      boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.UPDATE_EMBEDDER_ANY);
      boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.UPDATE_EMBEDDER_OWN);
      
      // If user is not the owner, they must have UPDATE_EMBEDDER_ANY permission
      if (!isOwner && !hasManagePermission && !hasAnyPermission) {
        Logger.error("User lacks permission to update embedders owned by others");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // If user is the owner, they must have at least UPDATE_EMBEDDER_OWN permission
      if (isOwner && !hasManagePermission && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to update their own embedders");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // Process label updates
      Map<String, String> newLabels;
      if (hasReplaceLabels) {
        // Replace all existing labels with the ones provided
        newLabels = new HashMap<>(request.getReplaceLabels().getLabelsMap());
      } else if (hasMergeLabels) {
        // Merge existing labels with new ones
        newLabels = new HashMap<>(existingEmbedder.labels());
        newLabels.putAll(request.getMergeLabels().getLabelsMap());
      } else {
        // No label changes, keep existing labels
        newLabels = existingEmbedder.labels();
      }
      
      // Process individual field updates
      String newDisplayName = request.hasDisplayName() ? request.getDisplayName() : existingEmbedder.displayName();
      String newDescription = request.hasDescription() ? request.getDescription() : existingEmbedder.description();
      String newEndpointUrl = request.hasEndpointUrl() ? request.getEndpointUrl() : existingEmbedder.endpointUrl();
      String newApiPath = request.hasApiPath() ? request.getApiPath() : existingEmbedder.apiPath();
      String newModelIdentifier = request.hasModelIdentifier() ? request.getModelIdentifier() : existingEmbedder.modelIdentifier();
      int newDimensionality = request.hasDimensionality() ? request.getDimensionality() : existingEmbedder.dimensionality();
      
      // Handle nullable max_sequence_length
      Integer newMaxSequenceLength;
      if (request.hasMaxSequenceLength()) {
        newMaxSequenceLength = request.getMaxSequenceLength();
      } else {
        newMaxSequenceLength = existingEmbedder.maxSequenceLength();
      }
      
      // Process supported_modalities updates
      List<EmbedderModality> newModalities;
      if (request.getSupportedModalitiesCount() > 0) {
        newModalities = new ArrayList<>();
        for (Modality modality : request.getSupportedModalitiesList()) {
          newModalities.add(EnumConverters.fromProtoModality(modality));
        }
      } else {
        newModalities = existingEmbedder.supportedModalities();
      }
      
      // Process credentials update
      String newCredentials = request.hasCredentials() ? request.getCredentials() : existingEmbedder.credentials();
      
      // Process version update
      String newVersion = request.hasVersion() ? request.getVersion() : existingEmbedder.version();
      
      // Process monitoring_endpoint update
      String newMonitoringEndpoint = request.hasMonitoringEndpoint() ? 
          request.getMonitoringEndpoint() : existingEmbedder.monitoringEndpoint();
      
      // Create updated embedder record
      Instant now = Instant.now();
      
      com.goodmem.db.Embedder updatedEmbedder = new com.goodmem.db.Embedder(
          existingEmbedder.embedderId(),
          newDisplayName,
          newDescription,
          existingEmbedder.providerType(), // provider_type is immutable
          newEndpointUrl,
          newApiPath,
          newModelIdentifier,
          newDimensionality,
          newMaxSequenceLength,
          newModalities,
          newCredentials,
          newLabels,
          newVersion,
          newMonitoringEndpoint,
          existingEmbedder.ownerId(), // owner_id is immutable
          existingEmbedder.createdAt(),
          now, // updated now
          existingEmbedder.createdById(),
          authenticatedUser.getId() // updater is authenticated user
      );
      
      // Persist the updated embedder record
      StatusOr<Integer> saveResult = com.goodmem.db.Embedders.save(connection, updatedEmbedder);
      
      if (saveResult.isNotOk()) {
        Logger.error("Failed to save updated embedder: {}", saveResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Convert the database record to a proto message and return it
      Embedder protoEmbedder = updatedEmbedder.toProto();
      responseObserver.onNext(protoEmbedder);
      responseObserver.onCompleted();
      Logger.info("Embedder updated successfully: {}", embedderId);
      
    } catch (SQLException e) {
      Logger.error(e, "Database error during embedder update: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during embedder update: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Deletes an Embedder by ID.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Validate the embedder ID format
   * 3. Load the embedder to check ownership
   * 4. Check permissions (DELETE_EMBEDDER_OWN or DELETE_EMBEDDER_ANY based on ownership)
   * 5. Delete the embedder from the database
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - INVALID_ARGUMENT: Invalid embedder ID format
   * - NOT_FOUND: Embedder with the given ID does not exist
   * - PERMISSION_DENIED: User lacks necessary permissions to delete the embedder
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void deleteEmbedder(DeleteEmbedderRequest request, StreamObserver<Empty> responseObserver) {
    // Get the authenticated user from the Context
    User authenticatedUser = AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }

    // Validate and convert embedder ID
    StatusOr<UUID> embedderIdOr = UuidUtil.fromProtoBytes(request.getEmbedderId());
    
    if (embedderIdOr.isNotOk()) {
      Logger.error("Invalid embedder ID format: {}", embedderIdOr.getStatus().getMessage());
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid embedder ID format")
              .asRuntimeException());
      return;
    }
    
    UUID embedderId = embedderIdOr.getValue();
    Logger.info("Deleting embedder: {}", embedderId);

    try (Connection connection = config.dataSource().getConnection()) {
      // Load the embedder to check ownership
      StatusOr<java.util.Optional<com.goodmem.db.Embedder>> embedderOr = 
          com.goodmem.db.Embedders.loadById(connection, embedderId);
          
      if (embedderOr.isNotOk()) {
        Logger.error("Error loading embedder: {}", embedderOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Check if embedder exists
      if (embedderOr.getValue().isEmpty()) {
        Logger.error("Embedder not found: {}", embedderId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("Embedder not found")
                .asRuntimeException());
        return;
      }
      
      com.goodmem.db.Embedder embedder = embedderOr.getValue().get();
      
      // Check permissions based on ownership
      boolean isOwner = embedder.ownerId().equals(authenticatedUser.getId());
      boolean hasManagePermission = authenticatedUser.hasPermission(Permission.MANAGE_EMBEDDER);
      boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.DELETE_EMBEDDER_ANY);
      boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.DELETE_EMBEDDER_OWN);
      
      // If user is not the owner, they must have DELETE_EMBEDDER_ANY permission
      if (!isOwner && !hasManagePermission && !hasAnyPermission) {
        Logger.error("User lacks permission to delete embedders owned by others");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // If user is the owner, they must have at least DELETE_EMBEDDER_OWN permission
      if (isOwner && !hasManagePermission && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to delete their own embedders");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // Delete the embedder from the database
      StatusOr<Integer> deleteResult = com.goodmem.db.Embedders.delete(connection, embedderId);
      
      if (deleteResult.isNotOk()) {
        Logger.error("Failed to delete embedder: {}", deleteResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Ensure the embedder was actually deleted
      if (deleteResult.getValue() == 0) {
        Logger.error("Embedder not found during delete operation: {}", embedderId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("Embedder not found")
                .asRuntimeException());
        return;
      }
      
      // Success - return empty response
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
      Logger.info("Embedder deleted successfully: {}", embedderId);
      
    } catch (SQLException e) {
      Logger.error(e, "Database error during embedder deletion: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during embedder deletion: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }
}