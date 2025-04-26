package com.goodmem.db;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import com.goodmem.db.util.DbUtil;
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
import java.util.Optional;
import java.util.UUID;

/**
 * DAO helper class for the 'user' table.
 */
public final class Users {

    private Users() {
        // Utility class
    }

    /**
     * Loads all users.
     *
     * @param conn an open JDBC connection
     * @return StatusOr containing a list of User objects or an error
     */
    @Nonnull
    public static StatusOr<List<User>> loadAll(Connection conn) {
        String sql = """
                SELECT user_id, username, email, display_name, created_at, updated_at
                  FROM "user"
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            List<User> result = new ArrayList<>();
            while (rs.next()) {
                StatusOr<User> userOr = extractUser(rs);
                if (userOr.isNotOk()) {
                    return StatusOr.ofStatus(userOr.getStatus());
                }
                result.add(userOr.getValue());
            }
            return StatusOr.ofValue(ImmutableList.copyOf(result));
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Loads a single user by ID.
     *
     * @param conn an open JDBC connection
     * @param userId the UUID of the user to load
     * @return StatusOr containing an Optional User or an error
     */
    @Nonnull
    public static StatusOr<Optional<User>> loadById(Connection conn, UUID userId) {
        String sql = """
                SELECT user_id, username, email, display_name, created_at, updated_at
                  FROM "user"
                 WHERE user_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId); // Use setObject for UUID
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StatusOr<User> userOr = extractUser(rs);
                    if (userOr.isNotOk()) {
                        return StatusOr.ofStatus(userOr.getStatus());
                    }
                    return StatusOr.ofValue(Optional.of(userOr.getValue()));
                }
                return StatusOr.ofValue(Optional.empty());
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Loads a single user by email.
     *
     * @param conn an open JDBC connection
     * @param email the email of the user to load
     * @return StatusOr containing an Optional User or an error
     */
    @Nonnull
    public static StatusOr<Optional<User>> loadByEmail(Connection conn, String email) {
        String sql = """
                SELECT user_id, username, email, display_name, created_at, updated_at
                  FROM "user"
                 WHERE email = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StatusOr<User> userOr = extractUser(rs);
                    if (userOr.isNotOk()) {
                        return StatusOr.ofStatus(userOr.getStatus());
                    }
                    return StatusOr.ofValue(Optional.of(userOr.getValue()));
                }
                return StatusOr.ofValue(Optional.empty());
            }
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Inserts or updates a user row (upsert).
     *
     * @param conn an open JDBC connection
     * @param user the User object to save
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> save(Connection conn, User user) {
        String sql = """
                INSERT INTO "user"
                       (user_id, username, email, display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id)
                DO UPDATE SET username     = excluded.username,
                              email        = excluded.email,
                              display_name = excluded.display_name,
                              updated_at   = excluded.updated_at
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, user.userId()); // Use setObject for UUID
            stmt.setString(2, user.username());
            stmt.setString(3, user.email());
            stmt.setString(4, user.displayName());
            stmt.setTimestamp(5, DbUtil.toSqlTimestamp(user.createdAt()));
            stmt.setTimestamp(6, DbUtil.toSqlTimestamp(user.updatedAt()));
            
            int rowsAffected = stmt.executeUpdate();
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Deletes a user by ID.
     *
     * @param conn an open JDBC connection
     * @param userId the UUID of the user to delete
     * @return StatusOr containing the number of affected rows or an error
     */
    @Nonnull
    public static StatusOr<Integer> delete(Connection conn, UUID userId) {
        String sql = """
                DELETE FROM "user"
                 WHERE user_id = ?
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, userId); // Use setObject for UUID
            int rowsAffected = stmt.executeUpdate();
            return StatusOr.ofValue(rowsAffected);
        } catch (SQLException e) {
            return StatusOr.ofException(e);
        }
    }

    /**
     * Extracts a User from the current row of a ResultSet.
     */
    @Nonnull
    private static StatusOr<User> extractUser(ResultSet rs) throws SQLException {
        StatusOr<UUID> userIdOr = DbUtil.getUuid(rs, "user_id");
        if (userIdOr.isNotOk()) {
            return StatusOr.ofStatus(userIdOr.getStatus());
        }

        String username = rs.getString("username");
        String email = rs.getString("email");
        String displayName = rs.getString("display_name");

        StatusOr<Instant> createdAtOr = DbUtil.getInstant(rs, "created_at");
        if (createdAtOr.isNotOk()) {
            return StatusOr.ofStatus(createdAtOr.getStatus());
        }

        StatusOr<Instant> updatedAtOr = DbUtil.getInstant(rs, "updated_at");
        if (updatedAtOr.isNotOk()) {
            return StatusOr.ofStatus(updatedAtOr.getStatus());
        }

        return StatusOr.ofValue(new User(
                userIdOr.getValue(),
                username,
                email,
                displayName,
                createdAtOr.getValue(),
                updatedAtOr.getValue()
        ));
    }
}