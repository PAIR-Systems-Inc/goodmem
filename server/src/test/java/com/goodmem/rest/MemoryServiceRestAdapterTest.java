package com.goodmem.rest;

import com.goodmem.util.RestMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import goodmem.v1.MemoryOuterClass;
import goodmem.v1.MemoryServiceGrpc;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryServiceRestAdapterTest {

    @Mock
    private Context mockContext;

    private MemoryServiceRestAdapter adapter;
    private TestMemoryServiceImpl testServiceImpl;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        // Create the test service implementation
        testServiceImpl = new TestMemoryServiceImpl();

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
        MemoryServiceGrpc.MemoryServiceBlockingStub stub =
            MemoryServiceGrpc.newBlockingStub(channel);

        // Create the adapter with this stub
        adapter = new MemoryServiceRestAdapter(stub);
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
    void testCreateMemory_AllFields() {
        // Create request JSON with all fields
        Map<String, Object> requestJson = new HashMap<>();
        requestJson.put("space_id", "00000000-0000-0000-0000-000000000001");
        requestJson.put("original_content_ref", "s3://bucket/object.txt");
        requestJson.put("content_type", "text/plain");
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "document");
        metadata.put("author", "John Doe");
        requestJson.put("metadata", metadata);

        // Setup mock context
        when(mockContext.bodyAsClass(Map.class)).thenReturn(requestJson);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleCreateMemory(mockContext);

        // Get the captured request
        MemoryOuterClass.CreateMemoryRequest protoRequest = testServiceImpl.getLastCreateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify space_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getSpaceId(),
            "Space ID should match"
        );

        // Verify content reference was set correctly
        assertEquals("s3://bucket/object.txt", protoRequest.getOriginalContentRef(), 
            "Original content reference should match");
        
        // Verify content type was set correctly
        assertEquals("text/plain", protoRequest.getContentType(), "Content type should match");
        
        // Verify metadata was set correctly
        assertEquals(2, protoRequest.getMetadataCount(), "Metadata count should match");
        assertEquals("document", protoRequest.getMetadataMap().get("source"), 
            "Source metadata should match");
        assertEquals("John Doe", protoRequest.getMetadataMap().get("author"), 
            "Author metadata should match");

        // Verify response handling using captured JSON
        verify(mockContext).json(any(Map.class));
    }

    @Test
    void testCreateMemory_MinimalFields() {
        // Create request with only required field (space_id)
        Map<String, Object> requestJson = new HashMap<>();
        requestJson.put("space_id", "00000000-0000-0000-0000-000000000001");

        // Setup mock context
        when(mockContext.bodyAsClass(Map.class)).thenReturn(requestJson);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleCreateMemory(mockContext);

        // Get the captured request
        MemoryOuterClass.CreateMemoryRequest protoRequest = testServiceImpl.getLastCreateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify space_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getSpaceId(),
            "Space ID should match"
        );

        // Verify optional fields are empty
        assertEquals("", protoRequest.getOriginalContentRef(), "Original content reference should be empty");
        assertEquals("", protoRequest.getContentType(), "Content type should be empty");
        assertEquals(0, protoRequest.getMetadataCount(), "Metadata should be empty");

        // Verify response handling
        verify(mockContext).json(any(Map.class));
    }

    @Test
    void testGetMemory() {
        // Setup mock context
        String memoryId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(memoryId);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleGetMemory(mockContext);

        // Get the captured request
        MemoryOuterClass.GetMemoryRequest protoRequest = testServiceImpl.getLastGetRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify memory_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getMemoryId(),
            "Memory ID should match"
        );

        // Verify response handling
        verify(mockContext).json(any(Map.class));
    }

    @Test
    void testListMemories() {
        // Setup mock context
        String spaceId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("spaceId")).thenReturn(spaceId);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleListMemories(mockContext);

        // Get the captured request
        MemoryOuterClass.ListMemoriesRequest protoRequest = testServiceImpl.getLastListRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify space_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getSpaceId(),
            "Space ID should match"
        );

        // Capture the response Map
        ArgumentCaptor<Map<String, Object>> responseCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockContext).json(responseCaptor.capture());
        
        // Verify the response contains a "memories" list
        Map<String, Object> responseMap = responseCaptor.getValue();
        assertTrue(responseMap.containsKey("memories"), "Response should contain memories key");
        assertTrue(responseMap.get("memories") instanceof List, "Memories should be a list");
    }

    @Test
    void testDeleteMemory() {
        // Setup mock context
        String memoryId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(memoryId);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.status(anyInt())).thenReturn(mockContext);

        // Act
        adapter.handleDeleteMemory(mockContext);

        // Get the captured request
        MemoryOuterClass.DeleteMemoryRequest protoRequest = testServiceImpl.getLastDeleteRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify memory_id was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getMemoryId(),
            "Memory ID should match"
        );

        // Verify response handling - 204 No Content
        verify(mockContext).status(204);
    }

    /**
     * Mock implementation of the MemoryService for testing.
     * Captures requests and returns mock responses.
     */
    private static class TestMemoryServiceImpl extends MemoryServiceGrpc.MemoryServiceImplBase {
        private MemoryOuterClass.CreateMemoryRequest lastCreateRequest;
        private MemoryOuterClass.GetMemoryRequest lastGetRequest;
        private MemoryOuterClass.ListMemoriesRequest lastListRequest;
        private MemoryOuterClass.DeleteMemoryRequest lastDeleteRequest;
        
        @Override
        public void createMemory(MemoryOuterClass.CreateMemoryRequest request, 
                StreamObserver<MemoryOuterClass.Memory> responseObserver) {
            // Store the request for later verification
            this.lastCreateRequest = request;
            
            // Return a mock response
            MemoryOuterClass.Memory response = createMockMemory();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void getMemory(MemoryOuterClass.GetMemoryRequest request, 
                StreamObserver<MemoryOuterClass.Memory> responseObserver) {
            this.lastGetRequest = request;
            MemoryOuterClass.Memory response = createMockMemory();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void listMemories(MemoryOuterClass.ListMemoriesRequest request, 
                StreamObserver<MemoryOuterClass.ListMemoriesResponse> responseObserver) {
            this.lastListRequest = request;
            MemoryOuterClass.ListMemoriesResponse response = 
                MemoryOuterClass.ListMemoriesResponse.newBuilder()
                    .addMemories(createMockMemory())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void deleteMemory(MemoryOuterClass.DeleteMemoryRequest request, 
                StreamObserver<Empty> responseObserver) {
            this.lastDeleteRequest = request;
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        
        // Helper to create a mock memory for response
        private MemoryOuterClass.Memory createMockMemory() {
            // Create proper UUID bytes
            UUID memoryId = UUID.randomUUID();
            UUID spaceId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID userId = UUID.randomUUID();
            
            ByteBuffer memoryIdBuffer = ByteBuffer.wrap(new byte[16]);
            memoryIdBuffer.putLong(memoryId.getMostSignificantBits());
            memoryIdBuffer.putLong(memoryId.getLeastSignificantBits());
            ByteString memoryIdByteString = ByteString.copyFrom(memoryIdBuffer.array());
            
            ByteBuffer spaceIdBuffer = ByteBuffer.wrap(new byte[16]);
            spaceIdBuffer.putLong(spaceId.getMostSignificantBits());
            spaceIdBuffer.putLong(spaceId.getLeastSignificantBits());
            ByteString spaceIdByteString = ByteString.copyFrom(spaceIdBuffer.array());
            
            ByteBuffer userIdBuffer = ByteBuffer.wrap(new byte[16]);
            userIdBuffer.putLong(userId.getMostSignificantBits());
            userIdBuffer.putLong(userId.getLeastSignificantBits());
            ByteString userIdByteString = ByteString.copyFrom(userIdBuffer.array());
            
            // Generate timestamp for fields
            Timestamp now = Timestamp.newBuilder()
                .setSeconds(System.currentTimeMillis() / 1000)
                .setNanos((int) ((System.currentTimeMillis() % 1000) * 1000000))
                .build();
            
            return MemoryOuterClass.Memory.newBuilder()
                .setMemoryId(memoryIdByteString)
                .setSpaceId(spaceIdByteString)
                .setOriginalContentRef("s3://bucket/object-123.txt")
                .setContentType("text/plain")
                .setProcessingStatus("COMPLETED")
                .putMetadata("source", "document")
                .putMetadata("author", "Test Author")
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setCreatedById(userIdByteString)
                .setUpdatedById(userIdByteString)
                .build();
        }
        
        // Getters for the captured requests
        public MemoryOuterClass.CreateMemoryRequest getLastCreateRequest() {
            return lastCreateRequest;
        }
        
        public MemoryOuterClass.GetMemoryRequest getLastGetRequest() {
            return lastGetRequest;
        }
        
        public MemoryOuterClass.ListMemoriesRequest getLastListRequest() {
            return lastListRequest;
        }
        
        public MemoryOuterClass.DeleteMemoryRequest getLastDeleteRequest() {
            return lastDeleteRequest;
        }
    }
}