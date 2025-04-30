package com.goodmem;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.Permission;
import com.goodmem.security.Role;
import com.goodmem.security.Roles;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.SpaceOuterClass;
import goodmem.v1.SpaceOuterClass.CreateSpaceRequest;
import goodmem.v1.SpaceOuterClass.GetSpaceRequest;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.UserOuterClass;
import goodmem.v1.UserOuterClass.InitializeSystemRequest;
import goodmem.v1.UserOuterClass.InitializeSystemResponse;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for SpaceServiceImpl that verify functionality with a real PostgreSQL database.
 *
 * <p>This test follows a sequential workflow to test space operations:
 * 1. Initialize the system and get a root user and API key
 * 2. Create a space with the authenticated user as owner
 * 3. Create a second user
 * 4. Create a space for another user (requires CREATE_SPACE_ANY permission)
 * 5. Test creating a space with a name that already exists for the owner (should fail)
 * 6. Test creating a space with a name that exists for another user (should succeed)
 * 7. Test creating a space with labels and verify they're persisted
 */
@Testcontainers
public class SpaceServiceImplTest {

  private static PostgresContext postgresContext;
  private static HikariDataSource dataSource;
  private static UserServiceImpl userService;
  private static SpaceServiceImpl spaceService;
  private static AuthInterceptor authInterceptor;

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_spaceservice_test", SpaceServiceImplTest.class);
    
    // Create a HikariDataSource for the test container
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgresContext.getContainer().getJdbcUrl());
    config.setUsername(postgresContext.getContainer().getUsername());
    config.setPassword(postgresContext.getContainer().getPassword());
    config.setMaximumPoolSize(2);
    dataSource = new HikariDataSource(config);
    
    // Create the UserServiceImpl with the test datasource
    userService = new UserServiceImpl(new UserServiceImpl.Config(dataSource));
    
    // Create the SpaceServiceImpl with the test datasource
    spaceService = new SpaceServiceImpl(new SpaceServiceImpl.Config(dataSource, "openai-ada-002"));
    
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
  void testSpaceServiceOperations() throws SQLException {
    // =========================================================================
    // Step 1: Initialize the system and authenticate as root user
    // =========================================================================
    System.out.println("Step 1: Initialize the system");
    
    TestStreamObserver<InitializeSystemResponse> initObserver = new TestStreamObserver<>();
    userService.initializeSystem(InitializeSystemRequest.newBuilder().build(), initObserver);
    
    // Verify successful initialization
    assertFalse(initObserver.hasError(), 
        "System initialization should not error: " + (initObserver.hasError() ? initObserver.getError().getMessage() : ""));
    assertTrue(initObserver.hasValue(), "Should have initialization response");
    
    InitializeSystemResponse initResponse = initObserver.getValue();
    assertFalse(initResponse.getAlreadyInitialized(), "System should not already be initialized");
    assertEquals("System initialized successfully", initResponse.getMessage());
    
    // Extract and store the root API key and user ID for subsequent tests
    String rootApiKey = initResponse.getRootApiKey();
    ByteString rootUserIdBytes = initResponse.getUserId();
    UUID rootUserId = UUID.fromString(com.goodmem.db.util.UuidUtil.fromProtoBytes(rootUserIdBytes).getValue().toString());
    
    System.out.println("Root API Key: " + rootApiKey);
    System.out.println("Root User ID: " + rootUserId);
    
    // Authenticate as the root user for subsequent tests
    com.goodmem.security.User rootUser = authenticateUser(rootApiKey, rootUserId, Roles.ROOT.role());
    
    // =========================================================================
    // Step 2: Create a space with the authenticated user as owner
    // =========================================================================
    System.out.println("\nStep 2: Create a space with authenticated user as owner");
    
    Context authenticatedContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, rootUser);
    Context previousContext = authenticatedContext.attach();
    
    try {
      // Create a space without specifying owner (defaults to authenticated user)
      TestStreamObserver<Space> createSpaceObserver = new TestStreamObserver<>();
      CreateSpaceRequest ownSpaceRequest = CreateSpaceRequest.newBuilder()
          .setName("Root's Space")
          .putLabels("env", "test")
          .putLabels("owner", "root")
          .setPublicRead(true)
          .build();
      
      spaceService.createSpace(ownSpaceRequest, createSpaceObserver);
      
      // Verify space creation response
      assertFalse(createSpaceObserver.hasError(), 
          "Space creation should not error: " + 
          (createSpaceObserver.hasError() ? createSpaceObserver.getError().getMessage() : ""));
      assertTrue(createSpaceObserver.hasValue(), "Should have space response");
      
      Space createdSpace = createSpaceObserver.getValue();
      assertNotNull(createdSpace.getSpaceId(), "Space ID should be assigned");
      assertEquals("Root's Space", createdSpace.getName(), "Space name should match request");
      assertEquals(rootUserIdBytes, createdSpace.getOwnerId(), "Owner ID should be root user");
      assertEquals(rootUserIdBytes, createdSpace.getCreatedById(), "Creator ID should be root user");
      assertEquals(rootUserIdBytes, createdSpace.getUpdatedById(), "Updater ID should be root user");
      assertEquals("openai-ada-002", createdSpace.getEmbeddingModel(), "Default embedding model should be used");
      assertTrue(createdSpace.getPublicRead(), "Public read flag should be set");
      
      // Verify labels
      assertEquals(2, createdSpace.getLabelsMap().size(), "Should have 2 labels");
      assertEquals("test", createdSpace.getLabelsMap().get("env"), "Environment label should match");
      assertEquals("root", createdSpace.getLabelsMap().get("owner"), "Owner label should match");
      
      // Save space ID for future tests
      UUID rootSpaceId = com.goodmem.db.util.UuidUtil.fromProtoBytes(createdSpace.getSpaceId()).getValue();
      System.out.println("Created space ID: " + rootSpaceId);
      
      // =========================================================================
      // Step 3: Create a second user
      // =========================================================================
      System.out.println("\nStep 3: Create a second user");
      
      // Create a new user directly in the database
      UUID secondUserId = createUserInDatabase(
          "testuser", 
          "Test User", 
          "testuser@example.com", 
          "password123", 
          Roles.USER.role().getName(),
          rootUserId);
      
      System.out.println("Created second user ID: " + secondUserId);
      
      // =========================================================================
      // Step 4: Create a space for another user (requires CREATE_SPACE_ANY)
      // =========================================================================
      System.out.println("\nStep 4: Create a space for another user");
      
      TestStreamObserver<Space> createSpaceForOtherObserver = new TestStreamObserver<>();
      CreateSpaceRequest otherSpaceRequest = CreateSpaceRequest.newBuilder()
          .setName("Test User's Space")
          .putLabels("env", "test")
          .putLabels("owner", "testuser")
          .setOwnerId(com.goodmem.db.util.UuidUtil.toProtoBytes(secondUserId))
          .setPublicRead(false)
          .build();
      
      spaceService.createSpace(otherSpaceRequest, createSpaceForOtherObserver);
      
      // Verify space creation for other user
      assertFalse(createSpaceForOtherObserver.hasError(), 
          "Create space for other user should not error: " + 
          (createSpaceForOtherObserver.hasError() ? createSpaceForOtherObserver.getError().getMessage() : ""));
      assertTrue(createSpaceForOtherObserver.hasValue(), "Should have space response");
      
      Space otherUserSpace = createSpaceForOtherObserver.getValue();
      assertEquals("Test User's Space", otherUserSpace.getName(), "Space name should match request");
      
      ByteString secondUserIdBytes = com.goodmem.db.util.UuidUtil.toProtoBytes(secondUserId);
      assertEquals(secondUserIdBytes, otherUserSpace.getOwnerId(), "Owner ID should be second user");
      assertEquals(rootUserIdBytes, otherUserSpace.getCreatedById(), "Creator ID should be root user");
      assertFalse(otherUserSpace.getPublicRead(), "Public read flag should not be set");
      
      // Save space ID for future tests
      UUID otherSpaceId = com.goodmem.db.util.UuidUtil.fromProtoBytes(otherUserSpace.getSpaceId()).getValue();
      System.out.println("Created other space ID: " + otherSpaceId);
      
      // =========================================================================
      // Step 5: Test creating space with a name that already exists for owner (should fail)
      // =========================================================================
      System.out.println("\nStep 5: Test creating space with existing name for same owner");
      
      TestStreamObserver<Space> duplicateNameObserver = new TestStreamObserver<>();
      CreateSpaceRequest duplicateNameRequest = CreateSpaceRequest.newBuilder()
          .setName("Root's Space") // Same name as first space
          .putLabels("env", "duplicate")
          .setPublicRead(true)
          .build();
      
      spaceService.createSpace(duplicateNameRequest, duplicateNameObserver);
      
      // Verify duplicate name error
      assertTrue(duplicateNameObserver.hasError(), "Duplicate name should cause error");
      StatusRuntimeException exception = (StatusRuntimeException) duplicateNameObserver.getError();
      assertEquals(Status.ALREADY_EXISTS.getCode(), exception.getStatus().getCode(), 
                   "Error should be ALREADY_EXISTS");
      assertTrue(exception.getStatus().getDescription().contains("already exists"), 
                 "Error should mention space already exists");
      
      // =========================================================================
      // Step 6: Test creating space with name that exists for another user (should succeed)
      // =========================================================================
      System.out.println("\nStep 6: Test creating space with name that exists for another user");
      
      TestStreamObserver<Space> sameNameDifferentOwnerObserver = new TestStreamObserver<>();
      CreateSpaceRequest sameNameDifferentOwnerRequest = CreateSpaceRequest.newBuilder()
          .setName("Test User's Space") // Same name as second user's space
          .putLabels("env", "test")
          .putLabels("duplicate", "true")
          .setPublicRead(true)
          .build();
      
      spaceService.createSpace(sameNameDifferentOwnerRequest, sameNameDifferentOwnerObserver);
      
      // Verify successful creation despite name collision with different owner
      assertFalse(sameNameDifferentOwnerObserver.hasError(), 
          "Same name with different owner should not error: " + 
          (sameNameDifferentOwnerObserver.hasError() ? sameNameDifferentOwnerObserver.getError().getMessage() : ""));
      assertTrue(sameNameDifferentOwnerObserver.hasValue(), "Should have space response");
      
      Space sameNameDifferentOwnerSpace = sameNameDifferentOwnerObserver.getValue();
      assertEquals("Test User's Space", sameNameDifferentOwnerSpace.getName(), "Space name should match request");
      assertEquals(rootUserIdBytes, sameNameDifferentOwnerSpace.getOwnerId(), "Owner ID should be root user");
      assertTrue(sameNameDifferentOwnerSpace.getPublicRead(), "Public read flag should be set");
      
      // =========================================================================
      // Step 7: Test creating space with many labels and verify they're persisted
      // =========================================================================
      System.out.println("\nStep 7: Test creating space with many labels");
      
      TestStreamObserver<Space> manyLabelsObserver = new TestStreamObserver<>();
      
      // Create a space with many labels
      CreateSpaceRequest.Builder manyLabelsRequestBuilder = CreateSpaceRequest.newBuilder()
          .setName("Many Labels Space")
          .setPublicRead(true);
      
      // Add 10 labels
      for (int i = 0; i < 10; i++) {
        manyLabelsRequestBuilder.putLabels("key" + i, "value" + i);
      }
      
      // Special labels
      manyLabelsRequestBuilder.putLabels("user", "root");
      manyLabelsRequestBuilder.putLabels("bot", "assistant");
      manyLabelsRequestBuilder.putLabels("org", "goodmem");
      
      CreateSpaceRequest manyLabelsRequest = manyLabelsRequestBuilder.build();
      spaceService.createSpace(manyLabelsRequest, manyLabelsObserver);
      
      // Verify space creation with many labels
      assertFalse(manyLabelsObserver.hasError(), 
          "Create space with many labels should not error: " + 
          (manyLabelsObserver.hasError() ? manyLabelsObserver.getError().getMessage() : ""));
      assertTrue(manyLabelsObserver.hasValue(), "Should have space response");
      
      Space manyLabelsSpace = manyLabelsObserver.getValue();
      assertEquals("Many Labels Space", manyLabelsSpace.getName(), "Space name should match request");
      assertEquals(13, manyLabelsSpace.getLabelsMap().size(), "Should have 13 labels");
      
      // Verify special labels
      assertEquals("root", manyLabelsSpace.getLabelsMap().get("user"), "User label should match");
      assertEquals("assistant", manyLabelsSpace.getLabelsMap().get("bot"), "Bot label should match");
      assertEquals("goodmem", manyLabelsSpace.getLabelsMap().get("org"), "Org label should match");
      
      // Verify standard labels
      for (int i = 0; i < 10; i++) {
        assertEquals("value" + i, manyLabelsSpace.getLabelsMap().get("key" + i), 
                    "Label key" + i + " should match");
      }
      
      // Save space ID
      UUID manyLabelsSpaceId = com.goodmem.db.util.UuidUtil.fromProtoBytes(manyLabelsSpace.getSpaceId()).getValue();
      
      // =========================================================================
      // Step 8: Retrieve the space and verify all labels are present
      // =========================================================================
      System.out.println("\nStep 8: Retrieve space and verify labels persistence");
      
      // Get the space to verify label persistence
      TestStreamObserver<Space> getSpaceObserver = new TestStreamObserver<>();
      GetSpaceRequest getSpaceRequest = GetSpaceRequest.newBuilder()
          .setSpaceId(com.goodmem.db.util.UuidUtil.toProtoBytes(manyLabelsSpaceId))
          .build();
      
      spaceService.getSpace(getSpaceRequest, getSpaceObserver);
      
      // Verify get space response
      // Note: This test is assuming getSpace is implemented. If not, you'd need to 
      // query the database directly to verify label persistence.
      assertTrue(manyLabelsSpace.getLabelsMap().equals(manyLabelsRequest.getLabelsMap()),
          "Labels in created space should match requested labels");
      
      // Direct database verification of labels to ensure persistence
      Map<String, String> labelsFromDb = getSpaceLabelsFromDatabase(manyLabelsSpaceId);
      
      // Print for debugging
      System.out.println("Expected labels: " + manyLabelsRequest.getLabelsMap());
      System.out.println("Actual labels from DB: " + labelsFromDb);
      
      // Now verify the labels using our comparison logic
      assertTrue(compareLabels(manyLabelsRequest.getLabelsMap(), labelsFromDb),
          "Labels in database should match requested labels");
      
    } finally {
      previousContext.attach(); // Restore original context
    }
  }
  
  /**
   * Simulates authenticating a user by retrieving them from the database
   * and creating a security user object.
   */
  private com.goodmem.security.User authenticateUser(String apiKey, UUID userId, Role role) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // For test purposes, we'll bypass the full authentication and directly
      // create a security user with the specified role
      String sql = "SELECT user_id, username, display_name, email FROM \"user\" WHERE user_id = ?";
      
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, userId);
        
        try (ResultSet rs = stmt.executeQuery()) {
          if (rs.next()) {
            String username = rs.getString("username");
            String displayName = rs.getString("display_name");
            String email = rs.getString("email");
            
            com.goodmem.db.User dbUser = new com.goodmem.db.User(
                userId,
                username,
                email,
                displayName,
                java.time.Instant.now(),
                java.time.Instant.now()
            );
            
            return new com.goodmem.security.DefaultUserImpl(dbUser, role);
          } else {
            throw new RuntimeException("User not found: " + userId);
          }
        }
      }
    }
  }
  
  /**
   * Creates a user directly in the database for testing purposes.
   */
  private UUID createUserInDatabase(
      String username, 
      String displayName, 
      String email, 
      String password,
      String roleName,
      UUID createdByUserId) throws SQLException {
    
    try (Connection conn = dataSource.getConnection()) {
      UUID userId = UUID.randomUUID();
      
      // Insert the user
      String sql = "INSERT INTO \"user\" (user_id, username, display_name, email, " +
                  "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
      
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, userId);
        stmt.setString(2, username);
        stmt.setString(3, displayName);
        stmt.setString(4, email);
        stmt.setTimestamp(5, java.sql.Timestamp.from(java.time.Instant.now()));
        stmt.setTimestamp(6, java.sql.Timestamp.from(java.time.Instant.now()));
        
        int rowsAffected = stmt.executeUpdate();
        if (rowsAffected != 1) {
          throw new RuntimeException("Failed to insert user");
        }
      }
      
      // Insert a role for the user
      sql = "INSERT INTO user_role (user_id, role_name) VALUES (?, ?)";
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, userId);
        stmt.setString(2, roleName);
        
        int rowsAffected = stmt.executeUpdate();
        if (rowsAffected != 1) {
          throw new RuntimeException("Failed to insert user role");
        }
      }
      
      // Create an API key for the user using the security.ApiKey class
      java.security.SecureRandom secureRandom = new java.security.SecureRandom();
      com.goodmem.security.ApiKey securityApiKey = com.goodmem.security.ApiKey.newKey(secureRandom);
      
      // Create a db.ApiKey from the security.ApiKey
      UUID apiKeyId = UUID.randomUUID();
      java.time.Instant now = java.time.Instant.now();
      com.goodmem.db.ApiKey dbApiKey = new com.goodmem.db.ApiKey(
          apiKeyId,
          userId,
          securityApiKey.displayPrefix(),
          securityApiKey.hashedKeyMaterial(),
          "ACTIVE",
          java.util.Map.of(), // Empty labels
          null, // No expiration
          null, // No last used timestamp
          now,
          now,
          createdByUserId,
          createdByUserId
      );
      
      // Save the API key using the ApiKeys helper
      com.goodmem.common.status.StatusOr<Integer> saveResult = com.goodmem.db.ApiKeys.save(conn, dbApiKey);
      
      if (saveResult.isNotOk()) {
        throw new RuntimeException("Failed to insert API key: " + saveResult.getStatus().getMessage());
      }
      
      if (saveResult.getValue() != 1) {
        throw new RuntimeException("Failed to insert API key: unexpected rows affected: " + saveResult.getValue());
      }
      
      return userId;
    }
  }
  
  /**
   * Retrieves space labels directly from the database using DbUtil's JSON parsing.
   */
  private Map<String, String> getSpaceLabelsFromDatabase(UUID spaceId) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      String sql = "SELECT labels FROM space WHERE space_id = ?";
      
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, spaceId);
        
        try (ResultSet rs = stmt.executeQuery()) {
          if (rs.next()) {
            // Use the DbUtil method for JSONB parsing
            com.goodmem.common.status.StatusOr<Map<String, String>> labelsOr = 
                com.goodmem.db.util.DbUtil.parseJsonbToMap(rs, "labels");
            
            if (labelsOr.isNotOk()) {
              System.err.println("Error parsing labels: " + labelsOr.getStatus().getMessage());
              return Map.of(); // Return empty map on parsing error
            }
            
            return labelsOr.getValue();
          } else {
            throw new RuntimeException("Space not found: " + spaceId);
          }
        }
      }
    }
  }
  
  /**
   * Compares two label maps for equality.
   */
  private boolean compareLabels(Map<String, String> map1, Map<String, String> map2) {
    if (map1.size() != map2.size()) {
      return false;
    }
    
    for (Map.Entry<String, String> entry : map1.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      
      if (!map2.containsKey(key) || !map2.get(key).equals(value)) {
        return false;
      }
    }
    
    return true;
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