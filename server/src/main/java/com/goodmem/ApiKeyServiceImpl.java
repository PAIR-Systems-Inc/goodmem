package com.goodmem;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;
import com.zaxxer.hikari.HikariDataSource;
import goodmem.v1.ApiKeyServiceGrpc.ApiKeyServiceImplBase;
import goodmem.v1.Apikey.ApiKey;
import goodmem.v1.Apikey.CreateApiKeyRequest;
import goodmem.v1.Apikey.CreateApiKeyResponse;
import goodmem.v1.Apikey.DeleteApiKeyRequest;
import goodmem.v1.Apikey.ListApiKeysRequest;
import goodmem.v1.Apikey.ListApiKeysResponse;
import goodmem.v1.Apikey.Status;
import goodmem.v1.Apikey.UpdateApiKeyRequest;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

public class ApiKeyServiceImpl extends ApiKeyServiceImplBase {
  private static final Logger logger = Logger.getLogger(ApiKeyServiceImpl.class.getName());
  private final Config config;
  
  public record Config(HikariDataSource dataSource) {}
  
  public ApiKeyServiceImpl(Config config) {
    this.config = config;
  }

  @Override
  public void createApiKey(
      CreateApiKeyRequest request, StreamObserver<CreateApiKeyResponse> responseObserver) {
    logger.info("Creating API key");

    // TODO: Validate request
    // TODO: Generate proper UUID and secure API key
    // TODO: Store API key hash in database
    // TODO: Ensure user is authenticated

    // Generate a dummy API key (in production this would be secure random)
    String rawApiKey = "gm_" + UUID.randomUUID().toString().replace("-", "");
    String keyPrefix = rawApiKey.substring(0, 8);

    // For now, return dummy data
    ApiKey apiKey =
        ApiKey.newBuilder()
            .setApiKeyId(getBytesFromUUID(UUID.randomUUID()))
            .setUserId(getBytesFromUUID(UUID.randomUUID())) // From auth context
            .setKeyPrefix(keyPrefix)
            .setStatus(Status.ACTIVE)
            .putAllLabels(request.getLabelsMap())
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(getBytesFromUUID(UUID.randomUUID())) // From auth context
            .setUpdatedById(getBytesFromUUID(UUID.randomUUID())) // From auth context
            .build();

    // Only set expiresAt if it's provided
    if (request.hasExpiresAt()) {
      apiKey = apiKey.toBuilder().setExpiresAt(request.getExpiresAt()).build();
    }

    CreateApiKeyResponse response =
        CreateApiKeyResponse.newBuilder().setApiKeyMetadata(apiKey).setRawApiKey(rawApiKey).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void listApiKeys(
      ListApiKeysRequest request, StreamObserver<ListApiKeysResponse> responseObserver) {
    logger.info("Listing API keys");

    // TODO: Check permissions
    // TODO: Query database
    // TODO: Apply pagination

    // For now, return dummy data
    ApiKey dummyKey =
        ApiKey.newBuilder()
            .setApiKeyId(getBytesFromUUID(UUID.randomUUID()))
            .setUserId(getBytesFromUUID(UUID.randomUUID()))
            .setKeyPrefix("gm_12345")
            .setStatus(Status.ACTIVE)
            .putLabels("purpose", "testing")
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(getBytesFromUUID(UUID.randomUUID()))
            .build();

    ListApiKeysResponse response = ListApiKeysResponse.newBuilder().addKeys(dummyKey).build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void updateApiKey(UpdateApiKeyRequest request, StreamObserver<ApiKey> responseObserver) {
    logger.info("Updating API key: " + hex(request.getApiKeyId()));

    // TODO: Validate API key ID
    // TODO: Check permissions
    // TODO: Update in database

    // For now, return dummy data
    ApiKey updatedKey =
        ApiKey.newBuilder()
            .setApiKeyId(request.getApiKeyId())
            .setUserId(getBytesFromUUID(UUID.randomUUID()))
            .setKeyPrefix("gm_12345")
            .setStatus(Status.ACTIVE) // Default to ACTIVE if not specified
            .putAllLabels(request.getLabelsMap())
            .setCreatedAt(getCurrentTimestamp())
            .setUpdatedAt(getCurrentTimestamp())
            .setCreatedById(getBytesFromUUID(UUID.randomUUID()))
            .setUpdatedById(getBytesFromUUID(UUID.randomUUID()))
            .build();

    responseObserver.onNext(updatedKey);
    responseObserver.onCompleted();
  }

  @Override
  public void deleteApiKey(DeleteApiKeyRequest request, StreamObserver<Empty> responseObserver) {
    logger.info("Deleting API key: " + hex(request.getApiKeyId()));


    // TODO: Validate API key ID
    // TODO: Check permissions
    // TODO: Delete or mark as revoked in database

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

  private String hex(ByteString bs) {
    return BaseEncoding.base16().encode(bs.toByteArray());
  }
}
