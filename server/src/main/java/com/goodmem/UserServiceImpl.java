package com.goodmem;

import com.goodmem.operations.SystemInitOperation;
import com.goodmem.security.ConditionalAuthInterceptor;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.UserOuterClass.GetUserRequest;
import goodmem.v1.UserOuterClass.InitializeSystemRequest;
import goodmem.v1.UserOuterClass.InitializeSystemResponse;
import goodmem.v1.UserServiceGrpc.UserServiceImplBase;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.tinylog.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of the User service that provides user management functionality.
 * <p>
 * This service handles operations like retrieving user details and system initialization.
 * It connects to the PostgreSQL database using the provided configuration and implements
 * the gRPC service interface defined in the protocol buffer.
 */
public class UserServiceImpl extends UserServiceImplBase {
  private final Config config;

  public record Config(HikariDataSource dataSource) {}

  public UserServiceImpl(Config config) {
    this.config = config;
  }

  /**
   * Retrieves user details based on the provided request parameters.
   * 
   * Lookup behavior:
   * 1. If neither user_id nor email is set: Returns the current user based on API key authentication
   * 2. If user_id is set: Looks up user by ID
   * 3. If email is set (and user_id not set): Looks up user by email
   * 4. If both are set: Looks up by user_id and ignores email (with a warning)
   *
   * All API calls (except InitializeSystem) require authentication.
   * Possible error codes:
   * - NOT_FOUND: No matching user exists
   * - UNAUTHENTICATED: The request has no valid authentication
   * - PERMISSION_DENIED: The authenticated user is not authorized to view the requested user's information
   */
  @Override
  public void getUser(GetUserRequest request, StreamObserver<goodmem.v1.UserOuterClass.User> responseObserver) {
    Logger.info("Getting user details via gRPC");
    
    // Get the authenticated user from the Context
    com.goodmem.security.User authenticatedUser = io.grpc.Context.current().get(com.goodmem.security.AuthInterceptor.USER_CONTEXT_KEY);
    if (authenticatedUser == null) {
      Logger.error("No authentication context found");
      responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
          .withDescription("Authentication required")
          .asRuntimeException());
      return;
    }
    
    // Check permissions before attempting any database operations
    boolean hasAnyPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.DISPLAY_USER_ANY);
    boolean hasOwnPermission = authenticatedUser.hasPermission(com.goodmem.security.Permission.DISPLAY_USER_OWN);
    
    if (!hasAnyPermission && !hasOwnPermission) {
      Logger.error("User lacks necessary permissions for this operation");
      responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
          .withDescription("Permission denied")
          .asRuntimeException());
      return;
    }
    
    boolean userIdProvided = request.hasUserId() && request.getUserId().size() > 0;
    boolean emailProvided = request.hasEmail() && !request.getEmail().isEmpty();
    
    UUID requestedUserId = null;
    String requestedEmail = null;
    
    // If the user only has OWN permission, validate they're requesting their own data
    if (!hasAnyPermission && hasOwnPermission) {
      // If a user_id is provided, it must match the authenticated user
      if (userIdProvided) {
        StatusOr<UUID> userIdOr = com.goodmem.db.util.UuidUtil.fromProtoBytes(request.getUserId());
        if (userIdOr.isNotOk() || !userIdOr.getValue().equals(authenticatedUser.getId())) {
          Logger.error("User with DISPLAY_USER_OWN permission attempted to access another user's data");
          responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
              .withDescription("Permission denied")
              .asRuntimeException());
          return;
        }
        requestedUserId = userIdOr.getValue();
      }
      
      // If email is provided, it must match the authenticated user
      if (emailProvided && !request.getEmail().equals(authenticatedUser.getEmail())) {
        Logger.error("User with DISPLAY_USER_OWN permission attempted to access another user's data");
        responseObserver.onError(io.grpc.Status.PERMISSION_DENIED
            .withDescription("Permission denied")
            .asRuntimeException());
        return;
      }
      
      // If neither field is provided, default to the authenticated user
      if (!userIdProvided && !emailProvided) {
        requestedUserId = authenticatedUser.getId();
      }
      
      // If email is provided and matches, use it
      if (emailProvided) {
        requestedEmail = request.getEmail();
      }
    } else {
      // User has DISPLAY_USER_ANY permission, handle the request normally
      if (userIdProvided) {
        StatusOr<UUID> userIdOr = com.goodmem.db.util.UuidUtil.fromProtoBytes(request.getUserId());
        if (userIdOr.isNotOk()) {
          Logger.error("Invalid user ID format: {}", userIdOr.getStatus().getMessage());
          responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
              .withDescription("Invalid user ID format")
              .asRuntimeException());
          return;
        }
        requestedUserId = userIdOr.getValue();
      }
      
      if (emailProvided) {
        requestedEmail = request.getEmail();
        
        // If both fields are provided, log a warning that we'll use the user ID
        if (userIdProvided) {
          Logger.warn("Both user_id and email provided; using user_id and ignoring email");
          requestedEmail = null;
        }
      }
      
      // If neither field is provided, default to the authenticated user
      if (!userIdProvided && !emailProvided) {
        requestedUserId = authenticatedUser.getId();
      }
    }
    
    // Now that permissions are checked, proceed with database lookup
    try (Connection connection = config.dataSource().getConnection()) {
      StatusOr<Optional<com.goodmem.db.User>> userOr;
      
      if (requestedUserId != null) {
        Logger.info("Looking up user by ID: {}", requestedUserId);
        userOr = com.goodmem.db.Users.loadById(connection, requestedUserId);
      } else if (requestedEmail != null) {
        Logger.info("Looking up user by email: {}", requestedEmail);
        userOr = com.goodmem.db.Users.loadByEmail(connection, requestedEmail);
      } else {
        // This should never happen based on the logic above
        Logger.error("No lookup criteria specified");
        responseObserver.onError(io.grpc.Status.INTERNAL
            .withDescription("Unexpected error while processing request.")
            .asRuntimeException());
        return;
      }
      
      // Handle database errors
      if (userOr.isNotOk()) {
        Logger.error("Failed to retrieve user: {}", userOr.getStatus().getMessage());
        responseObserver.onError(io.grpc.Status.INTERNAL
            .withDescription("Unexpected error while processing request.")
            .asRuntimeException());
        return;
      }
      
      // Handle user not found
      Optional<com.goodmem.db.User> userOptional = userOr.getValue();
      if (userOptional.isEmpty()) {
        Logger.error("User not found");
        responseObserver.onError(io.grpc.Status.NOT_FOUND
            .withDescription("User not found")
            .asRuntimeException());
        return;
      }
      
      // Convert DB user to protobuf user and return
      goodmem.v1.UserOuterClass.User user = userOptional.get().toProto();
      responseObserver.onNext(user);
      responseObserver.onCompleted();
      
    } catch (SQLException e) {
      Logger.error(e, "Database connection error during user retrieval: {}", e.getMessage());
      responseObserver.onError(io.grpc.Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during user retrieval: {}", e.getMessage());
      responseObserver.onError(io.grpc.Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
    }
  }

  @Override
  public void initializeSystem(InitializeSystemRequest request, StreamObserver<InitializeSystemResponse> responseObserver) {
    Logger.info("Initializing system via gRPC");
    
    // Set up database connection using the connection pool
    try (Connection connection = config.dataSource().getConnection()) {
        
      // Create and execute the operation
      SystemInitOperation operation = new SystemInitOperation(connection);
      SystemInitOperation.InitResult result = operation.execute();

      if (!result.isSuccess()) {
        Logger.error("System initialization failed: {}", result.errorMessage());
        responseObserver.onError(Status.INTERNAL
            .withDescription("System initialization failed: " + result.errorMessage())
            .asRuntimeException());
        return;
      }

      InitializeSystemResponse.Builder responseBuilder = InitializeSystemResponse.newBuilder()
          .setAlreadyInitialized(result.alreadyInitialized())
          .setMessage(result.alreadyInitialized()
              ? "System is already initialized" 
              : "System initialized successfully");
      
      if (!result.alreadyInitialized() && result.userId() != null) {
        responseBuilder
            .setRootApiKey(result.apiKey())
            .setUserId(Uuids.getBytesFromUUID(result.userId()));
      }
      
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
      
    } catch (SQLException e) {
      Logger.error(e, "Database connection error during system initialization: {}", e.getMessage());
      responseObserver.onError(Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during system initialization: {}", e.getMessage());
      responseObserver.onError(Status.INTERNAL
          .withDescription("Unexpected error while processing request.")
          .asRuntimeException());
    }
  }

  private Timestamp getCurrentTimestamp() {
    Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(
        now.getEpochSecond()).setNanos(now.getNano()).build();
  }
}
