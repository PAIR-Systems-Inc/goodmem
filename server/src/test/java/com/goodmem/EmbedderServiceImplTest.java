package com.goodmem;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import com.goodmem.db.util.UuidUtil;
import com.goodmem.operations.SystemInitOperation;
import com.goodmem.security.AuthInterceptor;
import com.goodmem.security.Role;
import com.goodmem.security.Roles;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.Common.StringMap;
import goodmem.v1.EmbedderOuterClass.CreateEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.DeleteEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.Embedder;
import goodmem.v1.EmbedderOuterClass.GetEmbedderRequest;
import goodmem.v1.EmbedderOuterClass.ListEmbeddersRequest;
import goodmem.v1.EmbedderOuterClass.ListEmbeddersResponse;
import goodmem.v1.EmbedderOuterClass.Modality;
import goodmem.v1.EmbedderOuterClass.ProviderType;
import goodmem.v1.EmbedderOuterClass.UpdateEmbedderRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for EmbedderServiceImpl that verify functionality with a real PostgreSQL database.
 *
 * <p>This test follows a sequential workflow to test embedder operations:
 * 1. Initialize the system and get a root user and API key
 * 2. Create an embedder with the authenticated user as owner
 * 3. Create a second user
 * 4. Create an embedder for another user (requires CREATE_EMBEDDER_ANY permission)
 * 5. Test creating an embedder with the same connection details (should fail)
 * 6. Test retrieving an embedder by ID
 * 7. Test updating an embedder with various fields and label strategies
 * 8. Test listing embedders with different filter criteria
 * 9. Test deleting an embedder and verifying it's removed
 */
@Testcontainers
public class EmbedderServiceImplTest {

  private static PostgresContext postgresContext;
  private static HikariDataSource dataSource;
  private static UserServiceImpl userService;
  private static EmbedderServiceImpl embedderService;
  private static AuthInterceptor authInterceptor;

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_embedderservice_test", EmbedderServiceImplTest.class);

    // Create a HikariDataSource for the test container
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgresContext.getContainer().getJdbcUrl());
    config.setUsername(postgresContext.getContainer().getUsername());
    config.setPassword(postgresContext.getContainer().getPassword());
    config.setMaximumPoolSize(2);
    dataSource = new HikariDataSource(config);

    // Create the UserServiceImpl with the test datasource
    userService = new UserServiceImpl(new UserServiceImpl.Config(dataSource));

    // Create the EmbedderServiceImpl with the test datasource
    embedderService = new EmbedderServiceImpl(new EmbedderServiceImpl.Config(dataSource));

    // Create the AuthInterceptor with the test datasource
    authInterceptor = new AuthInterceptor(dataSource);
  }
  
  /**
   * Clears the embedder table before each test to ensure tests don't interfere with each other.
   * This prevents test coupling where one test's data affects another test's results.
   */
  @org.junit.jupiter.api.BeforeEach
  void clearDatabase() throws SQLException {
    // Drop all embedder records from the database
    try (Connection conn = dataSource.getConnection()) {
      // First truncate the embedder table
      String sql = "DELETE FROM embedder";
      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.executeUpdate();
      }
      
      System.out.println("Database cleared for new test");
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
          com.goodmem.db.Users.loadByUsername(conn, SystemInitOperation.ROOT_USERNAME);

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
  void testEmbedderServiceOperations() throws SQLException {
    // =========================================================================
    // Step 1: Initialize the system and authenticate as root user
    // =========================================================================
    System.out.println("Step 1: Initialize the system");

    com.goodmem.security.User rootUser = setupRootUser();

    // Get root user ID information from the rootUser object
    ByteString rootUserIdBytes = UuidUtil.toProtoBytes(rootUser.getId());
    UUID rootUserId = rootUser.getId();

    // =========================================================================
    // Step 2: Create an embedder with the authenticated user as owner
    // =========================================================================
    System.out.println("\nStep 2: Create an embedder with authenticated user as owner");

    Context authenticatedContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, rootUser);
    Context previousContext = authenticatedContext.attach();

    try {
      // Create an embedder without specifying owner (defaults to authenticated user)
      TestStreamObserver<Embedder> createEmbedderObserver = new TestStreamObserver<>();
      CreateEmbedderRequest ownEmbedderRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Root's OpenAI Embedder")
          .setDescription("Test embedder for OpenAI")
          .setProviderType(ProviderType.PROVIDER_TYPE_OPENAI)
          .setEndpointUrl("https://api.openai.com")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("text-embedding-3-small")
          .setDimensionality(1536)
          .setMaxSequenceLength(8192)
          .addSupportedModalities(Modality.MODALITY_TEXT)
          .setCredentials("sk-test-123456789")
          .putLabels("env", "test")
          .putLabels("owner", "root")
          .setVersion("1.0")
          .build();

      embedderService.createEmbedder(ownEmbedderRequest, createEmbedderObserver);

      // Verify embedder creation response
      assertFalse(createEmbedderObserver.hasError(),
          "Embedder creation should not error: " +
          (createEmbedderObserver.hasError() ? createEmbedderObserver.getError().getMessage() : ""));
      assertTrue(createEmbedderObserver.hasValue(), "Should have embedder response");

      Embedder createdEmbedder = createEmbedderObserver.getValue();
      assertNotNull(createdEmbedder.getEmbedderId(), "Embedder ID should be assigned");
      assertEquals("Root's OpenAI Embedder", createdEmbedder.getDisplayName(), "Embedder name should match request");
      assertEquals(rootUserIdBytes, createdEmbedder.getOwnerId(), "Owner ID should be root user");
      assertEquals(rootUserIdBytes, createdEmbedder.getCreatedById(), "Creator ID should be root user");
      assertEquals(rootUserIdBytes, createdEmbedder.getUpdatedById(), "Updater ID should be root user");
      assertEquals(ProviderType.PROVIDER_TYPE_OPENAI, createdEmbedder.getProviderType(), "Provider type should match");
      assertEquals(1536, createdEmbedder.getDimensionality(), "Dimensionality should match");
      assertEquals(8192, createdEmbedder.getMaxSequenceLength(), "Max sequence length should match");
      assertEquals(1, createdEmbedder.getSupportedModalitiesCount(), "Should have 1 modality");
      assertEquals(Modality.MODALITY_TEXT, createdEmbedder.getSupportedModalities(0), "Modality should be TEXT");

      // Verify labels
      assertEquals(2, createdEmbedder.getLabelsMap().size(), "Should have 2 labels");
      assertEquals("test", createdEmbedder.getLabelsMap().get("env"), "Environment label should match");
      assertEquals("root", createdEmbedder.getLabelsMap().get("owner"), "Owner label should match");

      // Save embedder ID for future tests
      UUID rootEmbedderId = UuidUtil.fromProtoBytes(createdEmbedder.getEmbedderId()).getValue();
      System.out.println("Created embedder ID: " + rootEmbedderId);

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
      // Step 4: Create an embedder for another user (requires CREATE_EMBEDDER_ANY)
      // =========================================================================
      System.out.println("\nStep 4: Create an embedder for another user");

      TestStreamObserver<Embedder> createEmbedderForOtherObserver = new TestStreamObserver<>();
      CreateEmbedderRequest otherEmbedderRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Test User's vLLM Embedder")
          .setDescription("Test embedder for vLLM")
          .setProviderType(ProviderType.PROVIDER_TYPE_VLLM)
          .setEndpointUrl("http://localhost:8000")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("e5-large-v2")
          .setDimensionality(1024)
          .addSupportedModalities(Modality.MODALITY_TEXT)
          .setCredentials("local-token-123")
          .putLabels("env", "test")
          .putLabels("owner", "testuser")
          .setOwnerId(UuidUtil.toProtoBytes(secondUserId))
          .build();

      embedderService.createEmbedder(otherEmbedderRequest, createEmbedderForOtherObserver);

      // Verify embedder creation for other user
      assertFalse(createEmbedderForOtherObserver.hasError(),
          "Create embedder for other user should not error: " +
          (createEmbedderForOtherObserver.hasError() ? createEmbedderForOtherObserver.getError().getMessage() : ""));
      assertTrue(createEmbedderForOtherObserver.hasValue(), "Should have embedder response");

      Embedder otherUserEmbedder = createEmbedderForOtherObserver.getValue();
      assertEquals("Test User's vLLM Embedder", otherUserEmbedder.getDisplayName(), "Embedder name should match request");

      ByteString secondUserIdBytes = UuidUtil.toProtoBytes(secondUserId);
      assertEquals(secondUserIdBytes, otherUserEmbedder.getOwnerId(), "Owner ID should be second user");
      assertEquals(rootUserIdBytes, otherUserEmbedder.getCreatedById(), "Creator ID should be root user");
      assertEquals(ProviderType.PROVIDER_TYPE_VLLM, otherUserEmbedder.getProviderType(), "Provider type should match");

      // Save embedder ID for future tests
      UUID otherEmbedderId = UuidUtil.fromProtoBytes(otherUserEmbedder.getEmbedderId()).getValue();
      System.out.println("Created other embedder ID: " + otherEmbedderId);

      // =========================================================================
      // Step 5: Test creating an embedder with the same connection details (should fail)
      // =========================================================================
      System.out.println("\nStep 5: Test creating embedder with existing connection details");

      TestStreamObserver<Embedder> duplicateObserver = new TestStreamObserver<>();
      CreateEmbedderRequest duplicateRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Duplicate Embedder")
          .setDescription("This should fail")
          .setProviderType(ProviderType.PROVIDER_TYPE_OPENAI)
          .setEndpointUrl("https://api.openai.com") // Same as first embedder
          .setApiPath("/v1/embeddings") // Same as first embedder
          .setModelIdentifier("text-embedding-3-small") // Same as first embedder
          .setDimensionality(1536)
          .setCredentials("different-key-but-same-endpoint")
          .putLabels("test", "duplicate")
          .build();

      embedderService.createEmbedder(duplicateRequest, duplicateObserver);

      // Verify duplicate connection details error
      assertTrue(duplicateObserver.hasError(), "Duplicate connection details should cause error");
      StatusRuntimeException exception = (StatusRuntimeException) duplicateObserver.getError();
      assertEquals(Status.ALREADY_EXISTS.getCode(), exception.getStatus().getCode(),
                   "Error should be ALREADY_EXISTS");
      assertTrue(exception.getStatus().getDescription().contains("already exists"),
                 "Error should mention embedder already exists");

      // =========================================================================
      // Step 6: Test retrieving an embedder by ID
      // =========================================================================
      System.out.println("\nStep 6: Test retrieving an embedder by ID");

      TestStreamObserver<Embedder> getEmbedderObserver = new TestStreamObserver<>();
      GetEmbedderRequest getEmbedderRequest = GetEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(rootEmbedderId))
          .build();

      embedderService.getEmbedder(getEmbedderRequest, getEmbedderObserver);

      // Verify get embedder response
      assertFalse(getEmbedderObserver.hasError(),
          "Get embedder should not error: " +
          (getEmbedderObserver.hasError() ? getEmbedderObserver.getError().getMessage() : ""));
      assertTrue(getEmbedderObserver.hasValue(), "Should have embedder response");

      Embedder retrievedEmbedder = getEmbedderObserver.getValue();
      assertEquals(createdEmbedder.getEmbedderId(), retrievedEmbedder.getEmbedderId(), "Embedder ID should match");
      assertEquals(createdEmbedder.getDisplayName(), retrievedEmbedder.getDisplayName(), "Display name should match");
      assertEquals(createdEmbedder.getProviderType(), retrievedEmbedder.getProviderType(), "Provider type should match");
      assertEquals(createdEmbedder.getLabelsMap(), retrievedEmbedder.getLabelsMap(), "Labels should match");

      // =========================================================================
      // Step 7: Test updating an embedder with various fields and label strategies
      // =========================================================================
      System.out.println("\nStep 7: Test updating an embedder");

      // Test 7.1: Update basic fields (display name, description, etc.)
      TestStreamObserver<Embedder> updateBasicObserver = new TestStreamObserver<>();
      UpdateEmbedderRequest updateBasicRequest = UpdateEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(rootEmbedderId))
          .setDisplayName("Updated OpenAI Embedder")
          .setDescription("Updated description for testing")
          .setMaxSequenceLength(10000) // Updated max sequence length
          .build();

      embedderService.updateEmbedder(updateBasicRequest, updateBasicObserver);

      // Verify basic update was successful
      assertFalse(updateBasicObserver.hasError(),
          "Basic embedder update should not error: " +
          (updateBasicObserver.hasError() ? updateBasicObserver.getError().getMessage() : ""));
      assertTrue(updateBasicObserver.hasValue(), "Should have embedder response");

      Embedder updatedBasicEmbedder = updateBasicObserver.getValue();
      assertEquals("Updated OpenAI Embedder", updatedBasicEmbedder.getDisplayName(), "Display name should be updated");
      assertEquals("Updated description for testing", updatedBasicEmbedder.getDescription(), "Description should be updated");
      assertEquals(10000, updatedBasicEmbedder.getMaxSequenceLength(), "Max sequence length should be updated");
      assertEquals(createdEmbedder.getProviderType(), updatedBasicEmbedder.getProviderType(), "Provider type should not change (immutable)");
      
      // Verify labels are preserved when not specified in update
      assertEquals(createdEmbedder.getLabelsMap().size(), updatedBasicEmbedder.getLabelsMap().size(), 
          "Labels should be preserved when not specified in update");

      // Test 7.2: Update with replace_labels
      TestStreamObserver<Embedder> replaceLabelsObserver = new TestStreamObserver<>();

      // Create StringMap for replace_labels
      StringMap.Builder replaceLabelMapBuilder = StringMap.newBuilder();
      replaceLabelMapBuilder.putLabels("new-label", "new-value");
      replaceLabelMapBuilder.putLabels("another-label", "another-value");

      UpdateEmbedderRequest replaceLabelsRequest = UpdateEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(rootEmbedderId))
          .setReplaceLabels(replaceLabelMapBuilder.build())
          .build();

      embedderService.updateEmbedder(replaceLabelsRequest, replaceLabelsObserver);

      // Verify replace_labels update was successful
      assertFalse(replaceLabelsObserver.hasError(),
          "Replace labels update should not error: " +
          (replaceLabelsObserver.hasError() ? replaceLabelsObserver.getError().getMessage() : ""));
      assertTrue(replaceLabelsObserver.hasValue(), "Should have embedder response");

      Embedder replaceLabelsEmbedder = replaceLabelsObserver.getValue();
      assertEquals(2, replaceLabelsEmbedder.getLabelsMap().size(), "Should have exactly 2 labels after replacement");
      assertEquals("new-value", replaceLabelsEmbedder.getLabelsMap().get("new-label"), "Should have new label after replacement");
      assertFalse(replaceLabelsEmbedder.getLabelsMap().containsKey("env"), "Original labels should be removed after replacement");

      // Test 7.3: Update with merge_labels
      TestStreamObserver<Embedder> mergeLabelsObserver = new TestStreamObserver<>();

      // Create StringMap for merge_labels
      StringMap.Builder mergeLabelMapBuilder = StringMap.newBuilder();
      mergeLabelMapBuilder.putLabels("merged-label", "merged-value");
      mergeLabelMapBuilder.putLabels("new-label", "updated-value"); // Should update existing label

      UpdateEmbedderRequest mergeLabelsRequest = UpdateEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(rootEmbedderId))
          .setMergeLabels(mergeLabelMapBuilder.build())
          .build();

      embedderService.updateEmbedder(mergeLabelsRequest, mergeLabelsObserver);

      // Verify merge_labels update was successful
      assertFalse(mergeLabelsObserver.hasError(),
          "Merge labels update should not error: " +
          (mergeLabelsObserver.hasError() ? mergeLabelsObserver.getError().getMessage() : ""));
      assertTrue(mergeLabelsObserver.hasValue(), "Should have embedder response");

      Embedder mergeLabelsEmbedder = mergeLabelsObserver.getValue();
      assertEquals(3, mergeLabelsEmbedder.getLabelsMap().size(), "Should have 3 labels after merging");
      assertEquals("merged-value", mergeLabelsEmbedder.getLabelsMap().get("merged-label"), "Should have new merged label");
      assertEquals("updated-value", mergeLabelsEmbedder.getLabelsMap().get("new-label"), "Existing label should be updated");
      assertEquals("another-value", mergeLabelsEmbedder.getLabelsMap().get("another-label"), "Existing label not in merge should be preserved");

      // =========================================================================
      // Step 8: Test listing embedders with different filter criteria
      // =========================================================================
      System.out.println("\nStep 8: Test listing embedders with different filters");

      // Test 8.1: List all embedders (root has LIST_EMBEDDER_ANY permission)
      TestStreamObserver<ListEmbeddersResponse> listAllObserver = new TestStreamObserver<>();
      ListEmbeddersRequest listAllRequest = ListEmbeddersRequest.newBuilder().build();

      embedderService.listEmbedders(listAllRequest, listAllObserver);

      // Verify list all embedders response
      assertFalse(listAllObserver.hasError(),
          "List all embedders should not error: " +
          (listAllObserver.hasError() ? listAllObserver.getError().getMessage() : ""));
      assertTrue(listAllObserver.hasValue(), "Should have embedders response");

      ListEmbeddersResponse allEmbeddersResponse = listAllObserver.getValue();
      assertEquals(2, allEmbeddersResponse.getEmbeddersCount(), "Should have 2 embedders total");

      // Test 8.2: List embedders by owner ID
      TestStreamObserver<ListEmbeddersResponse> listByOwnerObserver = new TestStreamObserver<>();
      ListEmbeddersRequest listByOwnerRequest = ListEmbeddersRequest.newBuilder()
          .setOwnerId(UuidUtil.toProtoBytes(rootUserId))
          .build();

      embedderService.listEmbedders(listByOwnerRequest, listByOwnerObserver);

      // Verify list by owner response
      assertFalse(listByOwnerObserver.hasError(),
          "List by owner should not error: " +
          (listByOwnerObserver.hasError() ? listByOwnerObserver.getError().getMessage() : ""));
      assertTrue(listByOwnerObserver.hasValue(), "Should have embedders response");

      ListEmbeddersResponse ownerEmbeddersResponse = listByOwnerObserver.getValue();
      assertEquals(1, ownerEmbeddersResponse.getEmbeddersCount(), "Should have 1 embedder for root user");
      assertEquals(rootUserIdBytes, ownerEmbeddersResponse.getEmbedders(0).getOwnerId(), "Owner ID should match filter");

      // Test 8.3: List embedders by provider type
      TestStreamObserver<ListEmbeddersResponse> listByProviderObserver = new TestStreamObserver<>();
      ListEmbeddersRequest listByProviderRequest = ListEmbeddersRequest.newBuilder()
          .setProviderType(ProviderType.PROVIDER_TYPE_VLLM)
          .build();

      embedderService.listEmbedders(listByProviderRequest, listByProviderObserver);

      // Verify list by provider type response
      assertFalse(listByProviderObserver.hasError(),
          "List by provider type should not error: " +
          (listByProviderObserver.hasError() ? listByProviderObserver.getError().getMessage() : ""));
      assertTrue(listByProviderObserver.hasValue(), "Should have embedders response");

      ListEmbeddersResponse providerEmbeddersResponse = listByProviderObserver.getValue();
      assertEquals(1, providerEmbeddersResponse.getEmbeddersCount(), "Should have 1 embedder with VLLM provider");
      assertEquals(ProviderType.PROVIDER_TYPE_VLLM, 
                   providerEmbeddersResponse.getEmbedders(0).getProviderType(), 
                   "Provider type should match filter");

      // Test 8.4: List embedders by label selector
      TestStreamObserver<ListEmbeddersResponse> listByLabelObserver = new TestStreamObserver<>();
      ListEmbeddersRequest listByLabelRequest = ListEmbeddersRequest.newBuilder()
          .putLabelSelectors("merged-label", "merged-value")
          .build();

      embedderService.listEmbedders(listByLabelRequest, listByLabelObserver);

      // Verify list by label response
      assertFalse(listByLabelObserver.hasError(),
          "List by label should not error: " +
          (listByLabelObserver.hasError() ? listByLabelObserver.getError().getMessage() : ""));
      assertTrue(listByLabelObserver.hasValue(), "Should have embedders response");

      ListEmbeddersResponse labelEmbeddersResponse = listByLabelObserver.getValue();
      assertEquals(1, labelEmbeddersResponse.getEmbeddersCount(), "Should have 1 embedder with merged-label");
      assertEquals("merged-value", 
                   labelEmbeddersResponse.getEmbedders(0).getLabelsMap().get("merged-label"), 
                   "Label value should match filter");

      // =========================================================================
      // Step 9: Test deleting an embedder and verifying it's removed
      // =========================================================================
      System.out.println("\nStep 9: Test deleting an embedder");

      // Create a new embedder specifically for deletion testing
      TestStreamObserver<Embedder> deleteTestEmbedderObserver = new TestStreamObserver<>();
      CreateEmbedderRequest deleteTestRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Embedder To Delete")
          .setDescription("This embedder will be deleted")
          .setProviderType(ProviderType.PROVIDER_TYPE_TEI)
          .setEndpointUrl("http://tei-server:8080")
          .setApiPath("/embeddings")
          .setModelIdentifier("tei-embedder-1")
          .setDimensionality(768)
          .setCredentials("tei-test-token")
          .putLabels("purpose", "deletion-test")
          .build();

      embedderService.createEmbedder(deleteTestRequest, deleteTestEmbedderObserver);

      // Verify embedder was created successfully
      assertFalse(deleteTestEmbedderObserver.hasError(),
          "Create embedder for deletion test should not error: " +
          (deleteTestEmbedderObserver.hasError() ? deleteTestEmbedderObserver.getError().getMessage() : ""));
      assertTrue(deleteTestEmbedderObserver.hasValue(), "Should have embedder response");

      Embedder embedderToDelete = deleteTestEmbedderObserver.getValue();
      UUID embedderToDeleteId = UuidUtil.fromProtoBytes(embedderToDelete.getEmbedderId()).getValue();

      System.out.println("Created embedder for deletion test, ID: " + embedderToDeleteId);

      // Verify the embedder exists in the database before deletion
      assertTrue(embedderExistsInDatabase(embedderToDeleteId),
          "Embedder should exist before deletion");

      // Now delete the embedder
      TestStreamObserver<Empty> deleteEmbedderObserver = new TestStreamObserver<>();
      DeleteEmbedderRequest deleteEmbedderRequest = DeleteEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(embedderToDeleteId))
          .build();

      embedderService.deleteEmbedder(deleteEmbedderRequest, deleteEmbedderObserver);

      // Verify deletion was successful
      assertFalse(deleteEmbedderObserver.hasError(),
          "Embedder deletion should not error: " +
          (deleteEmbedderObserver.hasError() ? deleteEmbedderObserver.getError().getMessage() : ""));
      assertTrue(deleteEmbedderObserver.isCompleted(), "Response should be completed");

      // Verify the embedder no longer exists in the database
      assertFalse(embedderExistsInDatabase(embedderToDeleteId),
          "Embedder should not exist after deletion");

      // Try to delete the same embedder again (should fail with NOT_FOUND)
      TestStreamObserver<Empty> deleteAgainObserver = new TestStreamObserver<>();
      embedderService.deleteEmbedder(deleteEmbedderRequest, deleteAgainObserver);

      // Verify the second deletion fails with NOT_FOUND
      assertTrue(deleteAgainObserver.hasError(),
          "Second deletion of the same embedder should fail");

      StatusRuntimeException deleteException = (StatusRuntimeException) deleteAgainObserver.getError();
      assertEquals(Status.NOT_FOUND.getCode(), deleteException.getStatus().getCode(),
          "Error code should be NOT_FOUND");
      assertTrue(deleteException.getStatus().getDescription().contains("not found"),
          "Error should mention embedder not found");

    } finally {
      previousContext.attach(); // Restore original context
    }
  }

  /**
   * Test creating an embedder with invalid parameters (validation error cases).
   */
  @Test
  void testEmbedderValidation() throws SQLException {
    // Set up the root user for authentication
    com.goodmem.security.User rootUser = setupRootUser();

    // Set up authentication context
    Context authenticatedContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, rootUser);
    Context previousContext = authenticatedContext.attach();

    try {
      // Test case 1: Missing display name
      TestStreamObserver<Embedder> missingNameObserver = new TestStreamObserver<>();
      CreateEmbedderRequest missingNameRequest = CreateEmbedderRequest.newBuilder()
          // No display_name
          .setProviderType(ProviderType.PROVIDER_TYPE_OPENAI)
          .setEndpointUrl("https://api.openai.com")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("text-embedding-3-small")
          .setDimensionality(1536)
          .setCredentials("sk-test-key")
          .build();

      embedderService.createEmbedder(missingNameRequest, missingNameObserver);
      
      // Verify validation error for missing display name
      assertTrue(missingNameObserver.hasError(), "Missing display name should cause an error");
      StatusRuntimeException exception = (StatusRuntimeException) missingNameObserver.getError();
      assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode(), 
                   "Error should be INVALID_ARGUMENT");
      assertTrue(exception.getStatus().getDescription().toLowerCase().contains("name"),
                 "Error should mention display name is required");

      // Test case 2: Missing provider type
      TestStreamObserver<Embedder> missingProviderObserver = new TestStreamObserver<>();
      CreateEmbedderRequest missingProviderRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Test Embedder")
          // No provider_type or UNSPECIFIED
          .setProviderType(ProviderType.PROVIDER_TYPE_UNSPECIFIED)
          .setEndpointUrl("https://api.openai.com")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("text-embedding-3-small")
          .setDimensionality(1536)
          .setCredentials("sk-test-key")
          .build();

      embedderService.createEmbedder(missingProviderRequest, missingProviderObserver);
      
      // Verify validation error for missing provider type
      assertTrue(missingProviderObserver.hasError(), "Missing provider type should cause an error");
      exception = (StatusRuntimeException) missingProviderObserver.getError();
      assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode(), 
                   "Error should be INVALID_ARGUMENT");
      assertTrue(exception.getStatus().getDescription().toLowerCase().contains("provider"),
                 "Error should mention provider type is required");

      // Test case 3: Missing endpoint URL
      TestStreamObserver<Embedder> missingEndpointObserver = new TestStreamObserver<>();
      CreateEmbedderRequest missingEndpointRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Test Embedder")
          .setProviderType(ProviderType.PROVIDER_TYPE_OPENAI)
          // No endpoint_url
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("text-embedding-3-small")
          .setDimensionality(1536)
          .setCredentials("sk-test-key")
          .build();

      embedderService.createEmbedder(missingEndpointRequest, missingEndpointObserver);
      
      // Verify validation error for missing endpoint URL
      assertTrue(missingEndpointObserver.hasError(), "Missing endpoint URL should cause an error");
      exception = (StatusRuntimeException) missingEndpointObserver.getError();
      assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode(), 
                   "Error should be INVALID_ARGUMENT");
      assertTrue(exception.getStatus().getDescription().toLowerCase().contains("endpoint"),
                 "Error should mention endpoint URL is required");

      // Test case 4: Invalid dimensionality (negative or zero)
      TestStreamObserver<Embedder> invalidDimensionsObserver = new TestStreamObserver<>();
      CreateEmbedderRequest invalidDimensionsRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Test Embedder")
          .setProviderType(ProviderType.PROVIDER_TYPE_OPENAI)
          .setEndpointUrl("https://api.openai.com")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("text-embedding-3-small")
          .setDimensionality(0) // Invalid dimensionality
          .setCredentials("sk-test-key")
          .build();

      embedderService.createEmbedder(invalidDimensionsRequest, invalidDimensionsObserver);
      
      // Verify validation error for invalid dimensionality
      assertTrue(invalidDimensionsObserver.hasError(), "Invalid dimensionality should cause an error");
      exception = (StatusRuntimeException) invalidDimensionsObserver.getError();
      assertEquals(Status.INVALID_ARGUMENT.getCode(), exception.getStatus().getCode(), 
                   "Error should be INVALID_ARGUMENT");
      assertTrue(exception.getStatus().getDescription().toLowerCase().contains("dimensionality"),
                 "Error should mention dimensionality must be positive");

    } finally {
      previousContext.attach(); // Restore original context
    }
  }

  /**
   * Test permissions enforcement by creating a regular user and verifying
   * they cannot perform operations on embedders owned by others.
   */
  @Test
  void testEmbedderPermissions() throws SQLException {
    // Set up the root user for authentication
    com.goodmem.security.User rootUser = setupRootUser();
    UUID rootUserId = rootUser.getId();
    ByteString rootUserIdBytes = UuidUtil.toProtoBytes(rootUserId);

    // Create a regular user with limited permissions
    UUID regularUserId = createUserInDatabase(
        "regularuser",
        "Regular User",
        "regularuser@example.com",
        "password123",
        Roles.USER.role().getName(),
        rootUserId);

    // Create a regular user security User object with USER role
    com.goodmem.security.User regularUser = authenticateUser(
        "dummy-key", // Not actually used for authentication in tests
        regularUserId,
        Roles.USER.role());

    // First set up the root context and create an embedder that the root owns
    Context rootContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, rootUser);
    Context previousContext = rootContext.attach();

    UUID rootEmbedderId = null;
    try {
      // Create an embedder as root
      TestStreamObserver<Embedder> createEmbedderObserver = new TestStreamObserver<>();
      CreateEmbedderRequest rootEmbedderRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Root's Embedder")
          .setProviderType(ProviderType.PROVIDER_TYPE_OPENAI)
          .setEndpointUrl("https://api.openai.com/permissions-test")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("text-embedding-3-small")
          .setDimensionality(1536)
          .setCredentials("sk-test-key")
          .build();

      embedderService.createEmbedder(rootEmbedderRequest, createEmbedderObserver);
      
      assertFalse(createEmbedderObserver.hasError(), "Root should be able to create an embedder");
      Embedder rootEmbedder = createEmbedderObserver.getValue();
      rootEmbedderId = UuidUtil.fromProtoBytes(rootEmbedder.getEmbedderId()).getValue();
      
    } finally {
      rootContext.detach(previousContext);
    }

    // Now switch to the regular user context
    Context regularContext = Context.current().withValue(AuthInterceptor.USER_CONTEXT_KEY, regularUser);
    previousContext = regularContext.attach();

    try {
      // Test case 1: Regular user can create their own embedder
      TestStreamObserver<Embedder> createOwnEmbedderObserver = new TestStreamObserver<>();
      CreateEmbedderRequest ownEmbedderRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Regular User's Embedder")
          .setProviderType(ProviderType.PROVIDER_TYPE_VLLM)
          .setEndpointUrl("http://localhost:8000/permissions-test")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("local-model")
          .setDimensionality(1024)
          .setCredentials("local-key")
          .build();

      embedderService.createEmbedder(ownEmbedderRequest, createOwnEmbedderObserver);
      
      assertFalse(createOwnEmbedderObserver.hasError(), 
                 "Regular user should be able to create their own embedder");
      Embedder regularUserEmbedder = createOwnEmbedderObserver.getValue();
      UUID regularEmbedderId = UuidUtil.fromProtoBytes(regularUserEmbedder.getEmbedderId()).getValue();
      
      ByteString regularUserIdBytes = UuidUtil.toProtoBytes(regularUserId);
      assertEquals(regularUserIdBytes, regularUserEmbedder.getOwnerId(), 
                  "Regular user's embedder should be owned by them");

      // Test case 2: Regular user trying to create an embedder for another user (should fail)
      TestStreamObserver<Embedder> createForOtherObserver = new TestStreamObserver<>();
      CreateEmbedderRequest forOtherRequest = CreateEmbedderRequest.newBuilder()
          .setDisplayName("Unauthorized Embedder")
          .setProviderType(ProviderType.PROVIDER_TYPE_VLLM)
          .setEndpointUrl("http://localhost:8001")
          .setApiPath("/v1/embeddings")
          .setModelIdentifier("other-model")
          .setDimensionality(1024)
          .setCredentials("other-key")
          .setOwnerId(rootUserIdBytes) // Trying to create for root user
          .build();

      embedderService.createEmbedder(forOtherRequest, createForOtherObserver);
      
      assertTrue(createForOtherObserver.hasError(), 
                "Regular user should not be able to create embedder for others");
      StatusRuntimeException exception = (StatusRuntimeException) createForOtherObserver.getError();
      assertEquals(Status.PERMISSION_DENIED.getCode(), exception.getStatus().getCode(),
                  "Error should be PERMISSION_DENIED");

      // Test case 3: Regular user trying to get another user's embedder (should fail)
      TestStreamObserver<Embedder> getOtherEmbedderObserver = new TestStreamObserver<>();
      GetEmbedderRequest getOtherRequest = GetEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(rootEmbedderId))
          .build();

      embedderService.getEmbedder(getOtherRequest, getOtherEmbedderObserver);
      
      assertTrue(getOtherEmbedderObserver.hasError(), 
                "Regular user should not be able to get other user's embedder");
      exception = (StatusRuntimeException) getOtherEmbedderObserver.getError();
      assertEquals(Status.PERMISSION_DENIED.getCode(), exception.getStatus().getCode(),
                  "Error should be PERMISSION_DENIED");

      // Test case 4: Regular user trying to update another user's embedder (should fail)
      TestStreamObserver<Embedder> updateOtherObserver = new TestStreamObserver<>();
      UpdateEmbedderRequest updateOtherRequest = UpdateEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(rootEmbedderId))
          .setDisplayName("Unauthorized Update")
          .build();

      embedderService.updateEmbedder(updateOtherRequest, updateOtherObserver);
      
      assertTrue(updateOtherObserver.hasError(), 
                "Regular user should not be able to update other user's embedder");
      exception = (StatusRuntimeException) updateOtherObserver.getError();
      assertEquals(Status.PERMISSION_DENIED.getCode(), exception.getStatus().getCode(),
                  "Error should be PERMISSION_DENIED");

      // Test case 5: Regular user trying to delete another user's embedder (should fail)
      TestStreamObserver<Empty> deleteOtherObserver = new TestStreamObserver<>();
      DeleteEmbedderRequest deleteOtherRequest = DeleteEmbedderRequest.newBuilder()
          .setEmbedderId(UuidUtil.toProtoBytes(rootEmbedderId))
          .build();

      embedderService.deleteEmbedder(deleteOtherRequest, deleteOtherObserver);
      
      assertTrue(deleteOtherObserver.hasError(), 
                "Regular user should not be able to delete other user's embedder");
      exception = (StatusRuntimeException) deleteOtherObserver.getError();
      assertEquals(Status.PERMISSION_DENIED.getCode(), exception.getStatus().getCode(),
                  "Error should be PERMISSION_DENIED");

      // Test case 6: Regular user trying to list embedders (should only see their own)
      TestStreamObserver<ListEmbeddersResponse> listEmbeddersObserver = new TestStreamObserver<>();
      ListEmbeddersRequest listRequest = ListEmbeddersRequest.newBuilder().build();

      embedderService.listEmbedders(listRequest, listEmbeddersObserver);
      
      assertFalse(listEmbeddersObserver.hasError(), 
                 "Regular user should be able to list embedders");
      ListEmbeddersResponse listResponse = listEmbeddersObserver.getValue();
      
      // Should only see their own embedder, not root's
      assertEquals(1, listResponse.getEmbeddersCount(), 
                  "Regular user should only see their own embedder");
      assertEquals(regularUserIdBytes, listResponse.getEmbedders(0).getOwnerId(),
                  "Listed embedder should be owned by regular user");

    } finally {
      regularContext.detach(previousContext);
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
   * Checks if an embedder exists in the database.
   */
  private boolean embedderExistsInDatabase(UUID embedderId) throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      String sql = "SELECT 1 FROM embedder WHERE embedder_id = ?";

      try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, embedderId);

        try (ResultSet rs = stmt.executeQuery()) {
          return rs.next(); // Returns true if embedder exists
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