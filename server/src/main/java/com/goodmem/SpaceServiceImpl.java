package com.goodmem;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import goodmem.v1.Space.CreateSpaceRequest;
import goodmem.v1.Space.DeleteSpaceRequest;
import goodmem.v1.Space.ListSpacesRequest;
import goodmem.v1.Space.ListSpacesResponse;
import goodmem.v1.Space.Space;
import goodmem.v1.SpaceServiceGrpc.SpaceServiceImplBase;
import io.grpc.stub.StreamObserver;

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
                .setSpaceId("00000000-0000-0000-0000-000000000001")
                .setName(request.getName())
                .putAllLabels(request.getLabelsMap())
                .setEmbeddingModel(request.hasEmbeddingModel() ? request.getEmbeddingModel() : "openai-ada-002")
                .setCreatedAt(getCurrentTimestamp())
                .setOwnerId("user-123") // This would be derived from authenticated principal
                .setPublicRead(request.getPublicRead())
                .build();

        responseObserver.onNext(space);
        responseObserver.onCompleted();
    }

    @Override
    public void deleteSpace(DeleteSpaceRequest request, StreamObserver<Empty> responseObserver) {
        logger.info("Deleting space: " + request.getSpaceId());

        // TODO: Validate space ID
        // TODO: Check ownership
        // TODO: Delete from database 
        // TODO: Clean up embedding index

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void listSpaces(ListSpacesRequest request, StreamObserver<ListSpacesResponse> responseObserver) {
        logger.info("Listing spaces with label selectors: " + request.getLabelSelectorsMap());

        // TODO: Query database with label selectors
        // TODO: Filter by ownership
        // TODO: Provide pagination
        
        // For now, return a dummy space
        Space dummySpace = Space.newBuilder()
                .setSpaceId("00000000-0000-0000-0000-000000000001")
                .setName("Example Space")
                .putLabels("user", "alice")
                .putLabels("bot", "copilot")
                .setEmbeddingModel("openai-ada-002")
                .setCreatedAt(getCurrentTimestamp())
                .setOwnerId("user-123")
                .setPublicRead(true)
                .build();

        ListSpacesResponse response = ListSpacesResponse.newBuilder()
                .addSpaces(dummySpace)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private Timestamp getCurrentTimestamp() {
        Instant now = Instant.now();
        return Timestamp.newBuilder()
                .setSeconds(now.getEpochSecond())
                .setNanos(now.getNano())
                .build();
    }
}