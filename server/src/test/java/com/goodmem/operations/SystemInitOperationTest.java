package com.goodmem.operations;

import com.goodmem.operations.SystemInitOperation.InitResult;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the SystemInitOperation result class.
 * 
 * This tests the InitResult inner class to ensure it properly handles
 * the different initialization results.
 */
class SystemInitOperationTest {

    @Test
    void testInitResultAlreadyInitialized() {
        // Arrange & Act
        InitResult result = InitResult.alreadyInitialized();
        
        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.isAlreadyInitialized());
        assertNull(result.getApiKey());
        assertNull(result.getUserId());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testInitResultSuccess() {
        // Arrange
        String apiKey = "gm_test123";
        UUID userId = UUID.randomUUID();
        
        // Act
        InitResult result = InitResult.success(apiKey, userId);
        
        // Assert
        assertTrue(result.isSuccess());
        assertFalse(result.isAlreadyInitialized());
        assertEquals(apiKey, result.getApiKey());
        assertEquals(userId, result.getUserId());
        assertNull(result.getErrorMessage());
    }
    
    @Test
    void testInitResultError() {
        // Arrange
        String errorMessage = "Test error message";
        
        // Act
        InitResult result = InitResult.error(errorMessage);
        
        // Assert
        assertFalse(result.isSuccess());
        assertFalse(result.isAlreadyInitialized());
        assertNull(result.getApiKey());
        assertNull(result.getUserId());
        assertEquals(errorMessage, result.getErrorMessage());
    }
}