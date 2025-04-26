package com.goodmem.db.helpers;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.*;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the MemoryChunks helper class.
 * These tests focus on vector operations and interactions with pgvector.
 */
@Testcontainers
public class MemoryChunksTest {

    // Setup paths to the schema files
    private static final String PROJECT_ROOT = "/home/amin/clients/wsl_pairsys/goodmem";
    private static final String EXTENSIONS_SQL_PATH = PROJECT_ROOT + "/database/initdb/00-extensions.sql";
    private static final String SCHEMA_SQL_PATH = PROJECT_ROOT + "/database/initdb/01-schema.sql";
    
    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
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
    private static UUID testSpaceId;
    private static UUID testMemoryId;
    
    @BeforeAll
    static void setUp() throws SQLException {
        postgres.start();
        connection = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                
        // Setup test user, space, and memory that will be reused across tests
        testUserId = createTestUser();
        testSpaceId = createTestSpace(testUserId);
        testMemoryId = createTestMemory(testSpaceId, testUserId);
    }
    
    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
        postgres.stop();
    }
    
    @BeforeEach
    void clearChunks() throws SQLException {
        try (var stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM memory_chunk");
        }
    }

    @Test
    void testSaveAndLoadById() {
        // Given: A memory chunk
        MemoryChunk chunk = createTestChunk(testMemoryId, testUserId, 1, "Test chunk text", null);
        
        // When: We save the chunk
        StatusOr<Integer> saveResult = MemoryChunks.save(connection, chunk);
        
        // Then: The save is successful
        assertTrue(saveResult.isOk());
        assertEquals(1, saveResult.getValue());
        
        // When: We load the chunk by ID
        StatusOr<Optional<MemoryChunk>> loadResult = MemoryChunks.loadById(connection, chunk.chunkId());
        
        // Then: We get the chunk back with the correct data
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        
        MemoryChunk loadedChunk = loadResult.getValue().get();
        assertEquals(chunk.chunkId(), loadedChunk.chunkId());
        assertEquals(chunk.memoryId(), loadedChunk.memoryId());
        assertEquals(chunk.chunkSequenceNumber(), loadedChunk.chunkSequenceNumber());
        assertEquals(chunk.chunkText(), loadedChunk.chunkText());
        assertEquals(chunk.vectorStatus(), loadedChunk.vectorStatus());
    }
    
    @Test
    void testSaveWithEmbeddingVector() {
        // Given: A memory chunk with an embedding vector
        float[] vector = {0.1f, 0.2f, 0.3f};
        MemoryChunk chunk = createTestChunk(testMemoryId, testUserId, 1, "Vector chunk", vector);
        chunk = chunk.withVectorStatus("GENERATED");  // Use the convenience method to update status
        
        // When: We save the chunk
        StatusOr<Integer> saveResult = MemoryChunks.save(connection, chunk);
        
        // Then: The save is successful
        assertTrue(saveResult.isOk());
        assertEquals(1, saveResult.getValue());
        
        // When: We load the chunk by ID
        StatusOr<Optional<MemoryChunk>> loadResult = MemoryChunks.loadById(connection, chunk.chunkId());
        
        // Then: We get the chunk back with the correct vector
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        
        MemoryChunk loadedChunk = loadResult.getValue().get();
        assertNotNull(loadedChunk.embeddingVector());
        assertEquals(vector.length, loadedChunk.embeddingVector().length);
        assertArrayEquals(vector, loadedChunk.embeddingVector(), 0.0001f);
        assertEquals("GENERATED", loadedChunk.vectorStatus());
    }

    @Test
    void testLoadByMemoryId() {
        // Given: Multiple chunks for the same memory
        MemoryChunk chunk1 = createTestChunk(testMemoryId, testUserId, 1, "First chunk", null);
        MemoryChunk chunk2 = createTestChunk(testMemoryId, testUserId, 2, "Second chunk", null);
        MemoryChunk chunk3 = createTestChunk(testMemoryId, testUserId, 3, "Third chunk", null);
        
        MemoryChunks.save(connection, chunk1);
        MemoryChunks.save(connection, chunk2);
        MemoryChunks.save(connection, chunk3);
        
        // When: We load chunks by memory ID
        StatusOr<List<MemoryChunk>> result = MemoryChunks.loadByMemoryId(connection, testMemoryId);
        
        // Then: All chunks for that memory are returned in sequence order
        assertTrue(result.isOk());
        assertEquals(3, result.getValue().size());
        
        // Check that chunks are returned in sequence order
        List<Integer> sequenceNumbers = result.getValue().stream()
                .map(MemoryChunk::chunkSequenceNumber)
                .toList();
        assertEquals(List.of(1, 2, 3), sequenceNumbers);
    }
    
    @Test
    void testLoadByVectorStatus() {
        // Given: Chunks with different vector statuses
        MemoryChunk pendingChunk = createTestChunk(testMemoryId, testUserId, 1, "Pending chunk", null);
        pendingChunk = pendingChunk.withVectorStatus("PENDING");
        
        MemoryChunk generatedChunk = createTestChunk(testMemoryId, testUserId, 2, "Generated chunk", new float[]{0.1f, 0.2f, 0.3f});
        generatedChunk = generatedChunk.withVectorStatus("GENERATED");
        
        MemoryChunk failedChunk = createTestChunk(testMemoryId, testUserId, 3, "Failed chunk", null);
        failedChunk = failedChunk.withVectorStatus("FAILED");
        
        MemoryChunks.save(connection, pendingChunk);
        MemoryChunks.save(connection, generatedChunk);
        MemoryChunks.save(connection, failedChunk);
        
        // When: We load chunks by vector status
        StatusOr<List<MemoryChunk>> pendingResult = MemoryChunks.loadByVectorStatus(connection, "PENDING", 10);
        StatusOr<List<MemoryChunk>> generatedResult = MemoryChunks.loadByVectorStatus(connection, "GENERATED", 10);
        StatusOr<List<MemoryChunk>> failedResult = MemoryChunks.loadByVectorStatus(connection, "FAILED", 10);
        
        // Then: Only chunks with the matching status are returned
        assertTrue(pendingResult.isOk());
        assertEquals(1, pendingResult.getValue().size());
        assertEquals("Pending chunk", pendingResult.getValue().get(0).chunkText());
        
        assertTrue(generatedResult.isOk());
        assertEquals(1, generatedResult.getValue().size());
        assertEquals("Generated chunk", generatedResult.getValue().get(0).chunkText());
        
        assertTrue(failedResult.isOk());
        assertEquals(1, failedResult.getValue().size());
        assertEquals("Failed chunk", failedResult.getValue().get(0).chunkText());
    }
    
    @Test
    void testUpdateVectorStatus() {
        // Given: A chunk with PENDING status
        MemoryChunk chunk = createTestChunk(testMemoryId, testUserId, 1, "Status update chunk", null);
        chunk = chunk.withVectorStatus("PENDING");
        MemoryChunks.save(connection, chunk);
        
        // When: We update the vector status
        StatusOr<Integer> updateResult = MemoryChunks.updateVectorStatus(
                connection, chunk.chunkId(), "GENERATED", testUserId);
        
        // Then: The update is successful
        assertTrue(updateResult.isOk());
        assertEquals(1, updateResult.getValue());
        
        // And: The chunk status is updated in the database
        StatusOr<Optional<MemoryChunk>> loadResult = MemoryChunks.loadById(connection, chunk.chunkId());
        assertTrue(loadResult.isOk());
        assertTrue(loadResult.getValue().isPresent());
        assertEquals("GENERATED", loadResult.getValue().get().vectorStatus());
    }
    
    @Test
    void testVectorSearch() {
        // Given: Multiple chunks with embedding vectors at varying distances
        float[] queryVector = {1.0f, 1.0f, 1.0f};
        
        // Closest to query vector
        float[] vector1 = {1.1f, 0.9f, 1.0f};  // Very close to query
        MemoryChunk chunk1 = createTestChunk(testMemoryId, testUserId, 1, "Closest chunk", vector1);
        chunk1 = chunk1.withVectorStatus("GENERATED");
        
        // Second closest
        float[] vector2 = {0.7f, 0.7f, 0.7f};  // Moderately close
        MemoryChunk chunk2 = createTestChunk(testMemoryId, testUserId, 2, "Medium chunk", vector2);
        chunk2 = chunk2.withVectorStatus("GENERATED");
        
        // Furthest
        float[] vector3 = {-1.0f, -1.0f, -1.0f};  // Very far
        MemoryChunk chunk3 = createTestChunk(testMemoryId, testUserId, 3, "Furthest chunk", vector3);
        chunk3 = chunk3.withVectorStatus("GENERATED");
        
        MemoryChunks.save(connection, chunk1);
        MemoryChunks.save(connection, chunk2);
        MemoryChunks.save(connection, chunk3);
        
        // When: We perform a vector search
        StatusOr<List<MemoryChunk>> searchResult = MemoryChunks.vectorSearch(
                connection, queryVector, testSpaceId, 3);
        
        // Then: Results are returned in order of similarity
        assertTrue(searchResult.isOk());
        assertEquals(3, searchResult.getValue().size());
        
        // Check order: chunk1 (closest) should be first, chunk3 (furthest) should be last
        List<String> chunkTexts = searchResult.getValue().stream()
                .map(MemoryChunk::chunkText)
                .toList();
        
        assertEquals("Closest chunk", chunkTexts.get(0));
        assertEquals("Medium chunk", chunkTexts.get(1));
        assertEquals("Furthest chunk", chunkTexts.get(2));
    }
    
    @Test
    void testDeleteByMemoryId() {
        // Given: Multiple chunks for the same memory
        MemoryChunk chunk1 = createTestChunk(testMemoryId, testUserId, 1, "Delete test 1", null);
        MemoryChunk chunk2 = createTestChunk(testMemoryId, testUserId, 2, "Delete test 2", null);
        
        MemoryChunks.save(connection, chunk1);
        MemoryChunks.save(connection, chunk2);
        
        // When: We delete by memory ID
        StatusOr<Integer> deleteResult = MemoryChunks.deleteByMemoryId(connection, testMemoryId);
        
        // Then: All chunks for that memory are deleted
        assertTrue(deleteResult.isOk());
        assertEquals(2, deleteResult.getValue());
        
        // And: No chunks exist for that memory
        StatusOr<List<MemoryChunk>> loadResult = MemoryChunks.loadByMemoryId(connection, testMemoryId);
        assertTrue(loadResult.isOk());
        assertEquals(0, loadResult.getValue().size());
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
    
    private static UUID createTestMemory(UUID spaceId, UUID userId) {
        UUID memoryId = UUID.randomUUID();
        Instant now = Instant.now();
        Memory memory = new Memory(
                memoryId,
                spaceId,
                "test-content-ref",
                "text/plain",
                Map.of(),
                "COMPLETED",
                now,
                now,
                userId,
                userId
        );
        Memories.save(connection, memory);
        return memoryId;
    }
    
    private static MemoryChunk createTestChunk(UUID memoryId, UUID userId, int sequenceNumber, String text, float[] vector) {
        UUID chunkId = UUID.randomUUID();
        Instant now = Instant.now();
        return new MemoryChunk(
                chunkId,
                memoryId,
                sequenceNumber,
                text,
                vector,
                "PENDING",  // Default status, can be changed after creation
                0,  // startOffset
                text.length(),  // endOffset
                now,
                now,
                userId,
                userId
        );
    }
}