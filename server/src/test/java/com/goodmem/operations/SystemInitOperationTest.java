package com.goodmem.operations;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.operations.SystemInitOperation.InitResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Test for the SystemInitOperation result class.
 *
 * <p>This tests the InitResult inner class to ensure it properly handles the different
 * initialization results.
 */
class SystemInitOperationTest {

  @Test
  void testInitResultAlreadyInitialized() {
    // Arrange & Act
    InitResult result = InitResult.initialized();

    // Assert
    assertTrue(result.isSuccess());
    assertTrue(result.alreadyInitialized());
    assertNull(result.apiKey());
    assertNull(result.userId());
    assertNull(result.errorMessage());
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
    assertFalse(result.alreadyInitialized());
    assertEquals(apiKey, result.apiKey());
    assertEquals(userId, result.userId());
    assertNull(result.errorMessage());
  }

  @Test
  void testInitResultError() {
    // Arrange
    String errorMessage = "Test error message";

    // Act
    InitResult result = InitResult.error(errorMessage);

    // Assert
    assertFalse(result.isSuccess());
    assertFalse(result.alreadyInitialized());
    assertNull(result.apiKey());
    assertNull(result.userId());
    assertEquals(errorMessage, result.errorMessage());
  }
}
