package com.goodmem;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.db.User;
import com.goodmem.db.Users;
import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import com.goodmem.operations.SystemInitOperation;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.Permission;
import com.google.gson.JsonObject;
import com.google.protobuf.ByteString;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariConfig;
import goodmem.v1.UserOuterClass;
import goodmem.v1.UserOuterClass.GetUserRequest;
import goodmem.v1.UserOuterClass.InitializeSystemRequest;
import goodmem.v1.UserOuterClass.InitializeSystemResponse;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for UserServiceImpl that verify functionality with a real PostgreSQL database.
 *
 * <p>Tests initialize the system, verify user retrieval, and validate error conditions.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserServiceImplTest {

  private static PostgresContext postgresContext;
  private static HikariDataSource dataSource;
  private static UserServiceImpl userService;
  private static SystemInitOperation.InitResult initResult;

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
    
    // Set up the root user manually for tests
    try (Connection connection = dataSource.getConnection()) {
      SystemInitOperation operation = new SystemInitOperation(connection);
      initResult = operation.execute();
      assertTrue(initResult.isSuccess(), "System initialization should succeed");
      assertFalse(initResult.alreadyInitialized(), "System should not be already initialized");
    }
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
  @Order(1)
  void initializeSystem_ShouldSucceedFirstTime() {
    // Verify that our setup already initialized the system
    assertNotNull(initResult.apiKey(), "API key should not be null");
    assertNotNull(initResult.userId(), "User ID should not be null");
  }

  @Test
  @Order(2)
  void initializeSystem_ShouldIndicateAlreadyInitialized() {
    // Create a test observer for the response
    TestStreamObserver<InitializeSystemResponse> responseObserver = new TestStreamObserver<>();
    
    // Call the service method
    userService.initializeSystem(InitializeSystemRequest.newBuilder().build(), responseObserver);
    
    // Verify the response indicates the system is already initialized
    assertFalse(responseObserver.hasError(), "Should not have error: " + responseObserver.getError());
    assertTrue(responseObserver.hasValue(), "Should have response value");
    
    InitializeSystemResponse response = responseObserver.getValue();
    assertTrue(response.getAlreadyInitialized(), "System should be marked as already initialized");
    assertEquals("System is already initialized", response.getMessage());
    assertEquals("", response.getRootApiKey(), "Should not include API key for already initialized system");
    assertEquals(ByteString.EMPTY, response.getUserId(), "Should not include user ID for already initialized system");
  }

  @Test
  @Order(3)
  void getUser_NoArgs_ShouldReturnRootUser() {
    // Get the root user ID from the init result
    UUID rootUserId = initResult.userId();
    
    // Create an authenticated context with admin permissions
    Set<Permission> permissions = new HashSet<>();
    permissions.add(Permission.DISPLAY_USER_ANY);
    MockUser authUser = new MockUser(rootUserId, "root@goodmem.ai", "System Root User", permissions);
    
    // Set the authenticated user in the Context
    Context context = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, authUser);
    Context previousContext = context.attach();
    
    try {
      // Create a test observer for the response
      TestStreamObserver<UserOuterClass.User> responseObserver = new TestStreamObserver<>();
      
      // Call getUser with no arguments (should return current user)
      userService.getUser(GetUserRequest.newBuilder().build(), responseObserver);
      
      // Verify response
      assertFalse(responseObserver.hasError(), 
          "Should not have error: " + (responseObserver.hasError() ? responseObserver.getError().getMessage() : ""));
      assertTrue(responseObserver.hasValue(), "Should have response value");
      
      UserOuterClass.User user = responseObserver.getValue();
      assertEquals(Uuids.getBytesFromUUID(rootUserId), user.getUserId());
      assertEquals("root", user.getUsername());
      assertEquals("root@goodmem.ai", user.getEmail());
      assertEquals("System Root User", user.getDisplayName());
    } finally {
      // Clean up context
      context.detach(previousContext);
    }
  }

  @Test
  @Order(4)
  void getUser_WithUuid_ShouldReturnUser() {
    // Get the root user ID from the init result
    UUID rootUserId = initResult.userId();
    
    // Create an authenticated context with admin permissions
    Set<Permission> permissions = new HashSet<>();
    permissions.add(Permission.DISPLAY_USER_ANY);
    MockUser authUser = new MockUser(rootUserId, "root@goodmem.ai", "System Root User", permissions);
    
    // Set the authenticated user in the Context
    Context context = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, authUser);
    Context previousContext = context.attach();
    
    try {
      // Create a test observer for the response
      TestStreamObserver<UserOuterClass.User> responseObserver = new TestStreamObserver<>();
      
      // Call getUser with specific user ID
      ByteString userIdBytes = Uuids.getBytesFromUUID(rootUserId);
      userService.getUser(
          GetUserRequest.newBuilder()
              .setUserId(userIdBytes)
              .build(),
          responseObserver);
      
      // Verify response
      assertFalse(responseObserver.hasError(), 
          "Should not have error: " + (responseObserver.hasError() ? responseObserver.getError().getMessage() : ""));
      assertTrue(responseObserver.hasValue(), "Should have response value");
      
      UserOuterClass.User user = responseObserver.getValue();
      assertEquals(Uuids.getBytesFromUUID(rootUserId), user.getUserId());
      assertEquals("root", user.getUsername());
      assertEquals("root@goodmem.ai", user.getEmail());
      assertEquals("System Root User", user.getDisplayName());
    } finally {
      // Clean up context
      context.detach(previousContext);
    }
  }

  @Test
  @Order(5)
  void getUser_WithEmail_ShouldReturnUser() {
    // Get the root user ID from the init result
    UUID rootUserId = initResult.userId();
    
    // Create an authenticated context with admin permissions
    Set<Permission> permissions = new HashSet<>();
    permissions.add(Permission.DISPLAY_USER_ANY);
    MockUser authUser = new MockUser(rootUserId, "root@goodmem.ai", "System Root User", permissions);
    
    // Set the authenticated user in the Context
    Context context = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, authUser);
    Context previousContext = context.attach();
    
    try {
      // Create a test observer for the response
      TestStreamObserver<UserOuterClass.User> responseObserver = new TestStreamObserver<>();
      
      // Call getUser with email
      userService.getUser(
          GetUserRequest.newBuilder()
              .setEmail("root@goodmem.ai")
              .build(),
          responseObserver);
      
      // Verify response
      assertFalse(responseObserver.hasError(), 
          "Should not have error: " + (responseObserver.hasError() ? responseObserver.getError().getMessage() : ""));
      assertTrue(responseObserver.hasValue(), "Should have response value");
      
      UserOuterClass.User user = responseObserver.getValue();
      assertEquals(Uuids.getBytesFromUUID(rootUserId), user.getUserId());
      assertEquals("root", user.getUsername());
      assertEquals("root@goodmem.ai", user.getEmail());
      assertEquals("System Root User", user.getDisplayName());
    } finally {
      // Clean up context
      context.detach(previousContext);
    }
  }

  @Test
  @Order(6)
  void getUser_WithInvalidUuid_ShouldReturnNotFound() {
    // Get the root user ID from the init result
    UUID rootUserId = initResult.userId();
    UUID nonExistentUserId = UUID.randomUUID();
    
    // Create an authenticated context with admin permissions
    Set<Permission> permissions = new HashSet<>();
    permissions.add(Permission.DISPLAY_USER_ANY);
    MockUser authUser = new MockUser(rootUserId, "root@goodmem.ai", "System Root User", permissions);
    
    // Set the authenticated user in the Context
    Context context = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, authUser);
    Context previousContext = context.attach();
    
    try {
      // Create a test observer for the response
      TestStreamObserver<UserOuterClass.User> responseObserver = new TestStreamObserver<>();
      
      // Call getUser with invalid UUID
      ByteString nonExistentIdBytes = Uuids.getBytesFromUUID(nonExistentUserId);
      userService.getUser(
          GetUserRequest.newBuilder()
              .setUserId(nonExistentIdBytes)
              .build(),
          responseObserver);
      
      // Verify response
      assertTrue(responseObserver.hasError(), "Should have an error");
      StatusRuntimeException exception = (StatusRuntimeException) responseObserver.getError();
      assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode());
      assertTrue(exception.getStatus().getDescription().contains("User not found"));
    } finally {
      // Clean up context
      context.detach(previousContext);
    }
  }

  @Test
  @Order(7)
  void getUser_WithInvalidEmail_ShouldReturnNotFound() {
    // Get the root user ID from the init result
    UUID rootUserId = initResult.userId();
    
    // Create an authenticated context with admin permissions
    Set<Permission> permissions = new HashSet<>();
    permissions.add(Permission.DISPLAY_USER_ANY);
    MockUser authUser = new MockUser(rootUserId, "root@goodmem.ai", "System Root User", permissions);
    
    // Set the authenticated user in the Context
    Context context = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, authUser);
    Context previousContext = context.attach();
    
    try {
      // Create a test observer for the response
      TestStreamObserver<UserOuterClass.User> responseObserver = new TestStreamObserver<>();
      
      // Call getUser with invalid email
      userService.getUser(
          GetUserRequest.newBuilder()
              .setEmail("nonexistent@example.com")
              .build(),
          responseObserver);
      
      // Verify response
      assertTrue(responseObserver.hasError(), "Should have an error");
      StatusRuntimeException exception = (StatusRuntimeException) responseObserver.getError();
      assertEquals(Status.NOT_FOUND.getCode(), exception.getStatus().getCode());
      assertTrue(exception.getStatus().getDescription().contains("User not found"));
    } finally {
      // Clean up context
      context.detach(previousContext);
    }
  }

  /**
   * Test mock implementation of the User interface for testing.
   */
  static class MockUser implements com.goodmem.security.User {
    private final UUID id;
    private final String email;
    private final String displayName;
    private final Set<Permission> permissions;

    public MockUser(UUID id, String email, String displayName, Set<Permission> permissions) {
      this.id = id;
      this.email = email;
      this.displayName = displayName;
      this.permissions = permissions;
    }

    @Override
    public UUID getId() {
      return id;
    }

    @Override
    public String getEmail() {
      return email;
    }

    @Override
    public String getDisplayName() {
      return displayName;
    }

    @Override
    public Instant getActiveDate() {
      return Instant.EPOCH;
    }

    @Override
    public Instant getInactiveDate() {
      return Instant.MAX;
    }

    @Override
    public Instant getLastLogin() {
      return Instant.now();
    }

    @Override
    public JsonObject getAdditionalAttributes() {
      return new JsonObject();
    }

    @Override
    public boolean isActive(Instant when) {
      return true;
    }

    @Override
    public boolean isActive() {
      return true;
    }

    @Override
    public boolean hasPermission(Permission permission) {
      return permissions.contains(permission);
    }
  }

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