package com.goodmem.db.util;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.common.status.StatusOr;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests for the DbUtil class with a real PostgreSQL database using Testcontainers.
 *
 * <p>This test verifies that our utility methods interact with the actual JDBC driver as expected.
 */
@Testcontainers
public class DbUtilTest {

  private static PostgresTestHelper.PostgresContext postgresContext;
  private static Connection connection;

  @BeforeAll
  static void setUp() throws SQLException {
    // Use a standard PostgreSQL container (not pgvector) since we don't need vector operations
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_dbutil_test", DbUtilTest.class);
    connection = postgresContext.getConnection();
    
    // Set up test tables
    try (Statement stmt = connection.createStatement()) {
      // Create a test table with columns for all of the data types that DbUtil works with
      stmt.execute(
          """
          CREATE TABLE db_util_test (
            id SERIAL PRIMARY KEY,
            uuid_col UUID,
            timestamp_col TIMESTAMP WITH TIME ZONE,
            optional_uuid_col UUID,
            optional_timestamp_col TIMESTAMP WITH TIME ZONE
          )
          """);
    }
  }

  @AfterAll
  static void tearDown() throws SQLException {
    if (connection != null) {
      // Clean up test table
      try (Statement stmt = connection.createStatement()) {
        stmt.execute("DROP TABLE IF EXISTS db_util_test");
      }
    }
    
    // Close connection and stop container
    if (postgresContext != null) {
      postgresContext.close();
    }
  }

  @BeforeEach
  void clearTable() throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      stmt.execute("DELETE FROM db_util_test");
    }
  }

  /**
   * Tests the getUuid method that retrieves a UUID from a ResultSet using column name.
   */
  @Test
  void testGetUuidByName() throws SQLException {
    UUID expectedUuid = UUID.randomUUID();
    
    // Insert a row with a UUID
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (uuid_col) VALUES (?) RETURNING *")) {
      pstmt.setObject(1, expectedUuid);
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getUuid method
        StatusOr<UUID> result = DbUtil.getUuid(rs, "uuid_col");
        
        assertTrue(result.isOk(), "Expected getUuid to succeed");
        assertEquals(expectedUuid, result.getValue(), "Retrieved UUID should match the expected value");
      }
    }
  }
  
  /**
   * Tests the getUuid method with a null UUID value.
   */
  @Test
  void testGetUuidByNameWithNull() throws SQLException {
    // Insert a row with a null UUID
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (uuid_col) VALUES (NULL) RETURNING *")) {
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getUuid method with a null value
        StatusOr<UUID> result = DbUtil.getUuid(rs, "uuid_col");
        
        assertFalse(result.isOk(), "getUuid should fail for null value");
        assertTrue(result.getStatus().getMessage().contains("is null"), 
            "Error message should indicate null value");
      }
    }
  }
  
  /**
   * Tests the getUuid method that retrieves a UUID from a ResultSet using column index.
   */
  @Test
  void testGetUuidByIndex() throws SQLException {
    UUID expectedUuid = UUID.randomUUID();
    
    // Insert a row with a UUID
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (uuid_col) VALUES (?) RETURNING *")) {
      pstmt.setObject(1, expectedUuid);
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Get the column index for uuid_col
        int uuidColIndex = rs.findColumn("uuid_col");
        
        // Test the getUuid method with index
        StatusOr<UUID> result = DbUtil.getUuid(rs, "uuid_col", uuidColIndex);
        
        assertTrue(result.isOk(), "Expected getUuid with index to succeed");
        assertEquals(expectedUuid, result.getValue(), "Retrieved UUID should match the expected value");
      }
    }
  }
  
  /**
   * Tests the getUuid method with a null UUID value using column index.
   */
  @Test
  void testGetUuidByIndexWithNull() throws SQLException {
    // Insert a row with a null UUID
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (uuid_col) VALUES (NULL) RETURNING *")) {
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Get the column index for uuid_col
        int uuidColIndex = rs.findColumn("uuid_col");
        
        // Test the getUuid method with index and a null value
        StatusOr<UUID> result = DbUtil.getUuid(rs, "uuid_col", uuidColIndex);
        
        assertFalse(result.isOk(), "getUuid with index should fail for null value");
        assertTrue(result.getStatus().getMessage().contains("is null"), 
            "Error message should indicate null value");
      }
    }
  }
  
  /**
   * Tests the getOptionalUuid method with a non-null UUID value.
   */
  @Test
  void testGetOptionalUuidWithValue() throws SQLException {
    UUID expectedUuid = UUID.randomUUID();
    
    // Insert a row with a UUID
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (optional_uuid_col) VALUES (?) RETURNING *")) {
      pstmt.setObject(1, expectedUuid);
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getOptionalUuid method
        StatusOr<Optional<UUID>> result = DbUtil.getOptionalUuid(rs, "optional_uuid_col");
        
        assertTrue(result.isOk(), "Expected getOptionalUuid to succeed");
        assertTrue(result.getValue().isPresent(), "Result should be present");
        assertEquals(expectedUuid, result.getValue().get(), "Retrieved UUID should match the expected value");
      }
    }
  }
  
  /**
   * Tests the getOptionalUuid method with a null UUID value.
   */
  @Test
  void testGetOptionalUuidWithNull() throws SQLException {
    // Insert a row with a null UUID
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (optional_uuid_col) VALUES (NULL) RETURNING *")) {
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getOptionalUuid method with a null value
        StatusOr<Optional<UUID>> result = DbUtil.getOptionalUuid(rs, "optional_uuid_col");
        
        assertTrue(result.isOk(), "getOptionalUuid should succeed for null value");
        assertFalse(result.getValue().isPresent(), "Result should be empty Optional");
      }
    }
  }
  
  /**
   * Tests the getInstant method that retrieves an Instant from a ResultSet using column name.
   */
  @Test
  void testGetInstantByName() throws SQLException {
    Instant expectedInstant = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); // Postgres truncates to microseconds
    
    // Insert a row with a timestamp
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (timestamp_col) VALUES (?) RETURNING *")) {
      pstmt.setTimestamp(1, java.sql.Timestamp.from(expectedInstant));
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getInstant method
        StatusOr<Instant> result = DbUtil.getInstant(rs, "timestamp_col");
        
        assertTrue(result.isOk(), "Expected getInstant to succeed");
        assertEquals(expectedInstant, result.getValue(), "Retrieved Instant should match the expected value");
      }
    }
  }
  
  /**
   * Tests the getInstant method with a null timestamp value.
   */
  @Test
  void testGetInstantByNameWithNull() throws SQLException {
    // Insert a row with a null timestamp
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (timestamp_col) VALUES (NULL) RETURNING *")) {
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getInstant method with a null value
        StatusOr<Instant> result = DbUtil.getInstant(rs, "timestamp_col");
        
        assertFalse(result.isOk(), "getInstant should fail for null value");
        assertTrue(result.getStatus().getMessage().contains("is null"), 
            "Error message should indicate null value");
      }
    }
  }
  
  /**
   * Tests the getInstant method that retrieves an Instant from a ResultSet using column index.
   */
  @Test
  void testGetInstantByIndex() throws SQLException {
    Instant expectedInstant = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); // Postgres truncates to microseconds
    
    // Insert a row with a timestamp
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (timestamp_col) VALUES (?) RETURNING *")) {
      pstmt.setTimestamp(1, java.sql.Timestamp.from(expectedInstant));
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Get the column index for timestamp_col
        int timestampColIndex = rs.findColumn("timestamp_col");
        
        // Test the getInstant method with index
        StatusOr<Instant> result = DbUtil.getInstant(rs, "timestamp_col", timestampColIndex);
        
        assertTrue(result.isOk(), "Expected getInstant with index to succeed");
        assertEquals(expectedInstant, result.getValue(), "Retrieved Instant should match the expected value");
      }
    }
  }
  
  /**
   * Tests the getInstant method with a null timestamp value using column index.
   */
  @Test
  void testGetInstantByIndexWithNull() throws SQLException {
    // Insert a row with a null timestamp
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (timestamp_col) VALUES (NULL) RETURNING *")) {
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Get the column index for timestamp_col
        int timestampColIndex = rs.findColumn("timestamp_col");
        
        // Test the getInstant method with index and a null value
        StatusOr<Instant> result = DbUtil.getInstant(rs, "timestamp_col", timestampColIndex);
        
        assertFalse(result.isOk(), "getInstant with index should fail for null value");
        assertTrue(result.getStatus().getMessage().contains("is null"), 
            "Error message should indicate null value");
      }
    }
  }
  
  /**
   * Tests the getOptionalInstant method with a non-null timestamp value.
   */
  @Test
  void testGetOptionalInstantWithValue() throws SQLException {
    Instant expectedInstant = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS); // Postgres truncates to microseconds
    
    // Insert a row with a timestamp
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (optional_timestamp_col) VALUES (?) RETURNING *")) {
      pstmt.setTimestamp(1, java.sql.Timestamp.from(expectedInstant));
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getOptionalInstant method
        StatusOr<Optional<Instant>> result = DbUtil.getOptionalInstant(rs, "optional_timestamp_col");
        
        assertTrue(result.isOk(), "Expected getOptionalInstant to succeed");
        assertTrue(result.getValue().isPresent(), "Result should be present");
        assertEquals(expectedInstant, result.getValue().get(), "Retrieved Instant should match the expected value");
      }
    }
  }
  
  /**
   * Tests the getOptionalInstant method with a null timestamp value.
   */
  @Test
  void testGetOptionalInstantWithNull() throws SQLException {
    // Insert a row with a null timestamp
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO db_util_test (optional_timestamp_col) VALUES (NULL) RETURNING *")) {
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Test the getOptionalInstant method with a null value
        StatusOr<Optional<Instant>> result = DbUtil.getOptionalInstant(rs, "optional_timestamp_col");
        
        assertTrue(result.isOk(), "getOptionalInstant should succeed for null value");
        assertFalse(result.getValue().isPresent(), "Result should be empty Optional");
      }
    }
  }
  
  /**
   * Tests the timestamp conversion methods in DbUtil.
   */
  @Test
  void testTimestampConversions() {
    Instant now = Instant.now();
    
    // Test toSqlTimestamp and toInstant
    java.sql.Timestamp sqlTimestamp = DbUtil.toSqlTimestamp(now);
    Instant instantFromTimestamp = DbUtil.toInstant(sqlTimestamp);
    
    assertEquals(now.toEpochMilli(), instantFromTimestamp.toEpochMilli(), 
        "Conversion between Instant and Timestamp should preserve time");
    
    // Test toProtoTimestamp and fromProtoTimestamp
    com.google.protobuf.Timestamp protoTimestamp = DbUtil.toProtoTimestamp(now);
    Instant instantFromProto = DbUtil.fromProtoTimestamp(protoTimestamp);
    
    assertEquals(now.toEpochMilli(), instantFromProto.toEpochMilli(), 
        "Conversion between Instant and Protocol Buffer Timestamp should preserve time");
  }
  
  /**
   * Tests the truncate method.
   */
  @Test
  void testTruncate() {
    assertEquals("Hello", DbUtil.truncate("Hello, World!", 5), "String should be truncated to 5 characters");
    assertEquals("Hello, World!", DbUtil.truncate("Hello, World!", 20), "String should not be truncated if max length is greater");
    assertEquals("", DbUtil.truncate(null, 5), "Null string should be converted to empty string");
    assertEquals("", DbUtil.truncate("", 5), "Empty string should remain empty");
  }
  
  /**
   * Tests the vector formatting method.
   */
  @Test
  void testFormatVector() {
    float[] vector = {1.0f, 2.5f, -3.75f};
    String formatted = DbUtil.formatVector(vector);
    assertEquals("[1.0,2.5,-3.75]", formatted, "Vector should be formatted correctly");
    
    // Empty vector
    assertEquals("[]", DbUtil.formatVector(new float[0]), "Empty vector should format as []");
    
    // Null vector
    assertThrows(IllegalArgumentException.class, () -> DbUtil.formatVector(null), 
        "Null vector should throw IllegalArgumentException");
  }
}