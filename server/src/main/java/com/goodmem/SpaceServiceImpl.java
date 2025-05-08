package com.goodmem;

import com.goodmem.db.util.UuidUtil;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.Permission;
import com.goodmem.security.User;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.Common.SortOrder;
import goodmem.v1.SpaceOuterClass.CreateSpaceRequest;
import goodmem.v1.SpaceOuterClass.DeleteSpaceRequest;
import goodmem.v1.SpaceOuterClass.GetSpaceRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesNextPageToken;
import goodmem.v1.SpaceOuterClass.ListSpacesRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesResponse;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.SpaceOuterClass.UpdateSpaceRequest;
import goodmem.v1.SpaceServiceGrpc.SpaceServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.tinylog.Logger;

public class SpaceServiceImpl extends SpaceServiceImplBase {
  private static final int DEFAULT_MAX_RESULTS = 50;
  private static final int MAX_RESULTS_LIMIT = 1000;
  private static final int MIN_RESULTS_LIMIT = 1;
  
  private final Config config;

  /**
   * @param dataSource The database connection source
   * @param defaultEmbedderId The default embedder UUID to use if not specified
   */
  public record Config(HikariDataSource dataSource, UUID defaultEmbedderId) {}
  
  public SpaceServiceImpl(Config config) {
    this.config = config;
  }

  /**
   * Creates a new Space with the owner and creator derived from authentication context.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Check permissions (CREATE_SPACE_OWN or CREATE_SPACE_ANY based on context)
   * 3. Validate request fields
   * 4. Determine owner (from request if specified, otherwise from authenticated user)
   * 5. Check if a space with the same name already exists for this owner
   * 6. Persist the new space in the database
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - PERMISSION_DENIED: User lacks necessary permissions to create spaces
   * - INVALID_ARGUMENT: Required fields missing or invalid
   * - ALREADY_EXISTS: A space with the same name already exists for this owner
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void createSpace(CreateSpaceRequest request, StreamObserver<Space> responseObserver) {
    Logger.info("Creating space: {}", request.getName());

    // Get the authenticated user from the Context
    com.goodmem.security.User authenticatedUser = com.goodmem.security.AuthInterceptor.USER_CONTEXT_KEY.get();
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
    boolean hasAnyPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.CREATE_SPACE_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.CREATE_SPACE_OWN);
    
    // If trying to create a space for another user, must have CREATE_SPACE_ANY permission
    UUID ownerId;
    if (ownerIdProvided) {
      com.goodmem.common.status.StatusOr<UUID> ownerIdOr = 
          com.goodmem.db.util.UuidUtil.fromProtoBytes(request.getOwnerId());
      
      if (ownerIdOr.isNotOk()) {
        Logger.error("Invalid owner ID format: {}", ownerIdOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INVALID_ARGUMENT
                .withDescription("Invalid owner ID format")
                .asRuntimeException());
        return;
      }
      
      ownerId = ownerIdOr.getValue();
      
      // If owner_id specified and not equal to authenticated user, require CREATE_SPACE_ANY permission
      if (!ownerId.equals(authenticatedUser.getId()) && !hasAnyPermission) {
        Logger.error("User lacks permission to create spaces for other users");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
    } else {
      // No owner_id provided, use authenticated user's ID
      ownerId = authenticatedUser.getId();
      
      // Must have at least CREATE_SPACE_OWN permission
      if (!hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to create spaces");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
    }

    // Validate required fields
    if (request.getName() == null || request.getName().trim().isEmpty()) {
      Logger.error("Space name is required");
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Space name is required")
              .asRuntimeException());
      return;
    }

    // Creator is always the authenticated user
    UUID creatorId = authenticatedUser.getId();

    // Determine the embedder to use (default if not specified)
    UUID embedderId;
    if (request.hasEmbedderId()) {
      com.goodmem.common.status.StatusOr<UUID> embedderIdOr = 
          com.goodmem.db.util.UuidUtil.fromProtoBytes(request.getEmbedderId());
      
      if (embedderIdOr.isNotOk()) {
        Logger.error("Invalid embedder ID format: {}", embedderIdOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INVALID_ARGUMENT
                .withDescription("Invalid embedder ID format")
                .asRuntimeException());
        return;
      }
      
      embedderId = embedderIdOr.getValue();
    } else {
      embedderId = config.defaultEmbedderId();
    }

    try (java.sql.Connection connection = config.dataSource().getConnection()) {
      // Check if a space with the same name already exists for this owner
      com.goodmem.common.status.StatusOr<java.util.Optional<com.goodmem.db.Space>> existingSpaceOr = 
          com.goodmem.db.Spaces.loadByOwnerAndName(connection, ownerId, request.getName());
          
      if (existingSpaceOr.isNotOk()) {
        Logger.error("Error checking for existing space: {}", existingSpaceOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      if (existingSpaceOr.getValue().isPresent()) {
        Logger.error("Space with name '{}' already exists for this owner", request.getName());
        responseObserver.onError(
            io.grpc.Status.ALREADY_EXISTS
                .withDescription("A space with this name already exists")
                .asRuntimeException());
        return;
      }
      
      // Create a new space record
      UUID spaceId = UUID.randomUUID();
      java.time.Instant now = java.time.Instant.now();
      
      com.goodmem.db.Space spaceRecord = new com.goodmem.db.Space(
          spaceId,
          ownerId,
          request.getName(),
          request.getLabelsMap(),
          embedderId,
          request.getPublicRead(),
          now,
          now,
          creatorId,
          creatorId
      );
      
      // Persist the space record in the database
      com.goodmem.common.status.StatusOr<Integer> saveResult = com.goodmem.db.Spaces.save(connection, spaceRecord);
      
      if (saveResult.isNotOk()) {
        Logger.error("Failed to save space: {}", saveResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Convert the database record to a proto message and return it
      Space protoSpace = spaceRecord.toProto();
      responseObserver.onNext(protoSpace);
      responseObserver.onCompleted();
      
    } catch (java.sql.SQLException e) {
      Logger.error(e, "Database error during space creation: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during space creation: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  @Override
  public void getSpace(GetSpaceRequest request, StreamObserver<Space> responseObserver) {
    // TODO(claude): you need to fix this. This is a fake method.
    Logger.info("Getting space: {}", Uuids.bytesToHex(request.getSpaceId()));

    // TODO: Validate space ID
    // TODO: Retrieve from database
    // TODO: Check permissions

    // For now, return dummy data
    Space space =
        Space.newBuilder()
            .setSpaceId(request.getSpaceId())
            .setName("Example Space")
            .putLabels("user", "alice")
            .putLabels("bot", "copilot")
            .setEmbedderId(UuidUtil.toProtoBytes(UUID.randomUUID()))
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setOwnerId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setPublicRead(true)
            .build();

    responseObserver.onNext(space);
    responseObserver.onCompleted();
  }

  /**
   * Deletes a Space by ID.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Validate the space ID format
   * 3. Load the space to check ownership
   * 4. Check permissions (DELETE_SPACE_OWN or DELETE_SPACE_ANY based on ownership)
   * 5. Delete the space from the database
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - INVALID_ARGUMENT: Invalid space ID format
   * - NOT_FOUND: Space with the given ID does not exist
   * - PERMISSION_DENIED: User lacks necessary permissions to delete the space
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void deleteSpace(DeleteSpaceRequest request, StreamObserver<Empty> responseObserver) {
    // Get the authenticated user from the Context
    com.goodmem.security.User authenticatedUser = com.goodmem.security.AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }

    // Validate and convert space ID
    Logger.info("Deleting space: {}", Uuids.bytesToHex(request.getSpaceId()));
    
    com.goodmem.common.status.StatusOr<UUID> spaceIdOr = 
        com.goodmem.db.util.UuidUtil.fromProtoBytes(request.getSpaceId());
    
    if (spaceIdOr.isNotOk()) {
      Logger.error("Invalid space ID format: {}", spaceIdOr.getStatus().getMessage());
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid space ID format")
              .asRuntimeException());
      return;
    }
    
    UUID spaceId = spaceIdOr.getValue();

    try (java.sql.Connection connection = config.dataSource().getConnection()) {
      // Load the space to check ownership
      com.goodmem.common.status.StatusOr<java.util.Optional<com.goodmem.db.Space>> spaceOr = 
          com.goodmem.db.Spaces.loadById(connection, spaceId);
          
      if (spaceOr.isNotOk()) {
        Logger.error("Error loading space: {}", spaceOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Check if space exists
      if (spaceOr.getValue().isEmpty()) {
        Logger.error("Space not found: {}", spaceId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("Space not found")
                .asRuntimeException());
        return;
      }
      
      com.goodmem.db.Space space = spaceOr.getValue().get();
      
      // Check permissions based on ownership
      boolean isOwner = space.ownerId().equals(authenticatedUser.getId());
      boolean hasAnyPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.DELETE_SPACE_ANY);
      boolean hasOwnPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.DELETE_SPACE_OWN);
      
      // If user is not the owner, they must have DELETE_SPACE_ANY permission
      if (!isOwner && !hasAnyPermission) {
        Logger.error("User lacks permission to delete spaces owned by others");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // If user is the owner, they must have at least DELETE_SPACE_OWN permission
      if (isOwner && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to delete their own spaces");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // Delete the space from the database
      com.goodmem.common.status.StatusOr<Integer> deleteResult = com.goodmem.db.Spaces.delete(connection, spaceId);
      
      if (deleteResult.isNotOk()) {
        Logger.error("Failed to delete space: {}", deleteResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Ensure the space was actually deleted
      if (deleteResult.getValue() == 0) {
        Logger.error("Space not found during delete operation: {}", spaceId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("Space not found")
                .asRuntimeException());
        return;
      }
      
      // Success - return empty response
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
      Logger.info("Space deleted successfully: {}", spaceId);
      
    } catch (java.sql.SQLException e) {
      Logger.error(e, "Database error during space deletion: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during space deletion: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Lists spaces based on filters with pagination support.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Check permissions (LIST_SPACE_OWN or LIST_SPACE_ANY)
   * 3. Process pagination token if provided
   * 4. Validate and apply filters (owner_id, label_selectors, name_filter)
   * 5. Query the database with filter and pagination
   * 6. Generate next page token if needed
   * 7. Return the list of spaces and pagination token
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - PERMISSION_DENIED: User lacks necessary permissions to list spaces
   * - INVALID_ARGUMENT: Invalid filters or pagination token
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void listSpaces(
      ListSpacesRequest request, StreamObserver<ListSpacesResponse> responseObserver) {
    
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
    
    Logger.info("Listing spaces with label selectors: {}", request.getLabelSelectorsMap());
    
    // Check permissions
    boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.LIST_SPACE_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.LIST_SPACE_OWN);
    
    // User must have at least LIST_SPACE_OWN permission
    if (!hasAnyPermission && !hasOwnPermission) {
      Logger.error("User lacks necessary permissions to list spaces");
      responseObserver.onError(
          io.grpc.Status.PERMISSION_DENIED
              .withDescription("Permission denied")
              .asRuntimeException());
      return;
    }
    
    try {
      // Variables for the filter parameters
      UUID requestedOwnerId = null;
      Map<String, String> labelSelectors = null;
      String nameFilter = null;
      String sortBy = null;
      SortOrder sortOrder = null;
      int offset = 0;
      int maxResults = DEFAULT_MAX_RESULTS; // Default if not specified
      
      // Handle pagination token if provided
      if (request.hasNextToken()) {
        com.goodmem.common.status.StatusOr<ListSpacesNextPageToken> tokenOr = 
            decodeAndValidateNextPageToken(request.getNextToken(), authenticatedUser);
        
        if (tokenOr.isNotOk()) {
          Logger.error("Invalid pagination token: {}", tokenOr.getStatus().getMessage());
          responseObserver.onError(
              io.grpc.Status.INVALID_ARGUMENT
                  .withDescription("Invalid pagination token")
                  .asRuntimeException());
          return;
        }
        
        ListSpacesNextPageToken token = tokenOr.getValue();
        
        // Retrieve parameters from the token
        if (token.hasOwnerId()) {
          com.goodmem.common.status.StatusOr<UUID> ownerIdOr = 
              UuidUtil.fromProtoBytes(token.getOwnerId());
          
          if (ownerIdOr.isNotOk()) {
            Logger.error("Invalid owner ID format in token: {}", ownerIdOr.getStatus().getMessage());
            responseObserver.onError(
                io.grpc.Status.INVALID_ARGUMENT
                    .withDescription("Invalid owner ID format in token")
                    .asRuntimeException());
            return;
          }
          
          requestedOwnerId = ownerIdOr.getValue();
        }
        
        if (token.getLabelSelectorsCount() > 0) {
          labelSelectors = token.getLabelSelectorsMap();
        }
        
        if (token.hasNameFilter()) {
          nameFilter = token.getNameFilter();
        }
        
        offset = token.getStart();
        
        if (token.hasSortBy()) {
          sortBy = token.getSortBy();
        }
        
        if (token.hasSortOrder()) {
          sortOrder = token.getSortOrder();
        }
      } else {
        // No token, use the request parameters
        
        // Max results
        maxResults = request.hasMaxResults() ? request.getMaxResults() : DEFAULT_MAX_RESULTS;
        // Clamp max results to valid range
        maxResults = Math.max(MIN_RESULTS_LIMIT, Math.min(maxResults, MAX_RESULTS_LIMIT));
        
        // Owner ID
        if (request.hasOwnerId()) {
          com.goodmem.common.status.StatusOr<UUID> ownerIdOr = 
              com.goodmem.db.util.UuidUtil.fromProtoBytes(request.getOwnerId());
          
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
        
        // Label selectors
        if (request.getLabelSelectorsCount() > 0) {
          labelSelectors = request.getLabelSelectorsMap();
        }
        
        // Name filter
        if (request.hasNameFilter()) {
          nameFilter = request.getNameFilter();
        }
        
        // Sort parameters
        if (request.hasSortBy()) {
          sortBy = request.getSortBy();
        }
        
        if (request.hasSortOrder()) {
          sortOrder = request.getSortOrder();
        }
      }
      
      // Determine the owner ID to filter by based on permissions
      UUID ownerIdFilter = null;
      boolean includePublic = hasAnyPermission;
      
      if (hasAnyPermission) {
        // With LIST_SPACE_ANY permission:
        // - If an owner ID was requested, use it as a filter (show only that owner's spaces)
        // - If no owner ID was requested, show all spaces (no owner filter)
        ownerIdFilter = requestedOwnerId;
      } else if (hasOwnPermission) {
        // With only LIST_SPACE_OWN permission:
        // - If an owner ID was requested and it's not the authenticated user, reject with PERMISSION_DENIED
        // - Otherwise, filter to only show the authenticated user's spaces
        if (requestedOwnerId != null && !requestedOwnerId.equals(authenticatedUser.getId())) {
          Logger.error("User lacks permission to list spaces owned by others");
          responseObserver.onError(
              io.grpc.Status.PERMISSION_DENIED
                  .withDescription("Permission denied")
                  .asRuntimeException());
          return;
        }
        
        // Always filter by authenticated user ID when only having LIST_SPACE_OWN
        ownerIdFilter = authenticatedUser.getId();
        includePublic = false; // Don't include public spaces when only has LIST_SPACE_OWN
      }
      
      // Convert name filter to SQL pattern if provided
      String namePattern = null;
      if (nameFilter != null && !nameFilter.isEmpty()) {
        namePattern = globToSqlLike(nameFilter);
      }
      
      // Determine sort direction
      boolean sortAscending = (sortOrder == null || sortOrder == SortOrder.ASCENDING);
      
      // Query the database with the provided filters
      try (java.sql.Connection connection = config.dataSource().getConnection()) {
        com.goodmem.common.status.StatusOr<com.goodmem.db.Spaces.QueryResult> queryResultOr = 
            com.goodmem.db.Spaces.querySpaces(
                connection,
                ownerIdFilter,
                labelSelectors,
                namePattern,
                sortBy,
                sortAscending,
                offset,
                maxResults,
                includePublic,
                authenticatedUser.getId());
        
        if (queryResultOr.isNotOk()) {
          Logger.error("Database query error: {}", queryResultOr.getStatus().getMessage());
          responseObserver.onError(
              io.grpc.Status.INTERNAL
                  .withDescription("Unexpected error while processing request.")
                  .asRuntimeException());
          return;
        }
        
        com.goodmem.db.Spaces.QueryResult queryResult = queryResultOr.getValue();
        
        // Create the response builder
        ListSpacesResponse.Builder responseBuilder = ListSpacesResponse.newBuilder();
        
        // Add the spaces to the response
        for (com.goodmem.db.Space space : queryResult.getSpaces()) {
          responseBuilder.addSpaces(space.toProto());
        }
        
        // Generate next page token if there are more results
        if (queryResult.hasMore(offset, maxResults)) {
          int nextOffset = queryResult.getNextOffset(offset, maxResults);
          
          // Create the next page token
          ListSpacesNextPageToken nextPageToken = createNextPageToken(
              request.hasOwnerId() ? request.getOwnerId().toByteArray() : null,
              labelSelectors,
              nameFilter,
              authenticatedUser.getId(),
              nextOffset,
              sortBy,
              sortOrder);
          
          // Encode the token and add to the response
          String encodedToken = encodeNextPageToken(nextPageToken);
          responseBuilder.setNextToken(encodedToken);
        }
        
        // Send the response
        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
        
      } catch (java.sql.SQLException e) {
        Logger.error(e, "Database error during space listing: {}", e.getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
      }
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during space listing: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Updates mutable properties of a Space.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Validate the space ID format and update request fields
   * 3. Load the space to check ownership
   * 4. Check permissions (UPDATE_SPACE_OWN or UPDATE_SPACE_ANY based on ownership)
   * 5. Update the space in the database
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - INVALID_ARGUMENT: Invalid space ID format or invalid update parameters
   * - NOT_FOUND: Space with the given ID does not exist
   * - PERMISSION_DENIED: User lacks necessary permissions to update the space
   * - ALREADY_EXISTS: If trying to update name to one that already exists for this owner
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void updateSpace(UpdateSpaceRequest request, StreamObserver<Space> responseObserver) {
    // Get the authenticated user from the Context
    com.goodmem.security.User authenticatedUser = com.goodmem.security.AuthInterceptor.USER_CONTEXT_KEY.get();
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(
          io.grpc.Status.UNAUTHENTICATED
              .withDescription("Authentication required")
              .asRuntimeException());
      return;
    }

    // Check which label update strategy is being used (oneof ensures only one can be set)
    boolean hasReplaceLabels = request.getLabelUpdateStrategyCase() == UpdateSpaceRequest.LabelUpdateStrategyCase.REPLACE_LABELS;
    boolean hasMergeLabels = request.getLabelUpdateStrategyCase() == UpdateSpaceRequest.LabelUpdateStrategyCase.MERGE_LABELS;
    
    // Validate and convert space ID
    Logger.info("Updating space: {}", Uuids.bytesToHex(request.getSpaceId()));
    
    com.goodmem.common.status.StatusOr<UUID> spaceIdOr = 
        com.goodmem.db.util.UuidUtil.fromProtoBytes(request.getSpaceId());
    
    if (spaceIdOr.isNotOk()) {
      Logger.error("Invalid space ID format: {}", spaceIdOr.getStatus().getMessage());
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid space ID format")
              .asRuntimeException());
      return;
    }
    
    UUID spaceId = spaceIdOr.getValue();

    try (java.sql.Connection connection = config.dataSource().getConnection()) {
      // Load the space to check ownership
      com.goodmem.common.status.StatusOr<java.util.Optional<com.goodmem.db.Space>> spaceOr = 
          com.goodmem.db.Spaces.loadById(connection, spaceId);
          
      if (spaceOr.isNotOk()) {
        Logger.error("Error loading space: {}", spaceOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Check if space exists
      if (spaceOr.getValue().isEmpty()) {
        Logger.error("Space not found: {}", spaceId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("Space not found")
                .asRuntimeException());
        return;
      }
      
      com.goodmem.db.Space existingSpace = spaceOr.getValue().get();
      
      // Check permissions based on ownership
      boolean isOwner = existingSpace.ownerId().equals(authenticatedUser.getId());
      boolean hasAnyPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.UPDATE_SPACE_ANY);
      boolean hasOwnPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.UPDATE_SPACE_OWN);
      
      // If user is not the owner, they must have UPDATE_SPACE_ANY permission
      if (!isOwner && !hasAnyPermission) {
        Logger.error("User lacks permission to update spaces owned by others");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // If user is the owner, they must have at least UPDATE_SPACE_OWN permission
      if (isOwner && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to update their own spaces");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }
      
      // Process label updates
      java.util.Map<String, String> newLabels;
      if (hasReplaceLabels) {
        // Replace all existing labels with the ones provided
        newLabels = new java.util.HashMap<>(request.getReplaceLabels().getLabelsMap());
      } else if (hasMergeLabels) {
        // Merge existing labels with new ones
        newLabels = new java.util.HashMap<>(existingSpace.labels());
        newLabels.putAll(request.getMergeLabels().getLabelsMap());
      } else {
        // No label changes, keep existing labels
        newLabels = existingSpace.labels();
      }
      
      // Process name update
      String newName = request.hasName() ? request.getName() : existingSpace.name();
      
      // If name has changed, check for uniqueness
      if (!newName.equals(existingSpace.name())) {
        com.goodmem.common.status.StatusOr<java.util.Optional<com.goodmem.db.Space>> existingNameSpaceOr = 
            com.goodmem.db.Spaces.loadByOwnerAndName(connection, existingSpace.ownerId(), newName);
            
        if (existingNameSpaceOr.isNotOk()) {
          Logger.error("Error checking for existing space name: {}", existingNameSpaceOr.getStatus().getMessage());
          responseObserver.onError(
              io.grpc.Status.INTERNAL
                  .withDescription("Unexpected error while processing request.")
                  .asRuntimeException());
          return;
        }
        
        if (existingNameSpaceOr.getValue().isPresent()) {
          Logger.error("Space with name '{}' already exists for this owner", newName);
          responseObserver.onError(
              io.grpc.Status.ALREADY_EXISTS
                  .withDescription("A space with this name already exists")
                  .asRuntimeException());
          return;
        }
      }
      
      // Process public_read update
      boolean newPublicRead = request.hasPublicRead() ? request.getPublicRead() : existingSpace.publicRead();
      
      // Create updated space record
      java.time.Instant now = java.time.Instant.now();
      
      com.goodmem.db.Space updatedSpace = new com.goodmem.db.Space(
          existingSpace.spaceId(),
          existingSpace.ownerId(),
          newName,
          newLabels,
          existingSpace.embedderId(), // embedder_id is immutable
          newPublicRead,
          existingSpace.createdAt(),
          now, // updated now
          existingSpace.createdById(),
          authenticatedUser.getId() // updater is authenticated user
      );
      
      // Persist the updated space record
      com.goodmem.common.status.StatusOr<Integer> saveResult = com.goodmem.db.Spaces.save(connection, updatedSpace);
      
      if (saveResult.isNotOk()) {
        Logger.error("Failed to save updated space: {}", saveResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }
      
      // Convert the database record to a proto message and return it
      Space protoSpace = updatedSpace.toProto();
      responseObserver.onNext(protoSpace);
      responseObserver.onCompleted();
      Logger.info("Space updated successfully: {}", spaceId);
      
    } catch (java.sql.SQLException e) {
      Logger.error(e, "Database error during space update: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during space update: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  private Timestamp getCurrentTimestamp() {
    Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }
  
  /**
   * Encodes a ListSpacesNextPageToken protobuf message into a Base64 string.
   *
   * @param token The token to encode
   * @return The Base64-encoded string representation of the token
   */
  private String encodeNextPageToken(ListSpacesNextPageToken token) {
    if (token == null) {
      return "";
    }
    return BaseEncoding.base64().encode(token.toByteArray());
  }
  
  /**
   * Decodes a Base64 string into a ListSpacesNextPageToken protobuf message.
   * Also validates that the requestor ID in the token matches the authenticated user.
   *
   * @param tokenString The Base64-encoded token string
   * @param authenticatedUser The authenticated user making the request
   * @return StatusOr containing the decoded token or an error status
   */
  private com.goodmem.common.status.StatusOr<ListSpacesNextPageToken> decodeAndValidateNextPageToken(
      String tokenString, com.goodmem.security.User authenticatedUser) {
    if (tokenString == null || tokenString.isEmpty()) {
      return com.goodmem.common.status.StatusOr.ofValue(ListSpacesNextPageToken.getDefaultInstance());
    }
    
    try {
      // Decode the Base64 string to bytes
      byte[] tokenBytes = BaseEncoding.base64().decode(tokenString);
      
      // Parse the bytes into a protobuf message
      ListSpacesNextPageToken token = ListSpacesNextPageToken.parseFrom(tokenBytes);
      
      // Validate the requestor ID if present
      if (token.hasRequestorId()) {
        com.goodmem.common.status.StatusOr<UUID> requestorIdOr = 
            com.goodmem.db.util.UuidUtil.fromProtoBytes(token.getRequestorId());
            
        if (requestorIdOr.isNotOk()) {
          return com.goodmem.common.status.StatusOr.ofStatus(
              com.goodmem.common.status.Status.invalid("Invalid requestor ID in token"));
        }
        
        UUID tokenRequestorId = requestorIdOr.getValue();
        
        // Verify that the token's requestor ID matches the authenticated user
        if (!tokenRequestorId.equals(authenticatedUser.getId())) {
          Logger.warn("Token requestor ID does not match authenticated user: {} vs {}",
              tokenRequestorId, authenticatedUser.getId());
          return com.goodmem.common.status.StatusOr.ofStatus(
              com.goodmem.common.status.Status.permissionDenied("Invalid pagination token"));
        }
      }
      
      return com.goodmem.common.status.StatusOr.ofValue(token);
    } catch (IllegalArgumentException e) {
      // Base64 decoding failed
      Logger.warn("Failed to decode pagination token: {}", e.getMessage());
      return com.goodmem.common.status.StatusOr.ofStatus(
          com.goodmem.common.status.Status.invalid("Invalid pagination token format"));
    } catch (InvalidProtocolBufferException e) {
      // Protobuf parsing failed
      Logger.warn("Failed to parse pagination token: {}", e.getMessage());
      return com.goodmem.common.status.StatusOr.ofStatus(
          com.goodmem.common.status.Status.invalid("Invalid pagination token content"));
    }
  }
  
  /**
   * Creates a next page token with the given parameters.
   *
   * @param ownerId Filter by owner ID
   * @param labelSelectors Partial match on labels
   * @param nameFilter Glob-style match on space name
   * @param requestorId ID of the authenticated user making the request
   * @param start Cursor position for the next page
   * @param sortBy Field to sort by
   * @param sortOrder Ascending or descending sort
   * @return The constructed ListSpacesNextPageToken message
   */
  private ListSpacesNextPageToken createNextPageToken(
      byte[] ownerId,
      Map<String, String> labelSelectors,
      String nameFilter,
      UUID requestorId,
      int start,
      String sortBy,
      SortOrder sortOrder) {
    
    ListSpacesNextPageToken.Builder tokenBuilder = ListSpacesNextPageToken.newBuilder()
        .setRequestorId(com.goodmem.db.util.UuidUtil.toProtoBytes(requestorId))
        .setStart(start);
    
    // Set optional fields if provided
    if (ownerId != null && ownerId.length > 0) {
      tokenBuilder.setOwnerId(com.google.protobuf.ByteString.copyFrom(ownerId));
    }
    
    if (labelSelectors != null && !labelSelectors.isEmpty()) {
      tokenBuilder.putAllLabelSelectors(labelSelectors);
    }
    
    if (nameFilter != null) {
      tokenBuilder.setNameFilter(nameFilter);
    }
    
    if (sortBy != null) {
      tokenBuilder.setSortBy(sortBy);
    }
    
    if (sortOrder != null) {
      tokenBuilder.setSortOrder(sortOrder);
    }
    
    return tokenBuilder.build();
  }
  
  /**
   * Converts a glob-style pattern ( * and ? wildcards ) to a SQL LIKE / ILIKE
   * pattern, escaping literal %, _, and backslash characters.
   *
   * Examples:
   *   globToSqlLike("Boy*")   -> "Boy%"
   *   globToSqlLike("*Boy*")  -> "%Boy%"
   *   globToSqlLike("B?y*")   -> "B_y%"
   *
   * Pass the result to a PreparedStatement and add  "ESCAPE '\'"
   * in your SQL so the backslash is the explicit escape character.
   *
   *   String sql =
   *     "SELECT * FROM spaces WHERE name ILIKE ? ESCAPE '\\\\'";
   *   ps.setString(1, globToSqlLike(pattern));
   */
  public static String globToSqlLike(String glob) {
    if (glob == null || glob.isEmpty()) {
      return "%";             // match everything
    }

    StringBuilder sb = new StringBuilder(glob.length() + 4);

    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      switch (c) {
        case '\\': sb.append("\\\\"); break;   // escape backslash itself
        case '%':  sb.append("\\%"); break;    // escape SQL %
        case '_':  sb.append("\\_"); break;    // escape SQL _
        case '*':  sb.append('%'); break;      // glob * -> SQL %
        case '?':  sb.append('_'); break;      // glob ? -> SQL _
        default:   sb.append(c);
      }
    }
    return sb.toString();
  }
}
