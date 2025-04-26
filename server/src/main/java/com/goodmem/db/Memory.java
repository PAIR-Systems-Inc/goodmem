package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a row in the 'memory' table.
 *
 * @param memoryId The unique identifier of the memory
 * @param spaceId The space ID this memory belongs to
 * @param originalContentRef Reference to the original content
 * @param contentType The content type (e.g., "text/plain", "application/pdf")
 * @param metadata Additional metadata for this memory
 * @param processingStatus The processing status (e.g., "PENDING", "PROCESSING", "COMPLETED", "FAILED")
 * @param createdAt Timestamp when the record was created
 * @param updatedAt Timestamp when the record was last updated
 * @param createdById User ID who created this memory
 * @param updatedById User ID who last updated this memory
 */
public record Memory(
        UUID memoryId,
        UUID spaceId,
        String originalContentRef,
        String contentType,
        Map<String, String> metadata,
        String processingStatus,
        Instant createdAt,
        Instant updatedAt,
        UUID createdById,
        UUID updatedById
) {
    /**
     * Converts this database record to its corresponding Protocol Buffer message.
     *
     * @return The Protocol Buffer Memory message
     */
    public goodmem.v1.MemoryOuterClass.Memory toProto() {
        goodmem.v1.MemoryOuterClass.Memory.Builder builder = goodmem.v1.MemoryOuterClass.Memory.newBuilder()
                .setMemoryId(UuidUtil.toProtoBytes(memoryId))
                .setSpaceId(UuidUtil.toProtoBytes(spaceId))
                .setCreatedAt(DbUtil.toProtoTimestamp(createdAt))
                .setUpdatedAt(DbUtil.toProtoTimestamp(updatedAt))
                .setCreatedById(UuidUtil.toProtoBytes(createdById))
                .setUpdatedById(UuidUtil.toProtoBytes(updatedById));

        // Add optional fields if present
        if (originalContentRef != null) {
            builder.setOriginalContentRef(originalContentRef);
        }
        
        if (contentType != null) {
            builder.setContentType(contentType);
        }
        
        if (metadata != null) {
            builder.putAllMetadata(metadata);
        }
        
        if (processingStatus != null) {
            builder.setProcessingStatus(processingStatus);
        }

        return builder.build();
    }
}