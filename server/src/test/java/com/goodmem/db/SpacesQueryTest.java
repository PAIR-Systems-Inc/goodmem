package com.goodmem.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.goodmem.SpaceServiceImpl;
import com.goodmem.common.status.StatusOr;
import com.goodmem.db.Spaces.QueryResult;
import com.goodmem.db.helpers.EntityHelper;
import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.PostgresTestHelper;
import com.goodmem.db.util.PostgresTestHelper.PostgresContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the querySpaces method in Spaces class.
 * These tests use a real PostgreSQL database via TestContainers to verify
 * the query functionality with actual database interactions.
 */
@Testcontainers
public class SpacesQueryTest {

  private static PostgresContext postgresContext;
  private static HikariDataSource dataSource;
  
  // Test data
  private static UUID ownerA;
  private static UUID ownerB;
  private static List<UUID> spaceIds = new ArrayList<>();

  @BeforeAll
  static void setUp() throws SQLException {
    // Setup PostgreSQL container
    postgresContext = PostgresTestHelper.setupPostgres("goodmem_spacesquery_test", SpacesQueryTest.class);

    // Create a HikariDataSource for the test container
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(postgresContext.getContainer().getJdbcUrl());
    config.setUsername(postgresContext.getContainer().getUsername());
    config.setPassword(postgresContext.getContainer().getPassword());
    config.setMaximumPoolSize(2);
    dataSource = new HikariDataSource(config);
    
    // Create test data
    try (Connection conn = dataSource.getConnection()) {
      ownerA = EntityHelper.createTestUserWithKey(conn).userId();
      ownerB = EntityHelper.createTestUserWithKey(conn).userId();
      
      // Create test spaces with various attributes
      createTestSpaces(conn);
    }
  }
  
  @AfterAll
  static void tearDown() {
    if (dataSource != null) {
      dataSource.close();
    }
    
    if (postgresContext != null) {
      postgresContext.close();
    }
  }
  
  @Test
  void testBasicQuery() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Query all spaces without restrictions
      StatusOr<QueryResult> resultOr = Spaces.querySpaces(
          conn, null, null, null, null, true, 0, 10, true, ownerA);
      
      assertTrue(resultOr.isOk(), "Query should succeed");
      QueryResult result = resultOr.getValue();
      
      // Should return all spaces that are either owned by ownerA or public
      assertFalse(result.getSpaces().isEmpty(), "Should return spaces");
      
      // Count how many spaces are either owned by ownerA or public
      int expectedCount = 0;
      for (UUID spaceId : spaceIds) {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM space WHERE space_id = ? AND (owner_id = ? OR public_read = true)")) {
          stmt.setObject(1, spaceId);
          stmt.setObject(2, ownerA);
          if (stmt.executeQuery().next()) {
            expectedCount++;
          }
        }
      }
      
      assertEquals(expectedCount, result.getSpaces().size(), 
          "Should return correct number of spaces");
      assertEquals(expectedCount, result.getTotalCount(), 
          "Total count should match returned spaces");
    }
  }
  
  @Test
  void testOwnerFilter() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Query spaces owned by ownerA
      StatusOr<QueryResult> resultOr = Spaces.querySpaces(
          conn, ownerA, null, null, null, true, 0, 10, true, ownerA);
      
      assertTrue(resultOr.isOk(), "Query should succeed");
      QueryResult result = resultOr.getValue();
      
      // Verify all returned spaces are owned by ownerA
      assertFalse(result.getSpaces().isEmpty(), "Should return spaces");
      for (Space space : result.getSpaces()) {
        assertEquals(ownerA, space.ownerId(), "Space should be owned by ownerA");
      }
      
      // Count how many spaces are owned by ownerA
      int expectedCount = 0;
      for (UUID spaceId : spaceIds) {
        try (PreparedStatement stmt = conn.prepareStatement(
            "SELECT * FROM space WHERE space_id = ? AND owner_id = ?")) {
          stmt.setObject(1, spaceId);
          stmt.setObject(2, ownerA);
          if (stmt.executeQuery().next()) {
            expectedCount++;
          }
        }
      }
      
      assertEquals(expectedCount, result.getSpaces().size(), 
          "Should return correct number of spaces");
      assertEquals(expectedCount, result.getTotalCount(), 
          "Total count should match returned spaces");
    }
  }
  
  @Test
  void testLabelFilter() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Query spaces with specific label
      Map<String, String> labelSelectors = new HashMap<>();
      labelSelectors.put("env", "test");
      
      StatusOr<QueryResult> resultOr = Spaces.querySpaces(
          conn, null, labelSelectors, null, null, true, 0, 10, true, ownerA);
      
      assertTrue(resultOr.isOk(), "Query should succeed");
      QueryResult result = resultOr.getValue();
      
      // Verify all returned spaces have the specified label
      assertFalse(result.getSpaces().isEmpty(), "Should return spaces");
      for (Space space : result.getSpaces()) {
        assertTrue(space.labels().containsKey("env"), "Space should have env label");
        assertEquals("test", space.labels().get("env"), "Space should have env=test label");
      }
      
      // Multiple label filters
      labelSelectors.put("visibility", "public");
      
      StatusOr<QueryResult> multiLabelOr = Spaces.querySpaces(
          conn, null, labelSelectors, null, null, true, 0, 10, true, ownerA);
      
      assertTrue(multiLabelOr.isOk(), "Query with multiple labels should succeed");
      QueryResult multiLabelResult = multiLabelOr.getValue();
      
      // Should return fewer or equal spaces than the single label query
      assertTrue(multiLabelResult.getSpaces().size() <= result.getSpaces().size(),
          "Multiple label filters should return fewer or equal results");
      
      // Verify all returned spaces have all specified labels
      for (Space space : multiLabelResult.getSpaces()) {
        assertTrue(space.labels().containsKey("env"), "Space should have env label");
        assertEquals("test", space.labels().get("env"), "Space should have env=test label");
        assertTrue(space.labels().containsKey("visibility"), "Space should have visibility label");
        assertEquals("public", space.labels().get("visibility"), 
            "Space should have visibility=public label");
      }
    }
  }
  
  @Test
  void testNameFilter() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Convert glob pattern to SQL pattern
      String namePattern = SpaceServiceImpl.globToSqlLike("Test*");
      
      StatusOr<QueryResult> resultOr = Spaces.querySpaces(
          conn, null, null, namePattern, null, true, 0, 10, true, ownerA);
      
      assertTrue(resultOr.isOk(), "Query should succeed");
      QueryResult result = resultOr.getValue();
      
      // Verify all returned spaces have names starting with "Test"
      assertFalse(result.getSpaces().isEmpty(), "Should return spaces");
      for (Space space : result.getSpaces()) {
        assertTrue(space.name().startsWith("Test"), 
            "Space name should start with 'Test': " + space.name());
      }
      
      // More complex pattern
      namePattern = SpaceServiceImpl.globToSqlLike("*Space*");
      
      StatusOr<QueryResult> complexPatternOr = Spaces.querySpaces(
          conn, null, null, namePattern, null, true, 0, 10, true, ownerA);
      
      assertTrue(complexPatternOr.isOk(), "Query with complex pattern should succeed");
      QueryResult complexPatternResult = complexPatternOr.getValue();
      
      // Verify all returned spaces have "Space" in the name
      for (Space space : complexPatternResult.getSpaces()) {
        assertTrue(space.name().contains("Space"), 
            "Space name should contain 'Space': " + space.name());
      }
    }
  }
  
  @Test
  void testSorting() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Query with ascending name sort
      StatusOr<QueryResult> ascOr = Spaces.querySpaces(
          conn, null, null, null, "name", true, 0, 100, true, ownerA);
      
      assertTrue(ascOr.isOk(), "Ascending sort query should succeed");
      QueryResult ascResult = ascOr.getValue();
      
      // Verify spaces are sorted by name ascending
      List<Space> ascSpaces = ascResult.getSpaces();
      for (int i = 1; i < ascSpaces.size(); i++) {
        assertTrue(ascSpaces.get(i-1).name().compareTo(ascSpaces.get(i).name()) <= 0,
            "Spaces should be sorted by name ascending");
      }
      
      // Query with descending name sort
      StatusOr<QueryResult> descOr = Spaces.querySpaces(
          conn, null, null, null, "name", false, 0, 100, true, ownerA);
      
      assertTrue(descOr.isOk(), "Descending sort query should succeed");
      QueryResult descResult = descOr.getValue();
      
      // Verify spaces are sorted by name descending
      List<Space> descSpaces = descResult.getSpaces();
      for (int i = 1; i < descSpaces.size(); i++) {
        assertTrue(descSpaces.get(i-1).name().compareTo(descSpaces.get(i).name()) >= 0,
            "Spaces should be sorted by name descending");
      }
    }
  }
  
  @Test
  void testPagination() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Query to get total count first
      StatusOr<QueryResult> countResult = Spaces.querySpaces(
          conn, null, null, null, "name", true, 0, 100, true, ownerA);
      assertTrue(countResult.isOk(), "Initial count query should succeed");
      long totalCount = countResult.getValue().getTotalCount();
      
      // Test Case 1: Page size evenly divides total count
      // Example: 6 spaces with page size 2 = 3 full pages
      int pageSize = 2;
      int expectedFullPages = (int) Math.ceil((double) totalCount / pageSize);
      System.out.println("Testing pagination with " + totalCount + " spaces and page size " + 
          pageSize + " (expected " + expectedFullPages + " pages)");
      
      // Query all pages and verify
      List<Space> allSpaces = new ArrayList<>();
      int offset = 0;
      int pageNum = 1;
      
      while (true) {
        StatusOr<QueryResult> pageResult = Spaces.querySpaces(
            conn, null, null, null, "name", true, offset, pageSize, true, ownerA);
        assertTrue(pageResult.isOk(), "Page " + pageNum + " query should succeed");
        QueryResult result = pageResult.getValue();
        
        // Add all spaces from this page to our collected list
        allSpaces.addAll(result.getSpaces());
        
        // Verify page size (except possibly the last page)
        if (pageNum < expectedFullPages || totalCount % pageSize == 0) {
          assertEquals(Math.min(pageSize, totalCount - offset), result.getSpaces().size(),
              "Page " + pageNum + " should have correct number of spaces");
        } else {
          // Last page with partial results
          assertEquals(totalCount % pageSize, result.getSpaces().size(),
              "Last page should have correct number of remaining spaces");
        }
        
        // Verify has more pages and next offset
        boolean shouldHaveMore = offset + result.getSpaces().size() < totalCount;
        assertEquals(shouldHaveMore, result.hasMore(offset, pageSize),
            "hasMore should correctly indicate if there are more pages after page " + pageNum);
        
        if (shouldHaveMore) {
          int nextOffset = result.getNextOffset(offset, pageSize);
          assertTrue(nextOffset > offset, "Next offset should be greater than current offset");
          assertEquals(offset + result.getSpaces().size(), nextOffset,
              "Next offset should be current offset + current page size");
          offset = nextOffset;
          pageNum++;
        } else {
          // Verify we don't get a next token for the last page
          assertEquals(-1, result.getNextOffset(offset, pageSize),
              "Last page should return -1 for next offset");
          break;
        }
      }
      
      // Verify we got all spaces with no duplicates
      assertEquals(totalCount, allSpaces.size(), "Should have retrieved all spaces across all pages");
      assertEquals(allSpaces.size(), new HashSet<>(allSpaces.stream().map(Space::spaceId).toList()).size(),
          "Should have no duplicate spaces across pages");
      
      // Test Case 2: Single-item last page
      // Create a filter that returns a count not evenly divisible by page size
      int oddPageSize = 3;
      
      // Use a filter that ensures we get at least one full page + one partial page
      // For the case where the total visible records are 4,5,6,7 or 8, using page size 3
      // would give a non-empty last page with 1,2 records respectively
      Map<String, String> labelSelectors = new HashMap<>();
      if (totalCount <= 3) {
        // If we don't have enough total spaces, let's just test with all spaces
        // and verify the behavior for the specific count we have
        System.out.println("Not enough total spaces for odd-page test, using all spaces");
      } else {
        // No filter needed, we have enough spaces for the test
      }
      
      // Execute the test
      List<Space> collectedSpaces = new ArrayList<>();
      offset = 0;
      pageNum = 1;
      int previousPageCount = 0;
      
      while (true) {
        StatusOr<QueryResult> pageResult = Spaces.querySpaces(
            conn, null, labelSelectors, null, "name", true, offset, oddPageSize, true, ownerA);
        assertTrue(pageResult.isOk(), "Odd page size query " + pageNum + " should succeed");
        QueryResult result = pageResult.getValue();
        
        // Keep track of actual spaces received
        collectedSpaces.addAll(result.getSpaces());
        
        // Calculate if this should be the last page
        boolean shouldBeLastPage = collectedSpaces.size() == result.getTotalCount();
        
        if (shouldBeLastPage) {
          // This should be the last page
          // If we have a non-empty last page that isn't full, verify it has the correct number of items
          if (previousPageCount == oddPageSize && result.getSpaces().size() > 0 && 
              result.getSpaces().size() < oddPageSize) {
            System.out.println("Last page has " + result.getSpaces().size() + 
                " spaces (partial page as expected)");
          }
          
          // Verify we don't have more pages
          assertFalse(result.hasMore(offset, oddPageSize), 
              "Last page should not indicate more pages");
          assertEquals(-1, result.getNextOffset(offset, oddPageSize),
              "Last page nextOffset should be -1");
          break;
        } else {
          // Not the last page, so we should have more
          assertTrue(result.hasMore(offset, oddPageSize), 
              "Non-last page should indicate more pages");
          assertTrue(result.getNextOffset(offset, oddPageSize) > offset,
              "Next offset should be greater than current");
          
          previousPageCount = result.getSpaces().size();
          offset = result.getNextOffset(offset, oddPageSize);
          pageNum++;
        }
      }
      
      // Verify we got the right number of spaces
      assertEquals(totalCount, collectedSpaces.size(), 
          "Should have collected all spaces with odd page size");
      
      // Test Case 3: Page size = 1 (ensures we have multiple pages)
      pageSize = 1;
      offset = 0;
      
      // Get first page
      StatusOr<QueryResult> firstPageOr = Spaces.querySpaces(
          conn, null, null, null, "name", true, offset, pageSize, true, ownerA);
      assertTrue(firstPageOr.isOk(), "Single-item page query should succeed");
      QueryResult firstPageResult = firstPageOr.getValue();
      
      // Should always have exactly one item (if total > 0)
      if (totalCount > 0) {
        assertEquals(1, firstPageResult.getSpaces().size(), 
            "Page size 1 should return exactly one space");
        assertTrue(firstPageResult.hasMore(offset, pageSize),
            "Should have more pages when total > 1 and page size = 1");
        assertEquals(1, firstPageResult.getNextOffset(offset, pageSize),
            "Next offset should be 1 for page size 1");
      }
      
      // Test Case 4: Page size > total count (should return all items and no next page)
      pageSize = (int) totalCount + 5; // Ensure larger than total
      
      StatusOr<QueryResult> allItemsOr = Spaces.querySpaces(
          conn, null, null, null, "name", true, 0, pageSize, true, ownerA);
      assertTrue(allItemsOr.isOk(), "All-items query should succeed");
      QueryResult allItemsResult = allItemsOr.getValue();
      
      assertEquals(totalCount, allItemsResult.getSpaces().size(),
          "Page size > total should return all spaces");
      assertFalse(allItemsResult.hasMore(0, pageSize),
          "Should not have more pages when page size > total");
      assertEquals(-1, allItemsResult.getNextOffset(0, pageSize),
          "Next offset should be -1 when no more pages");
    }
  }
  
  @Test
  void testPermissionFiltering() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Get all spaces visible to ownerA (owned + public)
      StatusOr<QueryResult> withPublicOr = Spaces.querySpaces(
          conn, null, null, null, null, true, 0, 100, true, ownerA);
      
      assertTrue(withPublicOr.isOk(), "Query with public spaces should succeed");
      QueryResult withPublicResult = withPublicOr.getValue();
      
      // Get only spaces owned by ownerA
      StatusOr<QueryResult> ownedOnlyOr = Spaces.querySpaces(
          conn, null, null, null, null, true, 0, 100, false, ownerA);
      
      assertTrue(ownedOnlyOr.isOk(), "Query for owned spaces only should succeed");
      QueryResult ownedOnlyResult = ownedOnlyOr.getValue();
      
      // Owned-only result should be a subset of the with-public result
      assertTrue(ownedOnlyResult.getSpaces().size() <= withPublicResult.getSpaces().size(),
          "Owned-only result should be smaller or equal to with-public result");
      
      // All spaces in owned-only result should be owned by ownerA
      for (Space space : ownedOnlyResult.getSpaces()) {
        assertEquals(ownerA, space.ownerId(), "Space should be owned by ownerA");
      }
      
      // All spaces in with-public result should be either owned by ownerA or public
      for (Space space : withPublicResult.getSpaces()) {
        assertTrue(space.ownerId().equals(ownerA) || space.publicRead(),
            "Space should be owned by ownerA or public");
      }
    }
  }
  
  @Test
  void testSanitizeSortField() throws Exception {
    // Test private sanitizeSortField method using reflection
    java.lang.reflect.Method sanitizeMethod = Spaces.class.getDeclaredMethod("sanitizeSortField", String.class);
    sanitizeMethod.setAccessible(true);
    
    // Valid fields should be returned as is
    assertEquals("name", sanitizeMethod.invoke(null, "name"), "Valid field should be preserved");
    assertEquals("created_at", sanitizeMethod.invoke(null, "created_at"), "Valid field should be preserved");
    assertEquals("created_at", sanitizeMethod.invoke(null, "created_time"), "Alias should be mapped");
    
    // Invalid fields should be replaced with default
    assertEquals("created_at", sanitizeMethod.invoke(null, "nonexistent_field"), 
        "Invalid field should be replaced with default");
    assertEquals("created_at", sanitizeMethod.invoke(null, "'); DROP TABLE space; --"), 
        "SQL injection attempt should be replaced with default");
  }
  
  @Test
  void testGlobToSqlLike() {
    // Test glob pattern conversion
    assertEquals("Boy%", SpaceServiceImpl.globToSqlLike("Boy*"), "* should be converted to %");
    assertEquals("%Boy%", SpaceServiceImpl.globToSqlLike("*Boy*"), "* should be converted to %");
    assertEquals("B_y%", SpaceServiceImpl.globToSqlLike("B?y*"), "? should be converted to _");
    assertEquals("\\%something\\%", SpaceServiceImpl.globToSqlLike("%something%"), 
        "Literal % should be escaped");
    assertEquals("\\_thing", SpaceServiceImpl.globToSqlLike("_thing"), "Literal _ should be escaped");
    assertEquals("\\\\foo", SpaceServiceImpl.globToSqlLike("\\foo"), "Literal \\ should be escaped");
    assertEquals("%", SpaceServiceImpl.globToSqlLike(""), "Empty glob should match everything");
    assertEquals("%", SpaceServiceImpl.globToSqlLike(null), "Null glob should match everything");
  }
  
  @Test
  void testCombinedFilters() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
      // Combine multiple filters
      Map<String, String> labelSelectors = new HashMap<>();
      labelSelectors.put("env", "test");
      
      StatusOr<QueryResult> combinedOr = Spaces.querySpaces(
          conn, ownerA, labelSelectors, SpaceServiceImpl.globToSqlLike("Test*"), "name", true, 0, 10, true, ownerA);
      
      assertTrue(combinedOr.isOk(), "Combined filters query should succeed");
      QueryResult combinedResult = combinedOr.getValue();
      
      // Verify all returned spaces match all filters
      for (Space space : combinedResult.getSpaces()) {
        assertEquals(ownerA, space.ownerId(), "Space should be owned by ownerA");
        assertTrue(space.labels().containsKey("env"), "Space should have env label");
        assertEquals("test", space.labels().get("env"), "Space should have env=test label");
        assertTrue(space.name().startsWith("Test"), "Space name should start with 'Test'");
      }
    }
  }
  
  /**
   * Creates test spaces with various attributes.
   */
  private static void createTestSpaces(Connection conn) throws SQLException {
    // Space 1: Owned by A, public, with labels
    UUID space1Id = createSpace(conn, "Test Space A1", ownerA, ownerA,
        Map.of("env", "test", "visibility", "public", "project", "goodmem"), true);
    spaceIds.add(space1Id);
    
    // Space 2: Owned by A, private, with different labels
    UUID space2Id = createSpace(conn, "Test Space A2", ownerA, ownerA,
        Map.of("env", "test", "visibility", "private", "project", "goodmem"), false);
    spaceIds.add(space2Id);
    
    // Space 3: Owned by B, public, with some same labels
    UUID space3Id = createSpace(conn, "Public Space B1", ownerB, ownerB,
        Map.of("env", "test", "visibility", "public", "owner", "userB"), true);
    spaceIds.add(space3Id);
    
    // Space 4: Owned by B, private, with different labels
    UUID space4Id = createSpace(conn, "Private Space B2", ownerB, ownerB,
        Map.of("env", "prod", "visibility", "private", "owner", "userB"), false);
    spaceIds.add(space4Id);
    
    // Space 5: Owned by A, with minimal labels
    UUID space5Id = createSpace(conn, "Basic Space A3", ownerA, ownerA,
        Map.of("type", "basic"), true);
    spaceIds.add(space5Id);
    
    // Space 6: Owned by B, with minimal labels
    UUID space6Id = createSpace(conn, "Basic Space B3", ownerB, ownerB,
        Map.of("type", "basic"), false);
    spaceIds.add(space6Id);
    
    // Space 7-10: Additional spaces with various names for sorting/filtering tests
    UUID space7Id = createSpace(conn, "Alpha Space", ownerA, ownerA,
        Map.of("order", "first"), true);
    spaceIds.add(space7Id);
    
    UUID space8Id = createSpace(conn, "Zeta Space", ownerA, ownerA,
        Map.of("order", "last"), true);
    spaceIds.add(space8Id);
    
    UUID space9Id = createSpace(conn, "Something Different", ownerB, ownerB,
        Map.of("category", "other"), true);
    spaceIds.add(space9Id);
    
    UUID space10Id = createSpace(conn, "Test With Special%Chars?", ownerB, ownerB,
        Map.of("special", "true"), false);
    spaceIds.add(space10Id);
  }
  
  /**
   * Creates a single space in the database.
   */
  private static UUID createSpace(
      Connection conn, 
      String name, 
      UUID ownerId,
      UUID createdById,
      Map<String, String> labels,
      boolean publicRead) throws SQLException {
    
    UUID spaceId = UUID.randomUUID();
    Instant now = Instant.now();
    UUID embedderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    
    // Create an embedder if it doesn't exist yet (for the first call)
    try {
      EntityHelper.createTestEmbedder(conn, embedderId, createdById);
    } catch (RuntimeException e) {
      // Ignore if embedder already exists
      if (!e.getMessage().contains("duplicate key")) {
        throw e;
      }
    }

    // Create a Space record
    Space space = new Space(
        spaceId,
        ownerId,
        name,
        labels,
        embedderId,
        publicRead,
        now,
        now,
        createdById,
        createdById);
    
    // Save the space using Spaces.save()
    StatusOr<Integer> result = Spaces.save(conn, space);
    if (result.isNotOk()) {
      throw new SQLException("Failed to save space: " + result.getStatus().getMessage());
    }
    
    return spaceId;
  }
}