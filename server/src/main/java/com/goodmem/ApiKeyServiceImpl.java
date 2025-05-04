package com.goodmem;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.UuidUtil;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.Permission;
import com.goodmem.security.User;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.ApiKeyServiceGrpc.ApiKeyServiceImplBase;
import goodmem.v1.Apikey.ApiKey;
import goodmem.v1.Apikey.CreateApiKeyRequest;
import goodmem.v1.Apikey.CreateApiKeyResponse;
import goodmem.v1.Apikey.DeleteApiKeyRequest;
import goodmem.v1.Apikey.ListApiKeysRequest;
import goodmem.v1.Apikey.ListApiKeysResponse;
import goodmem.v1.Apikey.Status;
import goodmem.v1.Apikey.UpdateApiKeyRequest;
import goodmem.v1.Common.StringMap;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.tinylog.Logger;

public class ApiKeyServiceImpl extends ApiKeyServiceImplBase {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private final Config config;

  public record Config(HikariDataSource dataSource) {}

  public ApiKeyServiceImpl(Config config) {
    this.config = config;
  }

  /**
   * Creates a new API key for the authenticated user.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Check permissions (CREATE_APIKEY_OWN or CREATE_APIKEY_ANY)
   * 3. Generate secure API key
   * 4. Store API key hash in database
   * 5. Return the API key metadata and raw key
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - PERMISSION_DENIED: User lacks necessary permissions to create API keys
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void createApiKey(
      CreateApiKeyRequest request, StreamObserver<CreateApiKeyResponse> responseObserver) {
    Logger.info("Creating API key");

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

    // Check permissions
    boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.CREATE_APIKEY_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.CREATE_APIKEY_OWN);

    // User must have at least CREATE_APIKEY_OWN permission
    if (!hasAnyPermission && !hasOwnPermission) {
      Logger.error("User lacks necessary permissions to create API keys");
      responseObserver.onError(
          io.grpc.Status.PERMISSION_DENIED
              .withDescription("Permission denied")
              .asRuntimeException());
      return;
    }

    // Generate a secure API key
    com.goodmem.security.ApiKey apiKeyObj = com.goodmem.security.ApiKey.newKey(SECURE_RANDOM);
    String rawApiKey = apiKeyObj.keyString();
    String keyPrefix = apiKeyObj.displayPrefix();
    ByteString keyHash = apiKeyObj.hashedKeyMaterial();

    try (Connection connection = config.dataSource().getConnection()) {
      // Create a new API key record
      UUID apiKeyId = UUID.randomUUID();
      UUID userId = authenticatedUser.getId(); // API key belongs to authenticated user
      UUID creatorId = authenticatedUser.getId();
      Instant now = Instant.now();

      // Parse expiration time if provided
      Instant expiresAt = null;
      if (request.hasExpiresAt()) {
        expiresAt = Instant.ofEpochSecond(
            request.getExpiresAt().getSeconds(),
            request.getExpiresAt().getNanos());
      }

      // Create the API key record
      com.goodmem.db.ApiKey apiKeyRecord = new com.goodmem.db.ApiKey(
          apiKeyId,
          userId,
          keyPrefix,
          keyHash,
          Status.ACTIVE.name(),
          request.getLabelsMap(),
          expiresAt,
          null, // Last used at is initially null
          now,
          now,
          creatorId,
          creatorId
      );

      // Save the API key to the database
      StatusOr<Integer> saveResult = com.goodmem.db.ApiKeys.save(connection, apiKeyRecord);

      if (saveResult.isNotOk()) {
        Logger.error("Failed to save API key: {}", saveResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }

      // Convert the database record to a proto message
      ApiKey protoApiKey = apiKeyRecord.toProto();

      // Create the response with metadata and raw key
      CreateApiKeyResponse response = CreateApiKeyResponse.newBuilder()
          .setApiKeyMetadata(protoApiKey)
          .setRawApiKey(rawApiKey)
          .build();

      responseObserver.onNext(response);
      responseObserver.onCompleted();
      Logger.info("API key created successfully: {}", apiKeyId);

    } catch (SQLException e) {
      Logger.error(e, "Database error during API key creation: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during API key creation: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Lists API keys for the authenticated user.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Check permissions (LIST_APIKEY_OWN or LIST_APIKEY_ANY)
   * 3. Query database for API keys owned by the user
   * 4. Return the list of API keys
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - PERMISSION_DENIED: User lacks necessary permissions to list API keys
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void listApiKeys(
      ListApiKeysRequest request, StreamObserver<ListApiKeysResponse> responseObserver) {
    Logger.info("Listing API keys");

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

    // Check permissions
    boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.LIST_APIKEY_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.LIST_APIKEY_OWN);

    // User must have at least LIST_APIKEY_OWN permission
    if (!hasAnyPermission && !hasOwnPermission) {
      Logger.error("User lacks necessary permissions to list API keys");
      responseObserver.onError(
          io.grpc.Status.PERMISSION_DENIED
              .withDescription("Permission denied")
              .asRuntimeException());
      return;
    }

    try (Connection connection = config.dataSource().getConnection()) {
      // Determine which user's API keys to list
      UUID userId = authenticatedUser.getId();

      // Query the database for the user's API keys
      StatusOr<java.util.List<com.goodmem.db.ApiKey>> apiKeysOr;

      if (hasAnyPermission) {
        // Admin users with LIST_APIKEY_ANY permission can list all API keys
        apiKeysOr = com.goodmem.db.ApiKeys.loadAll(connection);
      } else {
        // Regular users can only list their own API keys
        apiKeysOr = com.goodmem.db.ApiKeys.loadByUserId(connection, userId);
      }

      if (apiKeysOr.isNotOk()) {
        Logger.error("Error loading API keys: {}", apiKeysOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }

      // Build the response with the list of API keys
      ListApiKeysResponse.Builder responseBuilder = ListApiKeysResponse.newBuilder();

      for (com.goodmem.db.ApiKey apiKey : apiKeysOr.getValue()) {
        responseBuilder.addKeys(apiKey.toProto());
      }

      // Send the response
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();

    } catch (SQLException e) {
      Logger.error(e, "Database error during API key listing: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during API key listing: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Updates an API key properties (labels, status).
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Validate the API key ID
   * 3. Load the API key to check ownership
   * 4. Check permissions (UPDATE_APIKEY_OWN or UPDATE_APIKEY_ANY based on ownership)
   * 5. Update the API key in the database
   * 6. Return the updated API key
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - INVALID_ARGUMENT: Invalid API key ID format
   * - NOT_FOUND: API key with the given ID does not exist
   * - PERMISSION_DENIED: User lacks necessary permissions to update the API key
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void updateApiKey(UpdateApiKeyRequest request, StreamObserver<ApiKey> responseObserver) {
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

    // Validate and convert API key ID
    Logger.info("Updating API key: {}", hex(request.getApiKeyId()));

    StatusOr<UUID> apiKeyIdOr = UuidUtil.fromProtoBytes(request.getApiKeyId());

    if (apiKeyIdOr.isNotOk()) {
      Logger.error("Invalid API key ID format: {}", apiKeyIdOr.getStatus().getMessage());
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid API key ID format")
              .asRuntimeException());
      return;
    }

    UUID apiKeyId = apiKeyIdOr.getValue();

    try (Connection connection = config.dataSource().getConnection()) {
      // Load the API key to check ownership
      StatusOr<java.util.Optional<com.goodmem.db.ApiKey>> apiKeyOr =
          com.goodmem.db.ApiKeys.loadById(connection, apiKeyId);

      if (apiKeyOr.isNotOk()) {
        Logger.error("Error loading API key: {}", apiKeyOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }

      // Check if API key exists
      if (apiKeyOr.getValue().isEmpty()) {
        Logger.error("API key not found: {}", apiKeyId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("API key not found")
                .asRuntimeException());
        return;
      }

      com.goodmem.db.ApiKey existingApiKey = apiKeyOr.getValue().get();

      // Check permissions based on ownership
      boolean isOwner = existingApiKey.userId().equals(authenticatedUser.getId());
      boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.UPDATE_APIKEY_ANY);
      boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.UPDATE_APIKEY_OWN);

      // If user is not the owner, they must have UPDATE_APIKEY_ANY permission
      if (!isOwner && !hasAnyPermission) {
        Logger.error("User lacks permission to update API keys owned by others");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }

      // If user is the owner, they must have at least UPDATE_APIKEY_OWN permission
      if (isOwner && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to update their own API keys");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }

      // Process label updates using the new oneof pattern
      java.util.Map<String, String> newLabels;

      switch (request.getLabelUpdateStrategyCase()) {
        case REPLACE_LABELS:
          // Replace all existing labels with the ones provided
          newLabels = new java.util.HashMap<>(request.getReplaceLabels().getLabelsMap());
          Logger.info("Replacing all labels with new set containing {} labels", newLabels.size());
          break;

        case MERGE_LABELS:
          // Merge the provided labels with existing ones
          newLabels = new java.util.HashMap<>(existingApiKey.labels());
          newLabels.putAll(request.getMergeLabels().getLabelsMap());
          Logger.info("Merging {} new labels with existing labels", request.getMergeLabels().getLabelsCount());
          break;

        case LABELUPDATESTRATEGY_NOT_SET:
        default:
          // No label changes, keep existing labels
          newLabels = existingApiKey.labels();
          break;
      }

      // Process status update
      String newStatus;
      if (request.hasStatus() && request.getStatus() != Status.STATUS_UNSPECIFIED) {
        newStatus = request.getStatus().name();
      } else {
        newStatus = existingApiKey.status();
      }

      // Create updated API key record
      java.time.Instant now = java.time.Instant.now();

      com.goodmem.db.ApiKey updatedApiKey = new com.goodmem.db.ApiKey(
          existingApiKey.apiKeyId(),
          existingApiKey.userId(),
          existingApiKey.keyPrefix(),
          existingApiKey.keyHash(),
          newStatus,
          newLabels,
          existingApiKey.expiresAt(),
          existingApiKey.lastUsedAt(),
          existingApiKey.createdAt(),
          now, // updated now
          existingApiKey.createdById(),
          authenticatedUser.getId() // updater is authenticated user
      );

      // Persist the updated API key record
      StatusOr<Integer> saveResult = com.goodmem.db.ApiKeys.save(connection, updatedApiKey);

      if (saveResult.isNotOk()) {
        Logger.error("Failed to save updated API key: {}", saveResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }

      // Convert the database record to a proto message and return it
      ApiKey protoApiKey = updatedApiKey.toProto();
      responseObserver.onNext(protoApiKey);
      responseObserver.onCompleted();
      Logger.info("API key updated successfully: {}", apiKeyId);

    } catch (SQLException e) {
      Logger.error(e, "Database error during API key update: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during API key update: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    }
  }

  /**
   * Deletes an API key by ID.
   *
   * <p>The method follows these steps:
   * 1. Retrieve the authenticated user from context
   * 2. Validate the API key ID format
   * 3. Load the API key to check ownership
   * 4. Check permissions (DELETE_APIKEY_OWN or DELETE_APIKEY_ANY based on ownership)
   * 5. Delete the API key from the database
   *
   * <p>Possible error conditions:
   * - UNAUTHENTICATED: No valid authentication provided
   * - INVALID_ARGUMENT: Invalid API key ID format
   * - NOT_FOUND: API key with the given ID does not exist
   * - PERMISSION_DENIED: User lacks necessary permissions to delete the API key
   * - INTERNAL: Database or other system errors
   */
  @Override
  public void deleteApiKey(DeleteApiKeyRequest request, StreamObserver<Empty> responseObserver) {
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

    // Validate and convert API key ID
    Logger.info("Deleting API key: {}", hex(request.getApiKeyId()));

    StatusOr<UUID> apiKeyIdOr = UuidUtil.fromProtoBytes(request.getApiKeyId());

    if (apiKeyIdOr.isNotOk()) {
      Logger.error("Invalid API key ID format: {}", apiKeyIdOr.getStatus().getMessage());
      responseObserver.onError(
          io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid API key ID format")
              .asRuntimeException());
      return;
    }

    UUID apiKeyId = apiKeyIdOr.getValue();

    try (Connection connection = config.dataSource().getConnection()) {
      // Load the API key to check ownership
      StatusOr<java.util.Optional<com.goodmem.db.ApiKey>> apiKeyOr =
          com.goodmem.db.ApiKeys.loadById(connection, apiKeyId);

      if (apiKeyOr.isNotOk()) {
        Logger.error("Error loading API key: {}", apiKeyOr.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }

      // Check if API key exists
      if (apiKeyOr.getValue().isEmpty()) {
        Logger.error("API key not found: {}", apiKeyId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("API key not found")
                .asRuntimeException());
        return;
      }

      com.goodmem.db.ApiKey apiKey = apiKeyOr.getValue().get();

      // Check permissions based on ownership
      boolean isOwner = apiKey.userId().equals(authenticatedUser.getId());
      boolean hasAnyPermission = authenticatedUser.hasPermission(Permission.DELETE_APIKEY_ANY);
      boolean hasOwnPermission = authenticatedUser.hasPermission(Permission.DELETE_APIKEY_OWN);

      // If user is not the owner, they must have DELETE_APIKEY_ANY permission
      if (!isOwner && !hasAnyPermission) {
        Logger.error("User lacks permission to delete API keys owned by others");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }

      // If user is the owner, they must have at least DELETE_APIKEY_OWN permission
      if (isOwner && !hasAnyPermission && !hasOwnPermission) {
        Logger.error("User lacks necessary permissions to delete their own API keys");
        responseObserver.onError(
            io.grpc.Status.PERMISSION_DENIED
                .withDescription("Permission denied")
                .asRuntimeException());
        return;
      }

      // Delete the API key from the database
      StatusOr<Integer> deleteResult = com.goodmem.db.ApiKeys.delete(connection, apiKeyId);

      if (deleteResult.isNotOk()) {
        Logger.error("Failed to delete API key: {}", deleteResult.getStatus().getMessage());
        responseObserver.onError(
            io.grpc.Status.INTERNAL
                .withDescription("Unexpected error while processing request.")
                .asRuntimeException());
        return;
      }

      // Ensure the API key was actually deleted
      if (deleteResult.getValue() == 0) {
        Logger.error("API key not found during delete operation: {}", apiKeyId);
        responseObserver.onError(
            io.grpc.Status.NOT_FOUND
                .withDescription("API key not found")
                .asRuntimeException());
        return;
      }

      // Success - return empty response
      responseObserver.onNext(Empty.getDefaultInstance());
      responseObserver.onCompleted();
      Logger.info("API key deleted successfully: {}", apiKeyId);

    } catch (SQLException e) {
      Logger.error(e, "Database error during API key deletion: {}", e.getMessage());
      responseObserver.onError(
          io.grpc.Status.INTERNAL
              .withDescription("Unexpected error while processing request.")
              .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during API key deletion: {}", e.getMessage());
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

  private ByteString getBytesFromUUID(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return ByteString.copyFrom(bb.array());
  }

  private String hex(ByteString bs) {
    return BaseEncoding.base16().encode(bs.toByteArray());
  }
}
