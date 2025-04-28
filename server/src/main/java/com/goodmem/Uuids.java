package com.goodmem;

import java.nio.ByteBuffer;
import java.util.UUID;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;

/**
 * Utility class for handling UUID conversions between different formats.
 * 
 * <p>This class provides methods for converting between Java UUID objects and the binary
 * representation used in protocol buffers (ByteString), as well as formatting hexadecimal
 * representations with standard UUID notation.
 */
public class Uuids {

  /**
   * Converts a Java UUID to a ByteString for use in protocol buffer messages.
   *
   * <p>This method serializes a UUID into a 16-byte array and wraps it in a ByteString,
   * which is the format used for UUID fields in protocol buffer messages.
   *
   * @param uuid The Java UUID object to convert
   * @return A ByteString representation of the UUID (16 bytes)
   */
  public static ByteString getBytesFromUUID(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return ByteString.copyFrom(bb.array());
  }

  private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

  /**
   * Converts a ByteString containing a UUID to its standard hex string representation.
   *
   * <p>This is a convenience method that extracts the byte array from a ByteString
   * and delegates to {@link #bytesToHex(byte[])}.
   *
   * @param byteString The ByteString containing a 16-byte UUID
   * @return A formatted UUID string in standard 8-4-4-4-12 format (lowercase)
   */
  public static String bytesToHex(ByteString byteString) {
    return bytesToHex(byteString.toByteArray());
  }

  /**
   * Converts a byte array containing a UUID to its standard hex string representation.
   *
   * <p>This method encodes a binary UUID to a hexadecimal string and formats it with
   * the standard UUID dash pattern (8-4-4-4-12). If the input is not exactly 16 bytes,
   * it will return the raw hex string without formatting.
   *
   * @param bytes The byte array to convert, typically a 16-byte UUID
   * @return A formatted UUID string in standard 8-4-4-4-12 format (lowercase) if 
   *         the input is 16 bytes, or an unformatted hex string otherwise
   */
  public static String bytesToHex(byte[] bytes) {
    String result = BaseEncoding.base16().encode(bytes);

    // Format as standard UUID with dashes
    if (result.length() == 32) {
      return result.substring(0, 8)
             + "-"
             + result.substring(8, 12)
             + "-"
             + result.substring(12, 16)
             + "-"
             + result.substring(16, 20)
             + "-"
             + result.substring(20, 32);
    } else {
      return result;
    }
  }
}
