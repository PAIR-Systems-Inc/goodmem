package com.goodmem;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.SpaceOuterClass.CreateSpaceRequest;
import goodmem.v1.SpaceOuterClass.DeleteSpaceRequest;
import goodmem.v1.SpaceOuterClass.GetSpaceRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesResponse;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.SpaceOuterClass.UpdateSpaceRequest;
import goodmem.v1.SpaceServiceGrpc.SpaceServiceImplBase;
import io.grpc.stub.StreamObserver;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.UUID;

public class SpaceServiceImpl extends SpaceServiceImplBase {
  private final Config config;

  /**
   * @param dataSource
   * @param defaultEmbeddingModel
   */
  public record Config(HikariDataSource dataSource, String defaultEmbeddingModel) {}
  
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
    boolean ownerIdProvided = request.hasOwnerId() && !request.getOwnerId().isEmpty();
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

    // Determine the embedding model to use (default if not specified)
    String embeddingModel = request.getEmbeddingModel();
    if (embeddingModel == null || embeddingModel.isEmpty()) {
      embeddingModel = config.defaultEmbeddingModel();
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
          embeddingModel,
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
            .setEmbeddingModel("openai-ada-002")
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

  @Override
  public void listSpaces(
      ListSpacesRequest request, StreamObserver<ListSpacesResponse> responseObserver) {
    Logger.info("Listing spaces with label selectors: {}", request.getLabelSelectorsMap());

    // TODO: Query database with label selectors
    // TODO: Filter by ownership
    // TODO: Provide pagination

    // For now, return a dummy space
    Space dummySpace =
        Space.newBuilder()
            .setSpaceId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setName("Example Space")
            .putLabels("user", "alice")
            .putLabels("bot", "copilot")
            .setEmbeddingModel("openai-ada-002")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setOwnerId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setPublicRead(true)
            .build();

    ListSpacesResponse response = ListSpacesResponse.newBuilder().addSpaces(dummySpace).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void updateSpace(UpdateSpaceRequest request, StreamObserver<Space> responseObserver) {
    Logger.info("Updating space: {}" + Uuids.bytesToHex(request.getSpaceId()));

    // TODO: Validate space ID
    // TODO: Check ownership
    // TODO: Update in database

    // For now, return a dummy updated space
    Space updatedSpace =
        Space.newBuilder()
            .setSpaceId(request.getSpaceId())
            .setName(request.getName().isEmpty() ? "Example Space" : request.getName())
            .putAllLabels(request.getLabelsMap())
            .setEmbeddingModel("openai-ada-002")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setOwnerId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setPublicRead(request.getPublicRead())
            .build();

    responseObserver.onNext(updatedSpace);
    responseObserver.onCompleted();
  }

  private Timestamp getCurrentTimestamp() {
    Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }
}
