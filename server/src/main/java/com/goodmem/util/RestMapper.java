package com.goodmem.util;

import com.goodmem.Uuids;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import goodmem.v1.Apikey.ApiKey;
import goodmem.v1.EmbedderOuterClass.Embedder;
import goodmem.v1.EmbedderOuterClass.Modality;
import goodmem.v1.MemoryOuterClass.Memory;
import goodmem.v1.SpaceOuterClass.Space;
import goodmem.v1.UserOuterClass.User;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for converting protocol buffer messages to JSON-friendly Maps,
 * with consistent field naming conventions for REST APIs.
 */
public final class RestMapper {

  private RestMapper() {
    // Utility class, no instances
  }

  /**
   * Naming conventions for REST APIs.
   * Most common conventions are CAMEL_CASE (camelCase) and SNAKE_CASE (snake_case).
   */
  public enum NamingConvention {
    CAMEL_CASE,
    SNAKE_CASE
  }

  // Default naming convention used by the mapper
  private static final NamingConvention DEFAULT_CONVENTION = NamingConvention.CAMEL_CASE;

  /**
   * Converts a protocol buffer Space message to a JSON-friendly Map with proper field names.
   *
   * @param space The Space protocol buffer message
   * @return A Map containing Space fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(Space space) {
    return toJsonMap(space, DEFAULT_CONVENTION);
  }

  /**
   * Converts a protocol buffer Space message to a JSON-friendly Map with specified naming convention.
   *
   * @param space The Space protocol buffer message
   * @param convention The naming convention to use for field names
   * @return A Map containing Space fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(Space space, NamingConvention convention) {
    Map<String, Object> map = new HashMap<>();
    map.put(formatName("spaceId", convention), Uuids.bytesToHex(space.getSpaceId().toByteArray()));
    map.put(formatName("name", convention), space.getName());
    map.put(formatName("labels", convention), space.getLabelsMap());
    map.put(formatName("embeddingModel", convention), space.getEmbeddingModel());
    map.put(formatName("createdAt", convention), formatTimestamp(space.getCreatedAt()));
    map.put(formatName("updatedAt", convention), formatTimestamp(space.getUpdatedAt()));
    map.put(formatName("ownerId", convention), Uuids.bytesToHex(space.getOwnerId().toByteArray()));
    map.put(formatName("createdById", convention), Uuids.bytesToHex(space.getCreatedById().toByteArray()));
    map.put(formatName("updatedById", convention), Uuids.bytesToHex(space.getUpdatedById().toByteArray()));
    map.put(formatName("publicRead", convention), space.getPublicRead());
    return map;
  }

  /**
   * Converts a protocol buffer User message to a JSON-friendly Map with proper field names.
   *
   * @param user The User protocol buffer message
   * @return A Map containing User fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(User user) {
    return toJsonMap(user, DEFAULT_CONVENTION);
  }

  /**
   * Converts a protocol buffer User message to a JSON-friendly Map with specified naming convention.
   *
   * @param user The User protocol buffer message
   * @param convention The naming convention to use for field names
   * @return A Map containing User fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(User user, NamingConvention convention) {
    Map<String, Object> map = new HashMap<>();
    map.put(formatName("userId", convention), Uuids.bytesToHex(user.getUserId().toByteArray()));
    map.put(formatName("email", convention), user.getEmail());
    map.put(formatName("displayName", convention), user.getDisplayName());
    map.put(formatName("username", convention), user.getUsername());
    map.put(formatName("createdAt", convention), formatTimestamp(user.getCreatedAt()));
    map.put(formatName("updatedAt", convention), formatTimestamp(user.getUpdatedAt()));
    return map;
  }

  /**
   * Converts a protocol buffer Memory message to a JSON-friendly Map with proper field names.
   *
   * @param memory The Memory protocol buffer message
   * @return A Map containing Memory fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(Memory memory) {
    return toJsonMap(memory, DEFAULT_CONVENTION);
  }

  /**
   * Converts a protocol buffer Memory message to a JSON-friendly Map with specified naming convention.
   *
   * @param memory The Memory protocol buffer message
   * @param convention The naming convention to use for field names
   * @return A Map containing Memory fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(Memory memory, NamingConvention convention) {
    Map<String, Object> map = new HashMap<>();
    map.put(formatName("memoryId", convention), Uuids.bytesToHex(memory.getMemoryId().toByteArray()));
    map.put(formatName("spaceId", convention), Uuids.bytesToHex(memory.getSpaceId().toByteArray()));
    map.put(formatName("originalContentRef", convention), memory.getOriginalContentRef());
    map.put(formatName("contentType", convention), memory.getContentType());
    map.put(formatName("metadata", convention), memory.getMetadataMap());
    map.put(formatName("processingStatus", convention), memory.getProcessingStatus());
    map.put(formatName("createdAt", convention), formatTimestamp(memory.getCreatedAt()));
    map.put(formatName("updatedAt", convention), formatTimestamp(memory.getUpdatedAt()));
    map.put(formatName("createdById", convention), Uuids.bytesToHex(memory.getCreatedById().toByteArray()));
    map.put(formatName("updatedById", convention), Uuids.bytesToHex(memory.getUpdatedById().toByteArray()));
    return map;
  }

  /**
   * Converts a protocol buffer ApiKey message to a JSON-friendly Map with proper field names.
   *
   * @param apiKey The ApiKey protocol buffer message
   * @return A Map containing ApiKey fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(ApiKey apiKey) {
    return toJsonMap(apiKey, DEFAULT_CONVENTION);
  }

  /**
   * Converts a protocol buffer ApiKey message to a JSON-friendly Map with specified naming convention.
   *
   * @param apiKey The ApiKey protocol buffer message
   * @param convention The naming convention to use for field names
   * @return A Map containing ApiKey fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(ApiKey apiKey, NamingConvention convention) {
    Map<String, Object> map = new HashMap<>();
    map.put(formatName("apiKeyId", convention), Uuids.bytesToHex(apiKey.getApiKeyId().toByteArray()));
    map.put(formatName("userId", convention), Uuids.bytesToHex(apiKey.getUserId().toByteArray()));
    map.put(formatName("keyPrefix", convention), apiKey.getKeyPrefix());
    map.put(formatName("status", convention), apiKey.getStatus().name());
    map.put(formatName("labels", convention), apiKey.getLabelsMap());

    if (apiKey.hasExpiresAt()) {
      map.put(formatName("expiresAt", convention), formatTimestamp(apiKey.getExpiresAt()));
    }

    if (apiKey.hasLastUsedAt()) {
      map.put(formatName("lastUsedAt", convention), formatTimestamp(apiKey.getLastUsedAt()));
    }

    map.put(formatName("createdAt", convention), formatTimestamp(apiKey.getCreatedAt()));
    map.put(formatName("updatedAt", convention), formatTimestamp(apiKey.getUpdatedAt()));
    map.put(formatName("createdById", convention), Uuids.bytesToHex(apiKey.getCreatedById().toByteArray()));
    map.put(formatName("updatedById", convention), Uuids.bytesToHex(apiKey.getUpdatedById().toByteArray()));
    return map;
  }

  /**
   * Converts a protocol buffer Embedder message to a JSON-friendly Map with proper field names.
   *
   * @param embedder The Embedder protocol buffer message
   * @return A Map containing Embedder fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(Embedder embedder) {
    return toJsonMap(embedder, DEFAULT_CONVENTION);
  }

  /**
   * Converts a protocol buffer Embedder message to a JSON-friendly Map with specified naming convention.
   *
   * @param embedder The Embedder protocol buffer message
   * @param convention The naming convention to use for field names
   * @return A Map containing Embedder fields with REST-friendly names
   */
  public static Map<String, Object> toJsonMap(Embedder embedder, NamingConvention convention) {
    Map<String, Object> map = new HashMap<>();
    map.put(formatName("embedderId", convention), Uuids.bytesToHex(embedder.getEmbedderId().toByteArray()));
    map.put(formatName("displayName", convention), embedder.getDisplayName());
    map.put(formatName("description", convention), embedder.getDescription());
    map.put(formatName("providerType", convention), embedder.getProviderType().name());
    map.put(formatName("endpointUrl", convention), embedder.getEndpointUrl());
    map.put(formatName("apiPath", convention), embedder.getApiPath());
    map.put(formatName("modelIdentifier", convention), embedder.getModelIdentifier());
    map.put(formatName("dimensionality", convention), embedder.getDimensionality());
    
    if (embedder.hasMaxSequenceLength()) {
      map.put(formatName("maxSequenceLength", convention), embedder.getMaxSequenceLength());
    }
    
    map.put(formatName("supportedModalities", convention), embedder.getSupportedModalitiesList().stream()
        .map(Modality::name)
        .collect(Collectors.toList()));
    map.put(formatName("labels", convention), embedder.getLabelsMap());
    map.put(formatName("version", convention), embedder.getVersion());
    map.put(formatName("monitoringEndpoint", convention), embedder.getMonitoringEndpoint());
    map.put(formatName("ownerId", convention), Uuids.bytesToHex(embedder.getOwnerId().toByteArray()));
    map.put(formatName("createdAt", convention), formatTimestamp(embedder.getCreatedAt()));
    map.put(formatName("updatedAt", convention), formatTimestamp(embedder.getUpdatedAt()));
    map.put(formatName("createdById", convention), Uuids.bytesToHex(embedder.getCreatedById().toByteArray()));
    map.put(formatName("updatedById", convention), Uuids.bytesToHex(embedder.getUpdatedById().toByteArray()));
    return map;
  }

  /**
   * Formats a field name according to the specified naming convention.
   *
   * @param camelCaseName The field name in camelCase
   * @param convention The target naming convention
   * @return The formatted field name according to the convention
   */
  private static String formatName(String camelCaseName, NamingConvention convention) {
    if (convention == NamingConvention.CAMEL_CASE) {
      return camelCaseName;
    } else if (convention == NamingConvention.SNAKE_CASE) {
      // Convert from camelCase to snake_case
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < camelCaseName.length(); i++) {
        char c = camelCaseName.charAt(i);
        if (Character.isUpperCase(c)) {
          result.append('_');
          result.append(Character.toLowerCase(c));
        } else {
          result.append(c);
        }
      }
      return result.toString();
    }
    return camelCaseName; // Default to camelCase
  }

  /**
   * Formats a Protobuf Timestamp to milliseconds since epoch.
   *
   * @param timestamp The Protobuf Timestamp
   * @return Milliseconds since epoch (Long)
   */
  private static Long formatTimestamp(Timestamp timestamp) {
    return Timestamps.toMillis(timestamp);
  }

  /**
   * Formats a ByteString containing a UUID to a hex string.
   *
   * @param uuidBytes The UUID as a ByteString
   * @return The UUID as a hex string
   */
  private static String formatUuid(ByteString uuidBytes) {
    return Uuids.bytesToHex(uuidBytes.toByteArray());
  }
}