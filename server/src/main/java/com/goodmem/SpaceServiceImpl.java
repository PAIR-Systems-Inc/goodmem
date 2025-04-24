package com.goodmem;

import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import goodmem.v1.SpaceOuterClass.CreateSpaceRequest;
import goodmem.v1.SpaceOuterClass.DeleteSpaceRequest;
import goodmem.v1.SpaceOuterClass.GetSpaceRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesResponse;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.SpaceOuterClass.UpdateSpaceRequest;
import goodmem.v1.SpaceServiceGrpc.SpaceServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

public class SpaceServiceImpl extends SpaceServiceImplBase {
  private static final Logger logger = Logger.getLogger(SpaceServiceImpl.class.getName());

  @Override
  public void createSpace(CreateSpaceRequest request, StreamObserver<Space> responseObserver) {
    logger.info("Creating space: " + request.getName());

    // TODO: Validate request fields
    // TODO: Generate proper UUID
    // TODO: Persist in database
    // TODO: Create embedding index

    // For now, return dummy data
    Space space = Space.newBuilder()
        .setSpaceId(getBytesFromUUID(UUID.randomUUID()))
        .setName(request.getName())
        .putAllLabels(request.getLabelsMap())
        .setEmbeddingModel(
            request.getEmbeddingModel().isEmpty()
                ? "openai-ada-002"
                : request.getEmbeddingModel())
        .setCreatedAt(getCurrentTimestamp())
        .setUpdatedAt(getCurrentTimestamp())
        .setOwnerId(getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth context
        .setCreatedById(getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth context
        .setUpdatedById(getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth context
        .setPublicRead(request.getPublicRead())
        .build();

    responseObserver.onNext(space);
    responseObserver.onCompleted();
  }

  @Override
  public void getSpace(GetSpaceRequest request, StreamObserver<Space> responseObserver) {
    logger.info("Getting space: " + bytesToHex(request.getSpaceId().toByteArray()));

    // TODO: Validate space ID
    // TODO: Retrieve from database
    // TODO: Check permissions

    // For now, return dummy data
    Space space = Space.newBuilder()
        .setSpaceId(request.getSpaceId())
        .setName("Example Space")
        .putLabels("user", "alice")
        .putLabels("bot", "copilot")
        .setEmbeddingModel("openai-ada-002")
        .setCreatedAt(getCurrentTimestamp())
        .setUpdatedAt(getCurrentTimestamp())
        .setOwnerId(getBytesFromUUID(UUID.randomUUID()))
        .setCreatedById(getBytesFromUUID(UUID.randomUUID()))
        .setUpdatedById(getBytesFromUUID(UUID.randomUUID()))
        .setPublicRead(true)
        .build();

    responseObserver.onNext(space);
    responseObserver.onCompleted();
  }

  @Override
  public void deleteSpace(DeleteSpaceRequest request, StreamObserver<Empty> responseObserver) {
    logger.info("Deleting space: " + bytesToHex(request.getSpaceId().toByteArray()));

    // TODO: Validate space ID
    // TODO: Check ownership
    // TODO: Delete from database
    // TODO: Clean up embedding index

    responseObserver.onNext(Empty.getDefaultInstance());
    responseObserver.onCompleted();
  }

  @Override
  public void listSpaces(
      ListSpacesRequest request, StreamObserver<ListSpacesResponse> responseObserver) {
    logger.info("Listing spaces with label selectors: " + request.getLabelSelectorsMap());

    // TODO: Query database with label selectors
    // TODO: Filter by ownership
    // TODO: Provide pagination

    // For now, return a dummy space
    Space dummySpace = Space.newBuilder()
        .setSpaceId(getBytesFromUUID(UUID.randomUUID()))
        .setName("Example Space")
        .putLabels("user", "alice")
        .putLabels("bot", "copilot")
        .setEmbeddingModel("openai-ada-002")
        .setCreatedAt(getCurrentTimestamp())
        .setUpdatedAt(getCurrentTimestamp())
        .setOwnerId(getBytesFromUUID(UUID.randomUUID()))
        .setCreatedById(getBytesFromUUID(UUID.randomUUID()))
        .setUpdatedById(getBytesFromUUID(UUID.randomUUID()))
        .setPublicRead(true)
        .build();

    ListSpacesResponse response = ListSpacesResponse.newBuilder().addSpaces(dummySpace).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void updateSpace(UpdateSpaceRequest request, StreamObserver<Space> responseObserver) {
    logger.info("Updating space: " + bytesToHex(request.getSpaceId().toByteArray()));

    // TODO: Validate space ID
    // TODO: Check ownership
    // TODO: Update in database

    // For now, return a dummy updated space
    Space updatedSpace = Space.newBuilder()
        .setSpaceId(request.getSpaceId())
        .setName(request.getName().isEmpty() ? "Example Space" : request.getName())
        .putAllLabels(request.getLabelsMap())
        .setEmbeddingModel("openai-ada-002")
        .setCreatedAt(getCurrentTimestamp())
        .setUpdatedAt(getCurrentTimestamp())
        .setOwnerId(getBytesFromUUID(UUID.randomUUID()))
        .setCreatedById(getBytesFromUUID(UUID.randomUUID()))
        .setUpdatedById(getBytesFromUUID(UUID.randomUUID()))
        .setPublicRead(request.getPublicRead())
        .build();

    responseObserver.onNext(updatedSpace);
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