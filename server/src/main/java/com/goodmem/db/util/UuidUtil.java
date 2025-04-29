package com.goodmem.db.util;

import com.goodmem.common.status.Status;
import com.goodmem.common.status.StatusOr;
import java.nio.ByteBuffer;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Utility methods for working with UUIDs. */
public final class UuidUtil {

  private UuidUtil() {
    // Utility class, no instances
  }

  /**
   * Converts a UUID to a byte array.
   *
   * @param uuid The UUID to convert
   * @return The byte array representation of the UUID (16 bytes)
   */
  @Nonnull
  public static byte[] toBytes(@Nonnull UUID uuid) {
    ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
    buffer.putLong(uuid.getMostSignificantBits());
    buffer.putLong(uuid.getLeastSignificantBits());
    return buffer.array();
  }

  /**
   * Converts a byte array to a UUID.
   *
   * @param bytes The byte array representation of the UUID (16 bytes)
   * @return StatusOr containing the UUID or an error status
   */
  @Nonnull
  public static StatusOr<UUID> fromBytes(byte[] bytes) {
    if (bytes == null) {
      return StatusOr.ofStatus(Status.invalidArgument("Bytes cannot be null"));
    }
    if (bytes.length != 16) {
      return StatusOr.ofStatus(Status.invalidArgument("UUID bytes must be 16 bytes long"));
    }
    try {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      long mostSigBits = buffer.getLong();
      long leastSigBits = buffer.getLong();
      return StatusOr.ofValue(new UUID(mostSigBits, leastSigBits));
    } catch (Exception e) {
      return StatusOr.ofStatus(Status.internal("Failed to convert bytes to UUID", e));
    }
  }

  /**
   * Converts a UUID to its string representation.
   *
   * @param uuid The UUID to convert
   * @return The string representation of the UUID
   */
  @Nonnull
  public static String toString(@Nonnull UUID uuid) {
    return uuid.toString();
  }

  /**
   * Converts a string to a UUID.
   *
   * @param str The string representation of the UUID
   * @return StatusOr containing the UUID or an error status
   */
  @Nonnull
  public static StatusOr<UUID> fromString(String str) {
    if (str == null || str.isEmpty()) {
      return StatusOr.ofStatus(Status.invalidArgument("UUID string cannot be null or empty"));
    }
    try {
      return StatusOr.ofValue(UUID.fromString(str));
    } catch (IllegalArgumentException e) {
      return StatusOr.ofStatus(Status.invalidArgument("Invalid UUID string: " + str));
    }
  }

  /**
   * Converts a byte array to a hexadecimal string.
   *
   * @param bytes The byte array to convert
   * @return The hexadecimal string representation
   */
  @Nonnull
  public static String bytesToHex(byte[] bytes) {
    if (bytes == null) {
      return "";
    }
    StringBuilder hexString = new StringBuilder(2 * bytes.length);
    for (byte b : bytes) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  /**
   * Converts a ByteString from Protocol Buffers to a UUID.
   *
   * @param byteString The ByteString representation of the UUID
   * @return StatusOr containing the UUID or an error status
   */
  @Nonnull
  public static StatusOr<UUID> fromProtoBytes(@Nonnull com.google.protobuf.ByteString byteString) {
    if (byteString == null || byteString.isEmpty()) {
      return StatusOr.ofStatus(Status.invalidArgument("ByteString cannot be null or empty"));
    }
    return fromBytes(byteString.toByteArray());
  }

  /**
   * Converts a UUID to a ByteString for Protocol Buffers.
   *
   * @param uuid The UUID to convert
   * @return The ByteString representation of the UUID
   */
  @Nonnull
  public static com.google.protobuf.ByteString toProtoBytes(@Nonnull UUID uuid) {
    return com.google.protobuf.ByteString.copyFrom(toBytes(uuid));
  }
}
