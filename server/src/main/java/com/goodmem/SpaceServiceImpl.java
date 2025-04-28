package com.goodmem;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.SpaceOuterClass.CreateSpaceRequest;
import goodmem.v1.SpaceOuterClass.DeleteSpaceRequest;
import goodmem.v1.SpaceOuterClass.GetSpaceRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesRequest;
import goodmem.v1.SpaceOuterClass.ListSpacesResponse;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.SpaceOuterClass.UpdateSpaceRequest;
import goodmem.v1.SpaceServiceGrpc.SpaceServiceImplBase;
import io.grpc.stub.StreamObserver;
import org.tinylog.Logger;

import java.time.Instant;
import java.util.UUID;

public class SpaceServiceImpl extends SpaceServiceImplBase {
  private final Config config;

  /**
   * @param dataSource
   * @param defaultEmbeddingModel
   */
  public record Config(HikariDataSource dataSource, String defaultEmbeddingModel) {}
  
  public SpaceServiceImpl(Config config) {
    this.config = config;
  }

  @Override
  public void createSpace(CreateSpaceRequest request, StreamObserver<Space> responseObserver) {
    Logger.info("Creating space: {}", request.getName());

    // TODO: Validate request fields
    // TODO: Generate proper UUID
    // TODO: Persist in database
    // TODO: Create embedding index

    // For now, return dummy data
    Space space =
        Space.newBuilder()
            .setSpaceId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setName(request.getName())
            .putAllLabels(request.getLabelsMap())
            .setEmbeddingModel(
                request.getEmbeddingModel().isEmpty()
                    ? config.defaultEmbeddingModel()
                    : request.getEmbeddingModel())
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setOwnerId(
                Uuids.getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth context
            .setCreatedById(
                Uuids.getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth context
            .setUpdatedById(
                Uuids.getBytesFromUUID(UUID.randomUUID())) // This would be derived from auth context
            .setPublicRead(request.getPublicRead())
            .build();

    responseObserver.onNext(space);
    responseObserver.onCompleted();
  }

  @Override
  public void getSpace(GetSpaceRequest request, StreamObserver<Space> responseObserver) {
    Logger.info("Getting space: {}", Uuids.bytesToHex(request.getSpaceId()));

    // TODO: Validate space ID
    // TODO: Retrieve from database
    // TODO: Check permissions

    // For now, return dummy data
    Space space =
        Space.newBuilder()
            .setSpaceId(request.getSpaceId())
            .setName("Example Space")
            .putLabels("user", "alice")
            .putLabels("bot", "copilot")
            .setEmbeddingModel("openai-ada-002")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setOwnerId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setPublicRead(true)
            .build();

    responseObserver.onNext(space);
    responseObserver.onCompleted();
  }

  @Override
  public void deleteSpace(DeleteSpaceRequest request, StreamObserver<Empty> responseObserver) {
    Logger.info("Deleting space: {}", Uuids.bytesToHex(request.getSpaceId().toByteArray()));

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
    Logger.info("Listing spaces with label selectors: {}", request.getLabelSelectorsMap());

    // TODO: Query database with label selectors
    // TODO: Filter by ownership
    // TODO: Provide pagination

    // For now, return a dummy space
    Space dummySpace =
        Space.newBuilder()
            .setSpaceId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setName("Example Space")
            .putLabels("user", "alice")
            .putLabels("bot", "copilot")
            .setEmbeddingModel("openai-ada-002")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setOwnerId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setPublicRead(true)
            .build();

    ListSpacesResponse response = ListSpacesResponse.newBuilder().addSpaces(dummySpace).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void updateSpace(UpdateSpaceRequest request, StreamObserver<Space> responseObserver) {
    Logger.info("Updating space: {}" + Uuids.bytesToHex(request.getSpaceId()));

    // TODO: Validate space ID
    // TODO: Check ownership
    // TODO: Update in database

    // For now, return a dummy updated space
    Space updatedSpace =
        Space.newBuilder()
            .setSpaceId(request.getSpaceId())
            .setName(request.getName().isEmpty() ? "Example Space" : request.getName())
            .putAllLabels(request.getLabelsMap())
            .setEmbeddingModel("openai-ada-002")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setOwnerId(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setCreatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(Uuids.getBytesFromUUID(UUID.randomUUID()))
            .setPublicRead(request.getPublicRead())
            .build();

    responseObserver.onNext(updatedSpace);
    responseObserver.onCompleted();
  }

  private Timestamp getCurrentTimestamp() {
    Instant now = Instant.now();
    return Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
  }
}
