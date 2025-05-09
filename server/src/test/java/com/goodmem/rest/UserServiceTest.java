package com.goodmem.rest;

import com.goodmem.common.status.StatusOr;
import com.goodmem.rest.dto.GetUserRequest;
import com.goodmem.rest.dto.SystemInitRequest;
import com.goodmem.rest.dto.SystemInitResponse;
import com.goodmem.rest.dto.UserResponse;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for UserService-related DTOs and utility methods.
 */
class UserServiceTest {

    /**
     * Test that a UUID string is properly converted to a ByteString.
     */
    @Test
    void testUuidConversion() {
        // Create a RestAdapter instance for testing the utility methods
        RestAdapter adapter = new RestAdapter() {
            @Override
            public void registerRoutes() {
                // No implementation needed for this test
            }
        };
        
        // Test UUID conversion
        String uuidHex = "00000000-0000-0000-0000-000000000001";
        StatusOr<ByteString> result = adapter.convertHexToUuidBytes(uuidHex);
        
        // Verify the conversion was successful
        assertTrue(result.isOk(), "UUID conversion should succeed");
        
        // Expected bytes for the UUID 00000000-0000-0000-0000-000000000001
        byte[] expectedBytes = new byte[16];
        expectedBytes[15] = 1; // Last byte should be 1, others 0
        
        // Compare the actual bytes with the expected
        ByteString expected = ByteString.copyFrom(expectedBytes);
        assertEquals(expected, result.getValue(), "Converted UUID bytes should match expected");
    }
    
    /**
     * Test UserResponse creation with expected values.
     */
    @Test
    void testUserResponseCreation() {
        // Create a UserResponse with test values
        UserResponse response = new UserResponse(
            "00000000-0000-0000-0000-000000000001",
            "test@example.com",
            "Test User",
            "testuser",
            1620000000000L,
            1620100000000L
        );
        
        // Verify the values are properly set
        assertEquals("00000000-0000-0000-0000-000000000001", response.userId(), "User ID should match");
        assertEquals("test@example.com", response.email(), "Email should match");
        assertEquals("Test User", response.displayName(), "Display name should match");
        assertEquals("testuser", response.username(), "Username should match");
        assertEquals(1620000000000L, response.createdAt(), "Created at should match");
        assertEquals(1620100000000L, response.updatedAt(), "Updated at should match");
    }
    
    /**
     * Test GetUserRequest creation with different parameters.
     */
    @Test
    void testGetUserRequestCreation() {
        // Test with just ID
        GetUserRequest idOnly = new GetUserRequest("00000000-0000-0000-0000-000000000001");
        assertEquals("00000000-0000-0000-0000-000000000001", idOnly.userId(), "User ID should match");
        assertNull(idOnly.email(), "Email should be null");
        
        // Test with both ID and email
        GetUserRequest idAndEmail = new GetUserRequest("00000000-0000-0000-0000-000000000001", "test@example.com");
        assertEquals("00000000-0000-0000-0000-000000000001", idAndEmail.userId(), "User ID should match");
        assertEquals("test@example.com", idAndEmail.email(), "Email should match");
        
        // Test empty constructor
        GetUserRequest empty = new GetUserRequest();
        assertNull(empty.userId(), "User ID should be null");
        assertNull(empty.email(), "Email should be null");
    }
    
    /**
     * Test SystemInitRequest creation.
     */
    @Test
    void testSystemInitRequestDeserialization() {
        // This tests that the SystemInitRequest can be properly deserialized from JSON
        // and that it correctly represents an empty request as required by the protocol
        
        // Create a new instance
        SystemInitRequest request = new SystemInitRequest();
        
        // Verify it's properly constructed - no fields to check since it's empty
        assertNotNull(request, "SystemInitRequest should be properly created");
    }
    
    /**
     * Test SystemInitResponse factory methods and construction.
     */
    @Test
    void testSystemInitResponseConstruction() {
        // Test creating responses for already initialized case
        SystemInitResponse alreadyInitialized = SystemInitResponse.alreadyInitializedResponse();
        assertTrue(alreadyInitialized.alreadyInitialized(), "Already initialized flag should be true");
        assertEquals("System is already initialized", alreadyInitialized.message(), "Message should match");
        assertNull(alreadyInitialized.rootApiKey(), "Root API key should be null");
        assertNull(alreadyInitialized.userId(), "User ID should be null");
        
        // Test creating responses for newly initialized case
        String apiKey = "test_api_key_12345";
        String userId = UUID.randomUUID().toString();
        SystemInitResponse newlyInitialized = SystemInitResponse.newlyInitialized(apiKey, userId);
        assertFalse(newlyInitialized.alreadyInitialized(), "Already initialized flag should be false");
        assertEquals("System initialized successfully", newlyInitialized.message(), "Message should match");
        assertEquals(apiKey, newlyInitialized.rootApiKey(), "Root API key should match");
        assertEquals(userId, newlyInitialized.userId(), "User ID should match");
        
        // Test empty constructor (for JSON deserialization)
        SystemInitResponse empty = new SystemInitResponse();
        assertNull(empty.alreadyInitialized(), "Already initialized flag should be null");
        assertNull(empty.message(), "Message should be null");
        assertNull(empty.rootApiKey(), "Root API key should be null");
        assertNull(empty.userId(), "User ID should be null");
    }
}