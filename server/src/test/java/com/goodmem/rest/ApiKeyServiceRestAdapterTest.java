package com.goodmem.rest;

import com.goodmem.rest.dto.ApiKeyResponse;
import com.goodmem.rest.dto.CreateApiKeyRequest;
import com.goodmem.rest.dto.CreateApiKeyResponse;
import com.goodmem.rest.dto.DeleteApiKeyRequest;
import com.goodmem.rest.dto.ListApiKeysResponse;
import com.goodmem.rest.dto.UpdateApiKeyRequest;
import com.goodmem.util.RestMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import goodmem.v1.ApiKeyServiceGrpc;
import goodmem.v1.Apikey;
import goodmem.v1.Common;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceRestAdapterTest {

    @Mock
    private Context mockContext;

    private ApiKeyServiceRestAdapter adapter;
    private TestApiKeyServiceImpl testServiceImpl;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws IOException {
        // Create the test service implementation
        testServiceImpl = new TestApiKeyServiceImpl();

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
        ApiKeyServiceGrpc.ApiKeyServiceBlockingStub stub =
            ApiKeyServiceGrpc.newBlockingStub(channel);

        // Create the adapter with this stub
        adapter = new ApiKeyServiceRestAdapter(stub);
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
    void testCreateApiKey_WithLabelsAndExpiration() {
        // Create request with labels and expiration time
        long expiresAt = System.currentTimeMillis() + 3600000; // 1 hour from now
        CreateApiKeyRequest dto = new CreateApiKeyRequest(
            Map.of("env", "test", "service", "api-gateway"),
            expiresAt
        );

        // Setup mock context
        when(mockContext.bodyAsClass(CreateApiKeyRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleCreateApiKey(mockContext);

        // Get the captured request
        Apikey.CreateApiKeyRequest protoRequest = testServiceImpl.getLastCreateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify labels were set correctly
        assertEquals(2, protoRequest.getLabelsCount(), "Labels count should match");
        assertEquals("test", protoRequest.getLabelsMap().get("env"), "Label 'env' should match");
        assertEquals("api-gateway", protoRequest.getLabelsMap().get("service"), "Label 'service' should match");

        // Verify expiration time was set correctly (check if within 2 seconds to account for conversion differences)
        assertTrue(protoRequest.hasExpiresAt(), "Expiration time should be set");
        long protoExpiresMillis = protoRequest.getExpiresAt().getSeconds() * 1000 
            + protoRequest.getExpiresAt().getNanos() / 1000000;
        assertTrue(Math.abs(protoExpiresMillis - expiresAt) < 2000, 
            "Expiration time should be close to the input value");

        // Verify response handling
        verify(mockContext).json(any(CreateApiKeyResponse.class));
    }

    @Test
    void testCreateApiKey_MinimalFields() {
        // Create minimal request with no labels and no expiration
        CreateApiKeyRequest dto = new CreateApiKeyRequest(null, null);

        // Setup mock context
        when(mockContext.bodyAsClass(CreateApiKeyRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleCreateApiKey(mockContext);

        // Get the captured request
        Apikey.CreateApiKeyRequest protoRequest = testServiceImpl.getLastCreateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify labels are empty
        assertEquals(0, protoRequest.getLabelsCount(), "Labels should be empty");

        // Verify expiration time is not set
        assertFalse(protoRequest.hasExpiresAt(), "Expiration time should not be set");

        // Verify response handling
        verify(mockContext).json(any(CreateApiKeyResponse.class));
    }

    @Test
    void testListApiKeys() {
        // Setup mock context
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleListApiKeys(mockContext);

        // Get the captured request
        Apikey.ListApiKeysRequest protoRequest = testServiceImpl.getLastListRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // The current implementation doesn't have any parameters, so there's not much to verify
        // Just check that the list API was called and the response was properly handled

        // Verify response handling
        verify(mockContext).json(any(ListApiKeysResponse.class));
    }

    @Test
    void testUpdateApiKey_Status() {
        // Create a request to update only the status
        UpdateApiKeyRequest dto = new UpdateApiKeyRequest(
            "ACTIVE",    // status
            null,        // replaceLabels
            null         // mergeLabels
        );

        // Setup mock context
        String apiKeyId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(apiKeyId);
        when(mockContext.bodyAsClass(UpdateApiKeyRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleUpdateApiKey(mockContext);

        // Get the captured request
        Apikey.UpdateApiKeyRequest protoRequest = testServiceImpl.getLastUpdateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify API key ID was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getApiKeyId(),
            "API key ID should match"
        );

        // Verify status was set correctly
        assertEquals(Apikey.Status.ACTIVE, protoRequest.getStatus(), "Status should be ACTIVE");

        // Verify label strategy was not set
        assertEquals(
            Apikey.UpdateApiKeyRequest.LabelUpdateStrategyCase.LABELUPDATESTRATEGY_NOT_SET,
            protoRequest.getLabelUpdateStrategyCase(),
            "Label update strategy should not be set"
        );

        // Verify response handling
        verify(mockContext).json(any(ApiKeyResponse.class));
    }

    @Test
    void testUpdateApiKey_ReplaceLabels() {
        // Create a request to replace labels
        UpdateApiKeyRequest dto = new UpdateApiKeyRequest(
            null,    // status
            Map.of("env", "prod", "service", "web-app"),  // replaceLabels
            null     // mergeLabels
        );

        // Setup mock context
        String apiKeyId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(apiKeyId);
        when(mockContext.bodyAsClass(UpdateApiKeyRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleUpdateApiKey(mockContext);

        // Get the captured request
        Apikey.UpdateApiKeyRequest protoRequest = testServiceImpl.getLastUpdateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify API key ID was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getApiKeyId(),
            "API key ID should match"
        );

        // Verify status was not set (default)
        assertEquals(Apikey.Status.STATUS_UNSPECIFIED, protoRequest.getStatus(), 
            "Status should be STATUS_UNSPECIFIED");

        // Verify label update strategy was set to REPLACE_LABELS
        assertEquals(
            Apikey.UpdateApiKeyRequest.LabelUpdateStrategyCase.REPLACE_LABELS,
            protoRequest.getLabelUpdateStrategyCase(),
            "Label update strategy should be REPLACE_LABELS"
        );
        
        // Verify replace labels content
        assertEquals(2, protoRequest.getReplaceLabels().getLabelsCount(), "Replace labels count should match");
        assertEquals("prod", protoRequest.getReplaceLabels().getLabelsMap().get("env"), 
            "Replace label 'env' should match");
        assertEquals("web-app", protoRequest.getReplaceLabels().getLabelsMap().get("service"), 
            "Replace label 'service' should match");

        // Verify response handling
        verify(mockContext).json(any(ApiKeyResponse.class));
    }

    @Test
    void testUpdateApiKey_MergeLabels() {
        // Create a request to merge labels
        UpdateApiKeyRequest dto = new UpdateApiKeyRequest(
            null,    // status
            null,    // replaceLabels
            Map.of("new-key", "new-value", "updated-key", "updated-value")  // mergeLabels
        );

        // Setup mock context
        String apiKeyId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(apiKeyId);
        when(mockContext.bodyAsClass(UpdateApiKeyRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleUpdateApiKey(mockContext);

        // Get the captured request
        Apikey.UpdateApiKeyRequest protoRequest = testServiceImpl.getLastUpdateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify API key ID was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getApiKeyId(),
            "API key ID should match"
        );

        // Verify status was not set (default)
        assertEquals(Apikey.Status.STATUS_UNSPECIFIED, protoRequest.getStatus(), 
            "Status should be STATUS_UNSPECIFIED");

        // Verify label update strategy was set to MERGE_LABELS
        assertEquals(
            Apikey.UpdateApiKeyRequest.LabelUpdateStrategyCase.MERGE_LABELS,
            protoRequest.getLabelUpdateStrategyCase(),
            "Label update strategy should be MERGE_LABELS"
        );
        
        // Verify merge labels content
        assertEquals(2, protoRequest.getMergeLabels().getLabelsCount(), "Merge labels count should match");
        assertEquals("new-value", protoRequest.getMergeLabels().getLabelsMap().get("new-key"), 
            "Merge label 'new-key' should match");
        assertEquals("updated-value", protoRequest.getMergeLabels().getLabelsMap().get("updated-key"), 
            "Merge label 'updated-key' should match");

        // Verify response handling
        verify(mockContext).json(any(ApiKeyResponse.class));
    }

    @Test
    void testUpdateApiKey_StatusAndLabels() {
        // Create a request to update both status and labels
        UpdateApiKeyRequest dto = new UpdateApiKeyRequest(
            "INACTIVE",    // status
            Map.of("env", "staging"),  // replaceLabels
            null     // mergeLabels
        );

        // Setup mock context
        String apiKeyId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(apiKeyId);
        when(mockContext.bodyAsClass(UpdateApiKeyRequest.class)).thenReturn(dto);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.json(any())).thenReturn(mockContext);

        // Act
        adapter.handleUpdateApiKey(mockContext);

        // Get the captured request
        Apikey.UpdateApiKeyRequest protoRequest = testServiceImpl.getLastUpdateRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify API key ID was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getApiKeyId(),
            "API key ID should match"
        );

        // Verify status was set to INACTIVE
        assertEquals(Apikey.Status.INACTIVE, protoRequest.getStatus(), "Status should be INACTIVE");

        // Verify label update strategy was set to REPLACE_LABELS
        assertEquals(
            Apikey.UpdateApiKeyRequest.LabelUpdateStrategyCase.REPLACE_LABELS,
            protoRequest.getLabelUpdateStrategyCase(),
            "Label update strategy should be REPLACE_LABELS"
        );
        
        // Verify replace labels content
        assertEquals(1, protoRequest.getReplaceLabels().getLabelsCount(), "Replace labels count should match");
        assertEquals("staging", protoRequest.getReplaceLabels().getLabelsMap().get("env"), 
            "Replace label 'env' should match");

        // Verify response handling
        verify(mockContext).json(any(ApiKeyResponse.class));
    }

    @Test
    void testDeleteApiKey() {
        // Setup mock context
        String apiKeyId = "00000000-0000-0000-0000-000000000001";
        when(mockContext.pathParam("id")).thenReturn(apiKeyId);
        when(mockContext.header("x-api-key")).thenReturn("valid-api-key");
        when(mockContext.status(anyInt())).thenReturn(mockContext);

        // Act
        adapter.handleDeleteApiKey(mockContext);

        // Get the captured request
        Apikey.DeleteApiKeyRequest protoRequest = testServiceImpl.getLastDeleteRequest();
        assertNotNull(protoRequest, "Request should not be null");

        // Verify API key ID was set correctly
        assertEquals(
            ByteString.copyFrom(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}), 
            protoRequest.getApiKeyId(),
            "API key ID should match"
        );

        // Verify response handling - 204 No Content
        verify(mockContext).status(204);
    }

    /**
     * Mock implementation of the ApiKeyService for testing.
     * Captures requests and returns mock responses.
     */
    private static class TestApiKeyServiceImpl extends ApiKeyServiceGrpc.ApiKeyServiceImplBase {
        private Apikey.CreateApiKeyRequest lastCreateRequest;
        private Apikey.UpdateApiKeyRequest lastUpdateRequest;
        private Apikey.ListApiKeysRequest lastListRequest;
        private Apikey.DeleteApiKeyRequest lastDeleteRequest;
        
        @Override
        public void createApiKey(Apikey.CreateApiKeyRequest request, 
                StreamObserver<Apikey.CreateApiKeyResponse> responseObserver) {
            // Store the request for later verification
            this.lastCreateRequest = request;
            
            // Generate a mock API key
            Apikey.ApiKey apiKey = createMockApiKey();
            
            // Create response with the API key and a raw key value
            Apikey.CreateApiKeyResponse response = Apikey.CreateApiKeyResponse.newBuilder()
                .setApiKeyMetadata(apiKey)
                .setRawApiKey("gm_testapikey12345")
                .build();
                
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void updateApiKey(Apikey.UpdateApiKeyRequest request, 
                StreamObserver<Apikey.ApiKey> responseObserver) {
            this.lastUpdateRequest = request;
            Apikey.ApiKey response = createMockApiKey();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void listApiKeys(Apikey.ListApiKeysRequest request, 
                StreamObserver<Apikey.ListApiKeysResponse> responseObserver) {
            this.lastListRequest = request;
            Apikey.ListApiKeysResponse response = Apikey.ListApiKeysResponse.newBuilder()
                .addKeys(createMockApiKey())
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        
        @Override
        public void deleteApiKey(Apikey.DeleteApiKeyRequest request, 
                StreamObserver<Empty> responseObserver) {
            this.lastDeleteRequest = request;
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        }
        
        // Helper to create a mock API key for response
        private Apikey.ApiKey createMockApiKey() {
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
            
            Timestamp future = Timestamp.newBuilder()
                .setSeconds(now.getSeconds() + 3600) // 1 hour in the future
                .setNanos(now.getNanos())
                .build();
            
            return Apikey.ApiKey.newBuilder()
                .setApiKeyId(uuidByteString)
                .setUserId(uuidByteString)
                .setKeyPrefix("gm_test")
                .setStatus(Apikey.Status.ACTIVE)
                .putAllLabels(Map.of("env", "test", "purpose", "unit-testing"))
                .setExpiresAt(future)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setCreatedById(uuidByteString)
                .setUpdatedById(uuidByteString)
                .build();
        }
        
        // Getters for the captured requests
        public Apikey.CreateApiKeyRequest getLastCreateRequest() {
            return lastCreateRequest;
        }
        
        public Apikey.UpdateApiKeyRequest getLastUpdateRequest() {
            return lastUpdateRequest;
        }
        
        public Apikey.ListApiKeysRequest getLastListRequest() {
            return lastListRequest;
        }
        
        public Apikey.DeleteApiKeyRequest getLastDeleteRequest() {
            return lastDeleteRequest;
        }
    }
}