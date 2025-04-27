package com.goodmem;

import com.goodmem.config.MinioConfig;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.MemoryOuterClass.CreateMemoryRequest;
import goodmem.v1.MemoryOuterClass.DeleteMemoryRequest;
import goodmem.v1.MemoryOuterClass.GetMemoryRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesRequest;
import goodmem.v1.MemoryOuterClass.ListMemoriesResponse;
import goodmem.v1.MemoryOuterClass.Memory;
import goodmem.v1.MemoryServiceGrpc.MemoryServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

public class MemoryServiceImpl extends MemoryServiceImplBase {
  private static final Logger logger = Logger.getLogger(MemoryServiceImpl.class.getName());
  private final Config config;
  
  public record Config(HikariDataSource dataSource, MinioConfig minioConfig) {}
  
  public MemoryServiceImpl(Config config) {
    this.config = config;
  }

  @Override
  public void createMemory(CreateMemoryRequest request, StreamObserver<Memory> responseObserver) {
    logger.info("Creating memory in space: " + bytesToHex(request.getSpaceId().toByteArray()));

    // TODO: Validate request fields
    // TODO: Check space exists and user has permissions
    // TODO: Generate proper UUID
    // TODO: Persist in database
    // TODO: Queue for processing (chunking, vectorization)

    // For now, return dummy data
    Memory memory =
        Memory.newBuilder()
            .setMemoryId(getBytesFromUUID(UUID.randomUUID()))
            .setSpaceId(request.getSpaceId())
            .setOriginalContentRef(request.getOriginalContentRef())
            .setContentType(request.getContentType())
            .putAllMetadata(request.getMetadataMap())
            .setProcessingStatus("PENDING")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth
            .setUpdatedById(getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth
            .build();

    responseObserver.onNext(memory);
    responseObserver.onCompleted();
  }

  @Override
  public void getMemory(GetMemoryRequest request, StreamObserver<Memory> responseObserver) {
    logger.info("Getting memory: " + bytesToHex(request.getMemoryId().toByteArray()));

    // TODO: Validate memory ID
    // TODO: Retrieve from database
    // TODO: Check permissions

    // For now, return dummy data
    Memory memory =
        Memory.newBuilder()
            .setMemoryId(request.getMemoryId())
            .setSpaceId(getBytesFromUUID(UUID.randomUUID()))
            .setOriginalContentRef("s3://example-bucket/content.txt")
            .setContentType("text/plain")
            .putMetadata("source", "example-source")
            .putMetadata("category", "example-category")
            .setProcessingStatus("COMPLETED")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(getBytesFromUUID(UUID.randomUUID()))
            .build();

    responseObserver.onNext(memory);
    responseObserver.onCompleted();
  }

  @Override
  public void listMemories(
      ListMemoriesRequest request, StreamObserver<ListMemoriesResponse> responseObserver) {
    logger.info("Listing memories in space: " + bytesToHex(request.getSpaceId().toByteArray()));

    // TODO: Validate space ID
    // TODO: Check permissions
    // TODO: Query database
    // TODO: Apply pagination

    // For now, return dummy data
    Memory dummyMemory =
        Memory.newBuilder()
            .setMemoryId(getBytesFromUUID(UUID.randomUUID()))
            .setSpaceId(request.getSpaceId())
            .setOriginalContentRef("s3://example-bucket/content.txt")
            .setContentType("text/plain")
            .putMetadata("source", "example-source")
            .putMetadata("category", "example-category")
            .setProcessingStatus("COMPLETED")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(getBytesFromUUID(UUID.randomUUID()))
            .build();

    ListMemoriesResponse response =
        ListMemoriesResponse.newBuilder().addMemories(dummyMemory).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void deleteMemory(DeleteMemoryRequest request, StreamObserver<Empty> responseObserver) {
    logger.info("Deleting memory: " + bytesToHex(request.getMemoryId().toByteArray()));

    // TODO: Validate memory ID
    // TODO: Check permissions
    // TODO: Delete from database
    // TODO: Clean up vectors and chunks

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  private Timestamp getCurrentTimestamp() {
    Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }

  private ByteString getBytesFromUUID(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return ByteString.copyFrom(bb.array());
  }

  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

  private String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }
}
