package com.goodmem.db;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

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
 * DAO helper class for the 'apikey' table.
 */
public final class ApiKeys {

    private ApiKeys() {
        // Utility class
    }

    /**
     * Loads all API keys.
     *
     * @param conn an open JDBC connection
     * @return StatusOr containing a list of ApiKey objects or an error
     */
    @Nonnull
    public static StatusOr<List<ApiKey>> loadAll(Connection conn) {
        String sql = """
                SELECT api_key_id, user_id, key_prefix, key_hash, status, labels,
                       expires_at, last_used_at, created_at, updated_at,
                       created_by_id, updated_by_id
                  FROM apikey
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<ApiKey> result = new ArrayList<>();
            while (rs.next()) {
                StatusOr<ApiKey> apiKeyOr = extractApiKey(rs);
                if (apiKeyOr.isNotOk()) {
                    return StatusOr.ofStatus(apiKeyOr.getStatus());
                }
                result.add(apiKeyOr.getValue());
            }
            return StatusOr.ofValue(ImmutableList.copyOf(result));
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Loads a single API key by ID.
     *
     * @param conn an open JDBC connection
     * @param apiKeyId the UUID of the API key to load
     * @return StatusOr containing an Optional ApiKey or an error
     */
    @Nonnull
    public static StatusOr<Optional<ApiKey>> loadById(Connection conn, UUID apiKeyId) {
        String sql = """
                SELECT api_key_id, user_id, key_prefix, key_hash, status, labels,
                       expires_at, last_used_at, created_at, updated_at,
                       created_by_id, updated_by_id
                  FROM apikey
                 WHERE api_key_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, apiKeyId); // Use setObject for UUID
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StatusOr<ApiKey> apiKeyOr = extractApiKey(rs);
                    if (apiKeyOr.isNotOk()) {
                        return StatusOr.ofStatus(apiKeyOr.getStatus());
                    }
                    return StatusOr.ofValue(Optional.of(apiKeyOr.getValue()));
                }
                return StatusOr.ofValue(Optional.empty());
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Loads all API keys for a specific user.
     *
     * @param conn an open JDBC connection
     * @param userId the user ID to load API keys for
     * @return StatusOr containing a list of ApiKey objects or an error
     */
    @Nonnull
    public static StatusOr<List<ApiKey>> loadByUserId(Connection conn, UUID userId) {
        String sql = """
                SELECT api_key_id, user_id, key_prefix, key_hash, status, labels,
                       expires_at, last_used_at, created_at, updated_at,
                       created_by_id, updated_by_id
                  FROM apikey
                 WHERE user_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId); // Use setObject for UUID
            try (ResultSet rs = stmt.executeQuery()) {
                List<ApiKey> result = new ArrayList<>();
                while (rs.next()) {
                    StatusOr<ApiKey> apiKeyOr = extractApiKey(rs);
                    if (apiKeyOr.isNotOk()) {
                        return StatusOr.ofStatus(apiKeyOr.getStatus());
                    }
                    result.add(apiKeyOr.getValue());
                }
                return StatusOr.ofValue(ImmutableList.copyOf(result));
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Finds an API key by its hash value.
     *
     * @param conn an open JDBC connection
     * @param keyHash the hash of the API key to find
     * @return StatusOr containing an Optional ApiKey or an error
     */
    @Nonnull
    public static StatusOr<Optional<ApiKey>> loadByKeyHash(Connection conn, String keyHash) {
        String sql = """
                SELECT api_key_id, user_id, key_prefix, key_hash, status, labels,
                       expires_at, last_used_at, created_at, updated_at,
                       created_by_id, updated_by_id
                  FROM apikey
                 WHERE key_hash = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, keyHash);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StatusOr<ApiKey> apiKeyOr = extractApiKey(rs);
                    if (apiKeyOr.isNotOk()) {
                        return StatusOr.ofStatus(apiKeyOr.getStatus());
                    }
                    return StatusOr.ofValue(Optional.of(apiKeyOr.getValue()));
                }
                return StatusOr.ofValue(Optional.empty());
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Inserts or updates an API key row (upsert).
     *
     * @param conn an open JDBC connection
     * @param apiKey the ApiKey object to save
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> save(Connection conn, ApiKey apiKey) {
        System.out.println("ApiKeys.save: Starting save operation for apiKey=" + apiKey.apiKeyId());
        String sql = """
                INSERT INTO apikey
                       (api_key_id, user_id, key_prefix, key_hash, status, labels,
                        expires_at, last_used_at, created_at, updated_at,
                        created_by_id, updated_by_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(api_key_id)
                DO UPDATE SET user_id       = excluded.user_id,
                              key_prefix    = excluded.key_prefix,
                              key_hash      = excluded.key_hash,
                              status        = excluded.status,
                              labels        = excluded.labels,
                              expires_at    = excluded.expires_at,
                              last_used_at  = excluded.last_used_at,
                              updated_at    = excluded.updated_at,
                              updated_by_id = excluded.updated_by_id
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            System.out.println("ApiKeys.save: Setting parameters:");
            
            System.out.println("  1. api_key_id = " + apiKey.apiKeyId());
            stmt.setObject(1, apiKey.apiKeyId());
            
            System.out.println("  2. user_id = " + apiKey.userId());
            stmt.setObject(2, apiKey.userId());
            
            System.out.println("  3. key_prefix = " + apiKey.keyPrefix());
            stmt.setString(3, apiKey.keyPrefix());
            
            System.out.println("  4. key_hash = " + apiKey.keyHash());
            stmt.setBytes(4, apiKey.keyHash().toByteArray());
            
            System.out.println("  5. status = " + apiKey.status());
            stmt.setString(5, apiKey.status());
            
            // Note: In a real implementation, this would use proper JSONB handling
            System.out.println("  6. labels = (null placeholder)");
            stmt.setObject(6, null); // Placeholder for JSONB
            
            if (apiKey.expiresAt() != null) {
                System.out.println("  7. expires_at = " + apiKey.expiresAt());
                stmt.setTimestamp(7, DbUtil.toSqlTimestamp(apiKey.expiresAt()));
            } else {
                System.out.println("  7. expires_at = NULL");
                stmt.setNull(7, java.sql.Types.TIMESTAMP);
            }
            
            if (apiKey.lastUsedAt() != null) {
                System.out.println("  8. last_used_at = " + apiKey.lastUsedAt());
                stmt.setTimestamp(8, DbUtil.toSqlTimestamp(apiKey.lastUsedAt()));
            } else {
                System.out.println("  8. last_used_at = NULL");
                stmt.setNull(8, java.sql.Types.TIMESTAMP);
            }
            
            System.out.println("  9. created_at = " + apiKey.createdAt());
            stmt.setTimestamp(9, DbUtil.toSqlTimestamp(apiKey.createdAt()));
            
            System.out.println("  10. updated_at = " + apiKey.updatedAt());
            stmt.setTimestamp(10, DbUtil.toSqlTimestamp(apiKey.updatedAt()));
            
            System.out.println("  11. created_by_id = " + apiKey.createdById());
            stmt.setObject(11, apiKey.createdById());
            
            System.out.println("  12. updated_by_id = " + apiKey.updatedById());
            stmt.setObject(12, apiKey.updatedById());
            
            System.out.println("ApiKeys.save: Executing SQL statement...");
            int rowsAffected = stmt.executeUpdate();
            System.out.println("ApiKeys.save: SQL executed, rowsAffected=" + rowsAffected);
            
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            System.err.println("ApiKeys.save: SQL Exception: " + e.getMessage());
            e.printStackTrace();
            return StatusOr.ofException(e);
        }
    }

    /**
     * Updates the last_used_at timestamp for an API key.
     *
     * @param conn an open JDBC connection
     * @param apiKeyId the UUID of the API key to update
     * @param lastUsedAt the new last_used_at timestamp
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> updateLastUsed(Connection conn, UUID apiKeyId, Instant lastUsedAt) {
        System.out.println("ApiKeys.updateLastUsed: Starting update for apiKeyId=" + apiKeyId);
        String sql = """
                UPDATE apikey
                   SET last_used_at = ?,
                       updated_at = now()
                 WHERE api_key_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            System.out.println("ApiKeys.updateLastUsed: Setting parameters:");
            System.out.println("  1. last_used_at = " + lastUsedAt);
            stmt.setTimestamp(1, DbUtil.toSqlTimestamp(lastUsedAt));
            
            System.out.println("  2. api_key_id = " + apiKeyId);
            stmt.setObject(2, apiKeyId);
            
            System.out.println("ApiKeys.updateLastUsed: Executing SQL...");
            int rowsAffected = stmt.executeUpdate();
            System.out.println("ApiKeys.updateLastUsed: SQL executed, rowsAffected=" + rowsAffected);
            
            // If no rows were affected, let's try to find out why
            if (rowsAffected == 0) {
                System.out.println("ApiKeys.updateLastUsed: No rows were affected, checking if API key exists...");
                String checkSql = "SELECT COUNT(*) FROM apikey WHERE api_key_id = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setObject(1, apiKeyId);
                    try (var rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            int count = rs.getInt(1);
                            System.out.println("ApiKeys.updateLastUsed: Found " + count + " API keys with ID " + apiKeyId);
                        }
                    }
                }
            }
            
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            System.err.println("ApiKeys.updateLastUsed: SQL Exception: " + e.getMessage());
            e.printStackTrace();
            return StatusOr.ofException(e);
        }
    }

    /**
     * Updates the status of an API key.
     *
     * @param conn an open JDBC connection
     * @param apiKeyId the UUID of the API key to update
     * @param status the new status value
     * @param updatedById the UUID of the user making the update
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> updateStatus(
            Connection conn, UUID apiKeyId, String status, UUID updatedById) {
        String sql = """
                UPDATE apikey
                   SET status = ?,
                       updated_by_id = ?,
                       updated_at = now()
                 WHERE api_key_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setObject(2, updatedById);
            stmt.setObject(3, apiKeyId);
            int rowsAffected = stmt.executeUpdate();
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Deletes an API key by ID.
     *
     * @param conn an open JDBC connection
     * @param apiKeyId the UUID of the API key to delete
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> delete(Connection conn, UUID apiKeyId) {
        String sql = """
                DELETE FROM apikey
                 WHERE api_key_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, apiKeyId);
            int rowsAffected = stmt.executeUpdate();
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Extracts an ApiKey from the current row of a ResultSet.
     */
    @Nonnull
    private static StatusOr<ApiKey> extractApiKey(ResultSet rs) throws SQLException {
        StatusOr<UUID> apiKeyIdOr = DbUtil.getUuid(rs, "api_key_id");
        if (apiKeyIdOr.isNotOk()) {
            return StatusOr.ofStatus(apiKeyIdOr.getStatus());
        }

        StatusOr<UUID> userIdOr = DbUtil.getUuid(rs, "user_id");
        if (userIdOr.isNotOk()) {
            return StatusOr.ofStatus(userIdOr.getStatus());
        }

        String keyPrefix = rs.getString("key_prefix");
        ByteString keyHash = ByteString.copyFrom(rs.getBytes("key_hash"));
        String status = rs.getString("status");

        // Note: In a real implementation, you would use proper JSONB parsing
        // For example:
        // StatusOr<Map<String, String>> labelsOr = DbUtil.parseJsonbToMap(rs, "labels");
        Map<String, String> labels = Map.of(); // Placeholder for JSONB

        StatusOr<Optional<Instant>> expiresAtOr = DbUtil.getOptionalInstant(rs, "expires_at");
        if (expiresAtOr.isNotOk()) {
            return StatusOr.ofStatus(expiresAtOr.getStatus());
        }
        Instant expiresAt = expiresAtOr.getValue().orElse(null);

        StatusOr<Optional<Instant>> lastUsedAtOr = DbUtil.getOptionalInstant(rs, "last_used_at");
        if (lastUsedAtOr.isNotOk()) {
            return StatusOr.ofStatus(lastUsedAtOr.getStatus());
        }
        Instant lastUsedAt = lastUsedAtOr.getValue().orElse(null);

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

        return StatusOr.ofValue(new ApiKey(
                apiKeyIdOr.getValue(),
                userIdOr.getValue(),
                keyPrefix,
                keyHash,
                status,
                labels,
                expiresAt,
                lastUsedAt,
                createdAtOr.getValue(),
                updatedAtOr.getValue(),
                createdByIdOr.getValue(),
                updatedByIdOr.getValue()
        ));
    }
}