package com.goodmem;

import com.goodmem.config.MinioConfig;
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
import java.time.Instant;
import java.util.UUID;
import org.tinylog.Logger;

public class MemoryServiceImpl extends MemoryServiceImplBase {
  private final Config config;
  
  public record Config(HikariDataSource dataSource, MinioConfig minioConfig) {}
  
  public MemoryServiceImpl(Config config) {
    this.config = config;
  }

  @Override
  public void createMemory(CreateMemoryRequest request, StreamObserver<Memory> responseObserver) {
    Logger.info("Creating memory in space: {}", Uuids.bytesToHex(request.getSpaceId()));

    // TODO: Validate request fields
    // TODO: Check space exists and user has permissions
    // TODO: Generate proper UUID
    // TODO: Persist in database
    // TODO: Queue for processing (chunking, vectorization)

    // For now, return dummy data
    Memory memory =
        Memory.newBuilder()
            .setMemoryId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setSpaceId(request.getSpaceId())
            .setOriginalContentRef(request.getOriginalContentRef())
            .setContentType(request.getContentType())
            .putAllMetadata(request.getMetadataMap())
            .setProcessingStatus("PENDING")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth
            .build();

    responseObserver.onNext(memory);
    responseObserver.onCompleted();
  }

  @Override
  public void getMemory(GetMemoryRequest request, StreamObserver<Memory> responseObserver) {
    Logger.info("Getting memory: {}", Uuids.bytesToHex(request.getMemoryId()));

    // TODO: Validate memory ID
    // TODO: Retrieve from database
    // TODO: Check permissions

    // For now, return dummy data
    Memory memory =
        Memory.newBuilder()
            .setMemoryId(request.getMemoryId())
            .setSpaceId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setOriginalContentRef("s3://example-bucket/content.txt")
            .setContentType("text/plain")
            .putMetadata("source", "example-source")
            .putMetadata("category", "example-category")
            .setProcessingStatus("COMPLETED")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .build();

    responseObserver.onNext(memory);
    responseObserver.onCompleted();
  }

  @Override
  public void listMemories(
      ListMemoriesRequest request, StreamObserver<ListMemoriesResponse> responseObserver) {
    Logger.info("Listing memories in space: {}", Uuids.bytesToHex(request.getSpaceId()));

    // TODO: Validate space ID
    // TODO: Check permissions
    // TODO: Query database
    // TODO: Apply pagination

    // For now, return dummy data
    Memory dummyMemory =
        Memory.newBuilder()
            .setMemoryId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setSpaceId(request.getSpaceId())
            .setOriginalContentRef("s3://example-bucket/content.txt")
            .setContentType("text/plain")
            .putMetadata("source", "example-source")
            .putMetadata("category", "example-category")
            .setProcessingStatus("COMPLETED")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .build();

    ListMemoriesResponse response =
        ListMemoriesResponse.newBuilder().addMemories(dummyMemory).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void deleteMemory(DeleteMemoryRequest request, StreamObserver<Empty> responseObserver) {
    Logger.info("Deleting memory: {}", Uuids.bytesToHex(request.getMemoryId()));

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
}
