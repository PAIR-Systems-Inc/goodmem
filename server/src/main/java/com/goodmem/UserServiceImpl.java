package com.goodmem;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import goodmem.v1.UserOuterClass.GetUserRequest;
import goodmem.v1.UserOuterClass.User;
import goodmem.v1.UserServiceGrpc.UserServiceImplBase;
import io.grpc.stub.StreamObserver;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

public class UserServiceImpl extends UserServiceImplBase {
  private static final Logger logger = Logger.getLogger(UserServiceImpl.class.getName());

  @Override
  public void getUser(GetUserRequest request, StreamObserver<User> responseObserver) {
    logger.info("Getting user details");

    // TODO: Validate user ID
    // TODO: Retrieve user from database
    // TODO: Check permissions

    // For now, return dummy data
    User user = User.newBuilder()
        .setUserId(getBytesFromUUID(UUID.randomUUID()))
        .setEmail("user@example.com")
        .setDisplayName("Example User")
        .setUsername("exampleuser")
        .setCreatedAt(getCurrentTimestamp())
        .setUpdatedAt(getCurrentTimestamp())
        .build();

    responseObserver.onNext(user);
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
}