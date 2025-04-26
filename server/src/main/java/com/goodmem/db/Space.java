package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a row in the 'space' table.
 *
 * @param spaceId The unique identifier of the space
 * @param ownerId The user ID of the owner
 * @param name The name of the space
 * @param labels User-defined labels for the space
 * @param embeddingModel The embedding model used by this space
 * @param publicRead Whether this space is public readable
 * @param createdAt Timestamp when the record was created
 * @param updatedAt Timestamp when the record was last updated
 * @param createdById User ID who created this space
 * @param updatedById User ID who last updated this space
 */
public record Space(
        UUID spaceId,
        UUID ownerId,
        String name,
        Map<String, String> labels,
        String embeddingModel,
        boolean publicRead,
        Instant createdAt,
        Instant updatedAt,
        UUID createdById,
        UUID updatedById
) {
    /**
     * Converts this database record to its corresponding Protocol Buffer message.
     *
     * @return The Protocol Buffer Space message
     */
    public goodmem.v1.SpaceOuterClass.Space toProto() {
        goodmem.v1.SpaceOuterClass.Space.Builder builder = goodmem.v1.SpaceOuterClass.Space.newBuilder()
                .setSpaceId(UuidUtil.toProtoBytes(spaceId))
                .setOwnerId(UuidUtil.toProtoBytes(ownerId))
                .setName(name)
                .setEmbeddingModel(embeddingModel)
                .setPublicRead(publicRead)
                .setCreatedAt(DbUtil.toProtoTimestamp(createdAt))
                .setUpdatedAt(DbUtil.toProtoTimestamp(updatedAt))
                .setCreatedById(UuidUtil.toProtoBytes(createdById))
                .setUpdatedById(UuidUtil.toProtoBytes(updatedById));

        // Add labels if present
        if (labels != null) {
            builder.putAllLabels(labels);
        }

        return builder.build();
    }
}