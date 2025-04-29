package com.goodmem.db;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/**
 * Represents a row in the 'memory_chunk' table.
 *
 * @param chunkId The unique identifier of the chunk
 * @param memoryId The memory ID this chunk belongs to
 * @param chunkSequenceNumber The sequence number of this chunk within the memory
 * @param chunkText The text content of the chunk
 * @param embeddingVector The vector embedding of the chunk text
 * @param vectorStatus The status of the vector (e.g., "PENDING", "GENERATED", "FAILED")
 * @param startOffset The start position in the original content
 * @param endOffset The end position in the original content
 * @param createdAt Timestamp when the record was created
 * @param updatedAt Timestamp when the record was last updated
 * @param createdById User ID who created this chunk
 * @param updatedById User ID who last updated this chunk
 */
public record MemoryChunk(
    UUID chunkId,
    UUID memoryId,
    Integer chunkSequenceNumber,
    String chunkText,
    float[] embeddingVector,
    String vectorStatus,
    Integer startOffset,
    Integer endOffset,
    Instant createdAt,
    Instant updatedAt,
    UUID createdById,
    UUID updatedById) {
  /**
   * Converts this database record to its corresponding Protocol Buffer message.
   *
   * @return The Protocol Buffer MemoryChunk message
   */
  public goodmem.v1.MemoryOuterClass.MemoryChunk toProto() {
    goodmem.v1.MemoryOuterClass.MemoryChunk.Builder builder =
        goodmem.v1.MemoryOuterClass.MemoryChunk.newBuilder()
            .setChunkId(UuidUtil.toProtoBytes(chunkId))
            .setMemoryId(UuidUtil.toProtoBytes(memoryId))
            .setCreatedAt(DbUtil.toProtoTimestamp(createdAt))
            .setUpdatedAt(DbUtil.toProtoTimestamp(updatedAt))
            .setCreatedById(UuidUtil.toProtoBytes(createdById))
            .setUpdatedById(UuidUtil.toProtoBytes(updatedById));

    // Add optional fields if present
    if (chunkSequenceNumber != null) {
      builder.setChunkSequenceNumber(chunkSequenceNumber);
    }

    if (chunkText != null) {
      builder.setChunkText(chunkText);
    }

    if (embeddingVector != null) {
      // Convert float array to proto repeated float
      for (float value : embeddingVector) {
        builder.addEmbeddingVector(value);
      }
    }

    if (vectorStatus != null) {
      builder.setVectorStatus(vectorStatus);
    }

    if (startOffset != null) {
      builder.setStartOffset(startOffset);
    }

    if (endOffset != null) {
      builder.setEndOffset(endOffset);
    }

    return builder.build();
  }

  /** Creates a copy of this MemoryChunk with a new vector status. */
  public MemoryChunk withVectorStatus(String newStatus) {
    return new MemoryChunk(
        chunkId,
        memoryId,
        chunkSequenceNumber,
        chunkText,
        embeddingVector,
        newStatus,
        startOffset,
        endOffset,
        createdAt,
        updatedAt,
        createdById,
        updatedById);
  }

  /** Creates a copy of this MemoryChunk with a new embedding vector. */
  public MemoryChunk withEmbeddingVector(float[] newVector) {
    return new MemoryChunk(
        chunkId,
        memoryId,
        chunkSequenceNumber,
        chunkText,
        newVector,
        vectorStatus,
        startOffset,
        endOffset,
        createdAt,
        updatedAt,
        createdById,
        updatedById);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MemoryChunk that = (MemoryChunk) o;
    return chunkId.equals(that.chunkId)
        && memoryId.equals(that.memoryId)
        && java.util.Objects.equals(chunkSequenceNumber, that.chunkSequenceNumber)
        && java.util.Objects.equals(chunkText, that.chunkText)
        && Arrays.equals(embeddingVector, that.embeddingVector)
        && java.util.Objects.equals(vectorStatus, that.vectorStatus)
        && java.util.Objects.equals(startOffset, that.startOffset)
        && java.util.Objects.equals(endOffset, that.endOffset)
        && createdAt.equals(that.createdAt)
        && updatedAt.equals(that.updatedAt)
        && createdById.equals(that.createdById)
        && updatedById.equals(that.updatedById);
  }

  @Override
  public int hashCode() {
    int result =
        java.util.Objects.hash(
            chunkId,
            memoryId,
            chunkSequenceNumber,
            chunkText,
            vectorStatus,
            startOffset,
            endOffset,
            createdAt,
            updatedAt,
            createdById,
            updatedById);
    result = 31 * result + Arrays.hashCode(embeddingVector);
    return result;
  }

  @Override
  public String toString() {
    return "MemoryChunk["
        + "chunkId="
        + chunkId
        + ", memoryId="
        + memoryId
        + ", chunkSequenceNumber="
        + chunkSequenceNumber
        + ", chunkText='"
        + (chunkText != null
            ? chunkText.substring(0, Math.min(30, chunkText.length())) + "..."
            : null)
        + '\''
        + ", embeddingVector="
        + (embeddingVector != null ? "[" + embeddingVector.length + " elements]" : null)
        + ", vectorStatus='"
        + vectorStatus
        + '\''
        + ", startOffset="
        + startOffset
        + ", endOffset="
        + endOffset
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + ", createdById="
        + createdById
        + ", updatedById="
        + updatedById
        + ']';
  }
}
