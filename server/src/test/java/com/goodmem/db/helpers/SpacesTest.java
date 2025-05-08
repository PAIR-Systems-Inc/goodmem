package com.goodmem.db.helpers;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.Space;
import com.goodmem.db.Spaces;
import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
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
 * Tests for the Spaces helper class. These tests verify all CRUD operations and edge cases for
 * spaces.
 */
@Testcontainers
public class SpacesTest {

  private static PostgresContext postgresContext;
  private static Connection connection;
  private static UUID testUserId;

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container with schema initialization
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_spaces_test", SpacesTest.class);
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
      stmt.execute("DELETE FROM space");
    }
  }

  @Test
  void testLoadAll_ReturnsEmptyList_WhenNoSpaces() {
    // When: We load all spaces from an empty database
    StatusOr<List<Space>> result = Spaces.loadAll(connection);

    // Then: The operation succeeds but returns an empty list
    assertTrue(result.isOk());
    assertEquals(0, result.getValue().size());
  }

  @Test
  void testLoadAll_ReturnsAllSpaces_WhenMultipleExist() {
    // Given: Multiple spaces in the database
    Space space1 = createTestSpace("space1", false);
    Space space2 = createTestSpace("space2", true);

    Spaces.save(connection, space1);
    Spaces.save(connection, space2);

    // When: We load all spaces
    StatusOr<List<Space>> result = Spaces.loadAll(connection);

    // Then: All spaces are returned
    assertTrue(result.isOk());
    assertEquals(2, result.getValue().size());

    // And: The spaces match what we expect
    List<String> names = result.getValue().stream().map(Space::name).toList();
    assertTrue(names.contains("space1"));
    assertTrue(names.contains("space2"));
  }

  @Test
  void testLoadById_ReturnsSpace_WhenExists() {
    // Given: A space in the database
    Space space = createTestSpace("testspace", false);
    Spaces.save(connection, space);

    // When: We load the space by ID
    StatusOr<Optional<Space>> result = Spaces.loadById(connection, space.spaceId());

    // Then: The space is returned
    assertTrue(result.isOk());
    assertTrue(result.getValue().isPresent());
    assertEquals("testspace", result.getValue().get().name());
    // Check that the embedder ID is correctly set
    assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), result.getValue().get().embedderId());
    assertFalse(result.getValue().get().publicRead());
  }

  @Test
  void testLoadById_ReturnsEmpty_WhenSpaceDoesNotExist() {
    // Given: A non-existent UUID
    UUID nonExistentId = UUID.randomUUID();

    // When: We try to load a space with this ID
    StatusOr<Optional<Space>> result = Spaces.loadById(connection, nonExistentId);

    // Then: The operation succeeds but returns an empty Optional
    assertTrue(result.isOk());
    assertFalse(result.getValue().isPresent());
  }

  @Test
  void testLoadByOwnerId_ReturnsSpaces_WhenExistForOwner() {
    // Given: Multiple spaces for the same owner
    Space space1 = createTestSpace("ownerspace1", false);
    Space space2 = createTestSpace("ownerspace2", true);

    Spaces.save(connection, space1);
    Spaces.save(connection, space2);

    // When: We load spaces by owner ID
    StatusOr<List<Space>> result = Spaces.loadByOwnerId(connection, testUserId);

    // Then: All spaces for that owner are returned
    assertTrue(result.isOk());
    assertEquals(2, result.getValue().size());

    // And: The spaces match what we expect
    List<String> names = result.getValue().stream().map(Space::name).toList();
    assertTrue(names.contains("ownerspace1"));
    assertTrue(names.contains("ownerspace2"));
  }

  @Test
  void testLoadByOwnerAndName_ReturnsSpace_WhenExists() {
    // Given: A space with a specific owner and name
    Space space = createTestSpace("uniquename", false);
    Spaces.save(connection, space);

    // When: We load the space by owner and name
    StatusOr<Optional<Space>> result =
        Spaces.loadByOwnerAndName(connection, testUserId, "uniquename");

    // Then: The space is returned
    assertTrue(result.isOk());
    assertTrue(result.getValue().isPresent());
    assertEquals("uniquename", result.getValue().get().name());
    assertEquals(space.spaceId(), result.getValue().get().spaceId());
  }

  @Test
  void testLoadByOwnerAndName_ReturnsEmpty_WhenNameDoesNotExist() {
    // When: We try to load a space with a non-existent name
    StatusOr<Optional<Space>> result =
        Spaces.loadByOwnerAndName(connection, testUserId, "nonexistentname");

    // Then: The operation succeeds but returns an empty Optional
    assertTrue(result.isOk());
    assertFalse(result.getValue().isPresent());
  }

  @Test
  void testSave_CreatesNewSpace_WhenIdDoesNotExist() {
    // Given: A new space
    Space space = createTestSpace("newspace", false);

    // When: We save the space
    StatusOr<Integer> result = Spaces.save(connection, space);

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The space can be retrieved from the database
    StatusOr<Optional<Space>> loadResult = Spaces.loadById(connection, space.spaceId());
    assertTrue(loadResult.isOk());
    assertTrue(loadResult.getValue().isPresent());
  }

  @Test
  void testSave_UpdatesExistingSpace_WhenIdExists() {
    var uuid2 = EntityHelper.createTestEmbedder(
        connection,
        UUID.fromString("00000000-0000-0000-0000-000000000002"),
        testUserId);

    // Given: An existing space
    Space space = createTestSpace("updatespace", false);
    Spaces.save(connection, space);

    // When: We update the space
    Instant now = Instant.now();
    Space updatedSpace =
        new Space(
            space.spaceId(),
            space.ownerId(),
            space.name(),
            space.labels(),
            uuid2, // Changed embedder ID
            true, // Changed publicRead to true
            space.createdAt(),
            now, // Updated updatedAt
            space.createdById(),
            space.updatedById());
    StatusOr<Integer> result = Spaces.save(connection, updatedSpace);

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The space is updated in the database
    StatusOr<Optional<Space>> loadResult = Spaces.loadById(connection, space.spaceId());
    assertTrue(loadResult.isOk());
    assertTrue(loadResult.getValue().isPresent());
    assertEquals(uuid2, loadResult.getValue().get().embedderId());
    assertTrue(loadResult.getValue().get().publicRead());
  }

  @Test
  void testSave_EnforcesUniqueConstraint_OnOwnerAndName() {
    // Given: An existing space
    Space space1 = createTestSpace("uniquespace", false);
    Spaces.save(connection, space1);

    // When: We try to save another space with the same owner and name
    Space space2 = createTestSpace("uniquespace", true);
    StatusOr<Integer> result = Spaces.save(connection, space2);

    // Then: The operation fails due to unique constraint violation
    assertFalse(result.isOk());
    assertTrue(
        result.getStatus().getMessage().toLowerCase().contains("duplicate")
            || result.getStatus().getMessage().toLowerCase().contains("unique")
            || result.getStatus().getMessage().toLowerCase().contains("constraint"));
  }

  @Test
  void testDelete_RemovesSpace_WhenExists() {
    // Given: An existing space
    Space space = createTestSpace("deletespace", false);
    Spaces.save(connection, space);

    // When: We delete the space
    StatusOr<Integer> result = Spaces.delete(connection, space.spaceId());

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The space no longer exists in the database
    StatusOr<Optional<Space>> loadResult = Spaces.loadById(connection, space.spaceId());
    assertTrue(loadResult.isOk());
    assertFalse(loadResult.getValue().isPresent());
  }

  @Test
  void testDelete_ReturnsZero_WhenSpaceDoesNotExist() {
    // Given: A non-existent UUID
    UUID nonExistentId = UUID.randomUUID();

    // When: We try to delete a space with this ID
    StatusOr<Integer> result = Spaces.delete(connection, nonExistentId);

    // Then: The operation succeeds but returns 0 affected rows
    assertTrue(result.isOk());
    assertEquals(0, result.getValue());
  }

  // Helper methods to set up test data

  private static UUID createTestUser() {
    return EntityHelper.createTestUserWithKey(connection).userId();
  }

  private Space createTestSpace(String name, boolean publicRead) {
    UUID spaceId = UUID.randomUUID();
    Instant now = Instant.now();

    UUID embedderUUID;
    embedderUUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    
    // Create the test embedder in the database
    EntityHelper.createTestEmbedder(connection, embedderUUID, testUserId);
    
    return new Space(
        spaceId,
        testUserId,
        name,
        Map.of(), // Empty labels
        embedderUUID,
        publicRead,
        now,
        now,
        testUserId,
        testUserId);
  }
}
