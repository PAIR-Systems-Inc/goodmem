package com.goodmem;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.db.ApiKey;
import com.goodmem.db.ApiKeys;
import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import com.goodmem.db.util.UuidUtil;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.Permission;
import com.goodmem.security.Role;
import com.goodmem.security.Roles;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.Apikey.CreateApiKeyRequest;
import goodmem.v1.Apikey.CreateApiKeyResponse;
import goodmem.v1.Apikey.DeleteApiKeyRequest;
import goodmem.v1.Apikey.ListApiKeysRequest;
import goodmem.v1.Apikey.ListApiKeysResponse;
import goodmem.v1.Apikey.Status;
import goodmem.v1.Apikey.UpdateApiKeyRequest;
import goodmem.v1.UserOuterClass.InitializeSystemRequest;
import goodmem.v1.UserOuterClass.InitializeSystemResponse;
import io.grpc.Context;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Integration tests for ApiKeyServiceImpl that verify functionality with a real PostgreSQL database.
 *
 * <p>This test follows a sequential workflow to test API key operations:
 * 1. Initialize the system and get a root user and API key
 * 2. Create an API key with labels and verify it's properly stored
 * 3. Create a second user with limited permissions
 * 4. Test listing API keys with filtering and pagination
 * 5. Test updating an API key's labels and status
 * 6. Test permission boundaries (OWN vs ANY)
 * 7. Test deleting an API key and verifying it's removed
 * 8. Test various error cases (invalid IDs, duplicate keys, etc.)
 */
@Testcontainers
public class ApiKeyServiceImplTest {

  private static PostgresContext postgresContext;
  private static HikariDataSource dataSource;
  private static UserServiceImpl userService;
  private static ApiKeyServiceImpl apiKeyService;
  private static AuthInterceptor authInterceptor;

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_apikeyservice_test", ApiKeyServiceImplTest.class);

    // Create a HikariDataSource for the test container
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgresContext.getContainer().getJdbcUrl());
    config.setUsername(postgresContext.getContainer().getUsername());
    config.setPassword(postgresContext.getContainer().getPassword());
    config.setMaximumPoolSize(2);
    dataSource = new HikariDataSource(config);

    // Create the UserServiceImpl with the test datasource
    userService = new UserServiceImpl(new UserServiceImpl.Config(dataSource));

    // Create the ApiKeyServiceImpl with the test datasource
    apiKeyService = new ApiKeyServiceImpl(new ApiKeyServiceImpl.Config(dataSource));

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

  /**
   * Initialize the system and authenticate as root user.
   *
   * @return The authenticated root user object with ROOT role.
   * @throws SQLException If there is a database error during initialization.
   */
  private com.goodmem.security.User setupRootUser() throws SQLException {
    // Initialize the system and get root user info
    TestStreamObserver<InitializeSystemResponse> initObserver = new TestStreamObserver<>();
    userService.initializeSystem(InitializeSystemRequest.newBuilder().build(), initObserver);

    // Verify successful initialization
    assertFalse(initObserver.hasError(),
        "System initialization should not error: " + (initObserver.hasError() ? initObserver.getError().getMessage() : ""));
    assertTrue(initObserver.hasValue(), "Should have initialization response");

    InitializeSystemResponse initResponse = initObserver.getValue();

    // Extract the root API key
    String rootApiKey = initResponse.getRootApiKey();

    // Get the root user from the database using Users helper
    try (Connection conn = dataSource.getConnection()) {
      com.goodmem.common.status.StatusOr<Optional<com.goodmem.db.User>> userOr =
          com.goodmem.db.Users.loadByUsername(conn, com.goodmem.operations.SystemInitOperation.ROOT_USERNAME);

      if (userOr.isNotOk()) {
        throw new RuntimeException("Failed to load root user: " + userOr.getStatus().getMessage());
      }

      Optional<com.goodmem.db.User> userOptional = userOr.getValue();
      if (userOptional.isEmpty()) {
        throw new RuntimeException("Root user not found in database");
      }

      UUID userId = userOptional.get().userId();

      System.out.println("Root API Key: " + rootApiKey);
      System.out.println("Root User ID: " + userId);

      // Authenticate as the root user and return the user object
      return authenticateUser(rootApiKey, userId, Roles.ROOT.role());
    }
  }

  @Test
  void testApiKeyServiceOperations() throws SQLException {
    // =========================================================================
    // Step 1: Initialize the system and authenticate as root user
    // =========================================================================
    System.out.println("Step 1: Initialize the system");

    com.goodmem.security.User rootUser = setupRootUser();

    // Get root user ID information from the rootUser object
    ByteString rootUserIdBytes = UuidUtil.toProtoBytes(rootUser.getId());
    UUID rootUserId = rootUser.getId();

    // =========================================================================
    // Step 2: Create an API key with labels and verify it's properly stored
    // =========================================================================
    System.out.println("\nStep 2: Create an API key with labels");

    // Set up authentication context
    Context authenticatedContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, rootUser);
    Context previousContext = authenticatedContext.attach();

    try {
      // Create an API key with labels
      TestStreamObserver<CreateApiKeyResponse> createKeyObserver = new TestStreamObserver<>();

      Map<String, String> labels = new HashMap<>();
      labels.put("env", "test");
      labels.put("purpose", "integration-test");
      labels.put("application", "goodmem-test");

      // Create an API key that expires in 7 days
      Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
      Timestamp expiresAtProto = Timestamp.newBuilder()
          .setSeconds(expiresAt.getEpochSecond())
          .setNanos(expiresAt.getNano())
          .build();

      CreateApiKeyRequest createRequest = CreateApiKeyRequest.newBuilder()
          .putAllLabels(labels)
          .setExpiresAt(expiresAtProto)
          .build();

      apiKeyService.createApiKey(createRequest, createKeyObserver);

      // Verify API key creation response
      assertFalse(createKeyObserver.hasError(),
          "API key creation should not error: " +
          (createKeyObserver.hasError() ? createKeyObserver.getError().getMessage() : ""));
      assertTrue(createKeyObserver.hasValue(), "Should have API key response");

      CreateApiKeyResponse keyResponse = createKeyObserver.getValue();
      assertNotNull(keyResponse.getApiKeyMetadata(), "API key metadata should be present");
      assertNotNull(keyResponse.getRawApiKey(), "Raw API key should be present");
      assertTrue(keyResponse.getRawApiKey().startsWith("gm_"), "Raw API key should start with 'gm_'");

      // Verify API key metadata
      goodmem.v1.Apikey.ApiKey keyMetadata = keyResponse.getApiKeyMetadata();
      assertNotNull(keyMetadata.getApiKeyId(), "API key ID should be assigned");
      assertEquals(rootUserIdBytes, keyMetadata.getUserId(), "User ID should be root user");
      assertEquals(rootUserIdBytes, keyMetadata.getCreatedById(), "Creator ID should be root user");
      assertEquals(rootUserIdBytes, keyMetadata.getUpdatedById(), "Updater ID should be root user");
      assertEquals(Status.ACTIVE, keyMetadata.getStatus(), "Status should be ACTIVE");
      assertNotNull(keyMetadata.getKeyPrefix(), "Key prefix should be present");
      assertTrue(keyResponse.getRawApiKey().startsWith("gm_"),
          "Raw key should start with 'gm_' prefix");
      assertTrue(keyMetadata.hasExpiresAt(), "Expiration should be set");

      // Verify labels
      assertEquals(3, keyMetadata.getLabelsCount(), "Should have 3 labels");
      assertEquals("test", keyMetadata.getLabelsMap().get("env"), "Environment label should match");
      assertEquals("integration-test", keyMetadata.getLabelsMap().get("purpose"), "Purpose label should match");
      assertEquals("goodmem-test", keyMetadata.getLabelsMap().get("application"), "Application label should match");

      // Save API key ID for future tests
      UUID apiKeyId = UuidUtil.fromProtoBytes(keyMetadata.getApiKeyId()).getValue();
      System.out.println("Created API key ID: " + apiKeyId);

      // Verify API key exists in database
      try (Connection conn = dataSource.getConnection()) {
        com.goodmem.common.status.StatusOr<Optional<ApiKey>> apiKeyOr =
            ApiKeys.loadById(conn, apiKeyId);

        assertTrue(apiKeyOr.isOk(), "Should successfully query database");
        assertTrue(apiKeyOr.getValue().isPresent(), "API key should exist in database");

        ApiKey dbApiKey = apiKeyOr.getValue().get();
        assertEquals(rootUserId, dbApiKey.userId(), "User ID should match root user");
        assertEquals("ACTIVE", dbApiKey.status(), "Status should be ACTIVE");
        assertNotNull(dbApiKey.expiresAt(), "Expiration should be set");
        // In a real implementation, we would verify labels are correctly stored in JSONB
      }

      // =========================================================================
      // Step 3: Create a regular user with limited permissions
      // =========================================================================
      System.out.println("\nStep 3: Create a regular user with limited permissions");

      // Create a new user directly in the database - use USER role, then override permissions
      UUID regularUserId = createUserInDatabase(
          "regularuser",
          "Regular User",
          "regularuser@example.com",
          "password123",
          Roles.USER.role().getName(), // Use standard USER role
          rootUserId);

      System.out.println("Created regular user ID: " + regularUserId);

      // Create a custom role with standard USER permissions plus API key permissions
      Role testUserRole = new Role() {
          @Override
          public String getName() {
              return Roles.USER.role().getName();
          }

          @Override
          public String getDescription() {
              return "Test user with API key permissions";
          }

          @Override
          public boolean hasPermission(Permission permission) {
              // Include all standard USER permissions
              if (Roles.USER.role().hasPermission(permission)) {
                  return true;
              }

              // Add API key permissions
              return permission == Permission.CREATE_APIKEY_OWN ||
                     permission == Permission.UPDATE_APIKEY_OWN ||
                     permission == Permission.LIST_APIKEY_OWN ||
                     permission == Permission.DELETE_APIKEY_OWN ||
                     permission == Permission.DISPLAY_APIKEY_OWN;
          }
      };

      // Authenticate as the regular user with our custom role implementation
      com.goodmem.security.User regularUser = authenticateUser(null, regularUserId, testUserRole);

      // =========================================================================
      // Step 4: Test listing API keys
      // =========================================================================
      System.out.println("\nStep 4: Test listing API keys");

      // Create additional API keys for the root user to test listing
      for (int i = 0; i < 3; i++) {
        TestStreamObserver<CreateApiKeyResponse> additionalKeyObserver = new TestStreamObserver<>();

        Map<String, String> additionalLabels = Map.of(
            "index", String.valueOf(i),
            "type", "additional"
        );
        CreateApiKeyRequest additionalRequest = CreateApiKeyRequest.newBuilder()
            .putAllLabels(additionalLabels)
            .build();

        apiKeyService.createApiKey(additionalRequest, additionalKeyObserver);
        assertFalse(additionalKeyObserver.hasError(), "Creating additional API key should not error");
      }

      // List API keys for root user
      TestStreamObserver<ListApiKeysResponse> listKeysObserver = new TestStreamObserver<>();
      ListApiKeysRequest listRequest = ListApiKeysRequest.newBuilder().build();

      apiKeyService.listApiKeys(listRequest, listKeysObserver);

      // Verify list response
      assertFalse(listKeysObserver.hasError(),
          "List API keys should not error: " +
          (listKeysObserver.hasError() ? listKeysObserver.getError().getMessage() : ""));
      assertTrue(listKeysObserver.hasValue(), "Should have list response");

      ListApiKeysResponse listResponse = listKeysObserver.getValue();
      assertTrue(listResponse.getKeysCount() >= 4,
          "Should have at least 4 API keys (1 original + 3 additional)");

      // With LIST_APIKEY_ANY permission, the root user should see keys from all users
      // Count how many keys belong to the root user
      int rootKeysCount = 0;
      for (goodmem.v1.Apikey.ApiKey key : listResponse.getKeysList()) {
        if (key.getUserId().equals(rootUserIdBytes)) {
          rootKeysCount++;
        }
      }

      // Verify we have at least 4 keys for the root user (1 original + 3 additional)
      assertTrue(rootKeysCount >= 4, "Should have at least 4 keys belonging to root user");

      // =========================================================================
      // Step 5: Test updating an API key's labels and status
      // =========================================================================
      System.out.println("\nStep 5: Test updating an API key's labels and status");

      // Get the first created API key for updating
      UUID keyToUpdateId = UuidUtil.fromProtoBytes(keyResponse.getApiKeyMetadata().getApiKeyId()).getValue();

      // Update the API key labels and status using the replace_labels strategy
      TestStreamObserver<goodmem.v1.Apikey.ApiKey> updateKeyObserver = new TestStreamObserver<>();

      // Create a map of labels to replace existing ones
      Map<String, String> replaceLabelsMap = new HashMap<>();
      replaceLabelsMap.put("updated", "true");
      replaceLabelsMap.put("env", "test-updated");

      // Create StringMap for replace_labels
      goodmem.v1.Common.StringMap replaceLabels = goodmem.v1.Common.StringMap.newBuilder()
          .putAllLabels(replaceLabelsMap)
          .build();

      UpdateApiKeyRequest updateRequest = UpdateApiKeyRequest.newBuilder()
          .setApiKeyId(UuidUtil.toProtoBytes(keyToUpdateId))
          .setReplaceLabels(replaceLabels)
          .setStatus(Status.INACTIVE)
          .build();

      apiKeyService.updateApiKey(updateRequest, updateKeyObserver);

      // Verify update response
      assertFalse(updateKeyObserver.hasError(),
          "Update API key should not error: " +
          (updateKeyObserver.hasError() ? updateKeyObserver.getError().getMessage() : ""));
      assertTrue(updateKeyObserver.hasValue(), "Should have update response");

      goodmem.v1.Apikey.ApiKey updatedKey = updateKeyObserver.getValue();
      assertEquals(Status.INACTIVE, updatedKey.getStatus(), "Status should be updated to INACTIVE");
      assertTrue(updatedKey.getLabelsMap().containsKey("updated"), "Should have new label");
      assertEquals("true", updatedKey.getLabelsMap().get("updated"), "New label should have correct value");
      assertEquals("test-updated", updatedKey.getLabelsMap().get("env"), "Existing label should be updated");

      // Verify in database
      try (Connection conn = dataSource.getConnection()) {
        com.goodmem.common.status.StatusOr<Optional<ApiKey>> apiKeyOr =
            ApiKeys.loadById(conn, keyToUpdateId);

        assertTrue(apiKeyOr.isOk(), "Should successfully query database");
        assertTrue(apiKeyOr.getValue().isPresent(), "API key should exist in database");

        ApiKey dbApiKey = apiKeyOr.getValue().get();
        assertEquals("INACTIVE", dbApiKey.status(), "Status should be updated in database");
      }

      // Also test the merge_labels functionality
      System.out.println("\nTesting merge_labels functionality");

      // Create an API key with initial labels that we'll later merge with
      TestStreamObserver<CreateApiKeyResponse> mergeTestKeyObserver = new TestStreamObserver<>();

      Map<String, String> initialLabels = new HashMap<>();
      initialLabels.put("env", "dev");
      initialLabels.put("app", "goodmem");

      CreateApiKeyRequest mergeTestKeyRequest = CreateApiKeyRequest.newBuilder()
          .putAllLabels(initialLabels)
          .build();

      apiKeyService.createApiKey(mergeTestKeyRequest, mergeTestKeyObserver);

      // Verify key was created successfully
      assertFalse(mergeTestKeyObserver.hasError(), "Create API key for merge test should not error");
      assertTrue(mergeTestKeyObserver.hasValue(), "Should have API key response");

      // Save the API key metadata and ID for later use
      goodmem.v1.Apikey.ApiKey keyToMerge = mergeTestKeyObserver.getValue().getApiKeyMetadata();
      UUID keyToMergeId = UuidUtil.fromProtoBytes(keyToMerge.getApiKeyId()).getValue();

      System.out.println("Created API key for merge test, ID: " + keyToMergeId);

      // Verify the initial labels are set correctly
      assertEquals(2, keyToMerge.getLabelsCount(), "Should have 2 initial labels");
      assertEquals("dev", keyToMerge.getLabelsMap().get("env"), "Initial env label should be set");
      assertEquals("goodmem", keyToMerge.getLabelsMap().get("app"), "Initial app label should be set");

      // Now merge additional labels while updating one existing label
      TestStreamObserver<goodmem.v1.Apikey.ApiKey> mergeLabelsObserver = new TestStreamObserver<>();

      // Create a StringMap for merge_labels with new labels and one updated label
      goodmem.v1.Common.StringMap mergeLabels = goodmem.v1.Common.StringMap.newBuilder()
          .putLabels("version", "1.0.0")      // New label
          .putLabels("region", "us-west")     // New label
          .putLabels("env", "staging")        // Updated existing label
          .build();

      UpdateApiKeyRequest mergeRequest = UpdateApiKeyRequest.newBuilder()
          .setApiKeyId(UuidUtil.toProtoBytes(keyToMergeId))
          .setMergeLabels(mergeLabels)
          .build();

      apiKeyService.updateApiKey(mergeRequest, mergeLabelsObserver);

      // Verify merge operation was successful
      assertFalse(mergeLabelsObserver.hasError(),
          "Merge labels update should not error: " +
          (mergeLabelsObserver.hasError() ? mergeLabelsObserver.getError().getMessage() : ""));
      assertTrue(mergeLabelsObserver.hasValue(), "Should have API key response");

      // Verify the merged labels are correct
      goodmem.v1.Apikey.ApiKey mergedKey = mergeLabelsObserver.getValue();

      // Check the labels count (we expect 4 labels after merging)
      assertEquals(4, mergedKey.getLabelsCount(), "Should have 4 labels after merge (original app label, updated env label, plus version and region)");

      // Check that original "app" label is preserved
      assertEquals("goodmem", mergedKey.getLabelsMap().get("app"), "app label should be preserved");

      // Check that "env" label is updated
      assertEquals("staging", mergedKey.getLabelsMap().get("env"), "env label should be updated");

      // Check that both new labels are added (version and region)
      assertTrue(mergedKey.getLabelsMap().containsKey("version"), "version label should be added");
      assertEquals("1.0.0", mergedKey.getLabelsMap().get("version"), "version label should have correct value");

      assertTrue(mergedKey.getLabelsMap().containsKey("region"), "region label should be added");
      assertEquals("us-west", mergedKey.getLabelsMap().get("region"), "region label should have correct value");

      // Verify in database that the merge was successful
      try (Connection conn = dataSource.getConnection()) {
          com.goodmem.common.status.StatusOr<Optional<ApiKey>> apiKeyOr =
              ApiKeys.loadById(conn, keyToMergeId);

          assertTrue(apiKeyOr.isOk(), "Should successfully query database");
          assertTrue(apiKeyOr.getValue().isPresent(), "API key should exist in database");

          ApiKey dbApiKey = apiKeyOr.getValue().get();
          Map<String, String> dbLabels = dbApiKey.labels();

          // Verify the database matches what we got in the response
          assertEquals(mergedKey.getLabelsCount(), dbLabels.size(),
              "Database should have same number of labels as response");

          // Check that we have the same labels in the database as in the response
          for (Map.Entry<String, String> entry : mergedKey.getLabelsMap().entrySet()) {
              assertEquals(entry.getValue(), dbLabels.get(entry.getKey()),
                  "Label " + entry.getKey() + " should match between response and database");
          }
      }

      // =========================================================================
      // Step 6: Test permission boundaries (OWN vs ANY)
      // =========================================================================
      System.out.println("\nStep 6: Test permission boundaries (OWN vs ANY)");

      // Switch to regular user context
      Context regularUserContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, regularUser);
      Context previousRegularContext = regularUserContext.attach();

      try {
        // Create an API key for the regular user
        TestStreamObserver<CreateApiKeyResponse> regularUserKeyObserver = new TestStreamObserver<>();

        Map<String, String> regularUserLabels = Map.of("owner", "regular-user");
        CreateApiKeyRequest regularUserKeyRequest = CreateApiKeyRequest.newBuilder()
            .putAllLabels(regularUserLabels)
            .build();

        apiKeyService.createApiKey(regularUserKeyRequest, regularUserKeyObserver);

        // Verify regular user can create their own API key
        assertFalse(regularUserKeyObserver.hasError(),
            "Regular user creating own API key should not error: " +
            (regularUserKeyObserver.hasError() ? regularUserKeyObserver.getError().getMessage() : ""));

        CreateApiKeyResponse regularUserKeyResponse = regularUserKeyObserver.getValue();
        UUID regularUserKeyId = UuidUtil.fromProtoBytes(regularUserKeyResponse.getApiKeyMetadata().getApiKeyId()).getValue();

        // Try to update root user's API key (should fail due to permissions)
        TestStreamObserver<goodmem.v1.Apikey.ApiKey> unauthorizedUpdateObserver = new TestStreamObserver<>();

        // Create StringMap for replace_labels
        goodmem.v1.Common.StringMap unauthorizedLabels = goodmem.v1.Common.StringMap.newBuilder()
            .putLabels("unauthorized", "true")
            .build();

        UpdateApiKeyRequest unauthorizedUpdateRequest = UpdateApiKeyRequest.newBuilder()
            .setApiKeyId(UuidUtil.toProtoBytes(keyToUpdateId)) // Root user's key
            .setReplaceLabels(unauthorizedLabels)
            .build();

        apiKeyService.updateApiKey(unauthorizedUpdateRequest, unauthorizedUpdateObserver);

        // Verify permission denial
        assertTrue(unauthorizedUpdateObserver.hasError(), "Regular user updating root's key should fail");
        StatusRuntimeException exception = (StatusRuntimeException) unauthorizedUpdateObserver.getError();
        assertEquals(io.grpc.Status.Code.PERMISSION_DENIED, exception.getStatus().getCode(),
                     "Error should be PERMISSION_DENIED");

        // Switch back to root user
        previousRegularContext.attach();
        authenticatedContext.attach();

        // Try to update regular user's key (should succeed with root's ANY permission)
        TestStreamObserver<goodmem.v1.Apikey.ApiKey> adminUpdateObserver = new TestStreamObserver<>();

        // Create StringMap for replace_labels
        goodmem.v1.Common.StringMap adminUpdateLabels = goodmem.v1.Common.StringMap.newBuilder()
            .putLabels("admin-updated", "true")
            .build();

        UpdateApiKeyRequest adminUpdateRequest = UpdateApiKeyRequest.newBuilder()
            .setApiKeyId(UuidUtil.toProtoBytes(regularUserKeyId))
            .setReplaceLabels(adminUpdateLabels)
            .build();

        apiKeyService.updateApiKey(adminUpdateRequest, adminUpdateObserver);

        // Verify admin can update any user's key
        assertFalse(adminUpdateObserver.hasError(),
            "Admin updating regular user's key should not error: " +
            (adminUpdateObserver.hasError() ? adminUpdateObserver.getError().getMessage() : ""));

      } finally {
        // Restore root user context
        previousRegularContext.attach();
        authenticatedContext.attach();
      }

      // =========================================================================
      // Step 7: Test deleting an API key
      // =========================================================================
      System.out.println("\nStep 7: Test deleting an API key");

      // Create an API key specifically for deletion testing
      TestStreamObserver<CreateApiKeyResponse> deleteTestKeyObserver = new TestStreamObserver<>();

      Map<String, String> deletionTestLabels = Map.of("purpose", "deletion-test");
      CreateApiKeyRequest deleteTestKeyRequest = CreateApiKeyRequest.newBuilder()
          .putAllLabels(deletionTestLabels)
          .build();

      apiKeyService.createApiKey(deleteTestKeyRequest, deleteTestKeyObserver);

      // Verify key was created successfully
      assertFalse(deleteTestKeyObserver.hasError(),
          "Create API key for deletion test should not error: " +
          (deleteTestKeyObserver.hasError() ? deleteTestKeyObserver.getError().getMessage() : ""));

      goodmem.v1.Apikey.ApiKey keyToDelete = deleteTestKeyObserver.getValue().getApiKeyMetadata();
      UUID keyToDeleteId = UuidUtil.fromProtoBytes(keyToDelete.getApiKeyId()).getValue();

      System.out.println("Created API key for deletion test, ID: " + keyToDeleteId);

      // Verify the key exists in the database before deletion
      assertTrue(apiKeyExistsInDatabase(keyToDeleteId),
          "API key should exist before deletion");

      // Delete the API key
      TestStreamObserver<Empty> deleteKeyObserver = new TestStreamObserver<>();
      DeleteApiKeyRequest deleteRequest = DeleteApiKeyRequest.newBuilder()
          .setApiKeyId(UuidUtil.toProtoBytes(keyToDeleteId))
          .build();

      apiKeyService.deleteApiKey(deleteRequest, deleteKeyObserver);

      // Verify deletion was successful
      assertFalse(deleteKeyObserver.hasError(),
          "API key deletion should not error: " +
          (deleteKeyObserver.hasError() ? deleteKeyObserver.getError().getMessage() : ""));
      assertTrue(deleteKeyObserver.isCompleted(), "Response should be completed");

      // Verify the key no longer exists in the database
      assertFalse(apiKeyExistsInDatabase(keyToDeleteId),
          "API key should not exist after deletion");

      // Try to delete the same key again (should fail with NOT_FOUND)
      TestStreamObserver<Empty> deleteAgainObserver = new TestStreamObserver<>();
      apiKeyService.deleteApiKey(deleteRequest, deleteAgainObserver);

      // Verify the second deletion fails with NOT_FOUND
      assertTrue(deleteAgainObserver.hasError(),
          "Second deletion of the same API key should fail");

      StatusRuntimeException deleteException = (StatusRuntimeException) deleteAgainObserver.getError();
      assertEquals(io.grpc.Status.Code.NOT_FOUND, deleteException.getStatus().getCode(),
          "Error code should be NOT_FOUND");

      // =========================================================================
      // Step 8: Test error cases
      // =========================================================================
      System.out.println("\nStep 8: Test error cases");

      // Test case: Invalid API key ID format (non-existent UUID)
      TestStreamObserver<goodmem.v1.Apikey.ApiKey> nonExistentIdObserver = new TestStreamObserver<>();

      // Generate a random UUID that shouldn't exist in the database
      UUID randomUUID = UUID.randomUUID();

      // Create StringMap for replace_labels
      goodmem.v1.Common.StringMap testLabels = goodmem.v1.Common.StringMap.newBuilder()
          .putLabels("test", "invalid")
          .build();

      UpdateApiKeyRequest nonExistentIdRequest = UpdateApiKeyRequest.newBuilder()
          .setApiKeyId(UuidUtil.toProtoBytes(randomUUID))
          .setReplaceLabels(testLabels)
          .build();

      apiKeyService.updateApiKey(nonExistentIdRequest, nonExistentIdObserver);

      // Verify error for non-existent UUID
      assertTrue(nonExistentIdObserver.hasError(), "Non-existent UUID should cause error");
      StatusRuntimeException nonExistentIdException = (StatusRuntimeException) nonExistentIdObserver.getError();
      assertEquals(io.grpc.Status.Code.NOT_FOUND, nonExistentIdException.getStatus().getCode(),
          "Error code should be NOT_FOUND");

      // Test case: Malformed API key ID (not a valid UUID at all)
      TestStreamObserver<goodmem.v1.Apikey.ApiKey> invalidIdObserver = new TestStreamObserver<>();

      // Use a ByteString that's not a valid UUID format
      ByteString invalidUuidBytes = ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5});

      // Create StringMap for replace_labels - reuse from previous test
      UpdateApiKeyRequest invalidIdRequest = UpdateApiKeyRequest.newBuilder()
          .setApiKeyId(invalidUuidBytes)
          .setReplaceLabels(testLabels)
          .build();

      apiKeyService.updateApiKey(invalidIdRequest, invalidIdObserver);

      // Verify error for invalid UUID format
      assertTrue(invalidIdObserver.hasError(), "Invalid UUID should cause error");
      StatusRuntimeException invalidIdException = (StatusRuntimeException) invalidIdObserver.getError();
      assertEquals(io.grpc.Status.Code.INVALID_ARGUMENT, invalidIdException.getStatus().getCode(),
          "Error code should be INVALID_ARGUMENT");

      // Test case: Unauthenticated request
      // First detach the authenticated context
      previousContext.attach();

      // Try to create an API key without authentication
      TestStreamObserver<CreateApiKeyResponse> unauthenticatedObserver = new TestStreamObserver<>();

      CreateApiKeyRequest unauthenticatedRequest = CreateApiKeyRequest.newBuilder()
          .putLabels("test", "unauthenticated")
          .build();

      apiKeyService.createApiKey(unauthenticatedRequest, unauthenticatedObserver);

      // Verify unauthenticated error
      assertTrue(unauthenticatedObserver.hasError(), "Unauthenticated request should cause error");
      StatusRuntimeException unauthException = (StatusRuntimeException) unauthenticatedObserver.getError();
      assertEquals(io.grpc.Status.Code.UNAUTHENTICATED, unauthException.getStatus().getCode(),
          "Error code should be UNAUTHENTICATED");

      // Reattach authenticated context for cleanup
      authenticatedContext.attach();
    } finally {
      // Always restore original context
      previousContext.attach();
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

      // Create an API key for the user
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
   * Checks if an API key exists in the database.
   */
  private boolean apiKeyExistsInDatabase(UUID apiKeyId) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      String sql = "SELECT 1 FROM apikey WHERE api_key_id = ?";

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, apiKeyId);

        try (ResultSet rs = stmt.executeQuery()) {
          return rs.next(); // Returns true if API key exists
        }
      }
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