package com.goodmem.db.helpers;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.Memory;
import com.goodmem.db.Memories;
import com.goodmem.db.Space;
import com.goodmem.db.Spaces;
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
 * Tests for the Memories helper class.
 * These tests verify all CRUD operations and edge cases for memories.
 */
@Testcontainers
public class MemoriesTest {

    // Setup paths to the schema files
    private static final String PROJECT_ROOT = "/home/amin/clients/wsl_pairsys/goodmem";
    private static final String EXTENSIONS_SQL_PATH = PROJECT_ROOT + "/database/initdb/00-extensions.sql";
    private static final String SCHEMA_SQL_PATH = PROJECT_ROOT + "/database/initdb/01-schema.sql";
    
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("goodmem_memories_test")
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
    private static UUID testSpaceId;
    
    @BeforeAll
    static void setUp() throws SQLException {
        postgres.start();
        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                
        // Setup test user and space that will be reused across tests
        testUserId = createTestUser();
        testSpaceId = createTestSpace(testUserId);
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
            stmt.execute("DELETE FROM memory");
        }
    }

    @Test
    void testLoadAll_ReturnsEmptyList_WhenNoMemories() {
        // When: We load all memories from an empty database
        StatusOr<List<Memory>> result = Memories.loadAll(connection);
        
        // Then: The operation succeeds but returns an empty list
        assertTrue(result.isOk());
        assertEquals(0, result.getValue().size());
    }

    @Test
    void testLoadAll_ReturnsAllMemories_WhenMultipleExist() {
        // Given: Multiple memories in the database
        Memory memory1 = createTestMemory("ref1", "text/plain", "PENDING");
        Memory memory2 = createTestMemory("ref2", "text/html", "COMPLETED");
        
        Memories.save(connection, memory1);
        Memories.save(connection, memory2);
        
        // When: We load all memories
        StatusOr<List<Memory>> result = Memories.loadAll(connection);
        
        // Then: All memories are returned
        assertTrue(result.isOk());
        assertEquals(2, result.getValue().size());
        
        // And: The memories match what we expect
        List<String> refs = result.getValue().stream()
                .map(Memory::originalContentRef)
                .toList();
        assertTrue(refs.contains("ref1"));
        assertTrue(refs.contains("ref2"));
    }
    
    @Test
    void testLoadById_ReturnsMemory_WhenExists() {
        // Given: A memory in the database
        Memory memory = createTestMemory("testref", "text/plain", "PENDING");
        Memories.save(connection, memory);
        
        // When: We load the memory by ID
        StatusOr<Optional<Memory>> result = Memories.loadById(connection, memory.memoryId());
        
        // Then: The memory is returned
        assertTrue(result.isOk());
        assertTrue(result.getValue().isPresent());
        assertEquals("testref", result.getValue().get().originalContentRef());
        assertEquals("text/plain", result.getValue().get().contentType());
        assertEquals("PENDING", result.getValue().get().processingStatus());
    }
    
    @Test
    void testLoadById_ReturnsEmpty_WhenMemoryDoesNotExist() {
        // Given: A non-existent UUID
        UUID nonExistentId = UUID.randomUUID();
        
        // When: We try to load a memory with this ID
        StatusOr<Optional<Memory>> result = Memories.loadById(connection, nonExistentId);
        
        // Then: The operation succeeds but returns an empty Optional
        assertTrue(result.isOk());
        assertFalse(result.getValue().isPresent());
    }
    
    @Test
    void testLoadBySpaceId_ReturnsMemories_WhenExistForSpace() {
        // Given: Multiple memories for the same space
        Memory memory1 = createTestMemory("spaceref1", "text/plain", "PENDING");
        Memory memory2 = createTestMemory("spaceref2", "text/html", "COMPLETED");
        
        Memories.save(connection, memory1);
        Memories.save(connection, memory2);
        
        // When: We load memories by space ID
        StatusOr<List<Memory>> result = Memories.loadBySpaceId(connection, testSpaceId);
        
        // Then: All memories for that space are returned
        assertTrue(result.isOk());
        assertEquals(2, result.getValue().size());
        
        // And: The memories match what we expect
        List<String> refs = result.getValue().stream()
                .map(Memory::originalContentRef)
                .toList();
        assertTrue(refs.contains("spaceref1"));
        assertTrue(refs.contains("spaceref2"));
    }
    
    @Test
    void testLoadByProcessingStatus_ReturnsMemories_WhenMatch() {
        // Given: Memories with different processing statuses
        Memory pendingMemory = createTestMemory("pending", "text/plain", "PENDING");
        Memory processingMemory = createTestMemory("processing", "text/plain", "PROCESSING");
        Memory completedMemory = createTestMemory("completed", "text/plain", "COMPLETED");
        
        Memories.save(connection, pendingMemory);
        Memories.save(connection, processingMemory);
        Memories.save(connection, completedMemory);
        
        // When: We load memories by processing status
        StatusOr<List<Memory>> pendingResult = Memories.loadByProcessingStatus(connection, "PENDING");
        StatusOr<List<Memory>> completedResult = Memories.loadByProcessingStatus(connection, "COMPLETED");
        
        // Then: Only memories with the matching status are returned
        assertTrue(pendingResult.isOk());
        assertEquals(1, pendingResult.getValue().size());
        assertEquals("pending", pendingResult.getValue().get(0).originalContentRef());
        
        assertTrue(completedResult.isOk());
        assertEquals(1, completedResult.getValue().size());
        assertEquals("completed", completedResult.getValue().get(0).originalContentRef());
    }
    
    @Test
    void testSave_CreatesNewMemory_WhenIdDoesNotExist() {
        // Given: A new memory
        Memory memory = createTestMemory("newmemory", "text/plain", "PENDING");
        
        // When: We save the memory
        StatusOr<Integer> result = Memories.save(connection, memory);
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The memory can be retrieved from the database
        StatusOr<Optional<Memory>> loadResult = Memories.loadById(connection, memory.memoryId());
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
    }
    
    @Test
    void testSave_UpdatesExistingMemory_WhenIdExists() {
        // Given: An existing memory
        Memory memory = createTestMemory("updatememory", "text/plain", "PENDING");
        Memories.save(connection, memory);
        
        // When: We update the memory
        Instant now = Instant.now();
        Memory updatedMemory = new Memory(
                memory.memoryId(),
                memory.spaceId(),
                memory.originalContentRef(),
                memory.contentType(),
                memory.metadata(),
                "COMPLETED",  // Changed processing status
                memory.createdAt(),
                now,          // Updated updatedAt
                memory.createdById(),
                memory.updatedById()
        );
        StatusOr<Integer> result = Memories.save(connection, updatedMemory);
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The memory is updated in the database
        StatusOr<Optional<Memory>> loadResult = Memories.loadById(connection, memory.memoryId());
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        assertEquals("COMPLETED", loadResult.getValue().get().processingStatus());
    }
    
    @Test
    void testUpdateProcessingStatus_UpdatesStatus_WhenMemoryExists() {
        // Given: An existing memory
        Memory memory = createTestMemory("statusmemory", "text/plain", "PENDING");
        Memories.save(connection, memory);
        
        // When: We update the processing status
        StatusOr<Integer> result = Memories.updateProcessingStatus(
                connection, memory.memoryId(), "PROCESSING", testUserId);
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The memory's processing status is updated in the database
        StatusOr<Optional<Memory>> loadResult = Memories.loadById(connection, memory.memoryId());
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        assertEquals("PROCESSING", loadResult.getValue().get().processingStatus());
    }
    
    @Test
    void testDelete_RemovesMemory_WhenExists() {
        // Given: An existing memory
        Memory memory = createTestMemory("deletememory", "text/plain", "PENDING");
        Memories.save(connection, memory);
        
        // When: We delete the memory
        StatusOr<Integer> result = Memories.delete(connection, memory.memoryId());
        
        // Then: The operation succeeds and returns 1 affected row
        assertTrue(result.isOk());
        assertEquals(1, result.getValue());
        
        // And: The memory no longer exists in the database
        StatusOr<Optional<Memory>> loadResult = Memories.loadById(connection, memory.memoryId());
        assertTrue(loadResult.isOk());
        assertFalse(loadResult.getValue().isPresent());
    }
    
    @Test
    void testDelete_ReturnsZero_WhenMemoryDoesNotExist() {
        // Given: A non-existent UUID
        UUID nonExistentId = UUID.randomUUID();
        
        // When: We try to delete a memory with this ID
        StatusOr<Integer> result = Memories.delete(connection, nonExistentId);
        
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
    
    private static UUID createTestSpace(UUID ownerId) {
        UUID spaceId = UUID.randomUUID();
        Instant now = Instant.now();
        Space space = new Space(
                spaceId,
                ownerId,
                "test-space",
                Map.of(),
                "ada-002",
                false,
                now,
                now,
                ownerId,
                ownerId
        );
        Spaces.save(connection, space);
        return spaceId;
    }
    
    private Memory createTestMemory(String contentRef, String contentType, String status) {
        UUID memoryId = UUID.randomUUID();
        Instant now = Instant.now();
        return new Memory(
                memoryId,
                testSpaceId,
                contentRef,
                contentType,
                Map.of(),  // Empty metadata
                status,
                now,
                now,
                testUserId,
                testUserId
        );
    }
}