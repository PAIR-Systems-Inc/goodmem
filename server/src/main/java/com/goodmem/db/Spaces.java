package com.goodmem.db;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.DbUtil;
import com.google.common.collect.ImmutableList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.UUID;
import javax.annotation.Nonnull;

/** DAO helper class for the 'space' table. */
public final class Spaces {

  private Spaces() {
    // Utility class
  }

  /**
   * Loads all spaces.
   *
   * @param conn an open JDBC connection
   * @return StatusOr containing a list of Space objects or an error
   */
  @Nonnull
  public static StatusOr<List<Space>> loadAll(Connection conn) {
    String sql =
        """
        SELECT space_id, owner_id, name, labels, embedder_id, public_read,
               created_at, updated_at, created_by_id, updated_by_id
          FROM space
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      List<Space> result = new ArrayList<>();
      while (rs.next()) {
        StatusOr<Space> spaceOr = extractSpace(rs);
        if (spaceOr.isNotOk()) {
          return StatusOr.ofStatus(spaceOr.getStatus());
        }
        result.add(spaceOr.getValue());
      }
      return StatusOr.ofValue(ImmutableList.copyOf(result));
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads a single space by ID.
   *
   * @param conn an open JDBC connection
   * @param spaceId the UUID of the space to load
   * @return StatusOr containing an Optional Space or an error
   */
  @Nonnull
  public static StatusOr<Optional<Space>> loadById(Connection conn, UUID spaceId) {
    String sql =
        """
        SELECT space_id, owner_id, name, labels, embedder_id, public_read,
               created_at, updated_at, created_by_id, updated_by_id
          FROM space
         WHERE space_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, spaceId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          StatusOr<Space> spaceOr = extractSpace(rs);
          if (spaceOr.isNotOk()) {
            return StatusOr.ofStatus(spaceOr.getStatus());
          }
          return StatusOr.ofValue(Optional.of(spaceOr.getValue()));
        }
        return StatusOr.ofValue(Optional.empty());
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads spaces by owner ID.
   *
   * @param conn an open JDBC connection
   * @param ownerId the owner user ID to load spaces for
   * @return StatusOr containing a list of Space objects or an error
   */
  @Nonnull
  public static StatusOr<List<Space>> loadByOwnerId(Connection conn, UUID ownerId) {
    String sql =
        """
        SELECT space_id, owner_id, name, labels, embedder_id, public_read,
               created_at, updated_at, created_by_id, updated_by_id
          FROM space
         WHERE owner_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, ownerId);
      try (ResultSet rs = stmt.executeQuery()) {
        List<Space> result = new ArrayList<>();
        while (rs.next()) {
          StatusOr<Space> spaceOr = extractSpace(rs);
          if (spaceOr.isNotOk()) {
            return StatusOr.ofStatus(spaceOr.getStatus());
          }
          result.add(spaceOr.getValue());
        }
        return StatusOr.ofValue(ImmutableList.copyOf(result));
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads a space by owner ID and name.
   *
   * @param conn an open JDBC connection
   * @param ownerId the owner user ID
   * @param name the space name
   * @return StatusOr containing an Optional Space or an error
   */
  @Nonnull
  public static StatusOr<Optional<Space>> loadByOwnerAndName(
      Connection conn, UUID ownerId, String name) {
    String sql =
        """
        SELECT space_id, owner_id, name, labels, embedder_id, public_read,
               created_at, updated_at, created_by_id, updated_by_id
          FROM space
         WHERE owner_id = ? AND name = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, ownerId);
      stmt.setString(2, name);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          StatusOr<Space> spaceOr = extractSpace(rs);
          if (spaceOr.isNotOk()) {
            return StatusOr.ofStatus(spaceOr.getStatus());
          }
          return StatusOr.ofValue(Optional.of(spaceOr.getValue()));
        }
        return StatusOr.ofValue(Optional.empty());
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Inserts or updates a space row (upsert).
   *
   * @param conn an open JDBC connection
   * @param space the Space object to save
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> save(Connection conn, Space space) {
    String sql =
        """
        INSERT INTO space
               (space_id, owner_id, name, labels, embedder_id, public_read,
                created_at, updated_at, created_by_id, updated_by_id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(space_id)
        DO UPDATE SET owner_id       = excluded.owner_id,
                      name           = excluded.name,
                      labels         = excluded.labels,
                      embedder_id    = excluded.embedder_id,
                      public_read    = excluded.public_read,
                      updated_at     = excluded.updated_at,
                      updated_by_id  = excluded.updated_by_id
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, space.spaceId());
      stmt.setObject(2, space.ownerId());
      stmt.setString(3, space.name());

      // Process and set the labels as JSONB
      Status labelsStatus = DbUtil.setJsonbParameter(stmt, 4, space.labels());
      if (!labelsStatus.isOk()) {
        return StatusOr.ofStatus(labelsStatus);
      }

      stmt.setObject(5, space.embedderId());
      stmt.setBoolean(6, space.publicRead());
      stmt.setTimestamp(7, DbUtil.toSqlTimestamp(space.createdAt()));
      stmt.setTimestamp(8, DbUtil.toSqlTimestamp(space.updatedAt()));
      stmt.setObject(9, space.createdById());
      stmt.setObject(10, space.updatedById());

      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Deletes a space by ID.
   *
   * @param conn an open JDBC connection
   * @param spaceId the UUID of the space to delete
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> delete(Connection conn, UUID spaceId) {
    String sql =
        """
        DELETE FROM space
         WHERE space_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, spaceId);
      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /** Extracts a Space from the current row of a ResultSet. */
  @Nonnull
  private static StatusOr<Space> extractSpace(ResultSet rs) throws SQLException {
    StatusOr<UUID> spaceIdOr = DbUtil.getUuid(rs, "space_id");
    if (spaceIdOr.isNotOk()) {
      return StatusOr.ofStatus(spaceIdOr.getStatus());
    }

    StatusOr<UUID> ownerIdOr = DbUtil.getUuid(rs, "owner_id");
    if (ownerIdOr.isNotOk()) {
      return StatusOr.ofStatus(ownerIdOr.getStatus());
    }

    String name = rs.getString("name");
    
    StatusOr<UUID> embedderIdOr = DbUtil.getUuid(rs, "embedder_id");
    if (embedderIdOr.isNotOk()) {
      return StatusOr.ofStatus(embedderIdOr.getStatus());
    }
    
    boolean publicRead = rs.getBoolean("public_read");

    // Parse the JSONB labels
    StatusOr<Map<String, String>> labelsOr = DbUtil.parseJsonbToMap(rs, "labels");
    if (labelsOr.isNotOk()) {
      return StatusOr.ofStatus(labelsOr.getStatus());
    }
    Map<String, String> labels = labelsOr.getValue();

    StatusOr<Instant> createdAtOr = DbUtil.getInstant(rs, "created_at");
    if (createdAtOr.isNotOk()) {
      return StatusOr.ofStatus(createdAtOr.getStatus());
    }

    StatusOr<Instant> updatedAtOr = DbUtil.getInstant(rs, "updated_at");
    if (updatedAtOr.isNotOk()) {
      return StatusOr.ofStatus(updatedAtOr.getStatus());
    }

    StatusOr<UUID> createdByIdOr = DbUtil.getUuid(rs, "created_by_id");
    if (createdByIdOr.isNotOk()) {
      return StatusOr.ofStatus(createdByIdOr.getStatus());
    }

    StatusOr<UUID> updatedByIdOr = DbUtil.getUuid(rs, "updated_by_id");
    if (updatedByIdOr.isNotOk()) {
      return StatusOr.ofStatus(updatedByIdOr.getStatus());
    }

    return StatusOr.ofValue(
        new Space(
            spaceIdOr.getValue(),
            ownerIdOr.getValue(),
            name,
            labels,
            embedderIdOr.getValue(),
            publicRead,
            createdAtOr.getValue(),
            updatedAtOr.getValue(),
            createdByIdOr.getValue(),
            updatedByIdOr.getValue()));
  }
  
  /**
   * Query spaces with filtering, sorting, and pagination.
   *
   * @param conn An open JDBC connection
   * @param ownerId Optional filter by owner ID
   * @param labelSelectors Optional filter by labels (all keys and values must match)
   * @param namePattern Optional glob-style pattern for name matching
   * @param sortBy Field to sort by (created_at, updated_at, name, etc.)
   * @param sortAscending Whether to sort in ascending (true) or descending (false) order
   * @param offset Starting position for pagination
   * @param limit Maximum number of results to return
   * @param includePublic Whether to include spaces with public_read=true
   * @param userId User ID to restrict results to (for permission filtering)
   * @return StatusOr containing the list of spaces and total count, or an error
   */
  @Nonnull
  public static StatusOr<QueryResult> querySpaces(
      Connection conn,
      UUID ownerId,
      Map<String, String> labelSelectors,
      String namePattern,
      String sortBy,
      boolean sortAscending,
      int offset,
      int limit,
      boolean includePublic,
      UUID userId) {
    
    StringBuilder sqlBuilder = new StringBuilder();
    List<Object> params = new ArrayList<>();
    int paramIndex = 1;
    
    // Build the base query
    sqlBuilder.append(
        """
        SELECT space_id, owner_id, name, labels, embedder_id, public_read,
               created_at, updated_at, created_by_id, updated_by_id
          FROM space
         WHERE 1=1
        """);
    
    // Apply owner filter if specified
    if (ownerId != null) {
      sqlBuilder.append(" AND owner_id = ?");
      params.add(ownerId);
      paramIndex++;
    }
    
    // Apply name pattern filter if specified
    if (namePattern != null && !namePattern.isEmpty()) {
      sqlBuilder.append(" AND name ILIKE ? ESCAPE '\\'");
      params.add(namePattern);
      paramIndex++;
    }
    
    // Apply label selector filters if specified
    if (labelSelectors != null && !labelSelectors.isEmpty()) {
      for (Map.Entry<String, String> entry : labelSelectors.entrySet()) {
        // Add a condition for each label key-value pair
        // Check if the key exists and has the specified value
        sqlBuilder.append(" AND labels->? = to_jsonb(?::text)");
        params.add(entry.getKey());
        params.add(entry.getValue());
        paramIndex += 2;
      }
    }
    
    // Apply permission-based filtering
    if (includePublic) {
      // Include spaces where the user is the owner OR public_read is true
      sqlBuilder.append(" AND (owner_id = ? OR public_read = true)");
      params.add(userId);
      paramIndex++;
    } else {
      // Only include spaces where the user is the owner
      sqlBuilder.append(" AND owner_id = ?");
      params.add(userId);
      paramIndex++;
    }
    
    // Count query (for total results)
    String countSql = "SELECT COUNT(*) FROM (" + sqlBuilder + ") AS filtered_spaces";
    
    // Add sorting and pagination to the main query
    if (sortBy != null && !sortBy.isEmpty()) {
      sortBy = sanitizeSortField(sortBy); // Sanitize the sort field to prevent SQL injection
      sqlBuilder.append(" ORDER BY ").append(sortBy).append(sortAscending ? " ASC" : " DESC");
      
      // Add secondary sort by space_id for stable pagination
      sqlBuilder.append(", space_id ").append(sortAscending ? "ASC" : "DESC");
    } else {
      // Default sort by created_at descending if not specified
      sqlBuilder.append(" ORDER BY created_at DESC, space_id DESC");
    }
    
    // Add pagination
    sqlBuilder.append(" LIMIT ? OFFSET ?");
    params.add(limit);
    params.add(offset);
    paramIndex += 2;
    
    // Execute both queries (count and data)
    try {
      // First, get the total count
      long totalCount = 0;
      try (PreparedStatement countStmt = conn.prepareStatement(countSql)) {
        // Set parameters for count query
        for (int i = 0; i < params.size() - 2; i++) { // Exclude LIMIT and OFFSET parameters
          countStmt.setObject(i + 1, params.get(i));
        }
        
        try (ResultSet countRs = countStmt.executeQuery()) {
          if (countRs.next()) {
            totalCount = countRs.getLong(1);
          }
        }
      }
      
      // Then, get the actual data
      List<Space> spaces = new ArrayList<>();
      try (PreparedStatement dataStmt = conn.prepareStatement(sqlBuilder.toString())) {
        // Set parameters for data query
        for (int i = 0; i < params.size(); i++) {
          dataStmt.setObject(i + 1, params.get(i));
        }
        
        try (ResultSet rs = dataStmt.executeQuery()) {
          while (rs.next()) {
            StatusOr<Space> spaceOr = extractSpace(rs);
            if (spaceOr.isNotOk()) {
              return StatusOr.ofStatus(spaceOr.getStatus());
            }
            spaces.add(spaceOr.getValue());
          }
        }
      }
      
      return StatusOr.ofValue(new QueryResult(spaces, totalCount));
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }
  
  /**
   * Sanitizes the sort field to prevent SQL injection.
   * Only allows known valid sort fields.
   *
   * @param sortField The user-provided sort field
   * @return A sanitized version of the sort field, or a default sort field if invalid
   */
  private static String sanitizeSortField(String sortField) {
    // Define allowed sort fields
    Map<String, String> allowedFields = new HashMap<>();
    allowedFields.put("name", "name");
    allowedFields.put("created_at", "created_at");
    allowedFields.put("updated_at", "updated_at");
    allowedFields.put("created_time", "created_at"); // Alias for created_at to match API
    allowedFields.put("embedder_id", "embedder_id");
    allowedFields.put("public_read", "public_read");
    
    // Return the matching field or default to created_at
    return allowedFields.getOrDefault(sortField.toLowerCase(), "created_at");
  }
  
  /**
   * Container for query results with pagination information.
   */
  public static class QueryResult {
    private final List<Space> spaces;
    private final long totalCount;
    
    public QueryResult(List<Space> spaces, long totalCount) {
      this.spaces = ImmutableList.copyOf(spaces);
      this.totalCount = totalCount;
    }
    
    public List<Space> getSpaces() {
      return spaces;
    }
    
    public long getTotalCount() {
      return totalCount;
    }
    
    /**
     * Determines if there are more results beyond this page.
     *
     * @param offset The starting position of this page
     * @param limit The maximum number of results per page
     * @return True if there are more results beyond this page
     */
    public boolean hasMore(int offset, int limit) {
      return offset + spaces.size() < totalCount;
    }
    
    /**
     * Gets the next offset position for pagination.
     *
     * @param offset The current offset
     * @param limit The current limit
     * @return The next offset position, or -1 if there are no more results
     */
    public int getNextOffset(int offset, int limit) {
      if (hasMore(offset, limit)) {
        return offset + Math.min(limit, spaces.size());
      }
      return -1;
    }
  }
}
