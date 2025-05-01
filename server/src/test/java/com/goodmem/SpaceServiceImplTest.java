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
import goodmem.v1.SpaceOuterClass.DeleteSpaceRequest;
import goodmem.v1.SpaceOuterClass.GetSpaceRequest;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.SpaceOuterClass.StringMap;
import goodmem.v1.SpaceOuterClass.UpdateSpaceRequest;
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
  void testSpaceServiceOperations() throws SQLException {
    // =========================================================================
    // Step 1: Initialize the system and authenticate as root user
    // =========================================================================
    System.out.println("Step 1: Initialize the system");

    com.goodmem.security.User rootUser = setupRootUser();

    // Get root user ID information from the rootUser object
    ByteString rootUserIdBytes = com.goodmem.db.util.UuidUtil.toProtoBytes(rootUser.getId());
    UUID rootUserId = rootUser.getId();

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

      // =========================================================================
      // Step 9: Test deleteSpace functionality
      // =========================================================================
      System.out.println("\nStep 9: Test deleting a space and verifying it's gone");

      // Create a space specifically for deletion testing
      TestStreamObserver<Space> deleteTestSpaceObserver = new TestStreamObserver<>();
      CreateSpaceRequest deleteTestSpaceRequest = CreateSpaceRequest.newBuilder()
          .setName("Space To Delete")
          .putLabels("purpose", "deletion-test")
          .setPublicRead(true)
          .build();

      spaceService.createSpace(deleteTestSpaceRequest, deleteTestSpaceObserver);

      // Verify space was created successfully
      assertFalse(deleteTestSpaceObserver.hasError(),
          "Create space for deletion test should not error: " +
          (deleteTestSpaceObserver.hasError() ? deleteTestSpaceObserver.getError().getMessage() : ""));

      Space spaceToDelete = deleteTestSpaceObserver.getValue();
      UUID spaceToDeleteId = com.goodmem.db.util.UuidUtil.fromProtoBytes(spaceToDelete.getSpaceId()).getValue();

      System.out.println("Created space for deletion test, ID: " + spaceToDeleteId);

      // Verify the space exists in the database before deletion
      assertTrue(spaceExistsInDatabase(spaceToDeleteId),
          "Space should exist before deletion");

      // Now delete the space
      TestStreamObserver<Empty> deleteSpaceObserver = new TestStreamObserver<>();
      DeleteSpaceRequest deleteSpaceRequest = DeleteSpaceRequest.newBuilder()
          .setSpaceId(com.goodmem.db.util.UuidUtil.toProtoBytes(spaceToDeleteId))
          .build();

      spaceService.deleteSpace(deleteSpaceRequest, deleteSpaceObserver);

      // Verify deletion was successful
      assertFalse(deleteSpaceObserver.hasError(),
          "Space deletion should not error: " +
          (deleteSpaceObserver.hasError() ? deleteSpaceObserver.getError().getMessage() : ""));
      assertTrue(deleteSpaceObserver.isCompleted(), "Response should be completed");

      // Verify the space no longer exists in the database
      assertFalse(spaceExistsInDatabase(spaceToDeleteId),
          "Space should not exist after deletion");

      // Try to delete the same space again (should fail with NOT_FOUND)
      TestStreamObserver<Empty> deleteAgainObserver = new TestStreamObserver<>();
      spaceService.deleteSpace(deleteSpaceRequest, deleteAgainObserver);

      // Verify the second deletion fails with NOT_FOUND
      assertTrue(deleteAgainObserver.hasError(),
          "Second deletion of the same space should fail");

      StatusRuntimeException deleteException = (StatusRuntimeException) deleteAgainObserver.getError();
      assertEquals(Status.NOT_FOUND.getCode(), deleteException.getStatus().getCode(),
          "Error code should be NOT_FOUND");
      assertTrue(deleteException.getStatus().getDescription().contains("not found"),
          "Error should mention space not found");

    } finally {
      previousContext.attach(); // Restore original context
    }
  }

  /**
   * Tests the updateSpace method with various scenarios including label replacement,
   * label merging, and validation of error conditions.
   */
  @Test
  void testUpdateSpace() throws SQLException {
    // Set up the root user for authentication
    com.goodmem.security.User rootUser = setupRootUser();

    // Get root user ID information from the rootUser object
    ByteString rootUserIdBytes = com.goodmem.db.util.UuidUtil.toProtoBytes(rootUser.getId());

    // Set up authentication context
    Context authenticatedContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, rootUser);
    Context previousContext = authenticatedContext.attach();

    try {
      // Create a space for update testing
      TestStreamObserver<Space> createSpaceObserver = new TestStreamObserver<>();
      CreateSpaceRequest createSpaceRequest = CreateSpaceRequest.newBuilder()
          .setName("Space To Update")
          .putLabels("purpose", "update-test")
          .putLabels("environment", "test")
          .putLabels("version", "1.0")
          .setPublicRead(false)
          .build();

      spaceService.createSpace(createSpaceRequest, createSpaceObserver);

      // Verify space was created successfully
      assertFalse(createSpaceObserver.hasError(),
          "Create space for update test should not error: " +
          (createSpaceObserver.hasError() ? createSpaceObserver.getError().getMessage() : ""));

      Space spaceToUpdate = createSpaceObserver.getValue();
      UUID spaceToUpdateId = com.goodmem.db.util.UuidUtil.fromProtoBytes(spaceToUpdate.getSpaceId()).getValue();

      System.out.println("Created space for update test, ID: " + spaceToUpdateId);

      // Test 1: Update name and public_read flag
      TestStreamObserver<Space> updateBasicObserver = new TestStreamObserver<>();
      UpdateSpaceRequest updateBasicRequest = UpdateSpaceRequest.newBuilder()
          .setSpaceId(com.goodmem.db.util.UuidUtil.toProtoBytes(spaceToUpdateId))
          .setName("Updated Space Name")
          .setPublicRead(true)
          .build();

      spaceService.updateSpace(updateBasicRequest, updateBasicObserver);

      // Verify basic update was successful
      assertFalse(updateBasicObserver.hasError(),
          "Basic space update should not error: " +
          (updateBasicObserver.hasError() ? updateBasicObserver.getError().getMessage() : ""));

      Space basicUpdatedSpace = updateBasicObserver.getValue();
      assertEquals("Updated Space Name", basicUpdatedSpace.getName(),
          "Space name should be updated");
      assertTrue(basicUpdatedSpace.getPublicRead(),
          "Public read flag should be updated to true");

      // Verify labels are preserved when not specified in update
      assertEquals(spaceToUpdate.getLabelsMap().size(), basicUpdatedSpace.getLabelsMap().size(),
          "Labels should be preserved when not specified in update");
      assertEquals(spaceToUpdate.getLabelsMap().get("purpose"), basicUpdatedSpace.getLabelsMap().get("purpose"),
          "Original labels should be preserved");

      // Test 2: Update with replace_labels
      TestStreamObserver<Space> replaceLabelsObserver = new TestStreamObserver<>();

      // Create StringMap for replace_labels
      StringMap.Builder replaceLabelMapBuilder = StringMap.newBuilder();
      replaceLabelMapBuilder.putLabels("new-label", "new-value");
      replaceLabelMapBuilder.putLabels("another-label", "another-value");

      UpdateSpaceRequest replaceLabelsRequest = UpdateSpaceRequest.newBuilder()
          .setSpaceId(com.goodmem.db.util.UuidUtil.toProtoBytes(spaceToUpdateId))
          .setReplaceLabels(replaceLabelMapBuilder.build())
          .build();

      spaceService.updateSpace(replaceLabelsRequest, replaceLabelsObserver);

      // Verify replace_labels update was successful
      assertFalse(replaceLabelsObserver.hasError(),
          "Replace labels update should not error: " +
          (replaceLabelsObserver.hasError() ? replaceLabelsObserver.getError().getMessage() : ""));

      Space replaceLabelsSpace = replaceLabelsObserver.getValue();
      assertEquals(2, replaceLabelsSpace.getLabelsMap().size(),
          "Should have exactly 2 labels after replacement");
      assertEquals("new-value", replaceLabelsSpace.getLabelsMap().get("new-label"),
          "Should have new label after replacement");
      assertFalse(replaceLabelsSpace.getLabelsMap().containsKey("purpose"),
          "Original labels should be removed after replacement");

      // Test 3: Update with merge_labels
      TestStreamObserver<Space> mergeLabelsObserver = new TestStreamObserver<>();

      // Create StringMap for merge_labels
      StringMap.Builder mergeLabelMapBuilder = StringMap.newBuilder();
      mergeLabelMapBuilder.putLabels("merged-label", "merged-value");
      mergeLabelMapBuilder.putLabels("new-label", "updated-value"); // Should update existing label

      UpdateSpaceRequest mergeLabelsRequest = UpdateSpaceRequest.newBuilder()
          .setSpaceId(com.goodmem.db.util.UuidUtil.toProtoBytes(spaceToUpdateId))
          .setMergeLabels(mergeLabelMapBuilder.build())
          .build();

      spaceService.updateSpace(mergeLabelsRequest, mergeLabelsObserver);

      // Verify merge_labels update was successful
      assertFalse(mergeLabelsObserver.hasError(),
          "Merge labels update should not error: " +
          (mergeLabelsObserver.hasError() ? mergeLabelsObserver.getError().getMessage() : ""));

      Space mergeLabelsSpace = mergeLabelsObserver.getValue();
      assertEquals(3, mergeLabelsSpace.getLabelsMap().size(),
          "Should have 3 labels after merging");
      assertEquals("merged-value", mergeLabelsSpace.getLabelsMap().get("merged-label"),
          "Should have new merged label");
      assertEquals("updated-value", mergeLabelsSpace.getLabelsMap().get("new-label"),
          "Existing label should be updated with new value from merge");
      assertEquals("another-value", mergeLabelsSpace.getLabelsMap().get("another-label"),
          "Existing label not in merge request should be preserved");

      // Test 4: Error case - attempt to provide both replace_labels and merge_labels
      // Note: With oneof, this isn't actually possible in a well-formed protobuf message,
      // as setting one field in a oneof automatically clears any other fields in the same oneof.
      // However, our test suite can attempt this pattern to verify server behavior matches expectations.
      TestStreamObserver<Space> invalidLabelsObserver = new TestStreamObserver<>();

      // Create StringMap builders for both types of labels
      StringMap.Builder replaceLabelBuilder = StringMap.newBuilder();
      replaceLabelBuilder.putLabels("replace", "value");

      StringMap.Builder mergeLabelBuilder = StringMap.newBuilder();
      mergeLabelBuilder.putLabels("merge", "value");

      // We'll set replace_labels first, then merge_labels - the latter should override
      UpdateSpaceRequest.Builder requestBuilder = UpdateSpaceRequest.newBuilder()
          .setSpaceId(com.goodmem.db.util.UuidUtil.toProtoBytes(spaceToUpdateId))
          .setReplaceLabels(replaceLabelBuilder.build());

      // Try to verify behavior by explicitly checking case in our test
      requestBuilder.setMergeLabels(mergeLabelBuilder.build());
      UpdateSpaceRequest invalidLabelsRequest = requestBuilder.build();

      // Verify that the oneof is set to merge_labels (last one set)
      assertEquals(UpdateSpaceRequest.LabelUpdateStrategyCase.MERGE_LABELS,
          invalidLabelsRequest.getLabelUpdateStrategyCase(),
          "Only the last label strategy set should be active due to oneof semantics");

      spaceService.updateSpace(invalidLabelsRequest, invalidLabelsObserver);

      // We don't expect an error now because oneof ensures only one field is set
      assertFalse(invalidLabelsObserver.hasError(),
          "Update with oneof should succeed as only the last field set (merge_labels) is active");

      // The operation should have succeeded with the merge_labels field
      Space resultSpace = invalidLabelsObserver.getValue();
      assertTrue(resultSpace.getLabelsMap().containsKey("merge"),
          "The merge field should be present in the result");

      // Test 5: Error case - attempt to update name to one that already exists

      // First create another space with a unique name
      TestStreamObserver<Space> secondSpaceObserver = new TestStreamObserver<>();
      CreateSpaceRequest secondSpaceRequest = CreateSpaceRequest.newBuilder()
          .setName("Second Space")
          .putLabels("purpose", "duplicate-test")
          .build();

      spaceService.createSpace(secondSpaceRequest, secondSpaceObserver);
      assertFalse(secondSpaceObserver.hasError(), "Creating second space should not error");

      // Now try to update our test space to have the same name as the second space
      TestStreamObserver<Space> duplicateNameObserver = new TestStreamObserver<>();
      UpdateSpaceRequest duplicateNameRequest = UpdateSpaceRequest.newBuilder()
          .setSpaceId(com.goodmem.db.util.UuidUtil.toProtoBytes(spaceToUpdateId))
          .setName("Second Space")
          .build();

      spaceService.updateSpace(duplicateNameRequest, duplicateNameObserver);

      // Verify the update with duplicate name fails
      assertTrue(duplicateNameObserver.hasError(),
          "Update with duplicate name should fail");

      StatusRuntimeException nameException = (StatusRuntimeException) duplicateNameObserver.getError();
      assertEquals(Status.ALREADY_EXISTS.getCode(), nameException.getStatus().getCode(),
          "Error code should be ALREADY_EXISTS");
      assertTrue(nameException.getStatus().getDescription().contains("already exists"),
          "Error should mention name already exists");
    } finally {
      // Restore original context
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
   * Checks if a space exists in the database.
   */
  private boolean spaceExistsInDatabase(UUID spaceId) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      String sql = "SELECT 1 FROM space WHERE space_id = ?";

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, spaceId);

        try (ResultSet rs = stmt.executeQuery()) {
          return rs.next(); // Returns true if space exists
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