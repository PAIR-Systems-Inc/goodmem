package com.goodmem.db;

import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.DbUtil;
import com.google.common.collect.ImmutableList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;

/** DAO helper class for the 'memory_chunk' table. */
public final class MemoryChunks {

  private MemoryChunks() {
    // Utility class
  }

  /**
   * Loads all memory chunks.
   *
   * @param conn an open JDBC connection
   * @return StatusOr containing a list of MemoryChunk objects or an error
   */
  @Nonnull
  public static StatusOr<List<MemoryChunk>> loadAll(Connection conn) {
    String sql =
        """
        SELECT chunk_id, memory_id, chunk_sequence_number, chunk_text, embedding_vector,
               vector_status, start_offset, end_offset, created_at, updated_at,
               created_by_id, updated_by_id
          FROM memory_chunk
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = stmt.executeQuery()) {
      List<MemoryChunk> result = new ArrayList<>();
      while (rs.next()) {
        StatusOr<MemoryChunk> chunkOr = extractMemoryChunk(rs);
        if (chunkOr.isNotOk()) {
          return StatusOr.ofStatus(chunkOr.getStatus());
        }
        result.add(chunkOr.getValue());
      }
      return StatusOr.ofValue(ImmutableList.copyOf(result));
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads a single memory chunk by ID.
   *
   * @param conn an open JDBC connection
   * @param chunkId the UUID of the memory chunk to load
   * @return StatusOr containing an Optional MemoryChunk or an error
   */
  @Nonnull
  public static StatusOr<Optional<MemoryChunk>> loadById(Connection conn, UUID chunkId) {
    String sql =
        """
        SELECT chunk_id, memory_id, chunk_sequence_number, chunk_text, embedding_vector,
               vector_status, start_offset, end_offset, created_at, updated_at,
               created_by_id, updated_by_id
          FROM memory_chunk
         WHERE chunk_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, chunkId);
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          StatusOr<MemoryChunk> chunkOr = extractMemoryChunk(rs);
          if (chunkOr.isNotOk()) {
            return StatusOr.ofStatus(chunkOr.getStatus());
          }
          return StatusOr.ofValue(Optional.of(chunkOr.getValue()));
        }
        return StatusOr.ofValue(Optional.empty());
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads memory chunks by memory ID.
   *
   * @param conn an open JDBC connection
   * @param memoryId the memory ID to load chunks for
   * @return StatusOr containing a list of MemoryChunk objects or an error
   */
  @Nonnull
  public static StatusOr<List<MemoryChunk>> loadByMemoryId(Connection conn, UUID memoryId) {
    String sql =
        """
        SELECT chunk_id, memory_id, chunk_sequence_number, chunk_text, embedding_vector,
               vector_status, start_offset, end_offset, created_at, updated_at,
               created_by_id, updated_by_id
          FROM memory_chunk
         WHERE memory_id = ?
         ORDER BY chunk_sequence_number
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, memoryId);
      try (ResultSet rs = stmt.executeQuery()) {
        List<MemoryChunk> result = new ArrayList<>();
        while (rs.next()) {
          StatusOr<MemoryChunk> chunkOr = extractMemoryChunk(rs);
          if (chunkOr.isNotOk()) {
            return StatusOr.ofStatus(chunkOr.getStatus());
          }
          result.add(chunkOr.getValue());
        }
        return StatusOr.ofValue(ImmutableList.copyOf(result));
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Loads memory chunks by vector status.
   *
   * @param conn an open JDBC connection
   * @param vectorStatus the vector status to filter by
   * @param limit optional maximum number of results to return
   * @return StatusOr containing a list of MemoryChunk objects or an error
   */
  @Nonnull
  public static StatusOr<List<MemoryChunk>> loadByVectorStatus(
      Connection conn, String vectorStatus, Integer limit) {
    String sql =
        """
        SELECT chunk_id, memory_id, chunk_sequence_number, chunk_text, embedding_vector,
               vector_status, start_offset, end_offset, created_at, updated_at,
               created_by_id, updated_by_id
          FROM memory_chunk
         WHERE vector_status = ?
         ORDER BY created_at
         LIMIT ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, vectorStatus);
      stmt.setInt(2, limit != null ? limit : Integer.MAX_VALUE);
      try (ResultSet rs = stmt.executeQuery()) {
        List<MemoryChunk> result = new ArrayList<>();
        while (rs.next()) {
          StatusOr<MemoryChunk> chunkOr = extractMemoryChunk(rs);
          if (chunkOr.isNotOk()) {
            return StatusOr.ofStatus(chunkOr.getStatus());
          }
          result.add(chunkOr.getValue());
        }
        return StatusOr.ofValue(ImmutableList.copyOf(result));
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Performs a vector similarity search using pgvector.
   *
   * @param conn an open JDBC connection
   * @param queryVector the query vector
   * @param spaceId the space to search in (optional)
   * @param limit maximum number of results
   * @return StatusOr containing a list of MemoryChunk objects or an error
   */
  @Nonnull
  public static StatusOr<List<MemoryChunk>> vectorSearch(
      Connection conn, float[] queryVector, UUID spaceId, int limit) {
    // For pgvector, we need to use a vector literal in the SQL
    String vectorExpr = "'" + DbUtil.formatVector(queryVector) + "'::vector";

    String sql;
    if (spaceId != null) {
      sql =
          String.format(
              """
SELECT c.chunk_id, c.memory_id, c.chunk_sequence_number, c.chunk_text, c.embedding_vector,
       c.vector_status, c.start_offset, c.end_offset, c.created_at, c.updated_at,
       c.created_by_id, c.updated_by_id
  FROM memory_chunk c
  JOIN memory m ON c.memory_id = m.memory_id
 WHERE m.space_id = ?
   AND c.vector_status = 'GENERATED'
 ORDER BY c.embedding_vector <-> %s
 LIMIT ?
""",
              vectorExpr);
    } else {
      sql =
          String.format(
              """
              SELECT chunk_id, memory_id, chunk_sequence_number, chunk_text, embedding_vector,
                     vector_status, start_offset, end_offset, created_at, updated_at,
                     created_by_id, updated_by_id
                FROM memory_chunk
               WHERE vector_status = 'GENERATED'
               ORDER BY embedding_vector <-> %s
               LIMIT ?
              """,
              vectorExpr);
    }

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      int paramIndex = 1;

      if (spaceId != null) {
        stmt.setObject(paramIndex++, spaceId);
      }

      // Vector is now included in the SQL directly

      stmt.setInt(paramIndex, limit);

      try (ResultSet rs = stmt.executeQuery()) {
        List<MemoryChunk> result = new ArrayList<>();
        while (rs.next()) {
          StatusOr<MemoryChunk> chunkOr = extractMemoryChunk(rs);
          if (chunkOr.isNotOk()) {
            return StatusOr.ofStatus(chunkOr.getStatus());
          }
          result.add(chunkOr.getValue());
        }
        return StatusOr.ofValue(ImmutableList.copyOf(result));
      }
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Inserts or updates a memory chunk (upsert).
   *
   * @param conn an open JDBC connection
   * @param chunk the MemoryChunk object to save
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> save(Connection conn, MemoryChunk chunk) {
    // For pgvector, we need to build a SQL string with the vector cast
    String vectorSql =
        chunk.embeddingVector() != null
            ? "'" + DbUtil.formatVector(chunk.embeddingVector()) + "'::vector"
            : "NULL";

    String sql =
        String.format(
            """
            INSERT INTO memory_chunk
                   (chunk_id, memory_id, chunk_sequence_number, chunk_text, embedding_vector,
                    vector_status, start_offset, end_offset, created_at, updated_at,
                    created_by_id, updated_by_id)
            VALUES (?, ?, ?, ?, %s, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(chunk_id)
            DO UPDATE SET memory_id           = excluded.memory_id,
                          chunk_sequence_number = excluded.chunk_sequence_number,
                          chunk_text          = excluded.chunk_text,
                          embedding_vector    = excluded.embedding_vector,
                          vector_status       = excluded.vector_status,
                          start_offset        = excluded.start_offset,
                          end_offset          = excluded.end_offset,
                          updated_at          = excluded.updated_at,
                          updated_by_id       = excluded.updated_by_id
            """,
            vectorSql);

    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, chunk.chunkId());
      stmt.setObject(2, chunk.memoryId());

      if (chunk.chunkSequenceNumber() != null) {
        stmt.setInt(3, chunk.chunkSequenceNumber());
      } else {
        stmt.setNull(3, Types.INTEGER);
      }

      stmt.setString(4, chunk.chunkText());

      stmt.setString(5, chunk.vectorStatus());

      if (chunk.startOffset() != null) {
        stmt.setInt(6, chunk.startOffset());
      } else {
        stmt.setNull(6, Types.INTEGER);
      }

      if (chunk.endOffset() != null) {
        stmt.setInt(7, chunk.endOffset());
      } else {
        stmt.setNull(7, Types.INTEGER);
      }

      stmt.setTimestamp(8, DbUtil.toSqlTimestamp(chunk.createdAt()));
      stmt.setTimestamp(9, DbUtil.toSqlTimestamp(chunk.updatedAt()));
      stmt.setObject(10, chunk.createdById());
      stmt.setObject(11, chunk.updatedById());

      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Updates the vector status of a memory chunk.
   *
   * @param conn an open JDBC connection
   * @param chunkId the UUID of the memory chunk to update
   * @param vectorStatus the new vector status
   * @param updatedById the UUID of the user making the update
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> updateVectorStatus(
      Connection conn, UUID chunkId, String vectorStatus, UUID updatedById) {
    String sql =
        """
        UPDATE memory_chunk
           SET vector_status = ?,
               updated_by_id = ?,
               updated_at = now()
         WHERE chunk_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setString(1, vectorStatus);
      stmt.setObject(2, updatedById);
      stmt.setObject(3, chunkId);
      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Deletes a memory chunk by ID.
   *
   * @param conn an open JDBC connection
   * @param chunkId the UUID of the memory chunk to delete
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> delete(Connection conn, UUID chunkId) {
    String sql =
        """
        DELETE FROM memory_chunk
         WHERE chunk_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, chunkId);
      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /**
   * Deletes all memory chunks for a given memory.
   *
   * @param conn an open JDBC connection
   * @param memoryId the UUID of the memory to delete chunks for
   * @return StatusOr containing the number of affected rows or an error
   */
  @Nonnull
  public static StatusOr<Integer> deleteByMemoryId(Connection conn, UUID memoryId) {
    String sql =
        """
        DELETE FROM memory_chunk
         WHERE memory_id = ?
        """;
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
      stmt.setObject(1, memoryId);
      int rowsAffected = stmt.executeUpdate();
      return StatusOr.ofValue(rowsAffected);
    } catch (SQLException e) {
      return StatusOr.ofException(e);
    }
  }

  /** Extracts a MemoryChunk from the current row of a ResultSet. */
  @Nonnull
  private static StatusOr<MemoryChunk> extractMemoryChunk(ResultSet rs) throws SQLException {
    StatusOr<UUID> chunkIdOr = DbUtil.getUuid(rs, "chunk_id");
    if (chunkIdOr.isNotOk()) {
      return StatusOr.ofStatus(chunkIdOr.getStatus());
    }

    StatusOr<UUID> memoryIdOr = DbUtil.getUuid(rs, "memory_id");
    if (memoryIdOr.isNotOk()) {
      return StatusOr.ofStatus(memoryIdOr.getStatus());
    }

    Integer chunkSequenceNumber = rs.getInt("chunk_sequence_number");
    if (rs.wasNull()) {
      chunkSequenceNumber = null;
    }

    String chunkText = rs.getString("chunk_text");
    String vectorStatus = rs.getString("vector_status");

    // Extract vector embedding using our utility method
    float[] embeddingVector = null;
    StatusOr<float[]> embeddingVectorOr = DbUtil.getVector(rs, "embedding_vector");
    if (embeddingVectorOr.isOk()) {
      embeddingVector = embeddingVectorOr.getValue();
    }

    Integer startOffset = rs.getInt("start_offset");
    if (rs.wasNull()) {
      startOffset = null;
    }

    Integer endOffset = rs.getInt("end_offset");
    if (rs.wasNull()) {
      endOffset = null;
    }

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
        new MemoryChunk(
            chunkIdOr.getValue(),
            memoryIdOr.getValue(),
            chunkSequenceNumber,
            chunkText,
            embeddingVector,
            vectorStatus,
            startOffset,
            endOffset,
            createdAtOr.getValue(),
            updatedAtOr.getValue(),
            createdByIdOr.getValue(),
            updatedByIdOr.getValue()));
  }
}
