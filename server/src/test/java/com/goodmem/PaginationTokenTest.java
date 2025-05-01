package com.goodmem;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.UuidUtil;
import com.goodmem.security.DefaultUserImpl;
import com.goodmem.security.Role;
import com.goodmem.security.Roles;
import com.goodmem.security.User;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.InvalidProtocolBufferException;
import goodmem.v1.Common.SortOrder;
import goodmem.v1.SpaceOuterClass.ListSpacesNextPageToken;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pagination token encoding/decoding methods in SpaceServiceImpl.
 * Uses reflection to access the private methods directly.
 */
public class PaginationTokenTest {

  private SpaceServiceImpl spaceService;
  private Method encodeMethod;
  private Method decodeAndValidateMethod;
  private Method createNextPageTokenMethod;
  private User authenticatedUser;
  private UUID userId;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    // Create SpaceServiceImpl with null config (only token methods will be tested)
    spaceService = new SpaceServiceImpl(null);
    
    // Use reflection to access private methods
    encodeMethod = SpaceServiceImpl.class.getDeclaredMethod("encodeNextPageToken", ListSpacesNextPageToken.class);
    encodeMethod.setAccessible(true);
    
    decodeAndValidateMethod = SpaceServiceImpl.class.getDeclaredMethod(
        "decodeAndValidateNextPageToken", String.class, User.class);
    decodeAndValidateMethod.setAccessible(true);
    
    createNextPageTokenMethod = SpaceServiceImpl.class.getDeclaredMethod(
        "createNextPageToken", byte[].class, Map.class, String.class, 
        UUID.class, int.class, String.class, SortOrder.class);
    createNextPageTokenMethod.setAccessible(true);
    
    // Create a test user for authentication validation
    userId = UUID.randomUUID();
    com.goodmem.db.User dbUser = new com.goodmem.db.User(
        userId,
        "testuser",
        "test@example.com",
        "Test User",
        Instant.now(),
        Instant.now()
    );
    
    Role role = Roles.USER.role();
    authenticatedUser = new DefaultUserImpl(dbUser, role);
  }

  @Test
  void testCreateAndEncodeToken() throws Exception {
    // Test parameters
    UUID ownerId = UUID.randomUUID();
    Map<String, String> labelSelectors = new HashMap<>();
    labelSelectors.put("env", "test");
    labelSelectors.put("owner", "testuser");
    String nameFilter = "Test*";
    int start = 25;
    String sortBy = "name";
    SortOrder sortOrder = SortOrder.ASCENDING;
    
    // Create token using reflection
    ListSpacesNextPageToken token = (ListSpacesNextPageToken) createNextPageTokenMethod.invoke(
        spaceService,
        UuidUtil.toProtoBytes(ownerId).toByteArray(),
        labelSelectors,
        nameFilter,
        userId,
        start,
        sortBy,
        sortOrder);
    
    // Verify token content
    assertEquals(start, token.getStart(), "Start offset should match");
    assertArrayEquals(UuidUtil.toProtoBytes(ownerId).toByteArray(), token.getOwnerId().toByteArray(), 
        "Owner ID should match");
    assertEquals(labelSelectors, token.getLabelSelectorsMap(), "Label selectors should match");
    assertEquals(nameFilter, token.getNameFilter(), "Name filter should match");
    assertArrayEquals(UuidUtil.toProtoBytes(userId).toByteArray(), token.getRequestorId().toByteArray(), 
        "Requestor ID should match");
    assertEquals(sortBy, token.getSortBy(), "Sort by should match");
    assertEquals(sortOrder, token.getSortOrder(), "Sort order should match");
    
    // Encode token
    String encodedToken = (String) encodeMethod.invoke(spaceService, token);
    
    // Verify encoded token is not empty
    assertNotNull(encodedToken, "Encoded token should not be null");
    assertFalse(encodedToken.isEmpty(), "Encoded token should not be empty");
    
    // Try to decode token
    StatusOr<ListSpacesNextPageToken> resultOr = (StatusOr<ListSpacesNextPageToken>) 
        decodeAndValidateMethod.invoke(spaceService, encodedToken, authenticatedUser);
    
    // Verify decode result
    assertTrue(resultOr.isOk(), "Decoding should succeed: " + 
        (resultOr.isNotOk() ? resultOr.getStatus().getMessage() : ""));
    ListSpacesNextPageToken decodedToken = resultOr.getValue();
    
    // Verify decoded token matches original
    assertEquals(token.getStart(), decodedToken.getStart(), "Start offset should match after decode");
    assertArrayEquals(token.getOwnerId().toByteArray(), decodedToken.getOwnerId().toByteArray(), 
        "Owner ID should match after decode");
    assertEquals(token.getLabelSelectorsMap(), decodedToken.getLabelSelectorsMap(), 
        "Label selectors should match after decode");
    assertEquals(token.getNameFilter(), decodedToken.getNameFilter(), 
        "Name filter should match after decode");
    assertArrayEquals(token.getRequestorId().toByteArray(), decodedToken.getRequestorId().toByteArray(), 
        "Requestor ID should match after decode");
    assertEquals(token.getSortBy(), decodedToken.getSortBy(), 
        "Sort by should match after decode");
    assertEquals(token.getSortOrder(), decodedToken.getSortOrder(), 
        "Sort order should match after decode");
  }
  
  @Test
  void testOptionalFieldsOmitted() throws Exception {
    // Test with minimal parameters
    int start = 10;
    
    // Create token with minimal parameters (only start and requestorId are required)
    ListSpacesNextPageToken token = (ListSpacesNextPageToken) createNextPageTokenMethod.invoke(
        spaceService,
        null,  // No owner ID
        null,  // No label selectors
        null,  // No name filter
        userId,
        start,
        null,  // No sort by
        null); // No sort order
    
    // Verify token contains only the required fields
    assertEquals(start, token.getStart(), "Start offset should match");
    assertArrayEquals(UuidUtil.toProtoBytes(userId).toByteArray(), token.getRequestorId().toByteArray(), 
        "Requestor ID should match");
    
    // Verify optional fields are not set
    assertFalse(token.hasOwnerId(), "Owner ID should not be set");
    assertEquals(0, token.getLabelSelectorsCount(), "Label selectors should be empty");
    assertFalse(token.hasNameFilter(), "Name filter should not be set");
    assertFalse(token.hasSortBy(), "Sort by should not be set");
    assertFalse(token.hasSortOrder(), "Sort order should not be set");
    
    // Encode and decode token
    String encodedToken = (String) encodeMethod.invoke(spaceService, token);
    StatusOr<ListSpacesNextPageToken> resultOr = (StatusOr<ListSpacesNextPageToken>) 
        decodeAndValidateMethod.invoke(spaceService, encodedToken, authenticatedUser);
    
    // Verify decode success
    assertTrue(resultOr.isOk(), "Decoding should succeed with minimal fields");
    ListSpacesNextPageToken decodedToken = resultOr.getValue();
    
    // Verify decoded token matches original
    assertEquals(token.getStart(), decodedToken.getStart(), "Start offset should match after decode");
    assertArrayEquals(token.getRequestorId().toByteArray(), decodedToken.getRequestorId().toByteArray(), 
        "Requestor ID should match after decode");
    assertFalse(decodedToken.hasOwnerId(), "Owner ID should not be set after decode");
    assertEquals(0, decodedToken.getLabelSelectorsCount(), "Label selectors should be empty after decode");
    assertFalse(decodedToken.hasNameFilter(), "Name filter should not be set after decode");
    assertFalse(decodedToken.hasSortBy(), "Sort by should not be set after decode");
    assertFalse(decodedToken.hasSortOrder(), "Sort order should not be set after decode");
  }
  
  @Test
  void testDecodeEmptyToken() throws Exception {
    // Test decoding empty token string
    StatusOr<ListSpacesNextPageToken> resultOr = (StatusOr<ListSpacesNextPageToken>) 
        decodeAndValidateMethod.invoke(spaceService, "", authenticatedUser);
    
    // Empty token should decode to a default instance (not an error)
    assertTrue(resultOr.isOk(), "Decoding empty token should succeed");
    ListSpacesNextPageToken decodedToken = resultOr.getValue();
    assertEquals(ListSpacesNextPageToken.getDefaultInstance(), decodedToken, 
        "Empty token should decode to default instance");
  }
  
  @Test
  void testDecodeInvalidFormat() throws Exception {
    // Test decoding an invalid token format (not Base64)
    StatusOr<ListSpacesNextPageToken> resultOr = (StatusOr<ListSpacesNextPageToken>) 
        decodeAndValidateMethod.invoke(spaceService, "not-valid-base64!", authenticatedUser);
    
    // Should be an error
    assertTrue(resultOr.isNotOk(), "Decoding invalid token should fail");
    assertEquals("INVALID_ARGUMENT", resultOr.getStatus().getCode().name(), 
        "Error code should be INVALID_ARGUMENT");
    assertTrue(resultOr.getStatus().getMessage().contains("token format"), 
        "Error should mention invalid token format");
  }
  
  @Test
  void testDecodeInvalidContent() throws Exception {
    // Create valid Base64 that doesn't contain a valid proto message
    String invalidProto = BaseEncoding.base64().encode("not-a-proto-message".getBytes());
    
    StatusOr<ListSpacesNextPageToken> resultOr = (StatusOr<ListSpacesNextPageToken>) 
        decodeAndValidateMethod.invoke(spaceService, invalidProto, authenticatedUser);
    
    // Should be an error
    assertTrue(resultOr.isNotOk(), "Decoding invalid proto content should fail");
    assertEquals("INVALID_ARGUMENT", resultOr.getStatus().getCode().name(), 
        "Error code should be INVALID_ARGUMENT");
    assertTrue(resultOr.getStatus().getMessage().contains("token content"), 
        "Error should mention invalid token content");
  }
  
  @Test
  void testTokenRequestorValidation() throws Exception {
    // Create token with a different requestor ID than the authenticated user
    UUID differentUserId = UUID.randomUUID();
    
    // Create token with minimal parameters but different requestor ID
    ListSpacesNextPageToken token = ListSpacesNextPageToken.newBuilder()
        .setStart(10)
        .setRequestorId(UuidUtil.toProtoBytes(differentUserId))
        .build();
    
    // Encode token
    String encodedToken = (String) encodeMethod.invoke(spaceService, token);
    
    // Try to decode with mismatched user
    StatusOr<ListSpacesNextPageToken> resultOr = (StatusOr<ListSpacesNextPageToken>) 
        decodeAndValidateMethod.invoke(spaceService, encodedToken, authenticatedUser);
    
    // Should be a permission denied error
    assertTrue(resultOr.isNotOk(), "Decoding with mismatched requestor should fail");
    assertEquals("PERMISSION_DENIED", resultOr.getStatus().getCode().name(), 
        "Error code should be PERMISSION_DENIED");
    assertTrue(resultOr.getStatus().getMessage().contains("Invalid pagination token"), 
        "Error should mention invalid pagination token");
  }

  @Test
  void testInvalidRequestorId() throws Exception {
    // Create token with invalid requestor ID (not a valid UUID)
    ListSpacesNextPageToken token = ListSpacesNextPageToken.newBuilder()
        .setStart(10)
        .setRequestorId(com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3})) // Invalid UUID bytes
        .build();
    
    // Encode token
    String encodedToken = (String) encodeMethod.invoke(spaceService, token);
    
    // Try to decode with invalid requestor ID
    StatusOr<ListSpacesNextPageToken> resultOr = (StatusOr<ListSpacesNextPageToken>) 
        decodeAndValidateMethod.invoke(spaceService, encodedToken, authenticatedUser);
    
    // Should be an invalid argument error
    assertTrue(resultOr.isNotOk(), "Decoding with invalid requestor ID should fail");
    assertEquals("INVALID_ARGUMENT", resultOr.getStatus().getCode().name(), 
        "Error code should be INVALID_ARGUMENT");
    assertTrue(resultOr.getStatus().getMessage().contains("Invalid requestor ID"), 
        "Error should mention invalid requestor ID");
  }
}