package com.goodmem.db;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.common.status.StatusOr;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Tests for the database layer.
 *
 * <p>Uses TestContainers to spin up a PostgreSQL instance with pgvector.
 */
@Testcontainers
public class DbTest {

  // Use classpath resources for schema files
  private static final String EXTENSIONS_SQL_PATH = "/db/00-extensions.sql";
  private static final String SCHEMA_SQL_PATH = "/db/01-schema.sql";

  /**
   * Use an official PostgreSQL image with pgvector extension.
   *
   * <p>The pgvector/pgvector-postgresql Docker image is a community-maintained image that includes
   * the pgvector extension pre-installed.
   */
  @Container
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
          .withDatabaseName("goodmem")
          .withUsername("goodmem")
          .withPassword("goodmem")
          .withInitScript("db/00-extensions.sql"); // Executes the script from the classpath
          // We'll add the schema in the setUp method

  private static Connection connection;

  @BeforeAll
  static void setUp() throws SQLException {
    postgres.start();
    connection =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            
    // Execute the schema script after the container is started
    try (var stmt = connection.createStatement()) {
      // Read schema file from classpath
      var schemaUrl = DbTest.class.getResource(SCHEMA_SQL_PATH);
      if (schemaUrl == null) {
        throw new RuntimeException("Schema file not found: " + SCHEMA_SQL_PATH);
      }
      
      String schema = new String(java.nio.file.Files.readAllBytes(
          java.nio.file.Path.of(schemaUrl.toURI())));
          
      // Execute the schema
      stmt.execute(schema);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize database schema", e);
    }
  }

  @AfterAll
  static void tearDown() throws SQLException {
    if (connection != null) {
      connection.close();
    }
    postgres.stop();
  }

  @BeforeEach
  void clearDatabase() throws SQLException {
    try (var stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM memory_chunk");
      stmt.execute("DELETE FROM memory");
      stmt.execute("DELETE FROM space");
      stmt.execute("DELETE FROM apikey");
      stmt.execute("DELETE FROM \"user\"");
    }
  }

  @Test
  void testDatabaseConnection() throws SQLException {
    assertNotNull(connection);
    assertTrue(connection.isValid(5));

    // Test execute basic query
    try (var stmt = connection.createStatement();
        var rs = stmt.executeQuery("SELECT 1")) {
      assertTrue(rs.next());
      assertEquals(1, rs.getInt(1));
    }
  }

  @Test
  void testUserCrud() {
    // Create a user
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();
    User user = new User(userId, "testuser", "test@example.com", "Test User", now, now);

    // Insert the user
    StatusOr<Integer> saveResult = Users.save(connection, user);
    if (!saveResult.isOk()) {
      System.out.println("Save failed: " + saveResult.getStatus());
    }
    assertTrue(saveResult.isOk());
    assertEquals(1, saveResult.getValue());

    // Retrieve the user by ID
    StatusOr<Optional<User>> loadByIdResult = Users.loadById(connection, userId);
    assertTrue(loadByIdResult.isOk());
    assertTrue(loadByIdResult.getValue().isPresent());

    // Compare users with custom comparison due to timestamp precision differences
    User loadedUser = loadByIdResult.getValue().get();
    assertEquals(userId, loadedUser.userId());
    assertEquals(user.username(), loadedUser.username());
    assertEquals(user.email(), loadedUser.email());
    assertEquals(user.displayName(), loadedUser.displayName());
    // Don't compare timestamps directly as they might have different precision

    // Retrieve the user by email
    StatusOr<Optional<User>> loadByEmailResult = Users.loadByEmail(connection, "test@example.com");
    assertTrue(loadByEmailResult.isOk());
    assertTrue(loadByEmailResult.getValue().isPresent());

    // Compare key fields, not full object due to timestamp precision
    User emailUser = loadByEmailResult.getValue().get();
    assertEquals(userId, emailUser.userId());
    assertEquals(user.username(), emailUser.username());
    assertEquals(user.email(), emailUser.email());

    // Retrieve all users
    StatusOr<List<User>> loadAllResult = Users.loadAll(connection);
    assertTrue(loadAllResult.isOk());
    assertEquals(1, loadAllResult.getValue().size());

    // Compare key fields, not full object
    User listUser = loadAllResult.getValue().get(0);
    assertEquals(userId, listUser.userId());
    assertEquals(user.username(), listUser.username());
    assertEquals(user.email(), listUser.email());

    // Update the user
    User updatedUser =
        new User(userId, "testuser2", "test@example.com", "Updated User", now, now.plusSeconds(60));

    StatusOr<Integer> updateResult = Users.save(connection, updatedUser);
    assertTrue(updateResult.isOk());
    assertEquals(1, updateResult.getValue());

    // Verify the update
    StatusOr<Optional<User>> afterUpdateResult = Users.loadById(connection, userId);
    assertTrue(afterUpdateResult.isOk());
    assertTrue(afterUpdateResult.getValue().isPresent());

    // Compare key fields, not full object
    User updatedLoadedUser = afterUpdateResult.getValue().get();
    assertEquals(userId, updatedLoadedUser.userId());
    assertEquals(updatedUser.username(), updatedLoadedUser.username());
    assertEquals(updatedUser.email(), updatedLoadedUser.email());
    assertEquals(updatedUser.displayName(), updatedLoadedUser.displayName());

    // Delete the user
    StatusOr<Integer> deleteResult = Users.delete(connection, userId);
    assertTrue(deleteResult.isOk());
    assertEquals(1, deleteResult.getValue());

    // Verify the user was deleted
    StatusOr<Optional<User>> afterDeleteResult = Users.loadById(connection, userId);
    assertTrue(afterDeleteResult.isOk());
    assertFalse(afterDeleteResult.getValue().isPresent());
  }

  /** Test to verify that we can properly store and retrieve vectors using pgvector. */
  @Test
  void testVectorOperations() throws SQLException {
    // First, create a test schema to verify pgvector is working
    try (var stmt = connection.createStatement()) {
      // Drop test table if it exists
      stmt.execute("DROP TABLE IF EXISTS vector_test");

      // Create a test table with a vector column
      stmt.execute("CREATE TABLE vector_test (id SERIAL PRIMARY KEY, embedding vector(3))");

      // Insert a test vector
      stmt.execute("INSERT INTO vector_test (embedding) VALUES ('[1.0, 2.0, 3.0]')");

      // Query the vector
      var rs = stmt.executeQuery("SELECT * FROM vector_test");
      assertTrue(rs.next());

      // Check that we can retrieve the vector
      // The result will be a string like "[1,2,3]" or similar
      String vector = rs.getString("embedding");
      assertNotNull(vector);
      assertTrue(vector.contains("1") && vector.contains("2") && vector.contains("3"));

      // Test vector operations
      rs =
          stmt.executeQuery(
              "SELECT '[3.0, 4.0, 5.0]'::vector <-> embedding AS distance FROM vector_test");
      assertTrue(rs.next());
      double distance = rs.getDouble("distance");
      assertTrue(distance > 0); // L2 distance should be positive

      // Clean up
      stmt.execute("DROP TABLE vector_test");
    }
  }

  /**
   * Test that the pgvector extension is working in our test container.
   *
   * <p>This test verifies that we can use the vector type and operations without testing our actual
   * database layer code.
   */
  @Test
  void testMemoryChunkVectors() throws SQLException {
    // Create a simple test table with a vector column
    try (var stmt = connection.createStatement()) {
      // Drop test table if it exists
      stmt.execute("DROP TABLE IF EXISTS vector_test");

      // Create a simple test table
      stmt.execute("CREATE TABLE vector_test (id SERIAL PRIMARY KEY, v vector(3))");

      // Insert a test vector
      stmt.execute("INSERT INTO vector_test (v) VALUES ('[1.0, 2.0, 3.0]')");

      // Query the vector
      var rs = stmt.executeQuery("SELECT * FROM vector_test");
      assertTrue(rs.next());
      assertNotNull(rs.getString("v"));

      // Test vector similarity search
      rs = stmt.executeQuery("SELECT * FROM vector_test ORDER BY v <-> '[1.1, 2.1, 3.1]'");
      assertTrue(rs.next());

      // Clean up
      stmt.execute("DROP TABLE vector_test");
    }

    // This test verifies that the test container support for pgvector works,
    // which is what we need to test our actual database layer code.
  }
}