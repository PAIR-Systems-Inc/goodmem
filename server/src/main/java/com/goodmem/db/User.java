package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a row in the 'user' table.
 *
 * @param userId The unique identifier of the user
 * @param username The username (optional)
 * @param email The email address
 * @param displayName The display name (optional)
 * @param createdAt Timestamp when the record was created
 * @param updatedAt Timestamp when the record was last updated
 */
public record User(
        UUID userId,
        String username,
        String email,
        String displayName,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Converts this database record to its corresponding Protocol Buffer message.
     *
     * @return The Protocol Buffer User message
     */
    public goodmem.v1.UserOuterClass.User toProto() {
        return goodmem.v1.UserOuterClass.User.newBuilder()
                .setUserId(UuidUtil.toProtoBytes(userId))
                .setEmail(email)
                .setUsername(username != null ? username : "")
                .setDisplayName(displayName != null ? displayName : "")
                .setCreatedAt(DbUtil.toProtoTimestamp(createdAt))
                .setUpdatedAt(DbUtil.toProtoTimestamp(updatedAt))
                .build();
    }
}