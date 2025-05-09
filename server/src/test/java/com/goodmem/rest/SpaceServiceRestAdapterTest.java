package com.goodmem.rest;

import com.goodmem.rest.dto.CreateSpaceRequest;
import com.goodmem.rest.dto.DeleteSpaceRequest;
import com.goodmem.rest.dto.GetSpaceRequest;
import com.goodmem.rest.dto.ListSpacesRequest;
import com.goodmem.rest.dto.ListSpacesResponse;
import com.goodmem.rest.dto.SortOrder;
import com.goodmem.rest.dto.Space;
import com.goodmem.rest.dto.UpdateSpaceRequest;
import com.goodmem.util.RestMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import goodmem.v1.Common;
import goodmem.v1.SpaceOuterClass;
import goodmem.v1.SpaceOuterClass.Space.Builder;
import goodmem.v1.SpaceServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.javalin.http.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpaceServiceRestAdapterTest {

    @Mock
    private Context mockContext;

    private SpaceServiceRestAdapter adapter;
    private TestSpaceServiceImpl testServiceImpl;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        // Create the test service implementation
        testServiceImpl = new TestSpaceServiceImpl();

        // Create an in-process server
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(testServiceImpl)
            .build()
            .start();

        // Create a client channel and stub
        channel = InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .build();
        SpaceServiceGrpc.SpaceServiceBlockingStub stub =
            SpaceServiceGrpc.newBlockingStub(channel);

        // Create the adapter with this stub
        adapter = new SpaceServiceRestAdapter(stub);
    }
    
    @AfterEach
    void tearDown() {
        // Shutdown the channel and server
        if (channel != null) {
            channel.shutdown();
        }
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void testCreateSpace_AllFields() {
        // Create a request DTO with all fields set
        CreateSpaceRequest dto = new CreateSpaceRequest(
            "Test Space",
            "00000000-0000-0000-0000-000000000001", // embedderId
            true, // publicRead
            Map.of("env", "test", "project", "unit-testing"),
            "00000000-0000-0000-0000-000000000002" // ownerId
        );

        // Setup mock context
        when(mockContext.bodyAsClass(CreateSpaceRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleCreateSpace(mockContext);

        // Get the captured request
        SpaceOuterClass.CreateSpaceRequest protoRequest = testServiceImpl.getLastCreateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify each field was set correctly in the proto request
        assertEquals("Test Space", protoRequest.getName(), "Name should match");
        assertEquals(true, protoRequest.getPublicRead(), "Public read should match");
        
        // Verify embedder ID
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
            protoRequest.getEmbedderId(),
            "Embedder ID should match"
        );
        
        // Verify owner ID
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2}),
            protoRequest.getOwnerId(),
            "Owner ID should match"
        );

        // Verify labels
        assertEquals(2, protoRequest.getLabelsCount(), "Labels count should match");
        assertEquals("test", protoRequest.getLabelsMap().get("env"), "Label 'env' should match");
        assertEquals("unit-testing", protoRequest.getLabelsMap().get("project"), "Label 'project' should match");

        // Verify response handling
        verify(mockContext).json(any(Space.class));
    }

    @Test
    void testCreateSpace_MinimalFields() {
        // Create a request with only required fields
        CreateSpaceRequest dto = new CreateSpaceRequest(
            "Minimal Space",
            "00000000-0000-0000-0000-000000000001", // embedderId
            false // publicRead
        );

        // Setup mock context
        when(mockContext.bodyAsClass(CreateSpaceRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleCreateSpace(mockContext);

        // Get the captured request
        SpaceOuterClass.CreateSpaceRequest protoRequest = testServiceImpl.getLastCreateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify required fields are set
        assertEquals("Minimal Space", protoRequest.getName(), "Name should match");
        assertEquals(false, protoRequest.getPublicRead(), "Public read should match");
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
            protoRequest.getEmbedderId(),
            "Embedder ID should match"
        );

        // Verify optional fields are empty or default values
        assertEquals(0, protoRequest.getLabelsCount(), "Labels count should be 0");
        assertEquals(ByteString.EMPTY, protoRequest.getOwnerId(), "Owner ID should be empty");

        // Verify response handling
        verify(mockContext).json(any(Space.class));
    }

    @Test
    void testGetSpace() {
        // Setup mock context
        String spaceId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(spaceId);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleGetSpace(mockContext);

        // Get the captured request
        SpaceOuterClass.GetSpaceRequest protoRequest = testServiceImpl.getLastGetRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify space_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getSpaceId(),
            "Space ID should match"
        );

        // Verify response handling
        verify(mockContext).json(any(Space.class));
    }

    @Test
    void testListSpaces_WithFilters() {
        // Setup mock context with query parameters
        Map<String, List<String>> queryParams = new HashMap<>();
        queryParams.put("owner_id", List.of("00000000-0000-0000-0000-000000000001"));
        queryParams.put("name_filter", List.of("Test*"));
        queryParams.put("sort_by", List.of("name"));
        queryParams.put("sort_order", List.of("desc"));
        queryParams.put("max_results", List.of("20"));
        queryParams.put("next_token", List.of(""));
        queryParams.put("label.env", List.of("test"));
        queryParams.put("label.project", List.of("demo"));

        when(mockContext.queryParamMap()).thenReturn(queryParams);
        when(mockContext.queryParam("owner_id")).thenReturn("00000000-0000-0000-0000-000000000001");
        when(mockContext.queryParam("name_filter")).thenReturn("Test*");
        when(mockContext.queryParam("sort_by")).thenReturn("name");
        when(mockContext.queryParam("sort_order")).thenReturn("desc");
        when(mockContext.queryParam("max_results")).thenReturn("20");
        when(mockContext.queryParam("next_token")).thenReturn("");
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleListSpaces(mockContext);

        // Get the captured request
        SpaceOuterClass.ListSpacesRequest protoRequest = testServiceImpl.getLastListRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify owner_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getOwnerId(),
            "Owner ID should match"
        );

        // Verify sorting parameters
        assertEquals("name", protoRequest.getSortBy(),
            "Sort field should match");
        
        // The SortOrder that comes from "desc" string should be DESCENDING
        assertEquals(goodmem.v1.Common.SortOrder.DESCENDING, protoRequest.getSortOrder(),
            "Sort order should match");
        
        // Verify pagination parameters
        assertEquals(20, protoRequest.getMaxResults(), "Max results should match");

        // Verify label selectors
        assertEquals(2, protoRequest.getLabelSelectorsCount(), "Label selectors count should match");
        assertEquals("test", protoRequest.getLabelSelectorsMap().get("env"), "Label selector 'env' should match");
        assertEquals("demo", protoRequest.getLabelSelectorsMap().get("project"), "Label selector 'project' should match");

        // Verify response handling
        verify(mockContext).json(any(ListSpacesResponse.class));
    }

    @Test
    void testUpdateSpace() {
        // Create a request to update space fields
        UpdateSpaceRequest dto = new UpdateSpaceRequest(
            "00000000-0000-0000-0000-000000000001", // spaceId is required by the DTO
            "Updated Space Name",
            true, // publicRead
            Map.of("env", "prod", "status", "active"), // replaceLabels
            null // mergeLabels
        );

        // Setup mock context
        String spaceId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(spaceId);
        when(mockContext.bodyAsClass(UpdateSpaceRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleUpdateSpace(mockContext);

        // Get the captured request
        SpaceOuterClass.UpdateSpaceRequest protoRequest = testServiceImpl.getLastUpdateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify space_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getSpaceId(),
            "Space ID should match"
        );

        // Verify updated fields
        assertEquals("Updated Space Name", protoRequest.getName(), "Name should match");
        assertEquals(true, protoRequest.getPublicRead(), "Public read should match");

        // Verify label update strategy
        assertEquals(
            SpaceOuterClass.UpdateSpaceRequest.LabelUpdateStrategyCase.REPLACE_LABELS, 
            protoRequest.getLabelUpdateStrategyCase(),
            "Label update strategy should be REPLACE_LABELS"
        );
        assertEquals(2, protoRequest.getReplaceLabels().getLabelsCount(), "Replace labels count should match");
        assertEquals("prod", protoRequest.getReplaceLabels().getLabelsMap().get("env"), "Replace label 'env' should match");
        assertEquals("active", protoRequest.getReplaceLabels().getLabelsMap().get("status"), "Replace label 'status' should match");

        // Verify response handling
        verify(mockContext).json(any(Space.class));
    }

    @Test
    void testDeleteSpace() {
        // Setup mock context
        String spaceId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(spaceId);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.status(anyInt())).thenReturn(mockContext);

        // Act
        adapter.handleDeleteSpace(mockContext);

        // Get the captured request
        SpaceOuterClass.DeleteSpaceRequest protoRequest = testServiceImpl.getLastDeleteRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify space_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getSpaceId(),
            "Space ID should match"
        );

        // Verify response handling - 204 No Content
        verify(mockContext).status(204);
    }

    /**
     * Mock implementation of the SpaceService for testing.
     * Captures requests and returns mock responses.
     */
    private static class TestSpaceServiceImpl extends SpaceServiceGrpc.SpaceServiceImplBase {
        private SpaceOuterClass.CreateSpaceRequest lastCreateRequest;
        private SpaceOuterClass.UpdateSpaceRequest lastUpdateRequest;
        private SpaceOuterClass.GetSpaceRequest lastGetRequest;
        private SpaceOuterClass.ListSpacesRequest lastListRequest;
        private SpaceOuterClass.DeleteSpaceRequest lastDeleteRequest;
        
        @Override
        public void createSpace(SpaceOuterClass.CreateSpaceRequest request, 
                StreamObserver<SpaceOuterClass.Space> responseObserver) {
            // Store the request for later verification
            this.lastCreateRequest = request;
            
            // Return a mock response
            SpaceOuterClass.Space response = createMockSpace();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void updateSpace(SpaceOuterClass.UpdateSpaceRequest request, 
                StreamObserver<SpaceOuterClass.Space> responseObserver) {
            this.lastUpdateRequest = request;
            SpaceOuterClass.Space response = createMockSpace();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void getSpace(SpaceOuterClass.GetSpaceRequest request, 
                StreamObserver<SpaceOuterClass.Space> responseObserver) {
            this.lastGetRequest = request;
            SpaceOuterClass.Space response = createMockSpace();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void listSpaces(SpaceOuterClass.ListSpacesRequest request, 
                StreamObserver<SpaceOuterClass.ListSpacesResponse> responseObserver) {
            this.lastListRequest = request;
            SpaceOuterClass.ListSpacesResponse response = SpaceOuterClass.ListSpacesResponse.newBuilder()
                .addSpaces(createMockSpace())
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void deleteSpace(SpaceOuterClass.DeleteSpaceRequest request, 
                StreamObserver<Empty> responseObserver) {
            this.lastDeleteRequest = request;
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        
        // Helper to create a mock space for response
        private SpaceOuterClass.Space createMockSpace() {
            // Create proper UUID bytes
            UUID uuid = UUID.randomUUID();
            ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());
            ByteString uuidByteString = ByteString.copyFrom(buffer.array());
            
            // Generate timestamp for fields
            Timestamp now = Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
                .setNanos((int) ((System.currentTimeMillis() % 1000) * 1000000))
                .build();
            
            return SpaceOuterClass.Space.newBuilder()
                .setSpaceId(uuidByteString)
                .setName("Mock Space")
                .setEmbedderId(uuidByteString)
                .setPublicRead(false)
                .putAllLabels(Map.of("env", "test", "project", "unit-testing"))
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setOwnerId(uuidByteString)
                .setCreatedById(uuidByteString)
                .setUpdatedById(uuidByteString)
                .build();
        }
        
        // Getters for the captured requests
        public SpaceOuterClass.CreateSpaceRequest getLastCreateRequest() {
            return lastCreateRequest;
        }
        
        public SpaceOuterClass.UpdateSpaceRequest getLastUpdateRequest() {
            return lastUpdateRequest;
        }
        
        public SpaceOuterClass.GetSpaceRequest getLastGetRequest() {
            return lastGetRequest;
        }
        
        public SpaceOuterClass.ListSpacesRequest getLastListRequest() {
            return lastListRequest;
        }
        
        public SpaceOuterClass.DeleteSpaceRequest getLastDeleteRequest() {
            return lastDeleteRequest;
        }
    }
}