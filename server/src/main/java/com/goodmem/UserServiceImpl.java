package com.goodmem;

import com.goodmem.operations.SystemInitOperation;
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

  @Override
  public void getUser(GetUserRequest request, StreamObserver<goodmem.v1.UserOuterClass.User> responseObserver) {
    Logger.info("Getting user details");

    // TODO: Validate user ID
    // TODO: Retrieve user from database
    // TODO: Check permissions

    // For now, return dummy data
    goodmem.v1.UserOuterClass.User user =
        goodmem.v1.UserOuterClass.User.newBuilder()
            .setUserId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setEmail("user@example.com")
            .setDisplayName("Example User")
            .setUsername("exampleuser")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .build();

    responseObserver.onNext(user);
    responseObserver.onCompleted();
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
        Logger.error("System initialization failed: {}", result.getErrorMessage());
        responseObserver.onError(Status.INTERNAL
            .withDescription("System initialization failed: " + result.getErrorMessage())
            .asRuntimeException());
        return;
      }

      InitializeSystemResponse.Builder responseBuilder = InitializeSystemResponse.newBuilder()
          .setAlreadyInitialized(result.isAlreadyInitialized())
          .setMessage(result.isAlreadyInitialized() 
              ? "System is already initialized" 
              : "System initialized successfully");
      
      if (!result.isAlreadyInitialized() && result.getUserId() != null) {
        responseBuilder
            .setRootApiKey(result.getApiKey())
            .setUserId(Uuids.getBytesFromUUID(result.getUserId()));
      }
      
      responseObserver.onNext(responseBuilder.build());
      responseObserver.onCompleted();
      
    } catch (SQLException e) {
      Logger.error(e, "Database connection error during system initialization.");
      responseObserver.onError(Status.INTERNAL
          .withDescription("Database connection error: " + e.getMessage())
          .asRuntimeException());
    } catch (Exception e) {
      Logger.error(e, "Unexpected error during system initialization.");
      responseObserver.onError(Status.INTERNAL
          .withDescription("Unexpected error: " + e.getMessage())
          .asRuntimeException());
    }
  }

  private Timestamp getCurrentTimestamp() {
    Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(
        now.getEpochSecond()).setNanos(now.getNano()).build();
  }
}
