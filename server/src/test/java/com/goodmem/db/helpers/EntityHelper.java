package com.goodmem.db.helpers;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.Embedder;
import com.goodmem.db.EmbedderModality;
import com.goodmem.db.EmbedderProviderType;
import com.goodmem.db.Embedders;
import com.goodmem.db.User;
import com.goodmem.db.Users;
import com.google.common.io.BaseEncoding;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

/**
 * Helper class for creating test entities.
 */
public final class EntityHelper {

  private EntityHelper() {
    // Utility class, no instances
  }


  /**
   * Record to hold all user creation information.
   * Contains IDs for the user, API key, and the actual API key value.
   *
   * @param userId The UUID of the created user
   * @param apiKeyId The UUID of the created API key
   * @param apiKeyValue The raw API key value for authentication
   */
  public record TestUserInfo(UUID userId, UUID apiKeyId, String apiKeyValue) {}

  /**
   * Creates a user directly in the database for testing purposes with full role and API key setup.
   * This method is more comprehensive than the basic createTestUser methods and should be used
   * when you need a fully functional user for authentication tests.
   *
   * @param connection The database connection to use
   * @param username The username for the new user (must be unique)
   * @param displayName The display name for the new user
   * @param email The email address for the new user
   * @param roleName The role to assign to the user (must exist in the database)
   * @param createdByUserId The user ID to set as the creator of this user
   * @return A TestUserInfo record containing the user ID, API key ID, and API key value
   * @throws RuntimeException If any created entities cannot be verified
   */
  public static TestUserInfo createTestUserWithKey(
      Connection connection,
      String username,
      String displayName,
      String email,
      String roleName,
      @Nullable UUID createdByUserId) {

    UUID userId = UUID.randomUUID();
    if (createdByUserId == null) {
      createdByUserId = userId;
    }
    String rawApiKeyValue;
    UUID apiKeyId;

    // Insert the user
    String sql = "INSERT INTO \"user\" (user_id, username, display_name, email, " +
                 "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    // Insert a role for the user
    sql = "INSERT INTO user_role (user_id, role_name) VALUES (?, ?)";
    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
      stmt.setObject(1, userId);
      stmt.setString(2, roleName);

      int rowsAffected = stmt.executeUpdate();
      if (rowsAffected != 1) {
        throw new RuntimeException("Failed to insert user role");
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    // Create an API key for the user using the security.ApiKey class
    java.security.SecureRandom secureRandom = new java.security.SecureRandom();
    com.goodmem.security.ApiKey securityApiKey = com.goodmem.security.ApiKey.newKey(secureRandom);
    rawApiKeyValue = securityApiKey.keyString(); // Store this for return

    // Create a db.ApiKey from the security.ApiKey
    apiKeyId = UUID.randomUUID();
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
    com.goodmem.common.status.StatusOr<Integer> saveResult = com.goodmem.db.ApiKeys.save(connection, dbApiKey);

    if (saveResult.isNotOk()) {
      throw new RuntimeException("Failed to insert API key: " + saveResult.getStatus().getMessage());
    }

    if (saveResult.getValue() != 1) {
      throw new RuntimeException("Failed to insert API key: unexpected rows affected: " + saveResult.getValue());
    }

    return new TestUserInfo(userId, apiKeyId, rawApiKeyValue);
  }

  /**
   * Creates a user in the database with good defaults for testing purposes, including API key setup.
   * This is the most flexible convenience method with controlled parameters for role and creator.
   * 
   * <p>This method:
   * <ul>
   *   <li>Creates a user with a default username format (test_user_UUID_suffix)</li>
   *   <li>Assigns the specified role to the created user</li>
   *   <li>Creates and associates an API key for immediate authentication</li>
   *   <li>Sets the specified creator ID</li>
   * </ul>
   *
   * <p>Example usage:
   * <pre>{@code
   * try (Connection conn = dataSource.getConnection()) {
   *   // Create a root user first
   *   TestUserInfo rootInfo = EntityHelper.createTestUserWithKey(conn, "ROOT");
   *   
   *   // Then create users with the root user as creator
   *   TestUserInfo adminInfo = EntityHelper.createTestUserWithKey(
   *       conn, "ADMIN", rootInfo.userId());
   * }
   * }</pre>
   *
   * @param connection The database connection to use
   * @param roleName The role to assign to the user (e.g., "USER", "ADMIN", "ROOT")
   * @param creatorId The UUID of the user who is creating this user
   * @return A TestUserInfo record containing the user ID, API key ID, and API key value for authentication
   * @throws SQLException If any database operation fails
   * @throws RuntimeException If any created entities cannot be verified
   */
  public static TestUserInfo createTestUserWithKey(
      Connection connection, String roleName, UUID creatorId) throws SQLException {
    
    // Generate unique identifiers
    UUID userId = UUID.randomUUID();
    String uniqueSuffix = userId.toString().substring(0, 8);
    
    // Create with default values using a uniquely generated username
    return createTestUserWithKey(
        connection,
        "test_user_" + uniqueSuffix,          // Username with unique suffix
        "Test User " + uniqueSuffix,          // Display name
        "test" + uniqueSuffix + "@example.com", // Email
        roleName,                             // Specified role
        creatorId                             // Specified creator
    );
  }
  
  /**
   * Creates a user in the database with good defaults for testing purposes, including API key setup.
   * This version allows specifying the role but uses the user as their own creator.
   * 
   * <p>This method:
   * <ul>
   *   <li>Creates a user with a default username format (test_user_UUID_suffix)</li>
   *   <li>Assigns the specified role to the created user</li>
   *   <li>Creates and associates an API key for immediate authentication</li>
   *   <li>Sets the user as their own creator (self-referential for simplicity)</li>
   * </ul>
   *
   * <p>Example usage:
   * <pre>{@code
   * try (Connection conn = dataSource.getConnection()) {
   *   TestUserInfo adminInfo = EntityHelper.createTestUserWithKey(conn, "ADMIN");
   *   TestUserInfo regularUser = EntityHelper.createTestUserWithKey(conn, "USER");
   * }
   * }</pre>
   *
   * @param connection The database connection to use
   * @param roleName The role to assign to the user (e.g., "USER", "ADMIN", "ROOT")
   * @return A TestUserInfo record containing the user ID, API key ID, and API key value for authentication
   * @throws RuntimeException If any created entities cannot be verified
   */
  public static TestUserInfo createTestUserWithKey(Connection connection, String roleName) {
    // Generate unique identifiers
    String uniqueSuffix = randomId();
    
    // Create with default values using a uniquely generated username
    return createTestUserWithKey(
        connection,
        "test_user_" + uniqueSuffix,          // Username with unique suffix
        "Test User " + uniqueSuffix,          // Display name
        "test" + uniqueSuffix + "@example.com", // Email
        roleName,                             // Specified role
        null                                  // Self as creator for simplicity
    );
  }

  private static String randomId() {
    byte[] bytes = new byte[4]; // 4 bytes encode to 6 Base64 characters (before padding)
    new Random().nextBytes(bytes);
    return BaseEncoding.base64Url().encode(bytes).substring(0, 6);
  }
  
  /**
   * Creates a user in the database with good defaults for testing purposes, including API key setup.
   * This version creates a standard USER role account. See {@link #createTestUserWithKey(Connection, String)}
   * to specify a different role.
   * 
   * <p>This method:
   * <ul>
   *   <li>Creates a user with a default username format (test_user_UUID_suffix)</li>
   *   <li>Assigns the USER role to the created user</li>
   *   <li>Creates and associates an API key for immediate authentication</li>
   *   <li>Sets the user as their own creator (self-referential for simplicity)</li>
   * </ul>
   *
   * <p>Example usage:
   * <pre>{@code
   * try (Connection conn = dataSource.getConnection()) {
   *   TestUserInfo userInfo = EntityHelper.createTestUserWithKey(conn);
   *   String apiKey = userInfo.apiKeyValue(); // Use for authentication
   *   UUID userId = userInfo.userId();        // Use for referencing the user 
   * }
   * }</pre>
   *
   * @param connection The database connection to use
   * @return A TestUserInfo record containing the user ID, API key ID, and API key value for authentication
   * @throws RuntimeException If any created entities cannot be verified
   */
  public static TestUserInfo createTestUserWithKey(Connection connection) {
    return createTestUserWithKey(connection, "USER");
  }

  private static AtomicInteger embedderCounter = new AtomicInteger(0);

  /**
   * Creates a test embedder entity and inserts it into the database.
   *
   * @param connection a database connection
   * @param embedderId the UUID to use for the embedder
   * @param userId the user ID to set as owner, creator, and updater
   * @return the embedder ID
   */
  public static UUID createTestEmbedder(Connection connection, UUID embedderId, UUID userId) {
    Instant now = Instant.now();
    List<EmbedderModality> modalities = List.of(EmbedderModality.TEXT);
    
    Embedder embedder = new Embedder(
        embedderId,
        "Test Embedder",
        "Test embedder for unit tests",
        EmbedderProviderType.OPENAI,
        "https://api" + embedderCounter.getAndIncrement() + ".test.com",
        "/v1/embeddings",
        "text-embedding-test-model",
        1536, // dimensionality
        null, // maxSequenceLength
        modalities,
        null, // credentials 
        Map.of(), // empty labels
        "1.0", // version
        null, // monitoringEndpoint
        userId, // ownerId
        now, // createdAt
        now, // updatedAt
        userId, // createdById
        userId // updatedById
    );
    
    StatusOr<Integer> result = Embedders.save(connection, embedder);
    if (result.isNotOk()) {
      throw new RuntimeException(result.getStatus().toString());
    } else if (result.getValue() != 1) {
      throw new RuntimeException(
          "Expected one embedder saved, but got: " + result.getValue());
    }
    return embedderId;
  }
}