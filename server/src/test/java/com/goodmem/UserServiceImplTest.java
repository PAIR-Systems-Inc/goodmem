package com.goodmem;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import com.goodmem.security.AuthInterceptor;
import com.google.protobuf.ByteString;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.UserOuterClass;
import goodmem.v1.UserOuterClass.GetUserRequest;
import goodmem.v1.UserOuterClass.InitializeSystemRequest;
import goodmem.v1.UserOuterClass.InitializeSystemResponse;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for UserServiceImpl that verify functionality with a real PostgreSQL database.
 *
 * <p>This test follows a sequential workflow to test API key authentication and user operations:
 * 1. Initialize the system and get a root user and API key
 * 2. Verify the system reports as already initialized on second attempt
 * 3. Use the API key to authenticate and retrieve the root user
 * 4. Retrieve the root user by UUID
 * 5. Retrieve the root user by email
 * 6. Test error handling for invalid UUID
 * 7. Test error handling for invalid email
 */
@Testcontainers
public class UserServiceImplTest {

  private static PostgresContext postgresContext;
  private static HikariDataSource dataSource;
  private static UserServiceImpl userService;
  private static AuthInterceptor authInterceptor;

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_userservice_test", UserServiceImplTest.class);
    
    // Create a HikariDataSource for the test container
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgresContext.getContainer().getJdbcUrl());
    config.setUsername(postgresContext.getContainer().getUsername());
    config.setPassword(postgresContext.getContainer().getPassword());
    config.setMaximumPoolSize(2);
    dataSource = new HikariDataSource(config);
    
    // Create the UserServiceImpl with the test datasource
    userService = new UserServiceImpl(new UserServiceImpl.Config(dataSource));
    
    // Create the AuthInterceptor with the test datasource
    authInterceptor = new AuthInterceptor(dataSource);
  }

  @AfterAll
  static void tearDown() {
    if (dataSource != null) {
      dataSource.close();
    }
    
    if (postgresContext != null) {
      postgresContext.close();
    }
  }

  @Test
  void testUserServiceOperations() throws SQLException {
    // =========================================================================
    // Step 1: Initialize the system
    // =========================================================================
    System.out.println("Step 1: Initialize the system");
    
    TestStreamObserver<InitializeSystemResponse> initObserver = new TestStreamObserver<>();
    userService.initializeSystem(InitializeSystemRequest.newBuilder().build(), initObserver);
    
    // Verify successful initialization
    assertFalse(initObserver.hasError(), 
        "System initialization should not error: " + (initObserver.hasError() ? initObserver.getError().getMessage() : ""));
    assertTrue(initObserver.hasValue(), "Should have initialization response");
    assertTrue(initObserver.isCompleted(), "Observer should be completed");
    
    InitializeSystemResponse initResponse = initObserver.getValue();
    assertFalse(initResponse.getAlreadyInitialized(), "System should not already be initialized");
    assertEquals("System initialized successfully", initResponse.getMessage());
    assertFalse(initResponse.getRootApiKey().isEmpty(), "API key should be provided");
    assertFalse(initResponse.getUserId().isEmpty(), "User ID should be provided");
    
    // Extract and store the root API key and user ID for subsequent tests
    String rootApiKey = initResponse.getRootApiKey();
    ByteString rootUserIdBytes = initResponse.getUserId();
    // Convert ByteString to UUID using ByteBuffer
    UUID rootUserId = UUID.fromString(com.goodmem.db.util.UuidUtil.fromProtoBytes(rootUserIdBytes).getValue().toString());
    
    System.out.println("Root API Key: " + rootApiKey);
    System.out.println("Root User ID: " + rootUserId);
    
    // =========================================================================
    // Step 2: Initialize the system again (should report already initialized)
    // =========================================================================
    System.out.println("\nStep 2: Initialize the system again");
    
    TestStreamObserver<InitializeSystemResponse> reInitObserver = new TestStreamObserver<>();
    userService.initializeSystem(InitializeSystemRequest.newBuilder().build(), reInitObserver);
    
    // Verify "already initialized" response
    assertFalse(reInitObserver.hasError(), "Re-initialization should not error");
    assertTrue(reInitObserver.hasValue(), "Should have re-initialization response");
    
    InitializeSystemResponse reInitResponse = reInitObserver.getValue();
    assertTrue(reInitResponse.getAlreadyInitialized(), "System should be marked as already initialized");
    assertEquals("System is already initialized", reInitResponse.getMessage());
    assertEquals("", reInitResponse.getRootApiKey(), "API key should not be included");
    assertEquals(ByteString.EMPTY, reInitResponse.getUserId(), "User ID should not be included");
    
    // =========================================================================
    // Step 3: Authenticate using API key and retrieve current user
    // =========================================================================
    System.out.println("\nStep 3: Authenticate with API key and get current user");
    
    // Create metadata with API key
    Metadata metadata = new Metadata();
    Metadata.Key<String> apiKeyMetadataKey = Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
    metadata.put(apiKeyMetadataKey, rootApiKey);
    
    // Instead of mocking the interceptor, directly authenticate the way AuthInterceptor does
    try (java.sql.Connection conn = dataSource.getConnection()) {
        // Look up user by API key
        com.goodmem.common.status.StatusOr<java.util.Optional<com.goodmem.db.ApiKeys.UserWithApiKey>> userOr = 
            com.goodmem.db.ApiKeys.getUserByApiKey(conn, rootApiKey);
            
        assertFalse(userOr.isNotOk(), "API key lookup should succeed");
        assertTrue(userOr.getValue().isPresent(), "Should find user with API key");
        
        // Get the user and create a security user
        com.goodmem.db.User dbUser = userOr.getValue().get().user();
        com.goodmem.security.Role role = com.goodmem.security.Roles.ADMIN.role();
        com.goodmem.security.User securityUser = new com.goodmem.security.DefaultUserImpl(dbUser, role);
        
        // Create an authenticated context with the security user
        Context authenticatedContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, securityUser);
        Context currentContext = Context.current(); // Save the current context
        authenticatedContext.attach(); // Attach the authenticated context
        
        // Now perform a user lookup without specifying any user (should return the current authenticated user)
        TestStreamObserver<UserOuterClass.User> currentUserObserver = new TestStreamObserver<>();
    
        try {
            userService.getUser(GetUserRequest.newBuilder().build(), currentUserObserver);
            
            // Verify current user lookup response
            assertFalse(currentUserObserver.hasError(), 
                "Current user lookup should not error: " + 
                (currentUserObserver.hasError() ? currentUserObserver.getError().getMessage() : ""));
            assertTrue(currentUserObserver.hasValue(), "Should have user response");
            
            UserOuterClass.User user = currentUserObserver.getValue();
            assertEquals(rootUserIdBytes, user.getUserId(), "User ID should match root user");
            assertEquals("root", user.getUsername(), "Username should be root");
            assertEquals("root@goodmem.ai", user.getEmail(), "Email should match root user");
            assertEquals("System Root User", user.getDisplayName(), "Display name should match root user");
        } finally {
            currentContext.attach(); // Restore the original context
        }
    }
    
    // =========================================================================
    // Step 4: Retrieve user by UUID
    // =========================================================================
    System.out.println("\nStep 4: Retrieve user by UUID");
    
    // Reauthenticate for next tests
    try (java.sql.Connection conn = dataSource.getConnection()) {
        // Look up user by API key
        com.goodmem.common.status.StatusOr<java.util.Optional<com.goodmem.db.ApiKeys.UserWithApiKey>> userOr = 
            com.goodmem.db.ApiKeys.getUserByApiKey(conn, rootApiKey);
            
        // Get the user and create a security user
        com.goodmem.db.User dbUser = userOr.getValue().get().user();
        com.goodmem.security.Role role = com.goodmem.security.Roles.ADMIN.role();
        com.goodmem.security.User securityUser = new com.goodmem.security.DefaultUserImpl(dbUser, role);
        
        // Create an authenticated context with the security user
        Context authenticatedContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, securityUser);
        Context currentContext = Context.current(); // Save the current context
        
        // Test UUID lookup
        TestStreamObserver<UserOuterClass.User> uuidLookupObserver = new TestStreamObserver<>();
        authenticatedContext.attach(); // Attach the authenticated context
        
        try {
            userService.getUser(
                GetUserRequest.newBuilder()
                    .setUserId(rootUserIdBytes)
                    .build(), 
                uuidLookupObserver);
            
            // Verify UUID lookup response
            assertFalse(uuidLookupObserver.hasError(), 
                "UUID lookup should not error: " + 
                (uuidLookupObserver.hasError() ? uuidLookupObserver.getError().getMessage() : ""));
            assertTrue(uuidLookupObserver.hasValue(), "Should have user response for UUID lookup");
            
            UserOuterClass.User user = uuidLookupObserver.getValue();
            assertEquals(rootUserIdBytes, user.getUserId(), "User ID should match root user");
            assertEquals("root", user.getUsername(), "Username should be root");
            assertEquals("root@goodmem.ai", user.getEmail(), "Email should match root user");
            assertEquals("System Root User", user.getDisplayName(), "Display name should match root user");
        } finally {
            currentContext.attach(); // Restore the original context
        }
        
        // =========================================================================
        // Step 5: Retrieve user by email
        // =========================================================================
        System.out.println("\nStep 5: Retrieve user by email");
        
        TestStreamObserver<UserOuterClass.User> emailLookupObserver = new TestStreamObserver<>();
        authenticatedContext.attach(); // Attach the authenticated context
        
        try {
            userService.getUser(
                GetUserRequest.newBuilder()
                    .setEmail("root@goodmem.ai")
                    .build(), 
                emailLookupObserver);
            
            // Verify email lookup response
            assertFalse(emailLookupObserver.hasError(), 
                "Email lookup should not error: " + 
                (emailLookupObserver.hasError() ? emailLookupObserver.getError().getMessage() : ""));
            assertTrue(emailLookupObserver.hasValue(), "Should have user response for email lookup");
            
            UserOuterClass.User user = emailLookupObserver.getValue();
            assertEquals(rootUserIdBytes, user.getUserId(), "User ID should match root user");
            assertEquals("root", user.getUsername(), "Username should be root");
            assertEquals("root@goodmem.ai", user.getEmail(), "Email should match root user");
            assertEquals("System Root User", user.getDisplayName(), "Display name should match root user");
        } finally {
            currentContext.attach(); // Restore the original context
        }
        
        // =========================================================================
        // Step 6: Test invalid UUID lookup
        // =========================================================================
        System.out.println("\nStep 6: Test invalid UUID lookup");
        
        TestStreamObserver<UserOuterClass.User> invalidUuidObserver = new TestStreamObserver<>();
        authenticatedContext.attach(); // Attach the authenticated context
        
        try {
            // Generate a random UUID that doesn't exist
            UUID nonExistentUserId = UUID.randomUUID();
            ByteString nonExistentIdBytes = Uuids.getBytesFromUUID(nonExistentUserId);
            
            userService.getUser(
                GetUserRequest.newBuilder()
                    .setUserId(nonExistentIdBytes)
                    .build(), 
                invalidUuidObserver);
            
            // Verify invalid UUID response
            assertTrue(invalidUuidObserver.hasError(), "Invalid UUID lookup should return an error");
            StatusRuntimeException exception = (StatusRuntimeException) invalidUuidObserver.getError();
            assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode(), 
                         "Error should be NOT_FOUND");
            assertTrue(exception.getStatus().getDescription().contains("User not found"), 
                       "Error should mention user not found");
        } finally {
            currentContext.attach(); // Restore the original context
        }
        
        // =========================================================================
        // Step 7: Test invalid email lookup
        // =========================================================================
        System.out.println("\nStep 7: Test invalid email lookup");
        
        TestStreamObserver<UserOuterClass.User> invalidEmailObserver = new TestStreamObserver<>();
        authenticatedContext.attach(); // Attach the authenticated context
        
        try {
            userService.getUser(
                GetUserRequest.newBuilder()
                    .setEmail("nonexistent@example.com")
                    .build(), 
                invalidEmailObserver);
            
            // Verify invalid email response
            assertTrue(invalidEmailObserver.hasError(), "Invalid email lookup should return an error");
            StatusRuntimeException exception = (StatusRuntimeException) invalidEmailObserver.getError();
            assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode(), 
                         "Error should be NOT_FOUND");
            assertTrue(exception.getStatus().getDescription().contains("User not found"), 
                       "Error should mention user not found");
        } finally {
            currentContext.attach(); // Restore the original context
        }
    }
  }

  // We no longer need mock classes as we're directly using the real implementations

  /**
   * Test implementation of StreamObserver that captures responses and errors.
   *
   * @param <T> The type of response object.
   */
  static class TestStreamObserver<T> implements StreamObserver<T> {
    private final List<T> values = new ArrayList<>();
    private Throwable error;
    private boolean completed = false;

    @Override
    public void onNext(T value) {
      values.add(value);
    }

    @Override
    public void onError(Throwable t) {
      this.error = t;
    }

    @Override
    public void onCompleted() {
      this.completed = true;
    }

    public boolean hasValue() {
      return !values.isEmpty();
    }

    public T getValue() {
      if (values.isEmpty()) {
        return null;
      }
      return values.get(0);
    }

    public List<T> getValues() {
      return values;
    }

    public boolean hasError() {
      return error != null;
    }

    public Throwable getError() {
      return error;
    }

    public boolean isCompleted() {
      return completed;
    }
  }
}