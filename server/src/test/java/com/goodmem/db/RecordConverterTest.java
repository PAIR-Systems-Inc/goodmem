package com.goodmem.db;

import static org.junit.jupiter.api.Assertions.*;

import com.goodmem.db.util.DbUtil;
import com.goodmem.db.util.UuidUtil;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;

/** Tests for record-to-proto conversions. */
public class RecordConverterTest {

  @Test
  void testUserToProto() {
    // Create a User record
    UUID userId = UUID.randomUUID();
    Instant now = Instant.now();
    User user = new User(userId, "testuser", "test@example.com", "Test User", now, now);

    // Convert to proto
    goodmem.v1.UserOuterClass.User proto = user.toProto();

    // Verify fields
    assertEquals(userId, UuidUtil.fromProtoBytes(proto.getUserId()).getValue());
    assertEquals("testuser", proto.getUsername());
    assertEquals("test@example.com", proto.getEmail());
    assertEquals("Test User", proto.getDisplayName());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getCreatedAt()).toEpochMilli());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getUpdatedAt()).toEpochMilli());
  }

  @Test
  void testSpaceToProto() {
    // Create a Space record
    UUID spaceId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID createdById = UUID.randomUUID();
    UUID updatedById = UUID.randomUUID();
    Instant now = Instant.now();

    Space space =
        new Space(
            spaceId,
            ownerId,
            "Test Space",
            Map.of("key1", "value1"),
            UUID.fromString("00000000-0000-0000-0000-000000000001"), // Test embedder ID
            true,
            now,
            now,
            createdById,
            updatedById);

    // Convert to proto
    goodmem.v1.SpaceOuterClass.Space proto = space.toProto();

    // Verify fields
    assertEquals(spaceId, UuidUtil.fromProtoBytes(proto.getSpaceId()).getValue());
    assertEquals(ownerId, UuidUtil.fromProtoBytes(proto.getOwnerId()).getValue());
    assertEquals("Test Space", proto.getName());
    assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), 
                 UuidUtil.fromProtoBytes(proto.getEmbedderId()).getValue());
    assertTrue(proto.getPublicRead());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getCreatedAt()).toEpochMilli());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getUpdatedAt()).toEpochMilli());
    assertEquals(createdById, UuidUtil.fromProtoBytes(proto.getCreatedById()).getValue());
    assertEquals(updatedById, UuidUtil.fromProtoBytes(proto.getUpdatedById()).getValue());
    assertEquals("value1", proto.getLabelsMap().get("key1"));
  }

  @Test
  void testApiKeyToProto() {
    // Create an ApiKey record
    UUID apiKeyId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID createdById = UUID.randomUUID();
    UUID updatedById = UUID.randomUUID();
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(3600);

    ApiKey apiKey =
        new ApiKey(
            apiKeyId,
            userId,
            "sk_test_",
            ByteString.copyFrom(new byte[16]),
            "ACTIVE",
            Map.of("purpose", "test"),
            expiresAt,
            now,
            now,
            now,
            createdById,
            updatedById);

    // Convert to proto
    goodmem.v1.Apikey.ApiKey proto = apiKey.toProto();

    // Verify fields
    assertEquals(apiKeyId, UuidUtil.fromProtoBytes(proto.getApiKeyId()).getValue());
    assertEquals(userId, UuidUtil.fromProtoBytes(proto.getUserId()).getValue());
    assertEquals("sk_test_", proto.getKeyPrefix());
    assertEquals(goodmem.v1.Apikey.Status.ACTIVE, proto.getStatus());
    assertEquals(
        expiresAt.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getExpiresAt()).toEpochMilli());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getLastUsedAt()).toEpochMilli());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getCreatedAt()).toEpochMilli());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getUpdatedAt()).toEpochMilli());
    assertEquals(createdById, UuidUtil.fromProtoBytes(proto.getCreatedById()).getValue());
    assertEquals(updatedById, UuidUtil.fromProtoBytes(proto.getUpdatedById()).getValue());
    assertEquals("test", proto.getLabelsMap().get("purpose"));
  }

  @Test
  void testMemoryToProto() {
    // Create a Memory record
    UUID memoryId = UUID.randomUUID();
    UUID spaceId = UUID.randomUUID();
    UUID createdById = UUID.randomUUID();
    UUID updatedById = UUID.randomUUID();
    Instant now = Instant.now();

    Memory memory =
        new Memory(
            memoryId,
            spaceId,
            "s3://bucket/document.pdf",
            "application/pdf",
            Map.of("title", "Test Document"),
            "PENDING",
            now,
            now,
            createdById,
            updatedById);

    // Convert to proto
    goodmem.v1.MemoryOuterClass.Memory proto = memory.toProto();

    // Verify fields
    assertEquals(memoryId, UuidUtil.fromProtoBytes(proto.getMemoryId()).getValue());
    assertEquals(spaceId, UuidUtil.fromProtoBytes(proto.getSpaceId()).getValue());
    assertEquals("s3://bucket/document.pdf", proto.getOriginalContentRef());
    assertEquals("application/pdf", proto.getContentType());
    assertEquals("PENDING", proto.getProcessingStatus());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getCreatedAt()).toEpochMilli());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getUpdatedAt()).toEpochMilli());
    assertEquals(createdById, UuidUtil.fromProtoBytes(proto.getCreatedById()).getValue());
    assertEquals(updatedById, UuidUtil.fromProtoBytes(proto.getUpdatedById()).getValue());
    assertEquals("Test Document", proto.getMetadataMap().get("title"));
  }

  @Test
  void testMemoryChunkToProto() {
    // Create a MemoryChunk record
    UUID chunkId = UUID.randomUUID();
    UUID memoryId = UUID.randomUUID();
    UUID createdById = UUID.randomUUID();
    UUID updatedById = UUID.randomUUID();
    Instant now = Instant.now();
    float[] vector = new float[] {0.1f, 0.2f, 0.3f};

    MemoryChunk chunk =
        new MemoryChunk(
            chunkId,
            memoryId,
            1,
            "This is a test chunk.",
            vector,
            "GENERATED",
            0,
            20,
            now,
            now,
            createdById,
            updatedById);

    // Convert to proto
    goodmem.v1.MemoryOuterClass.MemoryChunk proto = chunk.toProto();

    // Verify fields
    assertEquals(chunkId, UuidUtil.fromProtoBytes(proto.getChunkId()).getValue());
    assertEquals(memoryId, UuidUtil.fromProtoBytes(proto.getMemoryId()).getValue());
    assertEquals(1, proto.getChunkSequenceNumber());
    assertEquals("This is a test chunk.", proto.getChunkText());
    assertEquals("GENERATED", proto.getVectorStatus());
    assertEquals(0, proto.getStartOffset());
    assertEquals(20, proto.getEndOffset());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getCreatedAt()).toEpochMilli());
    assertEquals(
        now.toEpochMilli(), DbUtil.fromProtoTimestamp(proto.getUpdatedAt()).toEpochMilli());
    assertEquals(createdById, UuidUtil.fromProtoBytes(proto.getCreatedById()).getValue());
    assertEquals(updatedById, UuidUtil.fromProtoBytes(proto.getUpdatedById()).getValue());

    // Check vector values
    assertEquals(3, proto.getEmbeddingVectorCount());
    assertEquals(0.1f, proto.getEmbeddingVector(0), 0.001);
    assertEquals(0.2f, proto.getEmbeddingVector(1), 0.001);
    assertEquals(0.3f, proto.getEmbeddingVector(2), 0.001);
  }
}
