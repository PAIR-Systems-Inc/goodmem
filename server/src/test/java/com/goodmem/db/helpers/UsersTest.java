package com.goodmem.db.helpers;

import com.goodmem.common.status.StatusOr;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Users helper class.
 * These tests verify all CRUD operations and edge cases.
 */
@Testcontainers
public class UsersTest {

    // Setup paths to the schema files
    private static final String PROJECT_ROOT = "/home/amin/clients/wsl_pairsys/goodmem";
    private static final String EXTENSIONS_SQL_PATH = PROJECT_ROOT + "/database/initdb/00-extensions.sql";
    private static final String SCHEMA_SQL_PATH = PROJECT_ROOT + "/database/initdb/01-schema.sql";
    
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("goodmem_users_test")
            .withUsername("goodmem")
            .withPassword("goodmem")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(EXTENSIONS_SQL_PATH),
                    "/docker-entrypoint-initdb.d/00-extensions.sql")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(SCHEMA_SQL_PATH),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    private static Connection connection;
    
    @BeforeAll
    static void setUp() throws SQLException {
        postgres.start();
        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
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
            stmt.execute("DELETE FROM \"user\"");
        }
    }

    @Test
    void testLoadAll_ReturnsEmptyList_WhenNoUsers() {
        // When: We load all users from an empty database
        StatusOr<List<User>> result = Users.loadAll(connection);
        
        // Then: The operation succeeds but returns an empty list
        assertTrue(result.isOk());
        assertEquals(0, result.getValue().size());
    }

    @Test
    void testLoadAll_ReturnsAllUsers_WhenMultipleExist() {
        // Given: Multiple users in the database
        User user1 = createTestUser("user1", "user1@example.com");
        User user2 = createTestUser("user2", "user2@example.com");
        
        Users.save(connection, user1);
        Users.save(connection, user2);
        
        // When: We load all users
        StatusOr<List<User>> result = Users.loadAll(connection);
        
        // Then: All users are returned
        assertTrue(result.isOk());
        assertEquals(2, result.getValue().size());
        
        // And: The users match what we expect
        List<String> emails = result.getValue().stream()
                .map(User::email)
                .toList();
        assertTrue(emails.contains("user1@example.com"));
        assertTrue(emails.contains("user2@example.com"));
    }
    
    @Test
    void testLoadById_ReturnsUser_WhenExists() {
        // Given: A user in the database
        User user = createTestUser("testuser", "test@example.com");
        Users.save(connection, user);
        
        // When: We load the user by ID
        StatusOr<Optional<User>> result = Users.loadById(connection, user.userId());
        
        // Then: The user is returned
        assertTrue(result.isOk());
        assertTrue(result.getValue().isPresent());
        assertEquals("testuser", result.getValue().get().username());
        assertEquals("test@example.com", result.getValue().get().email());
    }
    
    @Test
    void testLoadById_ReturnsEmpty_WhenUserDoesNotExist() {
        // Given: A non-existent UUID
        UUID nonExistentId = UUID.randomUUID();
        
        // When: We try to load a user with this ID
        StatusOr<Optional<User>> result = Users.loadById(connection, nonExistentId);
        
        // Then: The operation succeeds but returns an empty Optional
        assertTrue(result.isOk());
        assertFalse(result.getValue().isPresent());
    }
    
    @Test
    void testLoadByEmail_ReturnsUser_WhenExists() {
        // Given: A user in the database
        User user = createTestUser("emailuser", "lookup@example.com");
        Users.save(connection, user);
        
        // When: We load the user by email
        StatusOr<Optional<User>> result = Users.loadByEmail(connection, "lookup@example.com");
        
        // Then: The user is returned
        assertTrue(result.isOk());
        assertTrue(result.getValue().isPresent());
        assertEquals("emailuser", result.getValue().get().username());
        assertEquals(user.userId(), result.getValue().get().userId());
    }
    
    @Test
    void testLoadByEmail_ReturnsEmpty_WhenEmailDoesNotExist() {
        // When: We try to load a user with a non-existent email
        StatusOr<Optional<User>> result = Users.loadByEmail(connection, "nonexistent@example.com");
        
        // Then: The operation succeeds but returns an empty Optional
        assertTrue(result.isOk());
        assertFalse(result.getValue().isPresent());
    }
    
    @Test
    void testSave_CreatesNewUser_WhenIdDoesNotExist() {
        // Given: A new user
        User user = createTestUser("newuser", "new@example.com");
        
        // When: We save the user
        StatusOr<Integer> result = Users.save(connection, user);
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The user can be retrieved from the database
        StatusOr<Optional<User>> loadResult = Users.loadById(connection, user.userId());
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
    }
    
    @Test
    void testSave_UpdatesExistingUser_WhenIdExists() {
        // Given: An existing user
        User user = createTestUser("updateuser", "update@example.com");
        Users.save(connection, user);
        
        // When: We update the user
        User updatedUser = new User(
                user.userId(),
                "updateuser-new",
                "update@example.com",
                "Updated Display Name",
                user.createdAt(),
                user.updatedAt());
        StatusOr<Integer> result = Users.save(connection, updatedUser);
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The user is updated in the database
        StatusOr<Optional<User>> loadResult = Users.loadById(connection, user.userId());
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        assertEquals("updateuser-new", loadResult.getValue().get().username());
        assertEquals("Updated Display Name", loadResult.getValue().get().displayName());
    }
    
    @Test
    void testDelete_RemovesUser_WhenExists() {
        // Given: An existing user
        User user = createTestUser("deleteuser", "delete@example.com");
        Users.save(connection, user);
        
        // When: We delete the user
        StatusOr<Integer> result = Users.delete(connection, user.userId());
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The user no longer exists in the database
        StatusOr<Optional<User>> loadResult = Users.loadById(connection, user.userId());
        assertTrue(loadResult.isOk());
        assertFalse(loadResult.getValue().isPresent());
    }
    
    @Test
    void testDelete_ReturnsZero_WhenUserDoesNotExist() {
        // Given: A non-existent UUID
        UUID nonExistentId = UUID.randomUUID();
        
        // When: We try to delete a user with this ID
        StatusOr<Integer> result = Users.delete(connection, nonExistentId);
        
        // Then: The operation succeeds but returns 0 affected rows
        assertTrue(result.isOk());
        assertEquals(0, result.getValue());
    }
    
    @Test
    void testSave_HandlesNullOptionalFields() {
        // Given: A user with null optional fields
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        User user = new User(
                userId,
                null,  // null username
                "nullfields@example.com",
                null,  // null displayName
                now,
                now
        );
        
        // When: We save the user
        StatusOr<Integer> result = Users.save(connection, user);
        
        // Then: The operation succeeds
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The user can be retrieved with null fields intact
        StatusOr<Optional<User>> loadResult = Users.loadById(connection, userId);
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        
        User loadedUser = loadResult.getValue().get();
        assertNull(loadedUser.username());
        assertNull(loadedUser.displayName());
        assertEquals("nullfields@example.com", loadedUser.email());
    }
    
    // Helper method to create a test user
    private User createTestUser(String username, String email) {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        return new User(
                userId,
                username,
                email,
                "Display Name for " + username,
                now,
                now
        );
    }
}