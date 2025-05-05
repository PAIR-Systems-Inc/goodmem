package com.goodmem.db.helpers;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.Embedder;
import com.goodmem.db.EmbedderModality;
import com.goodmem.db.EmbedderProviderType;
import com.goodmem.db.Embedders;
import com.goodmem.db.User;
import com.goodmem.db.Users;
import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests for the Embedders helper class. These tests verify all CRUD operations and edge cases for
 * embedders.
 */
@Testcontainers
public class EmbeddersTest {

  private static PostgresContext postgresContext;
  private static Connection connection;
  private static UUID testUserId;

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container with schema initialization
    postgresContext =
        PostgresTestHelper.setupPostgres("goodmem_embedders_test", EmbeddersTest.class);
    connection = postgresContext.getConnection();

    // Create a test user that will be reused across tests
    testUserId = createTestUser();
  }

  @AfterAll
  static void tearDown() {
    // Close connection and stop container
    if (postgresContext != null) {
      postgresContext.close();
    }
  }

  @BeforeEach
  void clearData() throws SQLException {
    try (var stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM embedder");
    }
  }

  @Test
  void testLoadAll_ReturnsEmptyList_WhenNoEmbedders() {
    // When: We load all embedders from an empty database
    StatusOr<List<Embedder>> result = Embedders.loadAll(connection);

    // Then: The operation succeeds but returns an empty list
    assertTrue(result.isOk());
    assertEquals(0, result.getValue().size());
  }

  @Test
  void testLoadAll_ReturnsAllEmbedders_WhenMultipleExist() {
    // Given: Multiple embedders in the database
    Embedder embedder1 = createTestEmbedder("OpenAI Embedder", EmbedderProviderType.OPENAI);
    Embedder embedder2 = createTestEmbedder("VLLM Embedder", EmbedderProviderType.VLLM);

    Embedders.save(connection, embedder1);
    Embedders.save(connection, embedder2);

    // When: We load all embedders
    StatusOr<List<Embedder>> result = Embedders.loadAll(connection);

    // Then: All embedders are returned
    assertTrue(result.isOk());
    assertEquals(2, result.getValue().size());

    // And: The embedders match what we expect
    List<String> names = result.getValue().stream().map(Embedder::displayName).toList();
    assertTrue(names.contains("OpenAI Embedder"));
    assertTrue(names.contains("VLLM Embedder"));
  }

  @Test
  void testLoadById_ReturnsEmbedder_WhenExists() {
    // Given: An embedder in the database
    Embedder embedder = createTestEmbedder("Test Embedder", EmbedderProviderType.OPENAI);
    Embedders.save(connection, embedder);

    // When: We load the embedder by ID
    StatusOr<Optional<Embedder>> result = Embedders.loadById(connection, embedder.embedderId());

    // Then: The embedder is returned
    assertTrue(result.isOk());
    assertTrue(result.getValue().isPresent());
    assertEquals("Test Embedder", result.getValue().get().displayName());
    assertEquals(EmbedderProviderType.OPENAI, result.getValue().get().providerType());
    assertEquals("text-embedding-3-small", result.getValue().get().modelIdentifier());
  }

  @Test
  void testLoadById_ReturnsEmpty_WhenEmbedderDoesNotExist() {
    // Given: A non-existent UUID
    UUID nonExistentId = UUID.randomUUID();

    // When: We try to load an embedder with this ID
    StatusOr<Optional<Embedder>> result = Embedders.loadById(connection, nonExistentId);

    // Then: The operation succeeds but returns an empty Optional
    assertTrue(result.isOk());
    assertFalse(result.getValue().isPresent());
  }

  @Test
  void testLoadByOwnerId_ReturnsEmbedders_WhenExistForOwner() {
    // Given: Multiple embedders for the same owner
    Embedder embedder1 = createTestEmbedder("Owner Embedder 1", EmbedderProviderType.OPENAI);
    Embedder embedder2 = createTestEmbedder("Owner Embedder 2", EmbedderProviderType.VLLM);

    Embedders.save(connection, embedder1);
    Embedders.save(connection, embedder2);

    // When: We load embedders by owner ID
    StatusOr<List<Embedder>> result = Embedders.loadByOwnerId(connection, testUserId);

    // Then: All embedders for that owner are returned
    assertTrue(result.isOk());
    assertEquals(2, result.getValue().size());

    // And: The embedders match what we expect
    List<String> names = result.getValue().stream().map(Embedder::displayName).toList();
    assertTrue(names.contains("Owner Embedder 1"));
    assertTrue(names.contains("Owner Embedder 2"));
  }

  @Test
  void testLoadByProviderType_ReturnsEmbedders_WhenExistForType() {
    // Given: Multiple embedders with different provider types
    Embedder openaiEmbedder = createTestEmbedder("OpenAI Embedder", EmbedderProviderType.OPENAI);
    Embedder vllmEmbedder = createTestEmbedder("VLLM Embedder", EmbedderProviderType.VLLM);
    Embedder teiEmbedder = createTestEmbedder("TEI Embedder", EmbedderProviderType.TEI);

    Embedders.save(connection, openaiEmbedder);
    Embedders.save(connection, vllmEmbedder);
    Embedders.save(connection, teiEmbedder);

    // When: We load embedders by provider type
    StatusOr<List<Embedder>> result =
        Embedders.loadByProviderType(connection, EmbedderProviderType.OPENAI);

    // Then: Only embedders of that provider type are returned
    assertTrue(result.isOk());
    assertEquals(1, result.getValue().size());
    assertEquals("OpenAI Embedder", result.getValue().getFirst().displayName());
    assertEquals(EmbedderProviderType.OPENAI, result.getValue().getFirst().providerType());
  }

  @Test
  void testLoadByConnectionDetails_ReturnsEmbedder_WhenExists() {
    // Given: An embedder with specific connection details
    Embedder embedder = createTestEmbedder("Unique Embedder", EmbedderProviderType.OPENAI);
    Embedders.save(connection, embedder);

    // When: We load the embedder by connection details
    StatusOr<Optional<Embedder>> result =
        Embedders.loadByConnectionDetails(
            connection, "https://api.openai.com", "/v1/embeddings", "text-embedding-3-small");

    // Then: The embedder is returned
    assertTrue(result.isOk());
    assertTrue(result.getValue().isPresent());
    assertEquals("Unique Embedder", result.getValue().get().displayName());
    assertEquals(embedder.embedderId(), result.getValue().get().embedderId());
  }

  @Test
  void testSave_CreatesNewEmbedder_WhenIdDoesNotExist() {
    // Given: A new embedder
    Embedder embedder = createTestEmbedder("New Embedder", EmbedderProviderType.OPENAI);

    // When: We save the embedder
    StatusOr<Integer> result = Embedders.save(connection, embedder);

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The embedder can be retrieved from the database
    StatusOr<Optional<Embedder>> loadResult = Embedders.loadById(connection, embedder.embedderId());
    assertTrue(loadResult.isOk());
    assertTrue(loadResult.getValue().isPresent());
  }

  @Test
  void testSave_UpdatesExistingEmbedder_WhenIdExists() {
    // Given: An existing embedder
    Embedder embedder = createTestEmbedder("Update Embedder", EmbedderProviderType.OPENAI);
    Embedders.save(connection, embedder);

    // When: We update the embedder
    Instant now = Instant.now();
    Embedder updatedEmbedder =
        new Embedder(
            embedder.embedderId(),
            "Updated Name", // Changed display name
            "Updated description", // Changed description
            embedder.providerType(),
            embedder.endpointUrl(),
            embedder.apiPath(),
            embedder.modelIdentifier(),
            1024, // Changed dimensionality
            512, // Added max sequence length
            ImmutableList.of(EmbedderModality.TEXT, EmbedderModality.IMAGE), // Changed modalities
            embedder.credentials(),
            Map.of("updated", "true"), // Changed labels
            "1.1", // Changed version
            embedder.monitoringEndpoint(),
            embedder.ownerId(),
            embedder.createdAt(),
            now, // Updated updatedAt
            embedder.createdById(),
            embedder.updatedById());

    StatusOr<Integer> result = Embedders.save(connection, updatedEmbedder);

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The embedder is updated in the database
    StatusOr<Optional<Embedder>> loadResult = Embedders.loadById(connection, embedder.embedderId());
    assertTrue(loadResult.isOk());
    assertTrue(loadResult.getValue().isPresent());
    assertEquals("Updated Name", loadResult.getValue().get().displayName());
    assertEquals("Updated description", loadResult.getValue().get().description());
    assertEquals(1024, loadResult.getValue().get().dimensionality());
    assertEquals(Integer.valueOf(512), loadResult.getValue().get().maxSequenceLength());
    assertEquals(2, loadResult.getValue().get().supportedModalities().size());
    assertTrue(loadResult.getValue().get().supportedModalities().contains(EmbedderModality.IMAGE));
    assertEquals("true", loadResult.getValue().get().labels().get("updated"));
    assertEquals("1.1", loadResult.getValue().get().version());
  }

  @Test
  void testSave_EnforcesUniqueConstraint_OnConnectionDetails() {
    // Given: An existing embedder
    Embedder embedder1 = createTestEmbedder("Original Embedder", EmbedderProviderType.OPENAI);
    Embedders.save(connection, embedder1);

    // When: We try to save another embedder with the same connection details
    Embedder embedder2 = createTestEmbedder("Duplicate Embedder", EmbedderProviderType.OPENAI);
    StatusOr<Integer> result = Embedders.save(connection, embedder2);

    // Then: The operation fails due to unique constraint violation
    assertFalse(result.isOk());
    assertTrue(
        result.getStatus().getMessage().toLowerCase().contains("duplicate")
            || result.getStatus().getMessage().toLowerCase().contains("unique")
            || result.getStatus().getMessage().toLowerCase().contains("constraint"));
  }

  @Test
  void testDelete_RemovesEmbedder_WhenExists() {
    // Given: An existing embedder
    Embedder embedder = createTestEmbedder("Delete Embedder", EmbedderProviderType.OPENAI);
    Embedders.save(connection, embedder);

    // When: We delete the embedder
    StatusOr<Integer> result = Embedders.delete(connection, embedder.embedderId());

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The embedder no longer exists in the database
    StatusOr<Optional<Embedder>> loadResult = Embedders.loadById(connection, embedder.embedderId());
    assertTrue(loadResult.isOk());
    assertFalse(loadResult.getValue().isPresent());
  }

  @Test
  void testDelete_ReturnsZero_WhenEmbedderDoesNotExist() {
    // Given: A non-existent UUID
    UUID nonExistentId = UUID.randomUUID();

    // When: We try to delete an embedder with this ID
    StatusOr<Integer> result = Embedders.delete(connection, nonExistentId);

    // Then: The operation succeeds but returns 0 affected rows
    assertTrue(result.isOk());
    assertEquals(0, result.getValue());
  }

  // Helper methods to set up test data

  private static UUID createTestUser() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();
    User user = new User(userId, "testuser", "test@example.com", "Test User", now, now);
    Users.save(connection, user);
    return userId;
  }

  private Embedder createTestEmbedder(String displayName, EmbedderProviderType providerType) {
    UUID embedderId = UUID.randomUUID();
    Instant now = Instant.now();

    String modelIdentifier = "text-embedding-3-small";
    String endpointUrl = "https://api.openai.com";
    if (providerType == EmbedderProviderType.VLLM) {
      modelIdentifier = "llama3";
      endpointUrl = "http://vllm-server:8000";
    } else if (providerType == EmbedderProviderType.TEI) {
      modelIdentifier = "text-embedding";
      endpointUrl = "https://tei.googleapis.com";
    }

    return new Embedder(
        embedderId,
        displayName,
        "Test embedder for " + providerType,
        providerType,
        endpointUrl,
        "/v1/embeddings",
        modelIdentifier,
        1536,
        null, // No max sequence length
        ImmutableList.of(EmbedderModality.TEXT),
        "api_key_test_123",
        ImmutableMap.of("test", "true", "environment", "development"),
        "1.0",
        null, // No monitoring endpoint
        testUserId,
        now,
        now,
        testUserId,
        testUserId);
  }
}
