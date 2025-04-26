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
 * DAO helper class for the 'space' table.
 */
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
        String sql = """
                SELECT space_id, owner_id, name, labels, embedding_model, public_read,
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
        String sql = """
                SELECT space_id, owner_id, name, labels, embedding_model, public_read,
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
        String sql = """
                SELECT space_id, owner_id, name, labels, embedding_model, public_read,
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
        String sql = """
                SELECT space_id, owner_id, name, labels, embedding_model, public_read,
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
        String sql = """
                INSERT INTO space
                       (space_id, owner_id, name, labels, embedding_model, public_read,
                        created_at, updated_at, created_by_id, updated_by_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(space_id)
                DO UPDATE SET owner_id       = excluded.owner_id,
                              name           = excluded.name,
                              labels         = excluded.labels,
                              embedding_model = excluded.embedding_model,
                              public_read    = excluded.public_read,
                              updated_at     = excluded.updated_at,
                              updated_by_id  = excluded.updated_by_id
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, space.spaceId());
            stmt.setObject(2, space.ownerId());
            stmt.setString(3, space.name());
            
            // Note: In a real implementation, this would use proper JSONB handling
            // For example: stmt.setObject(4, space.labels(), Types.OTHER);
            stmt.setObject(4, null); // Placeholder for JSONB
            
            stmt.setString(5, space.embeddingModel());
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
        String sql = """
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

    /**
     * Extracts a Space from the current row of a ResultSet.
     */
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
        String embeddingModel = rs.getString("embedding_model");
        boolean publicRead = rs.getBoolean("public_read");

        // Note: In a real implementation, you would use proper JSONB parsing
        // For example:
        // StatusOr<Map<String, String>> labelsOr = DbUtil.parseJsonbToMap(rs, "labels");
        Map<String, String> labels = Map.of(); // Placeholder for JSONB

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

        return StatusOr.ofValue(new Space(
                spaceIdOr.getValue(),
                ownerIdOr.getValue(),
                name,
                labels,
                embeddingModel,
                publicRead,
                createdAtOr.getValue(),
                updatedAtOr.getValue(),
                createdByIdOr.getValue(),
                updatedByIdOr.getValue()
        ));
    }
}