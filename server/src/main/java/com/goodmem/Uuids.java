package com.goodmem;

import java.nio.ByteBuffer;
import java.util.UUID;

import com.google.protobuf.ByteString;

public class Uuids {


  /**
   *
   * @param uuid
   * @return
   */
  public static ByteString getBytesFromUUID(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return ByteString.copyFrom(bb.array());
  }
}
