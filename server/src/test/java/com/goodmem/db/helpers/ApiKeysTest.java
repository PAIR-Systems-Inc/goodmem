package com.goodmem.db.helpers;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.ApiKey;
import com.goodmem.db.ApiKeys;
import com.goodmem.db.User;
import com.goodmem.db.Users;
import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests for the ApiKeys helper class. These tests verify all CRUD operations and edge cases for API
 * keys.
 */
@Testcontainers
public class ApiKeysTest {

  private static PostgresContext postgresContext;
  private static Connection connection;
  private static UUID testUserId;

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container with schema initialization
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_apikeys_test", ApiKeysTest.class);
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
      stmt.execute("DELETE FROM apikey");
    }
  }

  @Test
  void testLoadAll_ReturnsEmptyList_WhenNoApiKeys() {
    // When: We load all API keys from an empty database
    StatusOr<List<ApiKey>> result = ApiKeys.loadAll(connection);

    // Then: The operation succeeds but returns an empty list
    assertTrue(result.isOk());
    assertEquals(0, result.getValue().size());
  }

  @Test
  void testLoadAll_ReturnsAllApiKeys_WhenMultipleExist() {
    // Given: Multiple API keys in the database
    ByteString hash1 = randomBytes();
    ByteString hash2 = randomBytes();
    ApiKey key1 = createTestApiKey("prf1", hash1, "ACTIVE");
    ApiKey key2 = createTestApiKey("prf2", hash2, "INACTIVE");

    ApiKeys.save(connection, key1);
    ApiKeys.save(connection, key2);

    // When: We load all API keys
    StatusOr<List<ApiKey>> result = ApiKeys.loadAll(connection);

    // Then: All API keys are returned
    assertTrue(result.isOk());
    assertEquals(2, result.getValue().size());

    // And: The API keys match what we expect
    List<String> prefixes = result.getValue().stream().map(ApiKey::keyPrefix).toList();
    assertTrue(prefixes.contains("prf1"));
    assertTrue(prefixes.contains("prf2"));
  }

  @Test
  void testLoadById_ReturnsApiKey_WhenExists() {
    // Given: An API key in the database
    ByteString uniqueHash = randomBytes();
    ApiKey key = createTestApiKey("tst_key", uniqueHash, "ACTIVE"); // 7 chars
    ApiKeys.save(connection, key);

    // When: We load the API key by ID
    StatusOr<Optional<ApiKey>> result = ApiKeys.loadById(connection, key.apiKeyId());

    // Then: The API key is returned
    assertTrue(result.isOk());
    assertTrue(result.getValue().isPresent());
    assertEquals("tst_key", result.getValue().get().keyPrefix());
    assertEquals(uniqueHash, result.getValue().get().keyHash());
    assertEquals("ACTIVE", result.getValue().get().status());
  }

  @Test
  void testLoadById_ReturnsEmpty_WhenApiKeyDoesNotExist() {
    // Given: A non-existent UUID
    UUID nonExistentId = UUID.randomUUID();

    // When: We try to load an API key with this ID
    StatusOr<Optional<ApiKey>> result = ApiKeys.loadById(connection, nonExistentId);

    // Then: The operation succeeds but returns an empty Optional
    assertTrue(result.isOk());
    assertFalse(result.getValue().isPresent());
  }

  @Test
  void testLoadByUserId_ReturnsApiKeys_WhenExistForUser() {
    // Given: Multiple API keys for the same user
    ByteString hash1 = randomBytes();
    ByteString hash2 = randomBytes();
    ApiKey key1 = createTestApiKey("usrkey1", hash1, "ACTIVE"); // 7 chars
    ApiKey key2 = createTestApiKey("usrkey2", hash2, "INACTIVE"); // 7 chars

    ApiKeys.save(connection, key1);
    ApiKeys.save(connection, key2);

    // When: We load API keys by user ID
    StatusOr<List<ApiKey>> result = ApiKeys.loadByUserId(connection, testUserId);

    // Then: All API keys for that user are returned
    assertTrue(result.isOk());
    assertEquals(2, result.getValue().size());

    // And: The API keys match what we expect
    List<String> prefixes = result.getValue().stream().map(ApiKey::keyPrefix).toList();
    assertTrue(prefixes.contains("usrkey1"));
    assertTrue(prefixes.contains("usrkey2"));
  }

  @Test
  void testLoadByKeyHash_ReturnsApiKey_WhenExists() {
    ByteString uniqueHash = randomBytes();

    // Given: An API key with a specific hash
    ApiKey key = createTestApiKey("tst_pre", uniqueHash, "ACTIVE"); // 7 chars
    ApiKeys.save(connection, key);

    // When: We load the API key by hash
    StatusOr<Optional<ApiKey>> result = ApiKeys.loadByKeyHash(connection, uniqueHash);

    // Then: The API key is returned
    assertTrue(result.isOk());
    assertTrue(result.getValue().isPresent());
    assertEquals("tst_pre", result.getValue().get().keyPrefix());
    assertEquals(key.apiKeyId(), result.getValue().get().apiKeyId());
  }

  @Test
  void testLoadByKeyHash_ReturnsEmpty_WhenHashDoesNotExist() {
    // Generate a random hash that won't exist in the database
    ByteString nonExistentHash = randomBytes();
    
    // When: We try to load an API key with a non-existent hash
    StatusOr<Optional<ApiKey>> result = ApiKeys.loadByKeyHash(connection, nonExistentHash);

    // Then: The operation succeeds but returns an empty Optional
    assertTrue(result.isOk());
    assertFalse(result.getValue().isPresent());
  }

  @Test
  void testSave_CreatesNewApiKey_WhenIdDoesNotExist() {
    // Given: A new API key
    ByteString uniqueHash = randomBytes();
    ApiKey key = createTestApiKey("newkey", uniqueHash, "ACTIVE"); // 6 chars

    // When: We save the API key
    StatusOr<Integer> result = ApiKeys.save(connection, key);

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The API key can be retrieved from the database
    StatusOr<Optional<ApiKey>> loadResult = ApiKeys.loadById(connection, key.apiKeyId());
    assertTrue(loadResult.isOk());
    assertTrue(loadResult.getValue().isPresent());
  }

  @Test
  void testSave_UpdatesExistingApiKey_WhenIdExists() {
    // Given: An existing API key
    ByteString uniqueHash = randomBytes();
    ApiKey key = createTestApiKey("updatekey", uniqueHash, "ACTIVE"); // 9 chars
    ApiKeys.save(connection, key);

    // When: We update the API key
    Instant now = Instant.now();
    ApiKey updatedKey =
        new ApiKey(
            key.apiKeyId(),
            key.userId(),
            key.keyPrefix(),
            key.keyHash(),
            "INACTIVE", // Changed status
            key.labels(),
            key.expiresAt(),
            now, // Updated lastUsedAt
            key.createdAt(),
            now, // Updated updatedAt
            key.createdById(),
            key.updatedById());
    StatusOr<Integer> result = ApiKeys.save(connection, updatedKey);

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The API key is updated in the database
    StatusOr<Optional<ApiKey>> loadResult = ApiKeys.loadById(connection, key.apiKeyId());
    assertTrue(loadResult.isOk());
    assertTrue(loadResult.getValue().isPresent());
    assertEquals("INACTIVE", loadResult.getValue().get().status());
  }

  @Test
  void testUpdateLastUsed_UpdatesTimestamp_WhenApiKeyExists() throws SQLException {
    System.out.println("===== Starting testUpdateLastUsed_UpdatesTimestamp_WhenApiKeyExists =====");

    // Verify testUserId exists
    System.out.println("Verifying test user ID: " + testUserId);
    try (var stmt = connection.prepareStatement("SELECT * FROM \"user\" WHERE user_id = ?")) {
      stmt.setObject(1, testUserId);
      try (var rs = stmt.executeQuery()) {
        if (rs.next()) {
          System.out.println("Test user found in database");
        } else {
          System.err.println("ERROR: Test user not found in database!");
        }
      }
    }

    // Given: An existing API key
    System.out.println("Creating new test API key...");
    ByteString uniqueHash = randomBytes();
    ApiKey key =
        createTestApiKey("lastused", uniqueHash, "ACTIVE"); // Key prefix must be <= 10 chars
    System.out.println("API Key created with ID: " + key.apiKeyId());

    // Let's verify the database schema
    System.out.println("Verifying API key table schema...");
    try (var stmt = connection.createStatement()) {
      try (var rs =
          stmt.executeQuery(
              "SELECT column_name, data_type FROM information_schema.columns WHERE table_name ="
                  + " 'apikey' ORDER BY ordinal_position")) {
        while (rs.next()) {
          System.out.println("Column: " + rs.getString(1) + ", Type: " + rs.getString(2));
        }
      }
    }

    // Save the API key using our method
    System.out.println("Saving API key using ApiKeys.save...");
    StatusOr<Integer> saveResult = ApiKeys.save(connection, key);

    if (saveResult.isOk()) {
      System.out.println("Save success, rows affected: " + saveResult.getValue());
    } else {
      System.err.println("Save failed: " + saveResult.getStatus().getMessage());
    }

    assertTrue(saveResult.isOk(), "Save operation should succeed");
    assertEquals(1, saveResult.getValue(), "Save should affect 1 row");

    // Check if the key was actually saved using direct SQL
    System.out.println("Verifying API key exists using direct SQL...");
    boolean keyExists = false;
    try (var stmt = connection.prepareStatement("SELECT * FROM apikey WHERE api_key_id = ?")) {
      stmt.setObject(1, key.apiKeyId());
      try (var rs = stmt.executeQuery()) {
        if (rs.next()) {
          keyExists = true;
          System.out.println("API key found in database");
          System.out.println("  user_id: " + rs.getObject("user_id", UUID.class));
          System.out.println("  key_prefix: " + rs.getString("key_prefix"));
          System.out.println("  key_hash: " + rs.getString("key_hash"));
          System.out.println("  status: " + rs.getString("status"));
        } else {
          System.err.println("ERROR: API key not found in database!");
        }
      }
    }
    assertTrue(keyExists, "API key should exist in database after saving");

    // Verify the key exists before updating using our method
    System.out.println("Verifying API key exists using ApiKeys.loadById...");
    StatusOr<Optional<ApiKey>> verifyResult = ApiKeys.loadById(connection, key.apiKeyId());

    if (verifyResult.isOk()) {
      if (verifyResult.getValue().isPresent()) {
        System.out.println("API key found using loadById");
      } else {
        System.err.println("ERROR: API key not found using loadById");
      }
    } else {
      System.err.println("ERROR: loadById failed: " + verifyResult.getStatus().getMessage());
    }

    assertTrue(verifyResult.isOk(), "loadById should succeed");
    assertTrue(verifyResult.getValue().isPresent(), "API key should exist after saving");

    // When: We update the last used timestamp
    Instant lastUsed = Instant.now().plusSeconds(3600);
    System.out.println("Updating last_used_at timestamp to: " + lastUsed);
    StatusOr<Integer> result = ApiKeys.updateLastUsed(connection, key.apiKeyId(), lastUsed);

    if (result.isOk()) {
      System.out.println("Update success, rows affected: " + result.getValue());
    } else {
      System.err.println("Update failed: " + result.getStatus().getMessage());
    }

    // Try a direct SQL update to see if it works
    if (result.getValue() == 0) {
      System.out.println("Trying direct SQL update as a test...");
      try (var stmt =
          connection.prepareStatement("UPDATE apikey SET last_used_at = ? WHERE api_key_id = ?")) {
        stmt.setTimestamp(1, java.sql.Timestamp.from(lastUsed));
        stmt.setObject(2, key.apiKeyId());
        int rowsAffected = stmt.executeUpdate();
        System.out.println("Direct SQL update affected " + rowsAffected + " rows");
      }
    }

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk(), "Update operation should succeed");
    assertEquals(1, result.getValue(), "Update should affect 1 row");

    // And: The API key's last used timestamp is updated in the database
    StatusOr<Optional<ApiKey>> loadResult = ApiKeys.loadById(connection, key.apiKeyId());
    assertTrue(loadResult.isOk(), "loadById after update should succeed");
    assertTrue(loadResult.getValue().isPresent(), "API key should exist after update");
    assertNotNull(
        loadResult.getValue().get().lastUsedAt(), "Last used timestamp should not be null");

    System.out.println(
        "===== Completed testUpdateLastUsed_UpdatesTimestamp_WhenApiKeyExists =====");
  }

  @Test
  void testUpdateStatus_UpdatesStatus_WhenApiKeyExists() {
    // Given: An existing API key
    ByteString uniqueHash = randomBytes();
    ApiKey key =
        createTestApiKey("status", uniqueHash, "ACTIVE"); // Changed from statuskey (10 chars max)
    ApiKeys.save(connection, key);

    // When: We update the status
    StatusOr<Integer> result =
        ApiKeys.updateStatus(connection, key.apiKeyId(), "REVOKED", testUserId);

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The API key's status is updated in the database
    StatusOr<Optional<ApiKey>> loadResult = ApiKeys.loadById(connection, key.apiKeyId());
    assertTrue(loadResult.isOk());
    assertTrue(loadResult.getValue().isPresent());
    assertEquals("REVOKED", loadResult.getValue().get().status());
  }

  @Test
  void testDelete_RemovesApiKey_WhenExists() {
    // Given: An existing API key
    ByteString uniqueHash = randomBytes();
    ApiKey key =
        createTestApiKey("delete", uniqueHash, "ACTIVE"); // Changed from deletekey (10 chars max)
    ApiKeys.save(connection, key);

    // When: We delete the API key
    StatusOr<Integer> result = ApiKeys.delete(connection, key.apiKeyId());

    // Then: The operation succeeds and returns 1 affected row
    assertTrue(result.isOk());
    assertEquals(1, result.getValue());

    // And: The API key no longer exists in the database
    StatusOr<Optional<ApiKey>> loadResult = ApiKeys.loadById(connection, key.apiKeyId());
    assertTrue(loadResult.isOk());
    assertFalse(loadResult.getValue().isPresent());
  }

  @Test
  void testDelete_ReturnsZero_WhenApiKeyDoesNotExist() {
    // Given: A non-existent UUID
    UUID nonExistentId = UUID.randomUUID();

    // When: We try to delete an API key with this ID
    StatusOr<Integer> result = ApiKeys.delete(connection, nonExistentId);

    // Then: The operation succeeds but returns 0 affected rows
    assertTrue(result.isOk());
    assertEquals(0, result.getValue());
  }

  // Helper methods to set up test data

  private static UUID createTestUser() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();

    System.out.println("Creating test user with ID: " + userId);

    User user = new User(userId, "testuser", "test@example.com", "Test User", now, now);

    System.out.println("Saving user to database...");
    StatusOr<Integer> result = Users.save(connection, user);

    if (result.isNotOk()) {
      System.err.println("ERROR: Failed to create test user: " + result.getStatus().getMessage());
      throw new RuntimeException("Failed to create test user: " + result.getStatus().getMessage());
    }

    System.out.println("Successfully created test user with ID: " + userId);

    // Verify the user exists
    try {
      var stmt = connection.prepareStatement("SELECT * FROM \"user\" WHERE user_id = ?");
      stmt.setObject(1, userId);
      var rs = stmt.executeQuery();
      if (rs.next()) {
        System.out.println("User found in database: " + userId);
      } else {
        System.err.println("ERROR: User not found in database: " + userId);
      }
      rs.close();
      stmt.close();
    } catch (SQLException e) {
      System.err.println("ERROR checking user: " + e.getMessage());
    }

    return userId;
  }


  private ByteString randomBytes() {
    byte[] bytes = new byte[16];
    new java.security.SecureRandom().nextBytes(bytes);
    return ByteString.copyFrom(bytes);
  }

  private ApiKey createTestApiKey(String prefix, ByteString hash, String status) {
    UUID apiKeyId = UUID.randomUUID();
    Instant now = Instant.now();

    System.out.println("Creating test API key with:");
    System.out.println("  apiKeyId: " + apiKeyId);
    System.out.println("  userId: " + testUserId);
    System.out.println("  createdById: " + testUserId);
    System.out.println("  updatedById: " + testUserId);

    return new ApiKey(
        apiKeyId,
        testUserId,
        prefix,
        hash,
        status,
        Map.of(), // Empty labels
        null, // No expiration
        null, // No last used timestamp
        now,
        now,
        testUserId,
        testUserId);
  }
}
