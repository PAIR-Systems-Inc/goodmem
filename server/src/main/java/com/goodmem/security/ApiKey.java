package com.goodmem.security;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;

/**
 * Represents an API Key, using immutable ByteString for key material. Encapsulates its raw
 * material, hashed version for storage, and user-facing string representation using Base32
 * encoding.
 *
 * <p>Use the static factory method `newKey` to generate new keys.
 */
public record ApiKey(
    ByteString keyMaterial, // Immutable raw random bytes (the actual secret)
    ByteString hashedKeyMaterial, // Immutable SHA3-256 hash of keyMaterial (for DB storage)
    String keyString // User-facing key string (e.g., "gm_...")
    ) {

  private static final String HASH_ALGORITHM = "SHA3-256";
  private static final String KEY_PREFIX = "gm_";
  private static final BaseEncoding KEY_ENCODING = BaseEncoding.base32().omitPadding();
  public static final int DEFAULT_KEY_BYTES = 16;
  private static final int DEFAULT_DISPLAY_PREFIX = 8;

  /**
   * Canonical constructor for the record. Parameters are already immutable.
   *
   * @param keyMaterial Immutable ByteString of raw random bytes. Must not be null.
   * @param hashedKeyMaterial Immutable ByteString of the hashed keyMaterial. Must not be null.
   * @param keyString User-facing key string. Must not be null.
   */
  public ApiKey { // Compact constructor form
    Objects.requireNonNull(keyMaterial, "keyMaterial must not be null");
    Objects.requireNonNull(hashedKeyMaterial, "hashedKeyMaterial must not be null");
    Objects.requireNonNull(keyString, "keyString must not be null");

    if (!keyString.startsWith(KEY_PREFIX)) {
      throw new IllegalArgumentException("keyString must start with prefix '" + KEY_PREFIX + "'");
    }
    // No defensive copies needed here as ByteString is immutable!
  }

  /**
   * Static factory method to generate a new API Key.
   *
   * @param secureRandom A SecureRandom instance for generating randomness.
   * @param numBytes The desired number of random bytes for the key material (e.g., 24 for ~192
   *     bits). Must be positive.
   * @return A newly generated ApiKey record.
   * @throws IllegalArgumentException if numBytes is not positive or secureRandom is null.
   * @throws RuntimeException if the SHA3-256 hashing algorithm is not available.
   */
  public static ApiKey newKey(SecureRandom secureRandom, int numBytes) {
    if (numBytes <= 0) {
      throw new IllegalArgumentException("Number of bytes must be positive.");
    }
    Objects.requireNonNull(secureRandom, "secureRandom must not be null");

    // 1. Generate Key Material as byte[]
    byte[] rawMaterialBytes = new byte[numBytes];
    secureRandom.nextBytes(rawMaterialBytes);

    // 2. Hash Key Material (SHA3-256) as byte[]
    byte[] hashedMaterialBytes = hashBytes(rawMaterialBytes);

    // 3. Encode original Key Material (Base32 - Guava) and create Key String
    //    We encode from the original byte[] for efficiency, could also use ByteString.toByteArray()
    String base32EncodedMaterial = KEY_ENCODING.encode(rawMaterialBytes).toLowerCase();
    String fullKeyString = KEY_PREFIX + base32EncodedMaterial;

    // 4. Convert byte arrays to immutable ByteString (single copy happens here)
    ByteString keyMaterialBS = ByteString.copyFrom(rawMaterialBytes);
    ByteString hashedMaterialBS = ByteString.copyFrom(hashedMaterialBytes);

    // 5. Construct and return the record using immutable ByteStrings
    return new ApiKey(keyMaterialBS, hashedMaterialBS, fullKeyString);
  }

  /**
   * Static factory method to generate a new API Key using the default number of bytes.
   *
   * @param secureRandom A SecureRandom instance for generating randomness.
   * @return A newly generated ApiKey record.
   * @throws IllegalArgumentException if secureRandom is null.
   * @throws RuntimeException if the SHA3-256 hashing algorithm is not available.
   */
  public static ApiKey newKey(SecureRandom secureRandom) {
    return newKey(secureRandom, DEFAULT_KEY_BYTES);
  }

  // Default accessors are now fine - they return immutable ByteString references.
  // No need to override keyMaterial() or hashedKeyMaterial() for defensive copies.

  @Override
  @NotNull
  public String toString() {
    // Avoid leaking raw key material in general logs
    // Use helper that now accepts ByteString
    return "ApiKey[keyString="
        + keyString
        + ", hashedKeyMaterial="
        + bytesToHex(hashedKeyMaterial)
        + "]";
  }

  /**
   * Returns a prefix of the API key string for display purposes. This is useful for UI displays
   * where you want to show part of the key for identification without revealing the full key.
   *
   * @param prefix_length The number of characters to include in the prefix
   * @return A prefix of the key string with the specified length
   * @throws IndexOutOfBoundsException if prefix_length is negative or greater than the key length
   */
  public String displayPrefix(int prefix_length) {
    return keyString.substring(0, prefix_length);
  }

  /**
   * Returns a standard-length prefix of the API key string for display purposes. Uses the default
   * display prefix length defined by DEFAULT_DISPLAY_PREFIX.
   *
   * @return A prefix of the key string with the default length
   * @throws IndexOutOfBoundsException if the default prefix length is greater than the key length
   */
  public String displayPrefix() {
    return displayPrefix(DEFAULT_DISPLAY_PREFIX);
  }

  // Helper to display bytes more cleanly - updated to accept ByteString
  private static String bytesToHex(ByteString bytes) {
    if (bytes == null) return "null";
    StringBuilder sb = new StringBuilder(bytes.size() * 2);
    // ByteString allows efficient iteration
    for (int i = 0; i < bytes.size(); i++) {
      sb.append(String.format("%02x", bytes.byteAt(i)));
    }
    return sb.toString();
  }

  // --- Example Usage ---
  public static void main(String[] args) {
    SecureRandom random = new SecureRandom();

    System.out.println("Generating key with default bytes (" + DEFAULT_KEY_BYTES + ")...");
    ApiKey key1 = ApiKey.newKey(random);
    System.out.println("  Key String: " + key1.keyString());
    System.out.println(
        "  Length of random part (Base32): " + (key1.keyString().length() - KEY_PREFIX.length()));
    System.out.println(
        "  Key Material (Hex): " + bytesToHex(key1.keyMaterial())); // Accessor returns ByteString
    System.out.println(
        "  Hashed Material (Hex): "
            + bytesToHex(key1.hashedKeyMaterial())); // Accessor returns ByteString
    System.out.println("  Record toString(): " + key1);

    System.out.println("\nGenerating key with 32 bytes (256 bits)...");
    ApiKey key2 = ApiKey.newKey(random, 32); // 256 bits
    System.out.println("  Key String: " + key2.keyString());
    System.out.println(
        "  Length of random part (Base32): " + (key2.keyString().length() - KEY_PREFIX.length()));
    System.out.println("  Hashed Material (Hex): " + bytesToHex(key2.hashedKeyMaterial()));

    // Demonstrate accessing components
    String keyToGiveUser = key2.keyString();
    // Store the immutable ByteString directly or convert back to byte[] if needed ONLY at the
    // boundary
    ByteString hashToStoreInDb = key2.hashedKeyMaterial(); // Get the immutable ByteString

    System.out.println("\nKey to give user: " + keyToGiveUser);
    System.out.println("Hash to store in DB (ByteString Hex): " + bytesToHex(hashToStoreInDb));

    // If your DB layer needs a byte[], convert only when necessary:
    // byte[] hashBytesForDb = hashToStoreInDb.toByteArray(); // Creates a copy
    // System.out.println("Hash bytes for DB: " + bytesToHex(hashBytesForDb)); // Using helper
    // overload
  }

  /**
   * Hashes raw bytes using the system's standard hashing algorithm (SHA3-256).
   * This is used internally for hashing key material and for validating keys.
   *
   * @param bytes The raw bytes to hash
   * @return The hashed bytes
   * @throws UnsupportedOperationException if the hash algorithm is not available
   */
  public static byte[] hashBytes(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      return digest.digest(bytes);
    } catch (NoSuchAlgorithmException e) {
      throw new UnsupportedOperationException(
          "Fatal: Required hash algorithm '" + HASH_ALGORITHM + "' not available.", e);
    }
  }
  
  /**
   * Hashes a raw API key string using the same algorithm as when creating a new key.
   * This is useful for looking up API keys in the database.
   *
   * @param keyString The raw API key string (e.g., "gm_abc123...")
   * @return A StatusOr containing either the ByteString hash or an error status
   */
  public static com.goodmem.common.status.StatusOr<ByteString> hashApiKeyString(String keyString) {
    if (keyString == null || !keyString.startsWith(KEY_PREFIX)) {
      return com.goodmem.common.status.StatusOr.ofStatus(
          com.goodmem.common.status.Status.invalidArgument("Invalid API key format: must start with '" + KEY_PREFIX + "'"));
    }
    
    try {
      byte[] hashedBytes = hashBytes(keyString.getBytes());
      return com.goodmem.common.status.StatusOr.ofValue(ByteString.copyFrom(hashedBytes));
    } catch (UnsupportedOperationException e) {
      return com.goodmem.common.status.StatusOr.ofStatus(
          com.goodmem.common.status.Status.internal("Hashing algorithm not available", e));
    }
  }
  
  // Overload helper for byte[] if needed elsewhere
  private static String bytesToHex(byte[] bytes) {
    if (bytes == null) return "null";
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
