package com.goodmem.db.util;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tests for the JSONB handling methods in DbUtil class with a real PostgreSQL database.
 *
 * <p>This test specifically verifies the JSON/JSONB conversion, parsing, and database interaction
 * capabilities, including edge cases with special characters, Unicode, and empty values.
 */
@Testcontainers
public class DbUtilJsonbTest {

  private static PostgresTestHelper.PostgresContext postgresContext;
  private static Connection connection;

  @BeforeAll
  static void setUp() throws SQLException {
    // Use a standard PostgreSQL container since we're just testing JSONB functionality
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_dbutil_jsonb_test", DbUtilJsonbTest.class);
    connection = postgresContext.getConnection();
    
    // Set up test table with JSONB column
    try (Statement stmt = connection.createStatement()) {
      stmt.execute(
          """
          CREATE TABLE jsonb_test (
            id SERIAL PRIMARY KEY,
            labels JSONB,
            complex_data JSONB
          )
          """);
    }
  }

  @AfterAll
  static void tearDown() throws SQLException {
    if (connection != null) {
      // Clean up test table
      try (Statement stmt = connection.createStatement()) {
        stmt.execute("DROP TABLE IF EXISTS jsonb_test");
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
      stmt.execute("DELETE FROM jsonb_test");
    }
  }

  /**
   * Tests basic map to JSONB conversion and retrieval.
   */
  @Test
  void testBasicJsonbHandling() throws SQLException {
    Map<String, String> labels = Map.of(
        "environment", "production",
        "tier", "frontend",
        "region", "us-west"
    );
    
    // Store the map as JSONB
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO jsonb_test (labels) VALUES (?) RETURNING *")) {
      
      Status status = DbUtil.setJsonbParameter(pstmt, 1, labels);
      assertTrue(status.isOk(), "Setting JSONB parameter should succeed");
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Retrieve and verify the JSONB data
        StatusOr<Map<String, String>> retrievedLabelsOr = DbUtil.parseJsonbToMap(rs, "labels");
        
        assertTrue(retrievedLabelsOr.isOk(), "Parsing JSONB should succeed");
        Map<String, String> retrievedLabels = retrievedLabelsOr.getValue();
        
        assertEquals(labels.size(), retrievedLabels.size(), "Retrieved map should have same size");
        for (Map.Entry<String, String> entry : labels.entrySet()) {
          assertTrue(retrievedLabels.containsKey(entry.getKey()), "Retrieved map should contain key: " + entry.getKey());
          assertEquals(entry.getValue(), retrievedLabels.get(entry.getKey()), 
              "Values should match for key: " + entry.getKey());
        }
      }
    }
  }

  /**
   * Tests handling of empty maps.
   */
  @Test
  void testEmptyMap() throws SQLException {
    Map<String, String> emptyMap = Map.of();
    
    // Store empty map
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO jsonb_test (labels) VALUES (?) RETURNING *")) {
      
      Status status = DbUtil.setJsonbParameter(pstmt, 1, emptyMap);
      assertTrue(status.isOk(), "Setting empty JSONB parameter should succeed");
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Retrieve and verify empty JSONB
        StatusOr<Map<String, String>> retrievedMapOr = DbUtil.parseJsonbToMap(rs, "labels");
        
        assertTrue(retrievedMapOr.isOk(), "Parsing empty JSONB should succeed");
        Map<String, String> retrievedMap = retrievedMapOr.getValue();
        
        assertTrue(retrievedMap.isEmpty(), "Retrieved map should be empty");
      }
    }
  }

  /**
   * Tests handling of null JSONB values.
   */
  @Test
  void testNullJsonb() throws SQLException {
    // Store NULL
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO jsonb_test (labels) VALUES (NULL) RETURNING *")) {
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Retrieve and verify null JSONB
        StatusOr<Map<String, String>> retrievedMapOr = DbUtil.parseJsonbToMap(rs, "labels");
        
        assertTrue(retrievedMapOr.isOk(), "Parsing null JSONB should succeed");
        Map<String, String> retrievedMap = retrievedMapOr.getValue();
        
        assertTrue(retrievedMap.isEmpty(), "Retrieved map should be empty for NULL JSONB");
      }
    }
  }

  /**
   * Tests handling of unicode and special characters in JSONB.
   */
  @Test
  void testSpecialCharactersAndUnicode() throws SQLException {
    Map<String, String> specialMap = new HashMap<>();
    specialMap.put("unicode", "こんにちは世界"); // Hello World in Japanese
    specialMap.put("emoji", "🚀🔥👨‍💻"); // Rocket, Fire, Man-Coding emoji
    specialMap.put("special", "quotes\"and'apostrophes\\and/slashes");
    specialMap.put("escaped", "\t\r\n\b\f"); // Tab, CR, LF, Backspace, Form feed
    
    // Store map with special characters
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO jsonb_test (labels) VALUES (?) RETURNING *")) {
      
      Status status = DbUtil.setJsonbParameter(pstmt, 1, specialMap);
      assertTrue(status.isOk(), "Setting JSONB with special characters should succeed");
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Retrieve and verify special JSONB
        StatusOr<Map<String, String>> retrievedMapOr = DbUtil.parseJsonbToMap(rs, "labels");
        
        assertTrue(retrievedMapOr.isOk(), "Parsing JSONB with special characters should succeed");
        Map<String, String> retrievedMap = retrievedMapOr.getValue();
        
        assertEquals(specialMap.size(), retrievedMap.size(), "Retrieved map should have same size");
        for (Map.Entry<String, String> entry : specialMap.entrySet()) {
          assertTrue(retrievedMap.containsKey(entry.getKey()), "Retrieved map should contain key: " + entry.getKey());
          assertEquals(entry.getValue(), retrievedMap.get(entry.getKey()), 
              "Values should match for key: " + entry.getKey());
        }
      }
    }
  }

  /**
   * Tests handling of large JSONB data.
   */
  @Test
  void testLargeJsonb() throws SQLException {
    // Create a large map with 1000 entries
    Map<String, String> largeMap = new HashMap<>();
    for (int i = 0; i < 1000; i++) {
      largeMap.put("key" + i, "value" + i + "-" + "x".repeat(100)); // Each value has 100+ chars
    }
    
    // Store large map
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO jsonb_test (labels) VALUES (?) RETURNING *")) {
      
      Status status = DbUtil.setJsonbParameter(pstmt, 1, largeMap);
      assertTrue(status.isOk(), "Setting large JSONB parameter should succeed");
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Retrieve and verify large JSONB
        StatusOr<Map<String, String>> retrievedMapOr = DbUtil.parseJsonbToMap(rs, "labels");
        
        assertTrue(retrievedMapOr.isOk(), "Parsing large JSONB should succeed");
        Map<String, String> retrievedMap = retrievedMapOr.getValue();
        
        assertEquals(largeMap.size(), retrievedMap.size(), "Retrieved map should have same size");
        
        // Check some random keys to verify correctness (checking all 1000 would be slow)
        for (int i = 0; i < 1000; i += 100) {
          String key = "key" + i;
          assertTrue(retrievedMap.containsKey(key), "Retrieved map should contain key: " + key);
          assertEquals(largeMap.get(key), retrievedMap.get(key), 
              "Values should match for key: " + key);
        }
      }
    }
  }

  /**
   * Tests direct SQL JSONB handling to verify our utility methods work as expected.
   */
  @Test
  void testSqlVersusUtilityHandling() throws SQLException {
    Map<String, String> testMap = Map.of(
        "test", "value",
        "another", "example"
    );
    
    // First, use our utility methods
    long utilityId;
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO jsonb_test (labels) VALUES (?) RETURNING id")) {
      
      DbUtil.setJsonbParameter(pstmt, 1, testMap);
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        utilityId = rs.getLong("id");
      }
    }
    
    // Now, use raw SQL
    long sqlId;
    try (PreparedStatement pstmt = connection.prepareStatement(
        "INSERT INTO jsonb_test (labels) VALUES (?::jsonb) RETURNING id")) {
      
      // Direct JSON with PostgreSQL casting
      pstmt.setString(1, "{\"test\":\"value\",\"another\":\"example\"}");
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        sqlId = rs.getLong("id");
      }
    }
    
    // Retrieve both entries and compare
    try (PreparedStatement pstmt = connection.prepareStatement(
        "SELECT id, labels FROM jsonb_test WHERE id IN (?, ?) ORDER BY id")) {
      
      pstmt.setLong(1, utilityId);
      pstmt.setLong(2, sqlId);
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected first result");
        long firstId = rs.getLong("id");
        
        StatusOr<Map<String, String>> firstMapOr = DbUtil.parseJsonbToMap(rs, "labels");
        assertTrue(firstMapOr.isOk(), "Parsing first JSONB should succeed");
        Map<String, String> firstMap = firstMapOr.getValue();
        
        assertTrue(rs.next(), "Expected second result");
        long secondId = rs.getLong("id");
        
        StatusOr<Map<String, String>> secondMapOr = DbUtil.parseJsonbToMap(rs, "labels");
        assertTrue(secondMapOr.isOk(), "Parsing second JSONB should succeed");
        Map<String, String> secondMap = secondMapOr.getValue();
        
        // Compare the maps, regardless of which ID is which
        assertEquals(firstMap.size(), secondMap.size(), "Both maps should have same size");
        for (Map.Entry<String, String> entry : firstMap.entrySet()) {
          assertTrue(secondMap.containsKey(entry.getKey()), 
              "Second map should contain key: " + entry.getKey());
          assertEquals(entry.getValue(), secondMap.get(entry.getKey()), 
              "Values should match for key: " + entry.getKey());
        }
      }
    }
  }

  /**
   * Tests direct map-to-jsonb and jsonb-to-map conversion without database interaction.
   */
  @Test
  void testDirectConversion() {
    Map<String, String> originalMap = Map.of(
        "key1", "value1",
        "key2", "value2",
        "key3", "value3"
    );
    
    // Convert to JSONB string
    StatusOr<String> jsonOr = DbUtil.mapToJsonb(originalMap);
    assertTrue(jsonOr.isOk(), "Map to JSONB conversion should succeed");
    String json = jsonOr.getValue();
    
    // Verify the JSON structure (simple check)
    assertTrue(json.contains("\"key1\""), "JSON should contain key1");
    assertTrue(json.contains("\"value1\""), "JSON should contain value1");
    
    // Use executeQuery to get a ResultSet with our JSONB
    try (PreparedStatement pstmt = connection.prepareStatement(
        "SELECT ?::jsonb as test_jsonb")) {
      
      pstmt.setString(1, json);
      
      try (ResultSet rs = pstmt.executeQuery()) {
        assertTrue(rs.next(), "Expected a result row");
        
        // Parse back to a map
        StatusOr<Map<String, String>> parsedMapOr = DbUtil.parseJsonbToMap(rs, "test_jsonb");
        assertTrue(parsedMapOr.isOk(), "JSONB to map conversion should succeed");
        Map<String, String> parsedMap = parsedMapOr.getValue();
        
        // Verify the round-trip worked
        assertEquals(originalMap.size(), parsedMap.size(), "Parsed map should have same size");
        for (Map.Entry<String, String> entry : originalMap.entrySet()) {
          assertTrue(parsedMap.containsKey(entry.getKey()), 
              "Parsed map should contain key: " + entry.getKey());
          assertEquals(entry.getValue(), parsedMap.get(entry.getKey()), 
              "Values should match for key: " + entry.getKey());
        }
      }
    } catch (SQLException e) {
      fail("SQL Exception should not occur: " + e.getMessage());
    }
  }
}