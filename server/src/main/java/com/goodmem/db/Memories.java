package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.Status;
import com.goodmem.db.util.StatusOr;
import com.goodmem.db.util.UuidUtil;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * DAO helper class for the 'memory' table.
 */
public final class Memories {

    private Memories() {
        // Utility class
    }

    /**
     * Loads all memories.
     *
     * @param conn an open JDBC connection
     * @return StatusOr containing a list of Memory objects or an error
     */
    @Nonnull
    public static StatusOr<List<Memory>> loadAll(Connection conn) {
        String sql = """
                SELECT memory_id, space_id, original_content_ref, content_type, metadata,
                       processing_status, created_at, updated_at, created_by_id, updated_by_id
                  FROM memory
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<Memory> result = new ArrayList<>();
            while (rs.next()) {
                StatusOr<Memory> memoryOr = extractMemory(rs);
                if (memoryOr.isNotOk()) {
                    return StatusOr.ofStatus(memoryOr.getStatus());
                }
                result.add(memoryOr.getValue());
            }
            return StatusOr.ofValue(ImmutableList.copyOf(result));
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Loads a single memory by ID.
     *
     * @param conn an open JDBC connection
     * @param memoryId the UUID of the memory to load
     * @return StatusOr containing an Optional Memory or an error
     */
    @Nonnull
    public static StatusOr<Optional<Memory>> loadById(Connection conn, UUID memoryId) {
        String sql = """
                SELECT memory_id, space_id, original_content_ref, content_type, metadata,
                       processing_status, created_at, updated_at, created_by_id, updated_by_id
                  FROM memory
                 WHERE memory_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, memoryId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StatusOr<Memory> memoryOr = extractMemory(rs);
                    if (memoryOr.isNotOk()) {
                        return StatusOr.ofStatus(memoryOr.getStatus());
                    }
                    return StatusOr.ofValue(Optional.of(memoryOr.getValue()));
                }
                return StatusOr.ofValue(Optional.empty());
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Loads memories by space ID.
     *
     * @param conn an open JDBC connection
     * @param spaceId the space ID to load memories for
     * @return StatusOr containing a list of Memory objects or an error
     */
    @Nonnull
    public static StatusOr<List<Memory>> loadBySpaceId(Connection conn, UUID spaceId) {
        String sql = """
                SELECT memory_id, space_id, original_content_ref, content_type, metadata,
                       processing_status, created_at, updated_at, created_by_id, updated_by_id
                  FROM memory
                 WHERE space_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, spaceId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Memory> result = new ArrayList<>();
                while (rs.next()) {
                    StatusOr<Memory> memoryOr = extractMemory(rs);
                    if (memoryOr.isNotOk()) {
                        return StatusOr.ofStatus(memoryOr.getStatus());
                    }
                    result.add(memoryOr.getValue());
                }
                return StatusOr.ofValue(ImmutableList.copyOf(result));
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Loads memories by processing status.
     *
     * @param conn an open JDBC connection
     * @param processingStatus the processing status to filter by
     * @return StatusOr containing a list of Memory objects or an error
     */
    @Nonnull
    public static StatusOr<List<Memory>> loadByProcessingStatus(Connection conn, String processingStatus) {
        String sql = """
                SELECT memory_id, space_id, original_content_ref, content_type, metadata,
                       processing_status, created_at, updated_at, created_by_id, updated_by_id
                  FROM memory
                 WHERE processing_status = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, processingStatus);
            try (ResultSet rs = stmt.executeQuery()) {
                List<Memory> result = new ArrayList<>();
                while (rs.next()) {
                    StatusOr<Memory> memoryOr = extractMemory(rs);
                    if (memoryOr.isNotOk()) {
                        return StatusOr.ofStatus(memoryOr.getStatus());
                    }
                    result.add(memoryOr.getValue());
                }
                return StatusOr.ofValue(ImmutableList.copyOf(result));
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Inserts or updates a memory row (upsert).
     *
     * @param conn an open JDBC connection
     * @param memory the Memory object to save
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> save(Connection conn, Memory memory) {
        String sql = """
                INSERT INTO memory
                       (memory_id, space_id, original_content_ref, content_type, metadata,
                        processing_status, created_at, updated_at, created_by_id, updated_by_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(memory_id)
                DO UPDATE SET space_id           = excluded.space_id,
                              original_content_ref = excluded.original_content_ref,
                              content_type       = excluded.content_type,
                              metadata           = excluded.metadata,
                              processing_status  = excluded.processing_status,
                              updated_at         = excluded.updated_at,
                              updated_by_id      = excluded.updated_by_id
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, memory.memoryId());
            stmt.setObject(2, memory.spaceId());
            stmt.setString(3, memory.originalContentRef());
            stmt.setString(4, memory.contentType());
            
            // Note: In a real implementation, this would use proper JSONB handling
            // For example: stmt.setObject(5, memory.metadata(), Types.OTHER);
            stmt.setObject(5, null); // Placeholder for JSONB
            
            stmt.setString(6, memory.processingStatus());
            stmt.setTimestamp(7, DbUtil.toSqlTimestamp(memory.createdAt()));
            stmt.setTimestamp(8, DbUtil.toSqlTimestamp(memory.updatedAt()));
            stmt.setObject(9, memory.createdById());
            stmt.setObject(10, memory.updatedById());
            
            int rowsAffected = stmt.executeUpdate();
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Updates the processing status of a memory.
     *
     * @param conn an open JDBC connection
     * @param memoryId the UUID of the memory to update
     * @param processingStatus the new processing status
     * @param updatedById the UUID of the user making the update
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> updateProcessingStatus(
            Connection conn, UUID memoryId, String processingStatus, UUID updatedById) {
        String sql = """
                UPDATE memory
                   SET processing_status = ?,
                       updated_by_id = ?,
                       updated_at = now()
                 WHERE memory_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, processingStatus);
            stmt.setObject(2, updatedById);
            stmt.setObject(3, memoryId);
            int rowsAffected = stmt.executeUpdate();
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Deletes a memory by ID.
     *
     * @param conn an open JDBC connection
     * @param memoryId the UUID of the memory to delete
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> delete(Connection conn, UUID memoryId) {
        String sql = """
                DELETE FROM memory
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

    /**
     * Extracts a Memory from the current row of a ResultSet.
     */
    @Nonnull
    private static StatusOr<Memory> extractMemory(ResultSet rs) throws SQLException {
        StatusOr<UUID> memoryIdOr = DbUtil.getUuid(rs, "memory_id");
        if (memoryIdOr.isNotOk()) {
            return StatusOr.ofStatus(memoryIdOr.getStatus());
        }

        StatusOr<UUID> spaceIdOr = DbUtil.getUuid(rs, "space_id");
        if (spaceIdOr.isNotOk()) {
            return StatusOr.ofStatus(spaceIdOr.getStatus());
        }

        String originalContentRef = rs.getString("original_content_ref");
        String contentType = rs.getString("content_type");
        String processingStatus = rs.getString("processing_status");

        // Note: In a real implementation, you would use proper JSONB parsing
        // For example:
        // StatusOr<Map<String, String>> metadataOr = DbUtil.parseJsonbToMap(rs, "metadata");
        Map<String, String> metadata = Map.of(); // Placeholder for JSONB

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

        return StatusOr.ofValue(new Memory(
                memoryIdOr.getValue(),
                spaceIdOr.getValue(),
                originalContentRef,
                contentType,
                metadata,
                processingStatus,
                createdAtOr.getValue(),
                updatedAtOr.getValue(),
                createdByIdOr.getValue(),
                updatedByIdOr.getValue()
        ));
    }
}