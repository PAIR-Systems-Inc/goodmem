package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;
import com.google.protobuf.ByteString;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a row in the 'apikey' table.
 *
 * @param apiKeyId The unique identifier of the API key
 * @param userId The user ID this API key belongs to
 * @param keyPrefix The prefix of the API key for display purposes
 * @param keyHash The hash of the API key (not returned in responses)
 * @param status The status of the API key (e.g., "ACTIVE", "INACTIVE")
 * @param labels User-defined labels for the API key
 * @param expiresAt Optional expiration time for the API key
 * @param lastUsedAt Optional timestamp of the last usage
 * @param createdAt Timestamp when the record was created
 * @param updatedAt Timestamp when the record was last updated
 * @param createdById User ID who created this API key
 * @param updatedById User ID who last updated this API key
 */
public record ApiKey(
        UUID apiKeyId,
        UUID userId,
        String keyPrefix,
        ByteString keyHash,
        String status,
        Map<String, String> labels,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID createdById,
        UUID updatedById
) {
    /**
     * Converts this database record to its corresponding Protocol Buffer message.
     *
     * @return The Protocol Buffer ApiKey message
     */
    public goodmem.v1.Apikey.ApiKey toProto() {
        goodmem.v1.Apikey.ApiKey.Builder builder = goodmem.v1.Apikey.ApiKey.newBuilder()
                .setApiKeyId(UuidUtil.toProtoBytes(apiKeyId))
                .setUserId(UuidUtil.toProtoBytes(userId))
                .setKeyPrefix(keyPrefix)
                .setCreatedAt(DbUtil.toProtoTimestamp(createdAt))
                .setUpdatedAt(DbUtil.toProtoTimestamp(updatedAt))
                .setCreatedById(UuidUtil.toProtoBytes(createdById))
                .setUpdatedById(UuidUtil.toProtoBytes(updatedById));

        // Set status enum
        if (status != null) {
            switch (status.toUpperCase()) {
                case "ACTIVE":
                    builder.setStatus(goodmem.v1.Apikey.Status.ACTIVE);
                    break;
                case "INACTIVE":
                    builder.setStatus(goodmem.v1.Apikey.Status.INACTIVE);
                    break;
                default:
                    builder.setStatus(goodmem.v1.Apikey.Status.STATUS_UNSPECIFIED);
                    break;
            }
        }

        // Add optional fields if present
        if (labels != null) {
            builder.putAllLabels(labels);
        }
        
        if (expiresAt != null) {
            builder.setExpiresAt(DbUtil.toProtoTimestamp(expiresAt));
        }
        
        if (lastUsedAt != null) {
            builder.setLastUsedAt(DbUtil.toProtoTimestamp(lastUsedAt));
        }

        return builder.build();
    }
}