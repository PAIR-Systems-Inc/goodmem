package com.goodmem.db.helpers;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.ApiKey;
import com.goodmem.db.ApiKeys;
import com.goodmem.db.User;
import com.goodmem.db.Users;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ApiKeys helper class.
 * These tests verify all CRUD operations and edge cases for API keys.
 */
@Testcontainers
public class ApiKeysTest {

    // Setup paths to the schema files
    private static final String PROJECT_ROOT = "/home/amin/clients/wsl_pairsys/goodmem";
    private static final String EXTENSIONS_SQL_PATH = PROJECT_ROOT + "/database/initdb/00-extensions.sql";
    private static final String SCHEMA_SQL_PATH = PROJECT_ROOT + "/database/initdb/01-schema.sql";
    
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"))
            .withDatabaseName("goodmem")
            .withUsername("goodmem")
            .withPassword("goodmem")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(EXTENSIONS_SQL_PATH),
                    "/docker-entrypoint-initdb.d/00-extensions.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(SCHEMA_SQL_PATH),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    private static Connection connection;
    private static UUID testUserId;
    
    @BeforeAll
    static void setUp() throws SQLException {
        postgres.start();
        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                
        // Create a test user that will be reused across tests
        testUserId = createTestUser();
    }
    
    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        postgres.stop();
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
        ApiKey key1 = createTestApiKey("prf1", "hash1", "ACTIVE");
        ApiKey key2 = createTestApiKey("prf2", "hash2", "INACTIVE");
        
        ApiKeys.save(connection, key1);
        ApiKeys.save(connection, key2);
        
        // When: We load all API keys
        StatusOr<List<ApiKey>> result = ApiKeys.loadAll(connection);
        
        // Then: All API keys are returned
        assertTrue(result.isOk());
        assertEquals(2, result.getValue().size());
        
        // And: The API keys match what we expect
        List<String> prefixes = result.getValue().stream()
                .map(ApiKey::keyPrefix)
                .toList();
        assertTrue(prefixes.contains("prf1"));
        assertTrue(prefixes.contains("prf2"));
    }
    
    @Test
    void testLoadById_ReturnsApiKey_WhenExists() {
        // Given: An API key in the database
        ApiKey key = createTestApiKey("testkey", "testhash", "ACTIVE");
        ApiKeys.save(connection, key);
        
        // When: We load the API key by ID
        StatusOr<Optional<ApiKey>> result = ApiKeys.loadById(connection, key.apiKeyId());
        
        // Then: The API key is returned
        assertTrue(result.isOk());
        assertTrue(result.getValue().isPresent());
        assertEquals("testkey", result.getValue().get().keyPrefix());
        assertEquals("testhash", result.getValue().get().keyHash());
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
        ApiKey key1 = createTestApiKey("usrkey1", "usrhash1", "ACTIVE");
        ApiKey key2 = createTestApiKey("usrkey2", "usrhash2", "INACTIVE");
        
        ApiKeys.save(connection, key1);
        ApiKeys.save(connection, key2);
        
        // When: We load API keys by user ID
        StatusOr<List<ApiKey>> result = ApiKeys.loadByUserId(connection, testUserId);
        
        // Then: All API keys for that user are returned
        assertTrue(result.isOk());
        assertEquals(2, result.getValue().size());
        
        // And: The API keys match what we expect
        List<String> prefixes = result.getValue().stream()
                .map(ApiKey::keyPrefix)
                .toList();
        assertTrue(prefixes.contains("usrkey1"));
        assertTrue(prefixes.contains("usrkey2"));
    }
    
    @Test
    void testLoadByKeyHash_ReturnsApiKey_WhenExists() {
        // Given: An API key with a specific hash
        ApiKey key = createTestApiKey("hashkey", "uniquehash", "ACTIVE");
        ApiKeys.save(connection, key);
        
        // When: We load the API key by hash
        StatusOr<Optional<ApiKey>> result = ApiKeys.loadByKeyHash(connection, "uniquehash");
        
        // Then: The API key is returned
        assertTrue(result.isOk());
        assertTrue(result.getValue().isPresent());
        assertEquals("hashkey", result.getValue().get().keyPrefix());
        assertEquals(key.apiKeyId(), result.getValue().get().apiKeyId());
    }
    
    @Test
    void testLoadByKeyHash_ReturnsEmpty_WhenHashDoesNotExist() {
        // When: We try to load an API key with a non-existent hash
        StatusOr<Optional<ApiKey>> result = ApiKeys.loadByKeyHash(connection, "nonexistenthash");
        
        // Then: The operation succeeds but returns an empty Optional
        assertTrue(result.isOk());
        assertFalse(result.getValue().isPresent());
    }
    
    @Test
    void testSave_CreatesNewApiKey_WhenIdDoesNotExist() {
        // Given: A new API key
        ApiKey key = createTestApiKey("newkey", "newhash", "ACTIVE");
        
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
        ApiKey key = createTestApiKey("updatekey", "updatehash", "ACTIVE");
        ApiKeys.save(connection, key);
        
        // When: We update the API key
        Instant now = Instant.now();
        ApiKey updatedKey = new ApiKey(
                key.apiKeyId(),
                key.userId(),
                key.keyPrefix(),
                key.keyHash(),
                "INACTIVE",  // Changed status
                key.labels(),
                key.expiresAt(),
                now,        // Updated lastUsedAt
                key.createdAt(),
                now,        // Updated updatedAt
                key.createdById(),
                key.updatedById()
        );
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
    void testUpdateLastUsed_UpdatesTimestamp_WhenApiKeyExists() {
        // Given: An existing API key
        ApiKey key = createTestApiKey("lastusedkey", "lastused", "ACTIVE");
        ApiKeys.save(connection, key);
        
        // When: We update the last used timestamp
        Instant lastUsed = Instant.now().plusSeconds(3600);
        StatusOr<Integer> result = ApiKeys.updateLastUsed(connection, key.apiKeyId(), lastUsed);
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The API key's last used timestamp is updated in the database
        StatusOr<Optional<ApiKey>> loadResult = ApiKeys.loadById(connection, key.apiKeyId());
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        assertNotNull(loadResult.getValue().get().lastUsedAt());
        // Cannot compare exact timestamp due to precision differences
    }
    
    @Test
    void testUpdateStatus_UpdatesStatus_WhenApiKeyExists() {
        // Given: An existing API key
        ApiKey key = createTestApiKey("statuskey", "statushash", "ACTIVE");
        ApiKeys.save(connection, key);
        
        // When: We update the status
        StatusOr<Integer> result = ApiKeys.updateStatus(
                connection, key.apiKeyId(), "REVOKED", testUserId);
        
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
        ApiKey key = createTestApiKey("deletekey", "deletehash", "ACTIVE");
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
        User user = new User(
                userId,
                "testuser",
                "test@example.com",
                "Test User",
                now,
                now
        );
        Users.save(connection, user);
        return userId;
    }
    
    private ApiKey createTestApiKey(String prefix, String hash, String status) {
        UUID apiKeyId = UUID.randomUUID();
        Instant now = Instant.now();
        return new ApiKey(
                apiKeyId,
                testUserId,
                prefix,
                hash,
                status,
                Map.of(),  // Empty labels
                null,      // No expiration
                null,      // No last used timestamp
                now,
                now,
                testUserId,
                testUserId
        );
    }
}